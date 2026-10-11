package com.github.pvtitov.noserver

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger


internal class NoServerActivity : Activity() {

    private val coroutineScope: CoroutineScope = CoroutineScope(
        Dispatchers.Default +
                SupervisorJob() +
                CoroutineExceptionHandler { _, e ->
                    Log.e(TAG, "Coroutine exception: $e, ${e.message}, ${e.cause}", e)
                }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        liveInstances.incrementAndGet()
        Log.d(TAG, "onCreate(): $this")
        handleStartingIntent()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent()")
        handleStartingIntent()
    }

    private fun handleStartingIntent() {
        coroutineScope.launch(Dispatchers.IO) {
            val extras = intent.extras ?: run {
                finish()
                return@launch
            }
            Log.d(TAG, "launchAuthorization(): intent = $intent, extras = ${extras.keySet()}")
            if (extras.containsKey(AUTHORIZATION_EXTRA_KEY)) {
                val userRecoverableExceptionIntent = extras.getParcelable<Intent>(AUTHORIZATION_EXTRA_KEY)
                    ?: run {
                        GoogleDriveAuthorizationManager.cancel()
                        finish()
                        return@launch
                    }

                Log.d(
                    TAG, "User recoverable exception intent: $userRecoverableExceptionIntent, " +
                            "calling activity = ${callingActivity}, calling package = ${callingPackage}, " +
                            "intent package = ${userRecoverableExceptionIntent.getPackage()}"
                )

                startActivityForResult(userRecoverableExceptionIntent, AUTHORIZATION_REQUEST_CODE)
            } else if (extras.containsKey(AUTHENTICATION_EXTRA_KEY)) {
                GoogleDriveAuthorizationManager.authenticate(this@NoServerActivity)
            } else if (extras.containsKey(LOGOUT_EXTRA_KEY)) {
                GoogleDriveAuthorizationManager.logout(this@NoServerActivity)
                finish()
            } else if (extras.containsKey(MANUAL_SAVE_EXTRA_KEY)) {
                val suggestedFileName = extras.getString(MANUAL_SAVE_EXTRA_KEY) ?: run {
                    finish()
                    return@launch
                }
                startActivityForResult(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = JSON_MIME_TYPE
                        putExtra(Intent.EXTRA_TITLE, suggestedFileName)
                    },
                    MANUAL_SAVE_REQUEST_CODE
                )
            } else if (extras.containsKey(MANUAL_LOAD_EXTRA_KEY)) {
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = JSON_MIME_TYPE
                    },
                    MANUAL_LOAD_REQUEST_CODE
                )
            } else {
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        GoogleDriveAuthenticator.onActivityResult(requestCode, resultCode, data)

        if (requestCode == AUTHORIZATION_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                coroutineScope.launch(Dispatchers.IO) {
                    GoogleDriveAuthorizationManager.authorize()
                }
            } else {
                GoogleDriveAuthorizationManager.cancel()
                finish()
            }
        } else if (requestCode == MANUAL_SAVE_REQUEST_CODE) {
            ManualStorageManager.onSaveResult(if (resultCode == RESULT_OK) data?.data else null)
            finish()
        } else if (requestCode == MANUAL_LOAD_REQUEST_CODE) {
            ManualStorageManager.onLoadResult(if (resultCode == RESULT_OK) data?.data else null)
            finish()
        } else {
            finish()
        }
    }

    override fun finish() {
        Log.d(TAG, "finish(): $this")
        super.finish()
    }

    override fun onDestroy() {
        liveInstances.decrementAndGet()
        if (coroutineScope.isActive) {
            coroutineScope.cancel()
        }
        super.onDestroy()
    }

    companion object {
        // A counter, not a flag: FLAG_ACTIVITY_CLEAR_TOP creates the new instance before the old one is destroyed.
        internal val liveInstances = AtomicInteger(0)
        internal const val AUTHENTICATION_EXTRA_KEY = "AUTHENTICATION_EXTRA_KEY"
        internal const val AUTHORIZATION_EXTRA_KEY = "AUTHORIZATION_EXTRA_KEY"
        internal const val LOGOUT_EXTRA_KEY = "LOGOUT_EXTRA_KEY"
        internal const val FINISH_EXTRA_KEY = "FINISH_EXTRA_KEY"
        internal const val MANUAL_SAVE_EXTRA_KEY = "MANUAL_SAVE_EXTRA_KEY"
        internal const val MANUAL_LOAD_EXTRA_KEY = "MANUAL_LOAD_EXTRA_KEY"
        private const val AUTHORIZATION_REQUEST_CODE = 14
        private const val MANUAL_SAVE_REQUEST_CODE = 15
        private const val MANUAL_LOAD_REQUEST_CODE = 16
        private const val JSON_MIME_TYPE = "application/json"
        private const val TAG = "NoServerActivity"
    }
}