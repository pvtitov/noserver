package com.github.pvtitov.noserver

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
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

@Serializable
private data class ManualFixture(val value: String)

/**
 * Exercises ManualRepository against a mocked [ManualStorageManager], standing in for the real
 * SAF file-picker flow (which needs a device/emulator). [ManualStorageManager.runSave]/[runLoad]
 * are stubbed to invoke their callback synchronously with a controlled [Uri]; [readText]/
 * [writeText] are stubbed directly since the real ContentResolver I/O is exactly the seam
 * ManualStorageManager abstracts away.
 */
class ManualRepositoryTest {

    private val uri = mockk<Uri>()

    @Before
    fun setUp() {
        mockkObject(ManualStorageManager)
    }

    @After
    fun tearDown() {
        unmockkObject(ManualStorageManager)
    }

    private fun repository(suggestedFileName: String = "fixture.json") = ManualRepository(suggestedFileName)

    @Test
    fun `download returns the deserialized object when a file is picked`() = runTest {
        every { ManualStorageManager.runLoad(any()) } answers {
            firstArg<(Uri?) -> Unit>().invoke(uri)
        }
        every { ManualStorageManager.readText(uri) } returns """{"value":"hello"}"""

        val result = repository().download<ManualFixture>()

        assertEquals(ManualFixture("hello"), result)
    }

    @Test
    fun `download returns null when the picker is cancelled`() = runTest {
        every { ManualStorageManager.runLoad(any()) } answers {
            firstArg<(Uri?) -> Unit>().invoke(null)
        }

        val result = repository().download<ManualFixture>()

        assertNull(result)
    }

    @Test
    fun `download returns null when the picked file can't be read`() = runTest {
        every { ManualStorageManager.runLoad(any()) } answers {
            firstArg<(Uri?) -> Unit>().invoke(uri)
        }
        every { ManualStorageManager.readText(uri) } returns null

        val result = repository().download<ManualFixture>()

        assertNull(result)
    }

    @Test
    fun `upload returns true and writes the serialized data when a destination is picked`() = runTest {
        every { ManualStorageManager.runSave(any(), any()) } answers {
            secondArg<(Uri?) -> Unit>().invoke(uri)
        }
        every { ManualStorageManager.writeText(uri, any()) } returns true

        val result = repository().upload(ManualFixture("hello"))

        assertTrue(result)
        verify { ManualStorageManager.writeText(uri, JsonUtils.toJson(ManualFixture("hello"))!!) }
    }

    @Test
    fun `upload returns false when the picker is cancelled`() = runTest {
        every { ManualStorageManager.runSave(any(), any()) } answers {
            secondArg<(Uri?) -> Unit>().invoke(null)
        }

        val result = repository().upload(ManualFixture("hello"))

        assertFalse(result)
    }

    @Test
    fun `upload returns false when writing fails`() = runTest {
        every { ManualStorageManager.runSave(any(), any()) } answers {
            secondArg<(Uri?) -> Unit>().invoke(uri)
        }
        every { ManualStorageManager.writeText(uri, any()) } returns false

        val result = repository().upload(ManualFixture("hello"))

        assertFalse(result)
    }

    @Test
    fun `upload passes the repository's suggested file name through to runSave`() = runTest {
        every { ManualStorageManager.runSave(any(), any()) } answers {
            secondArg<(Uri?) -> Unit>().invoke(null)
        }

        repository("custom.json").upload(ManualFixture("hello"))

        verify { ManualStorageManager.runSave("custom.json", any()) }
    }
}
