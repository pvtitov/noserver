package com.github.pvtitov.noserver

import com.google.api.client.http.AbstractInputStreamContent
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.About
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.services.drive.model.FileList
import com.google.api.services.drive.model.Permission
import com.google.api.services.drive.model.PermissionList
import com.google.api.services.drive.model.User as DriveUser
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
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
 * Exceptions thrown by an action therefore reach the test directly, without the manager's
 * wrapping into [NoServerException.Failed].
 */
class GoogleDriveRepositoryTest {

    private val drive = mockk<Drive>()
    private val filesApi = mockk<Drive.Files>()
    private val listQueries = mutableListOf<String>()
    private val listOrderBys = mutableListOf<String>()

    @Before
    fun setUp() {
        mockkObject(GoogleDriveAuthorizationManager, SharedFileReader)
        every { GoogleDriveAuthorizationManager.runWithGoogleDrive(any(), any()) } answers {
            firstArg<(Drive) -> Unit>().invoke(drive)
        }
        every { drive.files() } returns filesApi
    }

    @After
    fun tearDown() {
        unmockkObject(GoogleDriveAuthorizationManager, SharedFileReader)
    }

    private fun repository(initial: Fixture = Fixture("default")) =
        GoogleDriveRepository("wishlist.awl", initial)

    private fun driveFile(id: String) = DriveFile().setId(id)

    private fun httpError(code: Int) = HttpResponseException.Builder(code, "error", HttpHeaders()).build()

    /** What the marked-file queries currently return; tests change these to simulate Drive changes. */
    private var activeFiles = emptyList<DriveFile>()
    private var trashedFiles = emptyList<DriveFile>()

    private fun stubFilesList(active: List<DriveFile>, trashed: List<DriveFile> = emptyList()) {
        activeFiles = active
        trashedFiles = trashed
        val listRequest = mockk<Drive.Files.List>()
        every { filesApi.list() } returns listRequest
        every { listRequest.setQ(capture(listQueries)) } returns listRequest
        every { listRequest.setOrderBy(capture(listOrderBys)) } returns listRequest
        every { listRequest.setPageSize(any()) } returns listRequest
        every { listRequest.setSpaces(any()) } returns listRequest
        every { listRequest.setFields(any()) } returns listRequest
        every { listRequest.execute() } answers {
            FileList().setFiles(if (listQueries.last().endsWith("trashed=true")) trashedFiles else activeFiles)
        }
    }

    private fun stubFilesGet(fileId: String, json: String): Drive.Files.Get {
        val getRequest = mockk<Drive.Files.Get>()
        every { filesApi.get(fileId) } returns getRequest
        every { getRequest.executeMediaAndDownloadTo(any()) } answers {
            firstArg<OutputStream>().write(json.toByteArray())
        }
        return getRequest
    }

    private fun stubFilesCreate(newId: String): Pair<io.mockk.CapturingSlot<DriveFile>, io.mockk.CapturingSlot<AbstractInputStreamContent>> {
        val metadata = slot<DriveFile>()
        val content = slot<AbstractInputStreamContent>()
        val createRequest = mockk<Drive.Files.Create>()
        every { filesApi.create(capture(metadata), capture(content)) } returns createRequest
        every { createRequest.setFields(any()) } returns createRequest
        every { createRequest.execute() } returns driveFile(newId)
        return metadata to content
    }

    private fun stubFilesUpdate(fileId: String): io.mockk.CapturingSlot<AbstractInputStreamContent> {
        val content = slot<AbstractInputStreamContent>()
        val updateRequest = mockk<Drive.Files.Update>()
        every { filesApi.update(eq(fileId), isNull(), capture(content)) } returns updateRequest
        every { updateRequest.execute() } returns DriveFile()
        return content
    }

    private val permissionsApi = mockk<Drive.Permissions>()
    /** "fileId/permissionId" of every permission the code deleted. */
    private val deletedPermissions = mutableListOf<String>()

    /** Stubs the permissions of [fileId] with [existing] ones; returns the slot for created ones. */
    private fun stubPermissions(fileId: String, vararg existing: Permission): io.mockk.CapturingSlot<Permission> {
        every { drive.permissions() } returns permissionsApi
        val listRequest = mockk<Drive.Permissions.List>()
        every { permissionsApi.list(fileId) } returns listRequest
        every { listRequest.setFields(any()) } returns listRequest
        every { listRequest.execute() } returns PermissionList().setPermissions(existing.toList())
        val created = slot<Permission>()
        val createRequest = mockk<Drive.Permissions.Create>()
        every { permissionsApi.create(eq(fileId), capture(created)) } returns createRequest
        every { createRequest.execute() } returns Permission()
        every { permissionsApi.delete(fileId, any()) } answers {
            deletedPermissions += "$fileId/${secondArg<String>()}"
            mockk<Drive.Permissions.Delete>().also { every { it.execute() } returns null }
        }
        return created
    }

