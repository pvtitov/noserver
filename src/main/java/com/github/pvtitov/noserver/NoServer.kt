package com.github.pvtitov.noserver

/**
 * Library-wide configuration. Each consuming app registers its own Google Cloud project, so
 * [configure] must be called with that app's credentials before using [GoogleDriveRepository].
 * Neither can be baked into the library: both are tied to the app's package name and signing
 * certificate.
 *
 * @param googleWebClientId the app's OAuth web client ID, for signing users in.
 * @param apiKey the app's API key (Drive API enabled), for reading other users' files by share
 * token. It's one key per app, created by the app's developer, not something end users provide.
 */
object NoServer {
    fun configure(googleWebClientId: String, apiKey: String) {
        GoogleDriveAuthenticator.clientId = googleWebClientId
        SharedFileReader.apiKey = apiKey
    }
}
