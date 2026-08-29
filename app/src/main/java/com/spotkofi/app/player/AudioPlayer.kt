package com.spotkofi.app.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Buffered local audio player backed by Media3 ExoPlayer.
 *
 * ExoPlayer owns the asynchronous lifecycle and range requests. This wrapper
 * keeps the rest of SpotKofi independent from Media3 while making play, pause,
 * seek, buffering, completion, and release safe to call at any time.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class AudioPlayer(
    context: Context,
    private val providedDataSourceFactory: DataSource.Factory? = null,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player: ExoPlayer
    private var progressJob: Job? = null
    private var released = false
    private var completionReported = false
    private var currentStreamUrl: String? = null

    private var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    private var onPlaybackProgressChanged: ((Long, Long) -> Unit)? = null
    private var onPlaybackError: ((String) -> Unit)? = null
    private var onPlaybackCompleted: (() -> Unit)? = null

    init {
        val dataSourceFactory = providedDataSourceFactory ?: run {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT)
                .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(READ_TIMEOUT_MS)
                .setAllowCrossProtocolRedirects(true)
            // DefaultDataSource adds file/content support without changing the
            // HTTP resolver used for full-length YouTube streams.
            DefaultDataSource.Factory(
                context.applicationContext,
                httpDataSourceFactory,
            )
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_REBUFFER_MS,
            )
            .build()

        player = ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()
            .also { exoPlayer ->
                exoPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                )
                exoPlayer.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        publishPlaybackState()
                        emitProgress()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY,
                            Player.STATE_BUFFERING,
                            -> publishPlaybackState()

                            Player.STATE_ENDED -> {
                                progressJob?.cancel()
                                publishPlaybackState(forcePlaying = false)
                                emitProgress()
                                if (!completionReported) {
                                    completionReported = true
                                    onPlaybackCompleted?.invoke()
                                }
                            }

                            else -> publishPlaybackState()
                        }
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int,
                    ) {
                        emitProgress()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        progressJob?.cancel()
                        player.playWhenReady = false
                        publishPlaybackState(forcePlaying = false)
                        val message = "Playback error ${error.errorCodeName}: ${error.message.orEmpty()}"
                        Log.e(TAG, message, error)
                        onPlaybackError?.invoke(message)
                    }
                })
            }
    }

    /**
     * Loads a stream URL. When [autoPlay] is false the media is prepared but
     * remains paused, which preserves a pause tapped while the resolver is busy.
     */
    fun play(
        streamUrl: String,
        autoPlay: Boolean = true,
        cacheKey: String? = null,
        /**
         * Metadata for the platform MediaSession.
         *
         * Without it the system media controls, the lock screen and OEM
         * floating/dynamic island surfaces have nothing to render but the
         * package name.
         */
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        artworkUrl: String? = null,
    ) {
        if (released || streamUrl.isBlank()) return

        completionReported = false
        currentStreamUrl = streamUrl
        progressJob?.cancel()
        Log.d(TAG, "Preparing buffered audio URL")

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            // File URIs are already offline data. Never let a remote video ID
            // alias a local file in the streaming cache.
            .apply {
                cacheKey
                    ?.takeUnless { streamUrl.startsWith("file:", ignoreCase = true) }
                    ?.let(::setCustomCacheKey)
            }
            .build()
        player.setMediaItem(mediaItem)
        player.playWhenReady = autoPlay
        player.prepare()
        if (autoPlay) player.play()
        publishPlaybackState()
    }

    fun pause() {
        if (released) return
        player.playWhenReady = false
        player.pause()
        publishPlaybackState(forcePlaying = false)
        emitProgress()
    }

    fun resume() {
        if (released || player.currentMediaItem == null) return

        if (player.playbackState == Player.STATE_ENDED) {
            completionReported = false
            player.seekTo(0L)
        }
        player.playWhenReady = true
        player.play()
        publishPlaybackState()
    }

    fun stop() {
        if (released) return
        progressJob?.cancel()
        completionReported = false
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        currentStreamUrl = null
        publishPlaybackState(forcePlaying = false)
        onPlaybackProgressChanged?.invoke(0L, 0L)
    }

    fun seekTo(positionMs: Long) {
        if (released || player.currentMediaItem == null) return

        val duration = getDuration()
        val target = positionMs.coerceAtLeast(0L).let { requested ->
            if (duration > 0L) requested.coerceAtMost(duration) else requested
        }
        val shouldPlay = player.isPlaying || player.playWhenReady
        player.seekTo(target)
        player.playWhenReady = shouldPlay
        if (shouldPlay) player.play()
        emitProgress()
    }

    /**
     * The underlying Media3 player, exposed only so a [MediaSession] can be built
     * over the very same instance the app is already driving.
     *
     * Handing the session a second player would give the system controls their own
     * independent playback state, which is exactly how notification transport ends
     * up disagreeing with the in-app player.
     */
    val mediaPlayer: Player get() = player

    fun getCurrentPosition(): Long = if (released) 0L else player.currentPosition.coerceAtLeast(0L)

    fun getDuration(): Long = if (released) 0L else player.duration.takeIf { it > 0L } ?: 0L

    fun hasMediaItem(): Boolean = !released && player.currentMediaItem != null

    /** True while actively playing or buffering with play intent preserved. */
    fun isPlaying(): Boolean = !released && (
        player.isPlaying ||
            (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
        )

    fun setOnPlaybackStateChanged(callback: (Boolean) -> Unit) {
        onPlaybackStateChanged = callback
    }

    fun setOnPlaybackProgressChanged(callback: (Long, Long) -> Unit) {
        onPlaybackProgressChanged = callback
    }

    fun setOnPlaybackError(callback: (String) -> Unit) {
        onPlaybackError = callback
    }

    fun setOnPlaybackCompleted(callback: () -> Unit) {
        onPlaybackCompleted = callback
    }

    fun release() {
        if (released) return
        released = true
        progressJob?.cancel()
        player.release()
        currentStreamUrl = null
        onPlaybackStateChanged = null
        onPlaybackProgressChanged = null
        onPlaybackError = null
        onPlaybackCompleted = null
        scope.cancel()
    }

    private fun publishPlaybackState(forcePlaying: Boolean? = null) {
        if (released) return
        val playing = forcePlaying ?: isPlaying()
        onPlaybackStateChanged?.invoke(playing)
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
            startProgressUpdates()
        } else {
            progressJob?.cancel()
        }
    }

    private fun emitProgress() {
        if (released) return
        onPlaybackProgressChanged?.invoke(getCurrentPosition(), getDuration())
    }

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive && !released && player.playWhenReady &&
                player.playbackState != Player.STATE_ENDED
            ) {
                emitProgress()
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private companion object {
        const val TAG = "SpotKofiAudio"
        const val USER_AGENT = "SpotKofi/1.0 (Android)"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MIN_BUFFER_MS = 15_000
        const val MAX_BUFFER_MS = 60_000
        const val BUFFER_FOR_PLAYBACK_MS = 1_500
        const val BUFFER_FOR_REBUFFER_MS = 5_000
        const val PROGRESS_INTERVAL_MS = 500L
    }
}
