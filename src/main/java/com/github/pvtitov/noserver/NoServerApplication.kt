package com.github.pvtitov.noserver

import android.app.Application
import android.content.Context

class NoServerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private var instance: NoServerApplication? = null

        val applicationContext: Context?
            get() = instance?.applicationContext
    }
}