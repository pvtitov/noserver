package com.github.pvtitov.noserver

import android.util.Log
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.Permission
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class GoogleDriveRepository<T>(
    val fileName: String,
    val initialData: T
) {
    val coroutineContext =
        SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher() + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Error occurred in coroutine", e)
        }

    /**
     * Check if file exists and create one otherwise. Call before any other action requiring an existing file like
     * [download], [upload], [addFriend].
     */
    suspend inline fun <reified T> initialize() {
        if (initialData !is T) return

        if (!isFileExists()) {
            createFile()
            upload<T>(initialData)
        }
    }

    suspend fun getMyLogin(): String? {
        Log.d(TAG, "getMyLogin() called")
        return withContext(coroutineContext) {
            suspendCoroutine { continuation ->
                GoogleDriveAuthorizationManager.runWithGoogleDrive { drive: Drive ->
                    val login = drive.about().get()
                        .setFields("user(emailAddress, me)")
                        .execute()
                        .user.emailAddress
                    continuation.resume(login)
                }
            }
        }
    }

    /**
     * Call without parameter to load currently authenticated user data or provider user's e-mail as [login]
     */
    suspend inline fun <reified T> download(login: String = AUTHENTICATED_USER): T? {
        return withContext(coroutineContext) {
            suspendCoroutine { continuation ->
                GoogleDriveAuthorizationManager.runWithGoogleDrive { drive: Drive ->

                    val searchQuery = if (login == AUTHENTICATED_USER) {
                        "name='$fileName' and 'me' in owners"
                    } else {
                        "name='$fileName' and '$login' in owners"
                    }
                    val file = drive.files().list()
                        .setQ(searchQuery)
                        .setSpaces("drive")
                        .setFields("files(id, name, owners, ownedByMe, permissions)")
                        .execute()
                        .files
                        .firstOrNull()

                    Log.d(TAG, "download(): file found = ${file != null}")

                    val data = if (file != null) {
                        val outputStream = ByteArrayOutputStream()

                        drive.files().get(file.id).executeMediaAndDownloadTo(outputStream)

                        // Not logged: this is the user's file content, not diagnostic data.
                        val rawJsonString = outputStream.toString()

                        JsonUtils.fromJson<T>(rawJsonString)
                    } else {
                        null
                    }

                    continuation.resume(data)
                }
            }
        }
    }

    suspend inline fun <reified T> upload(
        data: T
    ): Boolean {
        // data itself is not logged: it's the user's file content, not diagnostic data.
        Log.d(TAG, "upload() called")
        return withContext(coroutineContext) {
            suspendCoroutine { continuation ->
                GoogleDriveAuthorizationManager.runWithGoogleDrive { drive: Drive ->
                    val file = getMyFile(drive)

                    if (file != null) {
                        val jsonString = JsonUtils.toJson<T>(data)
                        val mediaContent = ByteArrayContent.fromString("application/json", jsonString)
                        drive.files().update(file.id, null, mediaContent).execute()
                        continuation.resume(true)
                    } else {
                        Log.e(TAG, "File '$fileName' not found for upload.")
                        continuation.resume(false)
                    }
                }
            }
        }
    }

    suspend fun addFriend(login: String): Boolean {
        // login (an e-mail address) is not logged: not diagnostic data.
        Log.d(TAG, "addFriend() called")
        return withContext(coroutineContext) {
            suspendCoroutine { continuation ->
                GoogleDriveAuthorizationManager.runWithGoogleDrive { drive: Drive ->
                    val permission = Permission().apply {
                        type = "user"
                        role = "reader"
                        emailAddress = login
                    }

                    val file = getMyFile(drive) ?: run {
                        continuation.resume(false)
                        return@runWithGoogleDrive
                    }

                    drive.permissions().create(file.id, permission).execute()

                    Log.d(TAG, "Successfully added a reader to $fileName")
                    continuation.resume(true)
                }
            }
        }
    }

    suspend fun logout() {
        withContext(coroutineContext) {
            GoogleDriveAuthorizationManager.logout()
        }
    }

    @Deprecated(
        "Don't use directly from outside this class. " +
                "It is only public to let reified parameter for this or dependent functions"
    )
    suspend fun isFileExists(): Boolean {
        Log.d(TAG, "isFileExists() called")
        return withContext(coroutineContext) {
            suspendCoroutine { continuation ->
                GoogleDriveAuthorizationManager.runWithGoogleDrive { drive: Drive ->
                    val file = getMyFile(drive)
                    Log.d(TAG, "isFileExists() = ${file != null}")
                    continuation.resume(file != null)
                }
            }
        }
    }

    @Deprecated(
        "Don't use directly from outside this class. " +
                "It is only public to let reified parameter for this or dependent functions"
    )
    fun getMyFile(drive: Drive) = drive.files().list()
        .setQ("name='$fileName' and 'me' in owners")
        .setSpaces("drive")
        .setFields("files(id, name)")
        .execute()
        .files
        .firstOrNull()

    @Deprecated(
        "Don't use directly from outside this class. " +
                "It is only public to let reified parameter for this or dependent functions"
    )
    suspend fun createFile() {
        Log.d(TAG, "createFile() called")
        return withContext(coroutineContext) {
            suspendCoroutine { continuation ->
                GoogleDriveAuthorizationManager.runWithGoogleDrive { drive: Drive ->
                    val file = File().apply {
                        name = fileName
                    }
                    drive.files().create(file)
                        .setFields("id")
                        .execute()

                    continuation.resume(Unit)
                }
            }
        }
    }

    companion object {
        const val TAG = "GoogleDriveRepository"
        const val AUTHENTICATED_USER = ""
    }
}