package com.github.pvtitov.noserver

import android.content.Context
import androidx.startup.Initializer

/**
 * Registered as an App Startup component in AndroidManifest.xml, so this library gets an
 * application [Context] automatically at process start -- see [NoServerApplicationContext] for
 * why this replaces requiring consumers to declare a specific `Application` subclass.
 */
internal class NoServerInitializer : Initializer<Context> {
    override fun create(context: Context): Context {
        return context.applicationContext.also { NoServerApplicationContext.instance = it }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