    private fun stubAbout(email: String) {
        val aboutApi = mockk<Drive.About>()
        val aboutGet = mockk<Drive.About.Get>()
        every { drive.about() } returns aboutApi
        every { aboutApi.get() } returns aboutGet
        every { aboutGet.setFields(any()) } returns aboutGet
        every { aboutGet.execute() } returns About().setUser(DriveUser().setEmailAddress(email))
    }

    private fun AbstractInputStreamContent.text() = ByteArrayOutputStream().also { writeTo(it) }.toString()

    // login

    @Test
    fun `login creates the marked file from initialData, makes it readable by link and returns the email`() = runTest {
        stubFilesList(emptyList())
        val (metadata, content) = stubFilesCreate("new-file")
        val createdPermission = stubPermissions("new-file")
        stubAbout("me@example.com")

        val email = repository(Fixture("initial")).login<Fixture>()

        assertEquals("me@example.com", email)
        assertEquals("wishlist.awl", metadata.captured.name)
        assertEquals(mapOf("noserver" to "wishlist.awl"), metadata.captured.appProperties)
        assertEquals(Fixture("initial"), JsonUtils.fromJson<Fixture>(content.captured.text()))
        assertEquals("anyone", createdPermission.captured.type)
        assertEquals("reader", createdPermission.captured.role)
        assertEquals(false, createdPermission.captured.allowFileDiscovery)
    }

    @Test
    fun `login leaves an existing file that is already readable by link untouched`() = runTest {
        stubFilesList(listOf(driveFile("file-1")))
        val createdPermission = stubPermissions("file-1", Permission().setType("anyone"))
        stubAbout("me@example.com")

        repository().login<Fixture>()

        verify(exactly = 0) { filesApi.create(any(), any()) }
        assertFalse(createdPermission.isCaptured)
    }

    @Test
    fun `login makes an existing file readable by link again if the user turned sharing off`() = runTest {
        stubFilesList(listOf(driveFile("file-1")))
        val createdPermission = stubPermissions("file-1", Permission().setType("user"))
        stubAbout("me@example.com")

        repository().login<Fixture>()

        assertEquals("anyone", createdPermission.captured.type)
    }

    @Test
    fun `login stops link sharing of trashed files and starts a new file`() = runTest {
        stubFilesList(active = emptyList(), trashed = listOf(driveFile("trashed-file")))
        stubPermissions(
            "trashed-file",
            Permission().setId("p-anyone").setType("anyone"),
            Permission().setId("p-owner").setType("user")
        )
        stubFilesCreate("new-file")
        stubPermissions("new-file")
        stubAbout("me@example.com")

        repository().login<Fixture>()

        assertEquals(listOf("trashed-file/p-anyone"), deletedPermissions)
        verify { filesApi.create(any(), any()) }
    }

    @Test
    fun `login throws the sign-in failure instead of hanging`() = runTest {
        val failure = NoServerException.NoGoogleAccount()
        every { GoogleDriveAuthorizationManager.runWithGoogleDrive(any(), any()) } answers {
            secondArg<(NoServerException) -> Unit>().invoke(failure)
        }

        val thrown = runCatching { repository().login<Fixture>() }.exceptionOrNull()

        assertTrue(thrown is NoServerException.NoGoogleAccount)
    }

    // own file lookup

    @Test
    fun `own file is looked up by the app marker, excluding trashed files, youngest first`() = runTest {
        stubFilesList(listOf(driveFile("file-1")))
        stubFilesGet("file-1", """{"value":"hello"}""")

        repository().download<Fixture>()

        assertEquals(
            "appProperties has { key='noserver' and value='wishlist.awl' } and 'me' in owners and trashed=false",
            listQueries.single()
        )
        assertEquals(listOf("createdTime desc"), listOrderBys)
    }

    @Test
    fun `a file trashed or deleted mid-session is treated as gone right away`() = runTest {
        stubFilesList(listOf(driveFile("file-1")))
        stubFilesGet("file-1", """{"value":"hello"}""")
        val repository = repository()
        repository.download<Fixture>()

        activeFiles = emptyList()

        assertNull(repository.download<Fixture>())
        assertFalse(repository.upload(Fixture("lost")))
        verify(exactly = 0) { filesApi.update(any(), any(), any<AbstractInputStreamContent>()) }
    }

