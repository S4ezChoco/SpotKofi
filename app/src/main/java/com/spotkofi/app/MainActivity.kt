package com.spotkofi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.spotkofi.app.ui.SpotKofiApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the system splash can hand off to
        // the app theme without a flash of the wrong background.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Draw behind the status and navigation bars; individual composables
        // apply the insets they need.
        enableEdgeToEdge()

        val container = (application as SpotKofiApplication).container

        setContent {
            SpotKofiApp(container = container)
        }
    }
}
