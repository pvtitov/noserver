package com.github.pvtitov.noserver.sample

import android.app.Application
import com.github.pvtitov.noserver.NoServer

class SampleApplication : Application() {

    var isConfigured: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        val apiKey = BuildConfig.NOSERVER_API_KEY
        // With a key missing, the library would only fail on the first Drive call; checking here
        // lets the UI say so up front instead.
        if (clientId.isNotBlank() && apiKey.isNotBlank()) {
            NoServer.configure(clientId, apiKey)
            isConfigured = true
        }
    }
}
