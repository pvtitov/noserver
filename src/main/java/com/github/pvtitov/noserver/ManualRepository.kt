package com.github.pvtitov.noserver

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Manual, Google-Drive-free counterpart to [GoogleDriveRepository]: exports/imports the same
 * kind of JSON data to/from any location the user picks via Android's Storage Access Framework,
 * instead of a Drive-managed per-user file. There is no persisted "my file" in this mode, so
 * there's no [GoogleDriveRepository.initialize] equivalent, [download] always means "whatever
 * file the user picks" rather than a specific user's file, and there's no
 * [GoogleDriveRepository.getMyLogin] or [GoogleDriveRepository.addFriend] equivalent, since
 * there's no concept of another user's file. A cancelled picker resolves [download]/[upload] to
 * null/false rather than hanging.
 */
class ManualRepository(val suggestedFileName: String) {

    val coroutineContext =
        SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher() + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Error occurred in coroutine", e)
        }

    /**
     * Shows the system "open document" picker and decodes whatever JSON file the user picks.
     * Returns null if the user cancels, or if reading/decoding fails.
     */
    suspend inline fun <reified T> download(): T? {
        Log.d(TAG, "download() called")
        return withContext(coroutineContext) {
            suspendCoroutine { continuation ->
                ManualStorageManager.runLoad { uri: Uri? ->
                    val data = uri
                        ?.let { ManualStorageManager.readText(it) }
                        ?.let { JsonUtils.fromJson<T>(it) }
                    continuation.resume(data)
                }
            }
        }
    }

    /**
     * Shows the system "create document" picker (pre-filled with [suggestedFileName]) and writes
     * [data] as JSON to wherever the user picks. Returns false if the user cancels or the write
     * fails.
     */
    suspend inline fun <reified T> upload(data: T): Boolean {
        // data itself is not logged: it's the user's file content, not diagnostic data.
        Log.d(TAG, "upload() called")
        return withContext(coroutineContext) {
            suspendCoroutine { continuation ->
                ManualStorageManager.runSave(suggestedFileName) { uri: Uri? ->
                    val success = uri != null &&
                            JsonUtils.toJson<T>(data)?.let { ManualStorageManager.writeText(uri, it) } == true
                    continuation.resume(success)
                }
            }
        }
    }

    companion object {
        const val TAG = "ManualRepository"
    }
}
