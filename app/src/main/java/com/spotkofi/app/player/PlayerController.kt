package com.spotkofi.app.player

import com.spotkofi.app.data.model.PlaybackState
import com.spotkofi.app.data.model.RepeatMode
import com.spotkofi.app.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Playback surface the UI talks to.
 *
 * The mini player and Now Playing screen depend only on this interface, so
 * Phase 5 can drop in a Media3 / `MediaController` implementation without
 * touching a single composable.
 */
interface PlayerController {
    val state: StateFlow<PlaybackState>

    /** Starts [track], using [queue] for next/previous. */
    fun play(track: Track, queue: List<Track> = listOf(track))

    fun togglePlayPause()
    fun next()
    fun previous()

    /** Seeks to a fraction of the track, 0f..1f. */
    fun seekToFraction(fraction: Float)

    fun toggleShuffle()
    fun cycleRepeatMode()
    fun toggleSaved()
    fun stop()
}

/**
 * In-memory [PlayerController] that fakes a playhead with a coroutine ticker.
 *
 * There is no audio. The ticker exists so the progress bar actually moves and
 * tracks auto-advance, which is the only way to judge whether the player UI
 * feels right before real playback is wired up.
 */
class FakePlayerController(
    private val scope: CoroutineScope,
) : PlayerController {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var queue: List<Track> = emptyList()
    private var indexInQueue: Int = 0
    private var ticker: Job? = null

    override fun play(track: Track, queue: List<Track>) {
        this.queue = queue.ifEmpty { listOf(track) }
        indexInQueue = this.queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        _state.update {
            it.copy(
                track = this.queue[indexInQueue],
                isPlaying = true,
                positionMs = 0L,
                // Mock remote target so the mini player's device row is
                // exercised. Real device discovery lands with playback.
                deviceName = MOCK_DEVICE,
            )
        }
        restartTicker()
    }

    override fun togglePlayPause() {
        val playing = !_state.value.isPlaying
        _state.update { it.copy(isPlaying = playing) }
        if (playing) restartTicker() else ticker?.cancel()
    }

    override fun next() = advance(forward = true)

    override fun previous() {
        // Matches the usual convention: restart the track if we are past the
        // three second mark, otherwise step back in the queue.
        if (_state.value.positionMs > RESTART_THRESHOLD_MS) {
            _state.update { it.copy(positionMs = 0L) }
        } else {
            advance(forward = false)
        }
    }

    override fun seekToFraction(fraction: Float) {
        val duration = _state.value.track?.durationMs ?: return
        val target = (duration * fraction.coerceIn(0f, 1f)).toLong()
        _state.update { it.copy(positionMs = target) }
    }

    override fun toggleShuffle() {
        _state.update { it.copy(isShuffled = !it.isShuffled) }
    }

    override fun cycleRepeatMode() {
        _state.update {
            it.copy(
                repeatMode = when (it.repeatMode) {
                    RepeatMode.Off -> RepeatMode.All
                    RepeatMode.All -> RepeatMode.One
                    RepeatMode.One -> RepeatMode.Off
                },
            )
        }
    }

    override fun toggleSaved() {
        _state.update { it.copy(isSaved = !it.isSaved) }
    }

    override fun stop() {
        ticker?.cancel()
        queue = emptyList()
        indexInQueue = 0
        _state.value = PlaybackState()
    }

    private fun advance(forward: Boolean) {
        if (queue.isEmpty()) return

        if (_state.value.repeatMode == RepeatMode.One) {
            _state.update { it.copy(positionMs = 0L, isPlaying = true) }
            restartTicker()
            return
        }

        val step = if (forward) 1 else -1
        val next = indexInQueue + step

        indexInQueue = when {
            next in queue.indices -> next
            // Wrapping is only allowed when repeating the whole queue.
            _state.value.repeatMode == RepeatMode.All ->
                (next + queue.size) % queue.size
            forward -> {
                // Ran off the end with repeat off: hold on the last track, paused.
                ticker?.cancel()
                _state.update { it.copy(isPlaying = false, positionMs = 0L) }
                return
            }
            else -> 0
        }

        _state.update {
            it.copy(track = queue[indexInQueue], positionMs = 0L, isPlaying = true)
        }
        restartTicker()
    }

    private fun restartTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val current = _state.value
                val duration = current.track?.durationMs ?: break
                if (!current.isPlaying) continue

                val position = current.positionMs + TICK_MS
                if (position >= duration) {
                    advance(forward = true)
                    break
                }
                _state.update { it.copy(positionMs = position) }
            }
        }
    }

    private companion object {
        const val TICK_MS = 500L
        const val RESTART_THRESHOLD_MS = 3_000L
        const val MOCK_DEVICE = "SpotKofi Web Player"
    }
}
