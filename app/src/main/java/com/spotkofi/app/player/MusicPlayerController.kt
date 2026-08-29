package com.spotkofi.app.player

import android.content.Context
import android.util.Log
import com.spotkofi.app.data.model.PlaybackState
import com.spotkofi.app.data.model.RepeatMode
import com.spotkofi.app.data.model.Track
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
) : PlayerController {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioPlayer = AudioPlayer(context)
    private var activePlaybackJob: Job? = null
    private var playbackGeneration = 0L
    private var currentTrackIndex = 0
    private var playbackIntent = false
    private var currentVideoId: String? = null
    private var released = false

    /** Short-lived in-memory cache; resolved YouTube URLs are not metadata IDs. */
    private val streamUrlCache = linkedMapOf<String, String>()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    init {
        audioPlayer.setOnPlaybackStateChanged { isPlaying ->
            if (!released) {
                _state.update { it.copy(isPlaying = isPlaying) }
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
                currentVideoId?.let(streamUrlCache::remove)
                playbackIntent = false
                _state.update { it.copy(isPlaying = false, error = error) }
            }
        }

        audioPlayer.setOnPlaybackCompleted {
            handlePlaybackCompleted()
        }
    }

    override fun play(track: Track, queue: List<Track>) {
        coroutineScope.launch {
            if (released) return@launch

            val requestedQueue = queue.ifEmpty { listOf(track) }
            val normalizedQueue = if (requestedQueue.any { it.id == track.id }) {
                requestedQueue
            } else {
                listOf(track) + requestedQueue
            }
            _queue.value = normalizedQueue
            currentTrackIndex = normalizedQueue.indexOfFirst { it.id == track.id }
                .coerceAtLeast(0)
            playbackIntent = true
            requestPlayback(track)
        }
    }

    override fun togglePlayPause() {
        coroutineScope.launch {
            if (released || _state.value.track == null) return@launch

            if (playbackIntent) {
                playbackIntent = false
                audioPlayer.pause()
                _state.update { it.copy(isPlaying = false) }
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
            } else if (_state.value.repeatMode == RepeatMode.All || currentQueue.size == 1) {
                currentTrackIndex = 0
                playbackIntent = true
                requestPlayback(currentQueue[currentTrackIndex])
            } else {
                stopInternal()
            }
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
            if (!released) _state.update { it.copy(isSaved = !it.isSaved) }
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
        activePlaybackJob = null
        playbackIntent = false
        currentVideoId = null
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
        _state.update {
            it.copy(
                track = track,
                isPlaying = false,
                positionMs = 0L,
                error = null,
                streamDurationMs = track.durationMs.takeIf { duration -> duration > 0L },
            )
        }

        activePlaybackJob = coroutineScope.launch {
            val urlToPlay = try {
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
                            error = "Unable to resolve audio: ${error.message.orEmpty()}",
                        )
                    }
                }
                return@launch
            }

            if (!isCurrent(generation)) return@launch

            if (urlToPlay.isNullOrBlank()) {
                playbackIntent = false
                _state.update {
                    it.copy(
                        isPlaying = false,
                        error = if (track.videoId.isNullOrBlank()) {
                            "No playable audio is available for this track"
                        } else {
                            "Full-length stream unavailable; preview was not used"
                        },
                    )
                }
                return@launch
            }

            track.videoId?.trim()?.takeIf { it.isNotEmpty() }?.let { videoId ->
                cacheStreamUrl(videoId, urlToPlay)
            }
            audioPlayer.play(urlToPlay, autoPlay = playbackIntent)
        }
    }

    private suspend fun resolveStream(track: Track): String? {
        val videoId = track.videoId?.trim()?.takeIf { it.isNotEmpty() }
        return if (videoId != null) {
            streamUrlCache[videoId] ?: withContext(Dispatchers.IO) {
                musicService.getStreamUrl(videoId)
            }
        } else {
            track.audioUrl?.trim()?.takeIf { it.isNotEmpty() }
        }
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
                    playbackIntent = false
                    _state.update { it.copy(isPlaying = false) }
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
        audioPlayer.stop()
        _queue.value = emptyList()
        currentTrackIndex = 0
        _state.update {
            it.copy(
                track = null,
                isPlaying = false,
                positionMs = 0L,
                streamDurationMs = null,
                error = null,
            )
        }
    }

    private fun isCurrent(generation: Long): Boolean =
        !released && generation == playbackGeneration

    private fun cacheStreamUrl(videoId: String, url: String) {
        streamUrlCache.remove(videoId)
        streamUrlCache[videoId] = url
        while (streamUrlCache.size > MAX_CACHED_STREAMS) {
            streamUrlCache.remove(streamUrlCache.keys.first())
        }
    }

    private companion object {
        const val TAG = "SpotKofiPlayer"
        const val PREVIOUS_RESTART_THRESHOLD_MS = 5_000L
        const val MAX_CACHED_STREAMS = 12
    }
}
