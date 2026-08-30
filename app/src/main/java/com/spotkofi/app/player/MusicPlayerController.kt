package com.spotkofi.app.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import com.spotkofi.app.data.local.AppSettings
import com.spotkofi.app.data.local.AudioQuality
import com.spotkofi.app.data.local.LocalMusicStore
import com.spotkofi.app.data.model.PlaybackState
import com.spotkofi.app.data.model.PlaybackStatus
import com.spotkofi.app.data.model.RepeatMode
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.remote.YouTubeTrackResolver
import com.spotkofi.app.data.repository.MusicRepository
import com.spotkofi.app.data.service.MusicService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Coordinates queue state, stream resolution, and the Media3-backed player.
 *
 * Stream resolution is deliberately separate from Media3 preparation: a new
 * selection cancels the previous resolver and a generation check prevents a
 * slow old response from replacing the track the user selected most recently.
 */
class MusicPlayerController(
    context: Context,
    private val musicService: MusicService,
    private val localStore: LocalMusicStore,
    private val musicRepository: MusicRepository? = null,
    dataSourceFactory: DataSource.Factory? = null,
    /**
     * Read lazily rather than captured, so toggling a playback preference takes
     * effect on the next action instead of the next launch.
     */
    private val settingsProvider: () -> AppSettings = { AppSettings() },
) : PlayerController, QueueController {

    /**
     * Constructed here rather than injected: the resolver's type is internal to
     * the module, and exposing it through this public constructor would leak an
     * internal type into the public API.
     */
    private val trackResolver = YouTubeTrackResolver()

    private val appContext = context.applicationContext
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioPlayer = AudioPlayer(context, dataSourceFactory)

    /**
     * The player handed to the platform [MediaSession].
     *
     * The decoder only ever holds the one current item, so Media3 would report no
     * next/previous item and the system controls would grey those buttons out.
     * This wrapper advertises them and routes them into the app's own queue, so
     * the notification, lock screen and dynamic island drive the same queue the
     * in-app player uses.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    val mediaSessionPlayer: Player by lazy {
        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        object : ForwardingPlayer(audioPlayer.mediaPlayer) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands()
                    .buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()

            override fun isCommandAvailable(command: Int): Boolean = when (command) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                -> true

                else -> super.isCommandAvailable(command)
            }

            override fun hasNextMediaItem(): Boolean = _queue.value.size > 1

            override fun hasPreviousMediaItem(): Boolean = _queue.value.size > 1

            override fun seekToNext() = next()

            override fun seekToNextMediaItem() = next()

            override fun seekToPrevious() = previous()

            override fun seekToPreviousMediaItem() = previous()
        }
    }
    private var activePlaybackJob: Job? = null
    private var relatedLoading = false
    private var playbackGeneration = 0L
    private var currentTrackIndex = 0
    private var playbackIntent = false
    private var currentVideoId: String? = null
    private var currentStreamQuality: AudioQuality? = null
    private var released = false

    /**
     * Bumped only by [play], never by queue advances.
     *
     * The UI uses this to tell "the user picked this track" apart from "the queue
     * moved on by itself", so finishing a song no longer throws the full player
     * window back open over whatever the user was doing.
     */
    private var playRequestId = 0L

    /** Short-lived in-memory cache; resolved YouTube URLs are not metadata IDs. */
    private val streamUrlCache = linkedMapOf<String, String>()

    /** Serialized queue writes keep rapid reorder/remove actions in their visible order. */
    private var queueWriteJob: Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    override val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    /**
     * Restores the last queue before accepting a user mutation.
     *
     * The job is always created, even when restoring is switched off, because every
     * queue mutation joins it. Skipping the read rather than skipping the job keeps
     * those call sites correct without each of them having to know about the setting.
     */
    private val restoreJob = coroutineScope.launch {
        if (!settingsProvider().restoreQueueOnStart) {
            // The stored queue is left on disk. Turning the setting back on should
            // bring the queue back rather than have silently discarded it.
            return@launch
        }
        val restored = localStore.loadQueue()
        if (!released && _queue.value.isEmpty() && restored.isNotEmpty()) {
            _queue.value = restored
        }
    }

    init {
        audioPlayer.setOnPlaybackStateChanged { status ->
            if (!released) {
                _state.update { current ->
                    current.copy(
                        status = status,
                        isPlaying = status == PlaybackStatus.Playing,
                    )
                }
                // Started only once audio is genuinely playing. Starting it at
                // resolve time would leave a foreground service with nothing to
                // show while the stream URL is still being fetched.
                if (status == PlaybackStatus.Playing) ensurePlaybackService()
            }
        }

        // Saved state is observed rather than snapshotted. It used to be copied
        // into PlaybackState when a track started and then only flipped by
        // toggleSaved, so saving the playing song from any list screen left the
        // player and the mini player showing the opposite of the truth.
        coroutineScope.launch {
            localStore.savedTracks.collect { saved ->
                if (released) return@collect
                _state.update { current ->
                    val trackId = current.track?.id
                    val isSaved = trackId != null && saved.any { it.id == trackId }
                    if (current.isSaved == isSaved) current else current.copy(isSaved = isSaved)
                }
            }
        }

        audioPlayer.setOnPlaybackProgressChanged { positionMs, durationMs ->
            if (!released) {
                _state.update { current ->
                    current.copy(
                        positionMs = positionMs,
                        streamDurationMs = durationMs.takeIf { it > 0L }
                            ?: current.streamDurationMs,
                    )
                }
            }
        }

        audioPlayer.setOnPlaybackError { error ->
            if (!released) {
                currentVideoId?.let { videoId ->
                    streamUrlCache.remove(
                        streamCacheKey(
                            videoId,
                            currentStreamQuality ?: settingsProvider().audioQuality,
                        ),
                    )
                }
                playbackIntent = false
                _state.update { it.copy(isPlaying = false, status = PlaybackStatus.Error, error = error) }
            }
        }

        audioPlayer.setOnPlaybackCompleted {
            handlePlaybackCompleted()
        }
    }

    override fun play(track: Track, queue: List<Track>) {
        coroutineScope.launch {
            if (released) return@launch
            restoreJob.join()

            val requestedQueue = queue.ifEmpty { listOf(track) }.distinctBy { it.id }
            val normalizedQueue = if (requestedQueue.any { it.id == track.id }) {
                requestedQueue
            } else {
                listOf(track) + requestedQueue
            }
            _queue.value = normalizedQueue
            currentTrackIndex = normalizedQueue.indexOfFirst { it.id == track.id }
                .coerceAtLeast(0)
            playbackIntent = true
            persistQueue()
            // Only an explicit selection counts as intent to see the player.
            playRequestId++
            requestPlayback(track)
        }
    }

    override fun addToQueue(track: Track) {
        coroutineScope.launch {
            if (released) return@launch
            restoreJob.join()
            _queue.update { it + track }
            persistQueue()
        }
    }

    override fun playNext(track: Track) {
        coroutineScope.launch {
            if (released) return@launch
            restoreJob.join()
            _queue.update { current ->
                val insertionIndex = (currentTrackIndex + 1).coerceIn(0, current.size)
                current.toMutableList().apply { add(insertionIndex, track) }
            }
            persistQueue()
        }
    }

    override fun removeFromQueue(trackId: String) {
        coroutineScope.launch {
            if (released) return@launch
            restoreJob.join()
            removeQueueEntry(_queue.value.indexOfFirst { it.id == trackId })
        }
    }

    override fun removeFromQueueAt(index: Int) {
        coroutineScope.launch {
            if (released) return@launch
            restoreJob.join()
            removeQueueEntry(index)
        }
    }

    private fun removeQueueEntry(index: Int) {
        val current = _queue.value
        if (index !in current.indices) return
        val next = current.toMutableList().apply { removeAt(index) }
        if (index < currentTrackIndex) currentTrackIndex--
        _queue.value = next
        if (next.isEmpty()) {
            stopInternal()
        } else {
            currentTrackIndex = currentTrackIndex.coerceIn(0, next.lastIndex)
        }
        persistQueue()
    }

    override fun moveInQueue(from: Int, to: Int) {
        coroutineScope.launch {
            if (released) return@launch
            restoreJob.join()
            val current = _queue.value
            if (from !in current.indices || to !in current.indices || from == to) return@launch
            val mutable = current.toMutableList()
            val moved = mutable.removeAt(from)
            mutable.add(to, moved)
            currentTrackIndex = when {
                currentTrackIndex == from -> to
                from < currentTrackIndex && to >= currentTrackIndex -> currentTrackIndex - 1
                from > currentTrackIndex && to <= currentTrackIndex -> currentTrackIndex + 1
                else -> currentTrackIndex
            }
            _queue.value = mutable
            persistQueue()
        }
    }

    override fun clearQueue() {
        coroutineScope.launch {
            if (released) return@launch
            restoreJob.join()
            val current = _state.value.track
            _queue.value = current?.let(::listOf).orEmpty()
            currentTrackIndex = 0
            persistQueue()
        }
    }

    override fun togglePlayPause() {
        coroutineScope.launch {
            if (released || _state.value.track == null) return@launch

            if (playbackIntent) {
                playbackIntent = false
                audioPlayer.pause()
                _state.update { it.copy(isPlaying = false, status = PlaybackStatus.Paused) }
            } else {
                playbackIntent = true
                when {
                    audioPlayer.hasMediaItem() -> audioPlayer.resume()
                    activePlaybackJob?.isActive == true -> Unit
                    else -> _state.value.track?.let(::requestPlayback)
                }
            }
        }
    }

    override fun next() {
        coroutineScope.launch {
            if (released) return@launch
            val currentQueue = _queue.value
            if (currentQueue.isEmpty()) return@launch

            if (currentTrackIndex < currentQueue.lastIndex) {
                currentTrackIndex++
                playbackIntent = true
                requestPlayback(currentQueue[currentTrackIndex])
            } else if (_state.value.repeatMode == RepeatMode.All) {
                currentTrackIndex = 0
                playbackIntent = true
                requestPlayback(currentQueue[currentTrackIndex])
            } else {
                loadRelatedAndPlay()
            }
        }
    }

    /**
     * Extends a finite queue when the user reaches its end. The selected album,
     * search, or playlist order remains first; only then do we ask the repository
     * for same-artist/related material using the current content region.
     */
    private suspend fun loadRelatedAndPlay() {
        if (relatedLoading) return
        val seed = _state.value.track ?: return
        val repository = musicRepository ?: run {
            _state.update {
                it.copy(
                    isPlaying = false,
                    status = PlaybackStatus.Paused,
                    error = "No related songs are available",
                )
            }
            playbackIntent = false
            return
        }

        relatedLoading = true
        _state.update { it.copy(status = PlaybackStatus.Resolving, error = null) }
        try {
            val details = repository.trackDetails(seed)
            val existingIds = _queue.value.asSequence().map { it.id }.toHashSet()
            val additions = (details.recommendations + details.moreByArtist + details.albumTracks)
                .asSequence()
                .filter { it.id != seed.id && existingIds.add(it.id) }
                .take(12)
                .toList()

            if (additions.isEmpty()) {
                playbackIntent = false
                _state.update {
                    it.copy(
                        isPlaying = false,
                        status = PlaybackStatus.Paused,
                        error = "No related songs were found",
                    )
                }
                return
            }

            val expandedQueue = _queue.value + additions
            _queue.value = expandedQueue
            currentTrackIndex = (currentTrackIndex + 1).coerceAtMost(expandedQueue.lastIndex)
            playbackIntent = true
            persistQueue()
            requestPlayback(expandedQueue[currentTrackIndex])
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "Unable to load related songs", failure)
            playbackIntent = false
            _state.update {
                it.copy(
                    isPlaying = false,
                    status = PlaybackStatus.Paused,
                    error = "Could not load the next song",
                )
            }
        } finally {
            relatedLoading = false
        }
    }

    override fun previous() {
        coroutineScope.launch {
            if (released) return@launch
            val currentQueue = _queue.value
            if (currentQueue.isEmpty()) return@launch

            if (_state.value.positionMs > PREVIOUS_RESTART_THRESHOLD_MS) {
                // Seeking the existing decoder avoids another network resolve and
                // preserves whether the user was playing or paused.
                audioPlayer.seekTo(0L)
                _state.update { it.copy(positionMs = 0L) }
                return@launch
            }

            if (currentTrackIndex > 0) {
                currentTrackIndex--
                playbackIntent = true
                requestPlayback(currentQueue[currentTrackIndex])
            } else {
                audioPlayer.seekTo(0L)
                _state.update { it.copy(positionMs = 0L) }
            }
        }
    }

    override fun seekToFraction(fraction: Float) {
        coroutineScope.launch {
            if (released) return@launch
            val duration = _state.value.effectiveDurationMs
            if (duration <= 0L) return@launch

            val position = (duration * fraction.coerceIn(0f, 1f)).toLong()
            // AudioPlayer preserves playWhenReady, so a seek made while paused
            // remains paused and a seek made while playing resumes after rebuffer.
            audioPlayer.seekTo(position)
        }
    }

    override fun seekTo(positionMs: Long) {
        coroutineScope.launch {
            if (!released) audioPlayer.seekTo(positionMs)
        }
    }

    override fun toggleShuffle() {
        coroutineScope.launch {
            if (released) return@launch
            val newShuffled = !_state.value.isShuffled
            val currentTrack = _state.value.track
            val currentQueue = _queue.value

            if (newShuffled && currentQueue.size > 1) {
                val shuffledQueue = currentQueue.shuffled()
                val reordered = if (currentTrack == null) {
                    shuffledQueue
                } else {
                    listOf(currentTrack) + shuffledQueue.filterNot { it.id == currentTrack.id }
                }
                _queue.value = reordered
                currentTrackIndex = reordered.indexOfFirst { it.id == currentTrack?.id }
                    .takeIf { it >= 0 } ?: 0
            }

            _state.update { it.copy(isShuffled = newShuffled) }
            persistQueue()
        }
    }

    override fun cycleRepeatMode() {
        coroutineScope.launch {
            if (released) return@launch
            val nextMode = when (_state.value.repeatMode) {
                RepeatMode.Off -> RepeatMode.All
                RepeatMode.All -> RepeatMode.One
                RepeatMode.One -> RepeatMode.Off
            }
            _state.update { it.copy(repeatMode = nextMode) }
        }
    }

    override fun toggleSaved() {
        coroutineScope.launch {
            if (released) return@launch
            val track = _state.value.track ?: return@launch
            // Writes only to the store. The savedTracks collector above is what
            // updates the flag, so every surface reads one source of truth.
            if (localStore.isTrackSaved(track.id)) {
                localStore.removeTrack(track.id)
            } else {
                localStore.saveTrack(track)
            }
        }
    }

    override fun stop() {
        coroutineScope.launch {
            if (!released) stopInternal()
        }
    }

    /** Releases the resolver scope and the Media3 decoder. Safe to call twice. */
    fun release() {
        if (released) return
        released = true
        playbackGeneration++
        activePlaybackJob?.cancel()
        queueWriteJob?.cancel()
        queueWriteJob = null
        activePlaybackJob = null
        playbackIntent = false
        currentVideoId = null
        currentStreamQuality = null
        streamUrlCache.clear()
        audioPlayer.release()
        coroutineScope.cancel()
    }

    private fun requestPlayback(track: Track) {
        if (released) return

        activePlaybackJob?.cancel()
        val generation = ++playbackGeneration
        audioPlayer.stop()
        currentVideoId = track.videoId?.trim()?.takeIf { it.isNotEmpty() }
        currentStreamQuality = null
        _state.update {
            it.copy(
                track = track,
                status = PlaybackStatus.Resolving,
                isPlaying = false,
                positionMs = 0L,
                error = null,
                streamDurationMs = track.durationMs.takeIf { duration -> duration > 0L },
                isSaved = localStore.isTrackSaved(track.id),
                playRequestId = playRequestId,
            )
        }
        localStore.recordPlayed(track)

        activePlaybackJob = coroutineScope.launch {
            val resolved = try {
                resolveStream(track)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Unable to resolve ${track.title}", error)
                if (isCurrent(generation)) {
                    playbackIntent = false
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            status = PlaybackStatus.Error,
                            error = "Unable to resolve audio: ${error.message.orEmpty()}",
                        )
                    }
                }
                return@launch
            }

            if (!isCurrent(generation)) return@launch

            if (resolved == null) {
                playbackIntent = false
                _state.update {
                    it.copy(
                        isPlaying = false,
                        status = PlaybackStatus.Error,
                        error = "No full-length stream was found for this track",
                    )
                }
                return@launch
            }

            resolved.videoId?.let { videoId ->
                currentVideoId = videoId
                currentStreamQuality = resolved.quality
                cacheStreamUrl(videoId, resolved.url, resolved.quality)
            } ?: run {
                // A file URI is already the durable offline source; it has no
                // remote video identity and must not inherit one from the track.
                currentVideoId = null
                currentStreamQuality = null
            }
            audioPlayer.play(
                resolved.url,
                autoPlay = playbackIntent,
                cacheKey = resolved.cacheKey,
                title = track.title,
                artist = track.artistName,
                album = track.albumTitle,
                artworkUrl = track.artworkUrl,
            )
        }
    }

    /**
     * Resolves the audio to hand the decoder.
     *
     * Tracks from the iTunes catalog carry no video ID, so one is looked up on
     * demand. There is deliberately no 30-second preview fallback: playing a
     * clip silently instead of the song is the bug this replaced.
     */
    private suspend fun resolveStream(track: Track): ResolvedStream? {
        val downloadedFile = localStore.downloadedFile(track.id)
        if (downloadedFile != null) {
            // A local file must not reuse the remote video's cache key. The
            // playback cache is keyed by resolved remote streams, while this
            // URI already points at the user's durable offline bytes.
            return ResolvedStream(
                videoId = null,
                url = Uri.fromFile(File(downloadedFile)).toString(),
                quality = AudioQuality.Automatic,
            )
        }

        val contentRegion = settingsProvider().contentRegion
        val audioQuality = settingsProvider().audioQuality
        val videoId = withContext(Dispatchers.IO) {
            trackResolver.resolveVideoId(track, country = contentRegion)
        }

        if (videoId != null) {
            val cacheKey = streamCacheKey(videoId, audioQuality)
            streamUrlCache[cacheKey]?.let { cached ->
                return ResolvedStream(videoId, cached, audioQuality)
            }
            val url = withContext(Dispatchers.IO) {
                musicService.getStreamUrl(videoId, audioQuality)
            }
            return url?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { ResolvedStream(videoId, it, audioQuality) }
        }

        // Only a genuine direct/offline URL. The catalog mapper no longer supplies
        // preview clips here, so this can never degrade a song to 0:30.
        return track.audioUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                ResolvedStream(
                    videoId = null,
                    url = it,
                    quality = AudioQuality.Automatic,
                )
            }
    }

    private data class ResolvedStream(
        val videoId: String?,
        val url: String,
        val quality: AudioQuality,
    ) {
        /** Media3’s byte cache must distinguish streams selected at each quality. */
        val cacheKey: String?
            get() = videoId?.let { "$it:${quality.key}" }
    }

    private fun handlePlaybackCompleted() {
        if (released) return

        val currentQueue = _queue.value
        if (currentQueue.isEmpty()) {
            playbackIntent = false
            return
        }

        when (_state.value.repeatMode) {
            RepeatMode.One -> {
                currentQueue.getOrNull(currentTrackIndex)?.let { track ->
                    playbackIntent = true
                    requestPlayback(track)
                }
            }

            RepeatMode.All -> {
                currentTrackIndex = (currentTrackIndex + 1) % currentQueue.size
                playbackIntent = true
                requestPlayback(currentQueue[currentTrackIndex])
            }

            RepeatMode.Off -> {
                if (currentTrackIndex < currentQueue.lastIndex) {
                    currentTrackIndex++
                    playbackIntent = true
                    requestPlayback(currentQueue[currentTrackIndex])
                } else {
                    // Keep the current queue order intact and extend it only after
                    // the user has consumed the explicit list.
                    coroutineScope.launch { loadRelatedAndPlay() }
                }
            }
        }
    }

    private fun stopInternal() {
        activePlaybackJob?.cancel()
        activePlaybackJob = null
        playbackGeneration++
        playbackIntent = false
        currentVideoId = null
        currentStreamQuality = null
        audioPlayer.stop()
        _queue.value = emptyList()
        currentTrackIndex = 0
        persistQueue()
        _state.update {
            it.copy(
                track = null,
                status = PlaybackStatus.Idle,
                isPlaying = false,
                positionMs = 0L,
                streamDurationMs = null,
                error = null,
            )
        }
    }

    private fun persistQueue() {
        val snapshot = _queue.value
        val previous = queueWriteJob
        queueWriteJob = coroutineScope.launch {
            previous?.join()
            if (!released) localStore.saveQueue(snapshot)
        }
    }

    /**
     * Promotes the process to a foreground service for the duration of playback.
     *
     * Without it, audio is only as durable as the visible activity: minimising the
     * app makes the process a background candidate and the system is free to kill
     * it mid-song.
     */
    private fun ensurePlaybackService() {
        runCatching {
            ContextCompat.startForegroundService(appContext, PlaybackService.intent(appContext))
        }
    }

    private fun isCurrent(generation: Long): Boolean =
        !released && generation == playbackGeneration

    private fun cacheStreamUrl(videoId: String, url: String, quality: AudioQuality) {
        val key = streamCacheKey(videoId, quality)
        streamUrlCache.remove(key)
        streamUrlCache[key] = url
        while (streamUrlCache.size > MAX_CACHED_STREAMS) {
            streamUrlCache.remove(streamUrlCache.keys.first())
        }
    }

    private fun streamCacheKey(videoId: String, quality: AudioQuality): String =
        "$videoId:${quality.key}"

    private companion object {
        const val TAG = "SpotKofiPlayer"
        const val PREVIOUS_RESTART_THRESHOLD_MS = 5_000L
        const val MAX_CACHED_STREAMS = 12
    }
}
