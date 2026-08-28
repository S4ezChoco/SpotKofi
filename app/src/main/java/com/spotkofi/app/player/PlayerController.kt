package com.spotkofi.app.player

import com.spotkofi.app.data.model.PlaybackState
import com.spotkofi.app.data.model.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * Playback handoff surface used by the mini player and Now Playing page.
 *
 * The current catalog does not grant licensed in-app streams. Implementations
 * therefore record the selected track and may hand its official provider URL to
 * Android rather than extracting or embedding protected media.
 */
interface PlayerController {
    val state: StateFlow<PlaybackState>

    /** Opens [track]'s official provider page, using [queue] for related handoffs. */
    fun play(track: Track, queue: List<Track> = listOf(track))

    fun togglePlayPause()
    fun next()
    fun previous()
    fun seekToFraction(fraction: Float)
    fun toggleShuffle()
    fun cycleRepeatMode()
    fun toggleSaved()
    fun stop()
}
