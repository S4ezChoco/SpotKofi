package com.spotkofi.app.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotkofi.app.data.model.TrackDetails
import com.spotkofi.app.data.repository.MusicRepository
import com.spotkofi.app.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Backs the Now Playing page.
 *
 * Transport calls are forwarded straight to the [PlayerController]; the only
 * state this owns is the page content below the controls, which is refetched
 * whenever the track changes.
 */
class NowPlayingViewModel(
    private val repository: MusicRepository,
    private val player: PlayerController,
) : ViewModel() {

    val playbackState = player.state

    private val _details = MutableStateFlow<TrackDetails?>(null)
    val details: StateFlow<TrackDetails?> = _details.asStateFlow()

    init {
        viewModelScope.launch {
            // Keyed on track id, so ticking the playhead does not refetch.
            player.state
                .map { it.track }
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .collect { track ->
                    _details.value = track?.let { repository.trackDetails(it) }
                }
        }
    }

    fun onTogglePlayPause() = player.togglePlayPause()
    fun onNext() = player.next()
    fun onPrevious() = player.previous()
    fun onSeek(fraction: Float) = player.seekToFraction(fraction)
    fun onToggleShuffle() = player.toggleShuffle()
    fun onCycleRepeat() = player.cycleRepeatMode()
    fun onToggleSaved() = player.toggleSaved()
}
