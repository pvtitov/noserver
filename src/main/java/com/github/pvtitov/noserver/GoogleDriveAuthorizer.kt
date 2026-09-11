package com.github.pvtitov.noserver

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.util.Log
import androidx.core.app.ActivityCompat.startIntentSenderForResult
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal object GoogleDriveAuthorizer {
    private const val AUTHORIZATION_REQUEST_CODE = 11
    private const val TAG = "AuthorizationManager"

    private var authorizationClient: AuthorizationClient? = null
    private lateinit var cancellableContinuation: CancellableContinuation<Boolean>

    internal suspend fun authorize(activity: Activity): Boolean {
        return suspendCancellableCoroutine { cancellableContinuation ->
            this.cancellableContinuation = cancellableContinuation
            authorizeInternal(activity)
        }.also {
            cleanUp()
        }
    }

    private fun authorizeInternal(
        activity: Activity
    ) {
        Identity.getAuthorizationClient(activity)
            .also { authorizationClient = it }
            .authorize(buildAuthorizationRequest())
            .addOnSuccessListener { authorizationResult ->
                if (authorizationResult.hasResolution()) {
                    // Access needs to be granted by the user
                    val pendingIntent: PendingIntent = authorizationResult.pendingIntent
                        ?: run {
                            Log.e(TAG, "Couldn't start Authorization UI")
                            cancellableContinuation.resume(false)
                            return@addOnSuccessListener
                        }
                    try {
                        startIntentSenderForResult(
                            activity,
                            pendingIntent.intentSender,
                            AUTHORIZATION_REQUEST_CODE,
                            null,
                            0,
                            0,
                            0,
                            null
                        )
                    } catch (e: SendIntentException) {
                        Log.e(TAG, "Couldn't start Authorization UI: " + e.localizedMessage)
                        cancellableContinuation.resume(false)
                    }
                } else {
                    // Access already granted, continue with user action
                    // Example: saveToDriveAppFolder(authorizationResult)
                    cancellableContinuation.resume(true)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to authorize", e)
                cancellableContinuation.resume(false)
            }
    }

    private fun cleanUp() {
        authorizationClient = null
    }

    private fun buildAuthorizationRequest(): AuthorizationRequest {
        val scopes = listOf(
            Scope(DriveScopes.DRIVE)
        )

        return AuthorizationRequest.builder()
            .setRequestedScopes(scopes)
            .build()
    }

    internal fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            AUTHORIZATION_REQUEST_CODE -> {
                val authorizationResult = authorizationClient
                    ?.getAuthorizationResultFromIntent(data)

                if (resultCode == RESULT_OK && authorizationResult?.hasResolution() == true) {
                    cancellableContinuation.resume(true)
                } else {
                    cancellableContinuation.resume(false)
                }
            }
        }
    }
}