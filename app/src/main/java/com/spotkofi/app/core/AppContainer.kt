package com.spotkofi.app.core

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.spotkofi.app.data.local.LocalMusicStore
import com.spotkofi.app.data.local.SettingsStore
import com.spotkofi.app.data.repository.ItunesMusicRepository
import com.spotkofi.app.data.repository.MusicRepository
import com.spotkofi.app.data.service.DownloadManager
import com.spotkofi.app.data.service.DownloadPriority
import com.spotkofi.app.data.service.MusicService
import com.spotkofi.app.data.service.SimpleYouTubeMusicService
import com.spotkofi.app.player.MusicPlayerController
import com.spotkofi.app.player.PlaybackCache
import com.spotkofi.app.player.PlayerController
import com.spotkofi.app.player.QueueController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Application-scoped dependencies. User-owned state, the player, and downloads
 * all share this one composition root so the UI cannot accidentally create a
 * second in-memory source of truth.
 */
class AppContainer(
    context: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settingsStore = SettingsStore(context)
    val localStore = LocalMusicStore(context)

    /**
     * Settings are handed to the repository as a provider rather than a value, so a
     * change to the content region or the explicit filter takes effect on the next
     * request instead of at the next app launch.
     */
    val musicRepository: MusicRepository = ItunesMusicRepository(
        localStore = localStore,
        settingsProvider = { settingsStore.current },
    )
    val musicService: MusicService = SimpleYouTubeMusicService()
    val downloadManager = DownloadManager(context, musicService, localStore)
    val playbackCache = PlaybackCache(context)

    private val musicPlayerController = MusicPlayerController(
        context = context,
        musicService = musicService,
        localStore = localStore,
        dataSourceFactory = playbackCache.dataSourceFactory,
        settingsProvider = { settingsStore.current },
    )

    val playerController: PlayerController = musicPlayerController
    val queueController: QueueController = musicPlayerController

    init {
        // The downloader is told the preferred priority rather than asked to look it
        // up: every UI call site would otherwise have to pass the setting through,
        // and one that forgot would silently queue at the wrong priority.
        scope.launch {
            settingsStore.settings.collect { settings ->
                downloadManager.defaultPriority = if (settings.downloadHighPriority) {
                    DownloadPriority.HIGH
                } else {
                    DownloadPriority.NORMAL
                }
            }
        }
    }

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
        scope.cancel()
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
