package com.github.pvtitov.noserver

import android.content.Context

/**
 * Holds the application [Context] captured by [NoServerInitializer] via Android's App Startup
 * library. [GoogleDriveAuthorizationManager] needs an application Context to start
 * [NoServerActivity] outside of an existing Activity, and getting it this way -- rather than by
 * requiring consumers to declare or extend a specific `Application` subclass -- avoids a
 * manifest-merge conflict with a consumer app that already has its own `Application` subclass.
 */
internal object NoServerApplicationContext {
    internal var instance: Context? = null
}
