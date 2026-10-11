package com.github.pvtitov.noserver

import android.accounts.Account
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.credentials.*
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.supervisorScope
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal object GoogleDriveAuthenticator {

    internal var drive: Drive? = null
        private set
    internal var clientId: String? = null
    private var authorizationConsentCoroutineScope: CoroutineScope? = null
    private var authorizationConsentContinuation: Continuation<Boolean>? = null

    internal suspend fun authenticate(activity: Activity): Result<Drive> {
        drive?.let { return Result.success(it) }

        return try {
            val signedIn = try {
                handleSignIn(activity, googleIdRequest())
            } catch (e: NoCredentialException) {
                // The bottom sheet only lists accounts already on the device. The "Sign in with
                // Google" flow also offers to add one, which Google recommends for this case.
                Log.d(TAG, "No credential for the bottom sheet, falling back to Sign in with Google", e)
                handleSignIn(activity, signInWithGoogleRequest())
            }
            drive = signedIn
            Result.success(signedIn)
        } catch (e: NoCredentialException) {
            Log.e(TAG, "No Google account available", e)
            Result.failure(NoServerException.NoGoogleAccount(e))
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Sign-in cancelled by the user", e)
            Result.failure(NoServerException.Cancelled(e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoServerException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed", e)
            Result.failure(NoServerException.Failed(e))
        }
    }

    internal suspend fun logout(context: Context) {
        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            Log.d(TAG, "User logged out and credential state cleared successfully.")
        } catch (e: ClearCredentialException) {
            Log.e(TAG, "Error clearing credential state during logout", e)
        }
        drive = null
    }


    internal fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            AUTHORIZATION_CONSENT_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    authorizationConsentContinuation?.resume(true)
                } else {
                    authorizationConsentContinuation?.resume(false)
                }
            }
        }
    }

    private fun serverClientId(): String = clientId ?: error(
        "NoServer is not configured. Call NoServer.configure(googleWebClientId) with your app's " +
                "OAuth web client ID before using GoogleDriveRepository."
    )

    private fun googleIdRequest(): GetCredentialRequest = GetCredentialRequest.Builder()
        .addCredentialOption(
            GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId())
                .setAutoSelectEnabled(true)
                .build()
        )
        .build()

    private fun signInWithGoogleRequest(): GetCredentialRequest = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId()).build())
        .build()

    private suspend fun handleSignIn(activity: Activity, getCredentialRequest: GetCredentialRequest): Drive {
        try {
            val credentialManager = CredentialManager.create(activity)

            val getCredentialResponse = credentialManager.getCredential(
                context = activity,
                request = getCredentialRequest,
            )

            return getCredentialResponse.credential.signIn(activity)
        } catch (e: UserRecoverableAuthIOException) {
            Log.d(TAG, "Get credentials recoverable exception", e)
            getAuthorizationConsent(activity, e.intent)
            return handleSignIn(activity, getCredentialRequest)
        }
    }

    private suspend fun getAuthorizationConsent(activity: Activity, intent: Intent): Boolean {
        supervisorScope { authorizationConsentCoroutineScope = this }

        activity.startActivityForResult(intent, AUTHORIZATION_CONSENT_REQUEST_CODE)
        val hasConsent = suspendCoroutine { continuation ->
            authorizationConsentContinuation = continuation
        }
        authorizationConsentCoroutineScope?.cancel()
        authorizationConsentCoroutineScope = null
        return hasConsent
    }

    private fun Credential.signIn(context: Context): Drive {
        if (this !is CustomCredential || type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw NoServerException.Failed(IllegalStateException("Unexpected type of credential: $type"))
        }
        val googleIdTokenCredential = try {
            GoogleIdTokenCredential.createFrom(data)
        } catch (e: GoogleIdTokenParsingException) {
            throw NoServerException.Failed(e)
        }

        drive?.let { return it }

        val googleAccountCredential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE)
        )
            .setSelectedAccount(
                Account(googleIdTokenCredential.id, googleIdTokenCredential.type)
            )

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            googleAccountCredential
        )
            // Identifies the calling app to Google (shown in Drive API usage/quota
            // diagnostics); the consumer's own package name, not a placeholder.
            .setApplicationName(context.packageName)
            .build()
    }
}

private const val TAG = "AuthenticationManager"
private const val AUTHORIZATION_CONSENT_REQUEST_CODE = 12