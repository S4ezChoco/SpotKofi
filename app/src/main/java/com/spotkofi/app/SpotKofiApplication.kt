package com.spotkofi.app

import android.app.Application
import com.spotkofi.app.core.AppContainer

class SpotKofiApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(context = this)
        container.downloadManager.onApplicationReady()
    }

    override fun onTerminate() {
        // Nothing in the app owns an in-process media decoder. This still clears
        // the external-link player's current UI state for instrumentation.
        container.release()
        super.onTerminate()
    }
}
