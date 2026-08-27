package com.spotkofi.app

import android.app.Application
import com.spotkofi.app.core.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class SpotKofiApplication : Application() {

    /**
     * Scope for work that should outlive any single screen, such as the fake
     * playhead ticker. A [SupervisorJob] keeps one failing child from tearing
     * down the rest.
     */
    private val applicationScope = CoroutineScope(SupervisorJob())

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationScope)
    }
}
