package com.github.pvtitov.noserver.sample

import kotlinx.serialization.Serializable

/** [updatedAt] is epoch millis of the last upload, so a reader can tell that a newer version arrived. */
@Serializable
data class Note(val text: String, val updatedAt: Long)
