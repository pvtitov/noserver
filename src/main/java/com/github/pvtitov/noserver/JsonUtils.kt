package com.github.pvtitov.noserver

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@PublishedApi
internal object JsonUtils {

    inline fun <reified T> fromJson(json: String): T? {
        return try {
            Json.decodeFromString<T>(json)
        } catch (e: Exception) {
            null
        }
    }

    inline fun <reified T> toJson(instance: T): String? {
        return try {
            Json.encodeToString(instance)
        } catch (e: Exception) {
            null
        }
    }
}