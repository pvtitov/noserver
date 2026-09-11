package com.github.pvtitov.noserver

/**
 * Library-wide configuration. Each consuming app registers its own Google Cloud project and
 * OAuth web client ID (an ID baked into the library itself couldn't work across apps, since it's
 * tied to a specific package name and signing certificate), so [configure] must be called with
 * that app's client ID before using [GoogleDriveRepository].
 */
object NoServer {
    fun configure(googleWebClientId: String) {
        GoogleDriveAuthenticator.clientId = googleWebClientId
    }
}
