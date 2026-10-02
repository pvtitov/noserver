package com.github.pvtitov.noserver

import android.content.Intent
import android.net.Uri
import android.util.Log
import com.github.pvtitov.noserver.NoServerActivity.Companion.MANUAL_LOAD_EXTRA_KEY
import com.github.pvtitov.noserver.NoServerActivity.Companion.MANUAL_SAVE_EXTRA_KEY

/**
 * Manual-storage counterpart to [GoogleDriveAuthorizationManager]: drives the SAF file-picker
 * flow through [NoServerActivity] and holds the pending save/load action until the picker
 * returns. Unlike [GoogleDriveAuthorizationManager], a cancelled or unavailable picker always
 * resumes the pending action (with a null [Uri]) instead of leaving it pending forever.
 */
object ManualStorageManager {

    private const val TAG = "ManualStorageManager"

    // TODO: implement action queue (same single-pending-action limitation as
    // GoogleDriveAuthorizationManager -- only one concurrent save and one concurrent load
    // supported)
    private var saveAction: ((Uri?) -> Unit)? = null
    private var loadAction: ((Uri?) -> Unit)? = null

    fun runSave(suggestedFileName: String, action: (Uri?) -> Unit) {
        Log.d(TAG, "runSave() called")
        saveAction = action
        val context = NoServerApplicationContext.instance
        if (context == null) {
            Log.e(TAG, "runSave(): no application context available")
            onSaveResult(null)
            return
        }
        context.startActivity(
            Intent(context, NoServerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MANUAL_SAVE_EXTRA_KEY, suggestedFileName)
            }
        )
    }

    fun runLoad(action: (Uri?) -> Unit) {
        Log.d(TAG, "runLoad() called")
        loadAction = action
        val context = NoServerApplicationContext.instance
        if (context == null) {
            Log.e(TAG, "runLoad(): no application context available")
            onLoadResult(null)
            return
        }
        context.startActivity(
            Intent(context, NoServerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MANUAL_LOAD_EXTRA_KEY, true)
            }
        )
    }

    internal fun onSaveResult(uri: Uri?) {
        Log.d(TAG, "onSaveResult(): uri = $uri")
        val actionImmutable = saveAction
        saveAction = null
        actionImmutable?.invoke(uri)
    }

    internal fun onLoadResult(uri: Uri?) {
        Log.d(TAG, "onLoadResult(): uri = $uri")
        val actionImmutable = loadAction
        loadAction = null
        actionImmutable?.invoke(uri)
    }

    /**
     * Public (not internal) and non-inline: [ManualRepository]'s inline reified methods call
     * this directly, so it must be public, but because it isn't itself inline, its body (which
     * touches [NoServerApplicationContext]) is compiled once into this module rather than
     * copied into consumer bytecode -- so [NoServerApplicationContext] doesn't need to become
     * public too.
     */
    fun readText(uri: Uri): String? {
        val contentResolver = NoServerApplicationContext.instance?.contentResolver ?: return null
        return try {
            contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            Log.e(TAG, "readText() failed", e)
            null
        }
    }

    fun writeText(uri: Uri, text: String): Boolean {
        val contentResolver = NoServerApplicationContext.instance?.contentResolver ?: return false
        return try {
            contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "writeText() failed", e)
            false
        }
    }
}
