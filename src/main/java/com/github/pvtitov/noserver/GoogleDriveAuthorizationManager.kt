package com.github.pvtitov.noserver

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.github.pvtitov.noserver.NoServerActivity.Companion.AUTHENTICATION_EXTRA_KEY
import com.github.pvtitov.noserver.NoServerActivity.Companion.AUTHORIZATION_EXTRA_KEY
import com.github.pvtitov.noserver.NoServerActivity.Companion.FINISH_EXTRA_KEY
import com.github.pvtitov.noserver.NoServerActivity.Companion.LOGOUT_EXTRA_KEY
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.Drive

@SuppressLint("StaticFieldLeak")
object GoogleDriveAuthorizationManager {

    private const val TAG = "GoogleDriveAuthorizationManager"

    // TODO: implement action queue
    private var action: ((Drive) -> Unit)? = null

    fun runWithGoogleDrive(action: (Drive) -> Unit) {
        Log.d(TAG, "runWithGoogleDrive() called, action = $action")
        this.action = action

        val driveImmutable = GoogleDriveAuthenticator.drive
        if (driveImmutable != null) {
            Log.d(TAG, "runWithGoogleDrive(): already have Drive instance, don't need authentication")
            handleUserRecoverableAuthException {
                action.invoke(driveImmutable)
            }
        } else {
            Log.d(TAG, "runWithGoogleDrive(): need authentication, should start activity")
            NoServerApplication.applicationContext?.let { context ->
                context.startActivity(
                    Intent(context, NoServerActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(AUTHENTICATION_EXTRA_KEY, true)
                    }
                )
            }
        }
    }

    fun logout() {
        Log.d(TAG, "logout()")
        NoServerApplication.applicationContext?.let { context ->
            context.startActivity(
                Intent(context, NoServerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(LOGOUT_EXTRA_KEY, true)
                }
            )
        }
    }

    private fun finish() {
        Log.d(TAG, "finish()")
        NoServerApplication.applicationContext?.let { context ->
            context.startActivity(
                Intent(context, NoServerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(FINISH_EXTRA_KEY, true)
                }
            )
        }
    }

    internal suspend fun logout(context: Context) {
        GoogleDriveAuthenticator.logout(context)
    }

    internal suspend fun authenticate(activity: Activity) {
        Log.d(TAG, "authenticate(Activity) called")
        val authenticationResult = GoogleDriveAuthenticator.authenticate(activity)
        if (authenticationResult.isSuccess) {
            Log.d(TAG, "On authentication result")
            val driveImmutable = authenticationResult.getOrNull() ?: run {
                Log.d(TAG, "Drive is null")
                return
            }
            val actionImmutable = action
            if (actionImmutable != null) {
                handleUserRecoverableAuthException {
                    actionImmutable.invoke(driveImmutable)
                }
            }
        } else {
            finish()
        }
    }

    internal fun authorize() {
        Log.d(TAG, "authorize() called")
        val actionImmutable = action
        val driveImmutable = GoogleDriveAuthenticator.drive
        if (actionImmutable != null && driveImmutable != null) {
            handleUserRecoverableAuthException {
                actionImmutable.invoke(driveImmutable)
            }
        }
    }

    private fun handleUserRecoverableAuthException(action: () -> Unit) {
        try {
            action.invoke()
            this.action = null
            Log.d(TAG, "handleUserRecoverableAuthException(): action succeeded")
            finish()
        } catch (e: UserRecoverableAuthIOException) {
            Log.d(
                TAG,
                "handleUserRecoverableAuthException(): action failed with recoverable exception: $e, ${e.message}, ${e.cause}",
                e
            )
            val intent = e.intent
            val context = NoServerApplication.applicationContext
            if (context != null && intent != null) {
                context.startActivity(
                    Intent(context, NoServerActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(AUTHORIZATION_EXTRA_KEY, intent)
                    }
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "handleUserRecoverableAuthException(): action failed: $e, ${e.message}, ${e.cause}", e)
            finish()
        }
    }
}