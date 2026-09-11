package com.github.pvtitov.noserver

import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonUtilsTest {

    @Serializable
    data class Fixture(val name: String, val count: Int)

    @Test
    fun `toJson then fromJson round-trips the original value`() {
        val original = Fixture("wish", 3)

        val json = JsonUtils.toJson(original)
        val result = json?.let { JsonUtils.fromJson<Fixture>(it) }

        assertEquals(original, result)
    }

    @Test
    fun `fromJson returns null for malformed json`() {
        val result = JsonUtils.fromJson<Fixture>("not valid json")

        assertNull(result)
    }
}
