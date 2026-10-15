package com.github.pvtitov.noserver

/**
 * Thrown by [GoogleDriveRepository] operations when Google Drive can't be reached on the user's
 * behalf. Catch it to show your own message instead of letting the coroutine fail uncaught.
 */
sealed class NoServerException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * There is no Google account on the device, or the user didn't add one when offered. To let
     * the user add one, start
     * `Intent(Settings.ACTION_ADD_ACCOUNT).putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))`
     * and retry the operation afterwards.
     */
    class NoGoogleAccount(cause: Throwable? = null) :
        NoServerException("No Google account is available on this device", cause)

    /** The user dismissed the account picker or declined Google Drive access. */
    class Cancelled(cause: Throwable? = null) :
        NoServerException("The user cancelled Google sign-in or Drive authorization", cause)

    /**
     * A share token passed to `download(shareToken)` doesn't lead to a readable file: it's
     * malformed, or its owner deleted the file (deleting it in Drive gives them a new token on
     * their next login). Ask the owner for their current token.
     */
    class NoAccess(cause: Throwable? = null) :
        NoServerException("The share token doesn't lead to a readable file", cause)

    /** Sign-in or a Drive request failed for another reason (network, Play services, Drive API error). */
    class Failed(cause: Throwable) :
        NoServerException("Google Drive operation failed: ${cause.message}", cause)
}
