package com.github.pvtitov.noserver

import android.accounts.Account
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.credentials.*
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.supervisorScope
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal object GoogleDriveAuthenticator {

    internal var drive: Drive? = null
        private set
    private var authorizationConsentCoroutineScope: CoroutineScope? = null
    private var authorizationConsentContinuation: Continuation<Boolean>? = null

    internal suspend fun authenticate(activity: Activity): Result<Drive> {
        val driveImmutable = drive?.let { return Result.success(it) }
            ?: authenticateInternal(activity, true)
            ?: authenticateInternal(activity, false)

        return if (driveImmutable != null) {
            drive = driveImmutable
            Result.success(driveImmutable)
        } else {
            Result.failure(AuthenticationFailedException())
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

    private suspend fun authenticateInternal(
        activity: Activity,
        isAutoSelect: Boolean,
    ): Drive? {
        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setAutoSelectEnabled(isAutoSelect)
            .build()

        val getCredentialRequest: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return handleSignIn(activity, getCredentialRequest)
    }

    private suspend fun handleSignIn(activity: Activity, getCredentialRequest: GetCredentialRequest): Drive? {
        try {
            val credentialManager = CredentialManager.create(activity)

            val getCredentialResponse = credentialManager.getCredential(
                context = activity,
                request = getCredentialRequest,
            )

            return getCredentialResponse.credential.signIn(activity)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Get credentials exception", e)
            return null
        } catch (e: UserRecoverableAuthIOException) {
            Log.d(TAG, "Get credentials recoverable exception", e)
            getAuthorizationConsent(activity, e.intent)
            return handleSignIn(activity, getCredentialRequest)
        } catch (e: Throwable) {
            Log.e(TAG, "Get credentials another exception", e)
            return null
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

    private fun Credential.signIn(context: Context): Drive? {

        if (this is CustomCredential && type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(data)

                if (drive == null) {
                    val googleAccountCredential = GoogleAccountCredential.usingOAuth2(
                        context, listOf(DriveScopes.DRIVE)
                    )
                        .setSelectedAccount(
                            Account(googleIdTokenCredential.id, googleIdTokenCredential.type)
                        )

                    drive = Drive.Builder(
                        NetHttpTransport(),
                        GsonFactory.getDefaultInstance(),
                        googleAccountCredential
                    )
                        .setApplicationName("Test")
                        .build()
                }

                return drive
            } catch (e: GoogleIdTokenParsingException) {
                Log.e(TAG, "Received an invalid google id token response", e)
                return null
            }
        } else {
            Log.e(TAG, "Unexpected type of credential")
            return null
        }
    }

    class AuthenticationFailedException(
        override val message: String? = null,
        override val cause: Throwable? = null
    ) : Exception(message, cause)
}

private const val TAG = "AuthenticationManager"
private const val AUTHORIZATION_CONSENT_REQUEST_CODE = 12