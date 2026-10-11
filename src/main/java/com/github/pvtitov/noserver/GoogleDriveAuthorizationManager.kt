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
    private var onError: ((NoServerException) -> Unit)? = null

    // Kept for call sites inlined from GoogleDriveRepository by consumers compiled against 1.0.x.
    fun runWithGoogleDrive(action: (Drive) -> Unit) {
        runWithGoogleDrive(action) { e -> Log.e(TAG, "runWithGoogleDrive(): unhandled failure", e) }
    }

    /**
     * Runs [action] once a signed-in [Drive] is available. Exactly one of [action] completing or
     * [onError] being called happens, so a suspended caller is always resumed.
     */
    fun runWithGoogleDrive(action: (Drive) -> Unit, onError: (NoServerException) -> Unit) {
        Log.d(TAG, "runWithGoogleDrive() called, action = $action")
        this.action = action
        this.onError = onError

        val driveImmutable = GoogleDriveAuthenticator.drive
        if (driveImmutable != null) {
            Log.d(TAG, "runWithGoogleDrive(): already have Drive instance, don't need authentication")
            handleUserRecoverableAuthException {
                action.invoke(driveImmutable)
            }
        } else {
            Log.d(TAG, "runWithGoogleDrive(): need authentication, should start activity")
            val context = NoServerApplicationContext.instance
                ?: return fail(NoServerException.Failed(IllegalStateException("NoServer was not initialized")))
            context.startActivity(
                Intent(context, NoServerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(AUTHENTICATION_EXTRA_KEY, true)
                }
            )
        }
    }

    fun logout() {
        Log.d(TAG, "logout()")
        NoServerApplicationContext.instance?.let { context ->
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
        // Launching the activity only to finish it would flash it over the consumer's UI (and
        // pause their activity) after every Drive call made while already signed in.
        if (NoServerActivity.liveInstances.get() == 0) return
        NoServerApplicationContext.instance?.let { context ->
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
        GoogleDriveAuthenticator.authenticate(activity)
            .onSuccess { driveImmutable ->
                Log.d(TAG, "On authentication result")
                val actionImmutable = action
                if (actionImmutable != null) {
                    handleUserRecoverableAuthException {
                        actionImmutable.invoke(driveImmutable)
                    }
                } else {
                    finish()
                }
            }
            .onFailure { e ->
                fail(e as? NoServerException ?: NoServerException.Failed(e))
                finish()
            }
    }

    /** Fails the pending action, e.g. when the user declines the Drive consent screen. */
    internal fun cancel() {
        fail(NoServerException.Cancelled())
        finish()
    }

    private fun fail(e: NoServerException) {
        val onErrorImmutable = onError
        action = null
        onError = null
        onErrorImmutable?.invoke(e)
    }

    internal fun authorize() {
        Log.d(TAG, "authorize() called")
        val actionImmutable = action
        val driveImmutable = GoogleDriveAuthenticator.drive
        if (actionImmutable != null && driveImmutable != null) {
            handleUserRecoverableAuthException {
                actionImmutable.invoke(driveImmutable)
            }
        } else {
            finish()
        }
    }

    private fun handleUserRecoverableAuthException(action: () -> Unit) {
        try {
            action.invoke()
            this.action = null
            this.onError = null
            Log.d(TAG, "handleUserRecoverableAuthException(): action succeeded")
            finish()
        } catch (e: UserRecoverableAuthIOException) {
            Log.d(
                TAG,
                "handleUserRecoverableAuthException(): action failed with recoverable exception: $e, ${e.message}, ${e.cause}",
                e
            )
            val intent = e.intent
            val context = NoServerApplicationContext.instance
            if (context != null && intent != null) {
                context.startActivity(
                    Intent(context, NoServerActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(AUTHORIZATION_EXTRA_KEY, intent)
                    }
                )
            } else {
                fail(NoServerException.Failed(e))
                finish()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "handleUserRecoverableAuthException(): action failed: $e, ${e.message}, ${e.cause}", e)
            fail(NoServerException.Failed(e))
            finish()
        }
    }
}