    @Test
    fun `quotes and backslashes are escaped in query values`() {
        assertEquals("""o\'brien\\x""", GoogleDriveRepository.escapeQueryValue("""o'brien\x"""))
    }

    // download / upload of the own file

    @Test
    fun `download returns null when the user has no file yet`() = runTest {
        stubFilesList(emptyList())

        assertNull(repository().download<Fixture>())
    }

    @Test
    fun `download returns the deserialized own file`() = runTest {
        stubFilesList(listOf(driveFile("file-1")))
        stubFilesGet("file-1", """{"value":"hello"}""")

        assertEquals(Fixture("hello"), repository().download<Fixture>())
    }

    @Test
    fun `upload returns false when the user has no file yet`() = runTest {
        stubFilesList(emptyList())

        assertFalse(repository().upload(Fixture("hello")))
    }

    @Test
    fun `upload sends the serialized data and returns true`() = runTest {
        stubFilesList(listOf(driveFile("file-1")))
        val uploaded = stubFilesUpdate("file-1")

        val result = repository().upload(Fixture("hello"))

        assertTrue(result)
        assertEquals(Fixture("hello"), JsonUtils.fromJson<Fixture>(uploaded.captured.text()))
    }

    // sharing

    @Test
    fun `getShareToken returns the own file ID`() = runTest {
        stubFilesList(listOf(driveFile("file-1")))

        assertEquals("file-1", repository().getShareToken())
    }

    @Test
    fun `getShareToken fails when there is no file yet`() = runTest {
        stubFilesList(emptyList())

        val thrown = runCatching { repository().getShareToken() }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
    }

    @Test
    fun `download by share token signs in, then reads that file with the API-key reader`() = runTest {
        every { SharedFileReader.read("friend-file_1") } returns """{"value":"friend"}"""

        val result = repository().download<Fixture>("friend-file_1")

        assertEquals(Fixture("friend"), result)
        verify { GoogleDriveAuthorizationManager.runWithGoogleDrive(any(), any()) }
        verify(exactly = 0) { filesApi.list() }
        verify(exactly = 0) { filesApi.get(any()) }
    }

    @Test
    fun `download by share token maps a 404 to NoAccess`() = runTest {
        every { SharedFileReader.read("gone") } throws httpError(404)

        val thrown = runCatching { repository().download<Fixture>("gone") }.exceptionOrNull()

        assertTrue(thrown is NoServerException.NoAccess)
    }

    @Test
    fun `download by share token doesn't mistake a 403 for NoAccess`() = runTest {
        every { SharedFileReader.read("rate-limited") } throws httpError(403)

        val thrown = runCatching { repository().download<Fixture>("rate-limited") }.exceptionOrNull()

        assertTrue(thrown is HttpResponseException)
    }

    @Test
    fun `a malformed share token fails with NoAccess without calling Drive`() = runTest {
        for (token in listOf("", "  ", "https://drive.google.com/file/d/x", "id'or'1")) {
            val thrown = runCatching { repository().download<Fixture>(token) }.exceptionOrNull()
            assertTrue("token '$token'", thrown is NoServerException.NoAccess)
        }
        verify(exactly = 0) { GoogleDriveAuthorizationManager.runWithGoogleDrive(any(), any()) }
    }

    // cancellation

    @Test
    fun `cancelling the caller while sign-in is pending drops the pending action`() = runTest {
        val pendingAction = slot<(Drive) -> Unit>()
        // Sign-in never finishes: the action stays pending, as while the account picker is shown.
        every { GoogleDriveAuthorizationManager.runWithGoogleDrive(capture(pendingAction), any()) } answers { }
        every { GoogleDriveAuthorizationManager.discard(any()) } answers { }
        val call = async(start = CoroutineStart.UNDISPATCHED) { repository().download<Fixture>() }
        while (!pendingAction.isCaptured) Thread.sleep(10)

        call.cancel()
        call.join()

        assertTrue(call.isCancelled)
        verify { GoogleDriveAuthorizationManager.discard(pendingAction.captured) }
    }

    @Test
    fun `the signing certificate hash is uppercase hex without separators`() {
        // SHA-1 of the empty input.
        assertEquals("DA39A3EE5E6B4B0D3255BFEF95601890AFD80709", SharedFileReader.sha1Hex(ByteArray(0)))
    }
}
