package com.spotkofi.app.core

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.spotkofi.app.data.local.LocalMusicStore
import com.spotkofi.app.data.repository.ItunesMusicRepository
import com.spotkofi.app.data.repository.MusicRepository
import com.spotkofi.app.data.service.DownloadManager
import com.spotkofi.app.data.service.MusicService
import com.spotkofi.app.data.service.SimpleYouTubeMusicService
import com.spotkofi.app.player.MusicPlayerController
import com.spotkofi.app.player.PlaybackCache
import com.spotkofi.app.player.PlayerController
import com.spotkofi.app.player.QueueController

/**
 * Application-scoped dependencies. User-owned state, the player, and downloads
 * all share this one composition root so the UI cannot accidentally create a
 * second in-memory source of truth.
 */
class AppContainer(
    context: Context,
) {

    val localStore = LocalMusicStore(context)
    val musicRepository: MusicRepository = ItunesMusicRepository(localStore)
    val musicService: MusicService = SimpleYouTubeMusicService()
    val downloadManager = DownloadManager(context, musicService, localStore)
    private val playbackCache = PlaybackCache(context)

    private val musicPlayerController = MusicPlayerController(
        context = context,
        musicService = musicService,
        localStore = localStore,
        dataSourceFactory = playbackCache.dataSourceFactory,
    )

    val playerController: PlayerController = musicPlayerController
    val queueController: QueueController = musicPlayerController

    /**
     * The player the platform MediaSession is built over.
     *
     * Exposed so [PlaybackService] can attach a session to the running player
     * instead of creating a second one that would drift out of sync.
     */
    @get:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    val mediaSessionPlayer: androidx.media3.common.Player
        get() = musicPlayerController.mediaSessionPlayer

    /** Releases application-scoped media, download, and database resources. */
    fun release() {
        musicPlayerController.release()
        playbackCache.release()
        downloadManager.close()
        localStore.close()
    }
}

/**
 * Fails loudly rather than silently handing back a default, so a missing provider
 * shows up immediately instead of as confusing empty screens.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided. Wrap the tree in SpotKofiApp.")
}
