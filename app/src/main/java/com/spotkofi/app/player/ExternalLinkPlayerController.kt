package com.spotkofi.app.player

import android.content.Context
import com.spotkofi.app.core.ExternalLinkLauncher
import com.spotkofi.app.data.model.PlaybackState
import com.spotkofi.app.data.model.RepeatMode
import com.spotkofi.app.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * PlayerController for catalogs that cannot provide licensed in-app streams.
 *
 * Selecting a track records it for the existing mini/Now Playing UI and hands the
 * official YouTube search URL to Android. No media URL is extracted or embedded.
 */
class ExternalLinkPlayerController(context: Context) : PlayerController {

    private val launcher = ExternalLinkLauncher(context)
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var queue: List<Track> = emptyList()
    private var indexInQueue = 0

    override fun play(track: Track, queue: List<Track>) {
        this.queue = queue.ifEmpty { listOf(track) }
        indexInQueue = this.queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        val selected = this.queue[indexInQueue]
        _state.update {
            it.copy(
                track = selected,
                isPlaying = false,
                positionMs = 0L,
                streamDurationMs = null,
                error = null,
                deviceName = null,
            )
        }
        if (!launcher.open(selected)) {
            _state.update { it.copy(error = "Could not open YouTube on this device") }
        }
    }

    override fun togglePlayPause() {
        _state.value.track?.let(::playExternal)
    }

    override fun next() {
        if (queue.isEmpty()) return
        val nextIndex = indexInQueue + 1
        indexInQueue = when {
            nextIndex in queue.indices -> nextIndex
            _state.value.repeatMode == RepeatMode.All -> 0
            else -> return
        }
        playExternal(queue[indexInQueue])
    }

    override fun previous() {
        if (queue.isEmpty()) return
        indexInQueue = if (indexInQueue > 0) indexInQueue - 1 else queue.lastIndex
        playExternal(queue[indexInQueue])
    }

    override fun seekToFraction(fraction: Float) {
        // External YouTube playback owns its own seek bar.
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
        queue = emptyList()
        indexInQueue = 0
        _state.value = PlaybackState()
    }

    fun release() = stop()

    private fun playExternal(track: Track) {
        _state.update { it.copy(track = track, isPlaying = false, positionMs = 0L, error = null) }
        if (!launcher.open(track)) {
            _state.update { it.copy(error = "Could not open YouTube on this device") }
        }
    }
}
