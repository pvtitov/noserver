package com.github.pvtitov.noserver

import com.google.api.client.http.AbstractInputStreamContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.About
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.services.drive.model.FileList
import com.google.api.services.drive.model.Permission
import com.google.api.services.drive.model.User as DriveUser
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream

@Serializable
private data class Fixture(val value: String)

/**
 * Exercises GoogleDriveRepository against a mocked [Drive] client, standing in for the real
 * Drive API + auth flow (which needs a device/emulator and a signed-in Google account).
 * [GoogleDriveAuthorizationManager.runWithGoogleDrive] is stubbed to invoke its action
 * synchronously with [drive], as it does in production once the user is already authenticated.
 */
class GoogleDriveRepositoryTest {

    private val drive = mockk<Drive>()

    @Before
    fun setUp() {
        mockkObject(GoogleDriveAuthorizationManager)
        every { GoogleDriveAuthorizationManager.runWithGoogleDrive(any()) } answers {
            firstArg<(Drive) -> Unit>().invoke(drive)
        }
    }

    @After
    fun tearDown() {
        unmockkObject(GoogleDriveAuthorizationManager)
    }

    private fun repository(initial: Fixture = Fixture("default")) =
        GoogleDriveRepository("wishlist.awl", initial)

    /**
     * Stubs `drive.files().list()....execute().files` for as many calls as [resultsPerCall] has
     * entries; MockK repeats the last entry for any calls beyond that (used by the initialize()
     * scenario, where the same query is issued before and after the file is created).
     */
    private fun stubFilesList(vararg resultsPerCall: List<DriveFile>): Drive.Files {
        val filesApi = mockk<Drive.Files>()
        val listRequest = mockk<Drive.Files.List>()
        every { drive.files() } returns filesApi
        every { filesApi.list() } returns listRequest
        every { listRequest.setQ(any()) } returns listRequest
        every { listRequest.setSpaces(any()) } returns listRequest
        every { listRequest.setFields(any()) } returns listRequest
        val fileLists = resultsPerCall.map { files -> mockk<FileList>().also { every { it.files } returns files } }
        every { listRequest.execute() } returnsMany fileLists
        return filesApi
    }

    private fun stubAbout(email: String) {
        val aboutApi = mockk<Drive.About>()
        val aboutGet = mockk<Drive.About.Get>()
        val about = mockk<About>()
        val user = mockk<DriveUser>()
        every { drive.about() } returns aboutApi
        every { aboutApi.get() } returns aboutGet
        every { aboutGet.setFields(any()) } returns aboutGet
        every { aboutGet.execute() } returns about
        every { about.user } returns user
        every { user.emailAddress } returns email
    }

    @Test
    fun `download returns null when no matching file is found`() = runTest {
        stubFilesList(emptyList())

        val result = repository().download<Fixture>()

        assertNull(result)
    }

    @Test
    fun `download returns the deserialized object when a matching file is found`() = runTest {
        val file = mockk<DriveFile>()
        every { file.id } returns "file-1"
        val filesApi = stubFilesList(listOf(file))
        val getRequest = mockk<Drive.Files.Get>()
        every { filesApi.get("file-1") } returns getRequest
        val json = """{"value":"hello"}"""
        every { getRequest.executeMediaAndDownloadTo(any()) } answers {
            firstArg<OutputStream>().write(json.toByteArray())
        }

        val result = repository().download<Fixture>()

        assertEquals(Fixture("hello"), result)
    }

    @Test
    fun `upload returns false when the user's file does not exist yet`() = runTest {
        stubFilesList(emptyList())

        val result = repository().upload(Fixture("hello"))

        assertFalse(result)
    }

    @Test
    fun `upload returns true and sends the serialized data when the file exists`() = runTest {
        val file = mockk<DriveFile>()
        every { file.id } returns "file-1"
        val filesApi = stubFilesList(listOf(file))
        val updateRequest = mockk<Drive.Files.Update>(relaxed = true)
        val mediaContentSlot = slot<AbstractInputStreamContent>()
        every { filesApi.update(eq("file-1"), isNull(), capture(mediaContentSlot)) } returns updateRequest

        val result = repository().upload(Fixture("hello"))

        assertTrue(result)
        val uploadedBytes = ByteArrayOutputStream().also { mediaContentSlot.captured.writeTo(it) }
        assertEquals(Fixture("hello"), JsonUtils.fromJson<Fixture>(uploadedBytes.toString()))
    }

    @Test
    fun `addFriend returns false when the file does not exist`() = runTest {
        stubFilesList(emptyList())

        val result = repository().addFriend("friend@example.com")

        assertFalse(result)
    }

    @Test
    fun `addFriend grants reader access and returns true when the file exists`() = runTest {
        val file = mockk<DriveFile>()
        every { file.id } returns "file-1"
        stubFilesList(listOf(file))
        val permissionsApi = mockk<Drive.Permissions>()
        val createRequest = mockk<Drive.Permissions.Create>(relaxed = true)
        val permissionSlot = slot<Permission>()
        every { drive.permissions() } returns permissionsApi
        every { permissionsApi.create(eq("file-1"), capture(permissionSlot)) } returns createRequest

        val result = repository().addFriend("friend@example.com")

        assertTrue(result)
        assertEquals("user", permissionSlot.captured.type)
        assertEquals("reader", permissionSlot.captured.role)
        assertEquals("friend@example.com", permissionSlot.captured.emailAddress)
    }

    @Test
    fun `getMyLogin returns the authenticated user's email`() = runTest {
        stubAbout("me@example.com")

        val result = repository().getMyLogin()

        assertEquals("me@example.com", result)
    }

    @Test
    fun `initialize is a no-op when the file already exists`() = runTest {
        val file = mockk<DriveFile>()
        every { file.id } returns "file-1"
        val filesApi = stubFilesList(listOf(file))

        repository().initialize<Fixture>()

        verify(exactly = 0) { filesApi.create(any()) }
    }

    @Test
    fun `initialize creates the file and uploads initialData when the file doesn't exist`() = runTest {
        val newFile = mockk<DriveFile>()
        every { newFile.id } returns "new-file"
        val filesApi = stubFilesList(emptyList(), listOf(newFile))
        stubAbout("me@example.com")
        val createRequest = mockk<Drive.Files.Create>(relaxed = true)
        every { filesApi.create(any()) } returns createRequest
        val updateRequest = mockk<Drive.Files.Update>(relaxed = true)
        every { filesApi.update(any(), isNull(), any()) } returns updateRequest

        repository(Fixture("initial")).initialize<Fixture>()

        verify { filesApi.create(any()) }
        verify { filesApi.update(eq("new-file"), isNull(), any()) }
    }
}
