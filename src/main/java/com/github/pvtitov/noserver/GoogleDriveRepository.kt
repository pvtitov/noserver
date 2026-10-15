package com.github.pvtitov.noserver

import android.util.Log
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.HttpResponseException
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.Permission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * One JSON file per user in their own Google Drive, readable by anyone the user gives its share
 * token to.
 *
 * Call [login] first. It signs the user in, creates the file from [initialData] if there is none
 * yet, and makes it readable by link. Then use [download]/[upload] for the user's own data and
 * [getShareToken] to get the token the app passes on to friends (storing and delivering tokens is
 * the app's job). A friend's data is read with `download(shareToken)`.
 *
 * Anyone holding a token can read the file, and tokens can't be revoked. A token stops working
 * only when its owner deletes or trashes the file in Drive: the library then treats the file as
 * gone, and [login] creates a new one with a new token.
 */
class GoogleDriveRepository<T>(
    val fileName: String,
    val initialData: T
) {
    // Drive calls block, and one at a time per repository keeps them from racing on the same file.
    // The user's file ID is deliberately not cached: a file the user trashed stays readable and
    // writable by ID, so every operation looks it up (trashed excluded) instead of trusting an old ID.
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    /**
     * Signs the user in, creates their file from [initialData] if it doesn't exist yet (or the user
     * deleted or trashed it), and makes sure it's readable by link. Returns the signed-in account's e-mail address. Call before any
     * other operation; they also sign in on demand, but need the file this creates.
     */
    suspend inline fun <reified T> login(): String {
        require(initialData is T) { "login<T>() must be called with initialData's type" }
        val initialJson = checkNotNull(JsonUtils.toJson<T>(initialData as T)) { "initialData can't be serialized" }
        return loginWithInitialJson(initialJson)
    }

    /** The user's own data, or null if there is no file yet (see [login]) or it doesn't decode as [T]. */
    suspend inline fun <reified T> download(): T? =
        downloadMyJson()?.let { JsonUtils.fromJson<T>(it) }

    /**
     * Another user's data, read with the share token they passed on from their [getShareToken].
     * Returns null if the file doesn't decode as [T].
     *
     * @throws NoServerException.NoAccess if the token is malformed or its file no longer exists.
     */
    suspend inline fun <reified T> download(shareToken: String): T? =
        JsonUtils.fromJson<T>(downloadSharedJson(shareToken))

    /** Overwrites the user's file. Returns false if there is no file yet (see [login]). */
    suspend inline fun <reified T> upload(data: T): Boolean {
        // data itself is not logged: it's the user's file content, not diagnostic data.
        Log.d(TAG, "upload() called")
        return uploadJson(checkNotNull(JsonUtils.toJson<T>(data)) { "data can't be serialized" })
    }

    /**
     * The token that lets other users read this user's file with `download(shareToken)`. It's
     * opaque: store and pass it on as is. It stays the same until the user deletes or trashes the
     * file in Drive.
     *
     * @throws NoServerException.Failed if there is no file yet; call [login] first.
     */
    suspend fun getShareToken(): String {
        Log.d(TAG, "getShareToken() called")
        return runOnDrive { drive ->
            getMyFileId(drive) ?: throw IllegalStateException("There is no file to share yet. Call login() first.")
        }
    }

    suspend fun logout() {
        withContext(dispatcher) {
            GoogleDriveAuthorizationManager.logout()
        }
    }

    @PublishedApi
    internal suspend fun loginWithInitialJson(initialJson: String): String {
        Log.d(TAG, "login() called")
        return runOnDrive { drive ->
            stopSharingTrashedFiles(drive)
            val fileId = getMyFileId(drive) ?: createMyFile(drive, initialJson)
            ensureReadableByLink(drive, fileId)
            drive.about().get()
                .setFields("user(emailAddress)")
                .execute()
                .user.emailAddress
        }
    }

    @PublishedApi
    internal suspend fun downloadMyJson(): String? {
        Log.d(TAG, "download() called")
        return runOnDrive { drive ->
            getMyFileId(drive)?.let { fileId -> readFile(drive, fileId) }
                .also { Log.d(TAG, "download(): file found = ${it != null}") }
        }
    }

    @PublishedApi
    internal suspend fun downloadSharedJson(shareToken: String): String {
        Log.d(TAG, "download(shareToken) called")
        if (!SHARE_TOKEN_FORMAT.matches(shareToken)) throw NoServerException.NoAccess()
        // Readers must be signed in, though the read itself uses SharedFileReader, not their credential.
        return runOnDrive { _ ->
            try {
                SharedFileReader.read(shareToken)
            } catch (e: HttpResponseException) {
                // Only 404: it's what Drive returns for a missing or inaccessible file, while a
                // 403 means something else (rate limit, API key misconfiguration) that the
                // consumer shouldn't mistake for "ask your friend for a new token".
                if (e.statusCode == 404) throw NoServerException.NoAccess(e) else throw e
            }
        }
    }

    @PublishedApi
    internal suspend fun uploadJson(json: String): Boolean = runOnDrive { drive ->
        val fileId = getMyFileId(drive)
        if (fileId != null) {
            val mediaContent = ByteArrayContent.fromString("application/json", json)
            drive.files().update(fileId, null, mediaContent).execute()
        }
        val updated = fileId != null
        if (!updated) Log.e(TAG, "File '$fileName' not found for upload.")
        updated
    }

    /**
     * Runs [action] once signed in, as a child of the caller's coroutine: cancelling the caller
     * drops an action still waiting for sign-in or consent. A blocking Drive call that has already
     * started can't be interrupted; it finishes and its result is discarded.
     */
    private suspend fun <R> runOnDrive(action: (Drive) -> R): R = withContext(dispatcher) {
        suspendCancellableCoroutine { continuation ->
            val driveAction: (Drive) -> Unit = { drive -> continuation.resume(action(drive)) }
            continuation.invokeOnCancellation { GoogleDriveAuthorizationManager.discard(driveAction) }
            GoogleDriveAuthorizationManager.runWithGoogleDrive(driveAction, continuation::resumeWithException)
        }
    }

    private fun myFilesQuery(trashed: Boolean) =
        "appProperties has { key='$FILE_MARKER_KEY' and value='${escapeQueryValue(fileName)}' } " +
                "and 'me' in owners and trashed=$trashed"

    private fun getMyFileId(drive: Drive): String? = drive.files().list()
        .setQ(myFilesQuery(trashed = false))
        // Duplicates can exist (e.g. two devices running login() concurrently, or a file
        // restored from trash); the youngest is always the one in use.
        .setOrderBy("createdTime desc")
        .setPageSize(1)
        .setSpaces("drive")
        .setFields("files(id)")
        .execute()
        .files
        .firstOrNull()
        ?.id

    /**
     * Trashing counts as deleting, but Drive keeps a trashed file readable by its link until the
     * trash is emptied (30 days). Removing the link permission makes old tokens fail right away.
     */
    private fun stopSharingTrashedFiles(drive: Drive) {
        val trashedIds = drive.files().list()
            .setQ(myFilesQuery(trashed = true))
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
            .files
            .orEmpty()
            .map { it.id }
        var removed = 0
        for (fileId in trashedIds) {
            drive.permissions().list(fileId)
                .setFields("permissions(id,type)")
                .execute()
                .permissions
                .orEmpty()
                .filter { it.type == "anyone" }
                .forEach {
                    drive.permissions().delete(fileId, it.id).execute()
                    removed++
                }
        }
        if (removed > 0) Log.d(TAG, "Stopped link sharing of $removed trashed file(s)")
    }

    private fun createMyFile(drive: Drive, initialJson: String): String {
        Log.d(TAG, "Creating '$fileName'")
        val metadata = File().apply {
            name = fileName
            appProperties = mapOf(FILE_MARKER_KEY to fileName)
        }
        val content = ByteArrayContent.fromString("application/json", initialJson)
        return drive.files().create(metadata, content)
            .setFields("id")
            .execute()
            .id
    }

    /** Idempotent, so it also repairs sharing the user turned off by hand in Drive. */
    private fun ensureReadableByLink(drive: Drive, fileId: String) {
        val alreadyShared = drive.permissions().list(fileId)
            .setFields("permissions(type)")
            .execute()
            .permissions
            .orEmpty()
            .any { it.type == "anyone" }
        if (alreadyShared) return

        val permission = Permission().apply {
            type = "anyone"
            role = "reader"
            // Readable by whoever has the token, but never listed in anyone's Drive search.
            allowFileDiscovery = false
        }
        drive.permissions().create(fileId, permission).execute()
        Log.d(TAG, "'$fileName' is now readable by link")
    }

    private fun readFile(drive: Drive, fileId: String): String {
        val outputStream = ByteArrayOutputStream()
        drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
        // Not logged: this is the user's file content, not diagnostic data.
        return outputStream.toString()
    }

    @PublishedApi
    internal companion object {
        const val TAG = "GoogleDriveRepository"

        // appProperties are private to the Cloud project that set them, so the marker can't
        // collide with other apps' files of the same name.
        private const val FILE_MARKER_KEY = "noserver"

        // Drive file IDs; checked locally so a garbage token fails without a network call.
        private val SHARE_TOKEN_FORMAT = Regex("[A-Za-z0-9_-]+")

        /** Escapes a value for a single-quoted string in a Drive `files.list` query. */
        fun escapeQueryValue(value: String) = value.replace("\\", "\\\\").replace("'", "\\'")
    }
}
