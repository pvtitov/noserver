package com.github.pvtitov.noserver

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Reads other users' files by share token. Under the `drive.file` scope a user's own token can't
 * see files another account created, even public ones (Drive answers 404), so these reads go
 * through a client that carries no user credential, only the consuming app's API key.
 */
internal object SharedFileReader {

    internal var apiKey: String? = null

    @Volatile
    private var drive: Drive? = null

    fun read(fileId: String): String {
        val outputStream = ByteArrayOutputStream()
        drive().files().get(fileId)
            .setKey(apiKey())
            .executeMediaAndDownloadTo(outputStream)
        return outputStream.toString()
    }

    private fun apiKey(): String = apiKey ?: error(
        "NoServer is not configured. Call NoServer.configure(googleWebClientId, apiKey) before " +
                "using GoogleDriveRepository."
    )

    private fun drive(): Drive = drive ?: run {
        val context = NoServerApplicationContext.instance ?: error("NoServer was not initialized")
        val packageName = context.packageName
        val certSha1 = signingCertSha1(context)
        // A key restricted to Android apps (as README recommends) is only accepted with these
        // headers; Google's client library doesn't add them by itself.
        Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance()) { request ->
            request.headers["X-Android-Package"] = packageName
            request.headers["X-Android-Cert"] = certSha1
        }
            .setApplicationName(packageName)
            .build()
            .also { drive = it }
    }

    @Suppress("DEPRECATION") // GET_SIGNATURES is the only option below API 28.
    private fun signingCertSha1(context: Context): String {
        val packageManager = context.packageManager
        val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        } ?: error("Can't read the app's signing certificate")
        return sha1Hex(signatures.first().toByteArray())
    }

    /** Uppercase hex without separators, the format the `X-Android-Cert` header expects. */
    internal fun sha1Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02X".format(it) }
}
