package com.spotkofi.app.core

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.spotkofi.app.data.repository.ItunesMusicRepository
import com.spotkofi.app.data.repository.MusicRepository
import com.spotkofi.app.data.service.MusicService
import com.spotkofi.app.data.service.SimpleYouTubeMusicService
import com.spotkofi.app.player.MusicPlayerController
import com.spotkofi.app.player.PlayerController

/**
 * Manual dependency container.
 *
 * Still hand-written rather than Hilt: the graph is three objects deep, so a
 * container is smaller and clearer than annotation processing, and it avoids
 * pinning a KSP version. Hilt earns its place when accounts arrive and the graph
 * gains scoping.
 *
 * This class is the only place that decides which implementations the app runs
 * against, which is what made swapping the mock catalog and the fake player for
 * real ones a change here rather than a change in every screen.
 */
class AppContainer(
    context: Context,
) {

    val musicRepository: MusicRepository = ItunesMusicRepository()
    val musicService: MusicService = SimpleYouTubeMusicService()
    
    private val musicPlayerController = MusicPlayerController(context, musicService)

    val playerController: PlayerController = musicPlayerController

    /** Releases the music player controller and its Media3 decoder. */
    fun release() {
        musicPlayerController.release()
    }
}

/**
 * Fails loudly rather than silently handing back a default, so a missing provider
 * shows up immediately instead of as confusing empty screens.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided. Wrap the tree in SpotKofiApp.")
}
