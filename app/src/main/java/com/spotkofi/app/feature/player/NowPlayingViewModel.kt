package com.spotkofi.app.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.TrackDetails
import com.spotkofi.app.data.local.SettingsStore
import com.spotkofi.app.data.repository.MusicRepository
import com.spotkofi.app.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val playbackState = player.state

    private val _details = MutableStateFlow<TrackDetails?>(null)
    val details: StateFlow<TrackDetails?> = _details.asStateFlow()

    private val _isDetailsLoading = MutableStateFlow(false)
    val isDetailsLoading: StateFlow<Boolean> = _isDetailsLoading.asStateFlow()

    init {
        viewModelScope.launch {
            // A provider change is a real data change even when the track id stays
            // the same, so changing Settings immediately refreshes the visible sheet.
            val trackFlow = player.state
                .map { it.track }
                .distinctUntilChanged { old, new -> old?.id == new?.id }
            val lyricsConfig = settingsStore.settings
                .map { Triple(it.lyricsEnabled, it.lyricsProvider, it.contentRegion) }
                .distinctUntilChanged()
            combine(trackFlow, lyricsConfig) { track, _ -> track }
                .collect { track ->
                    if (track == null) {
                        _isDetailsLoading.value = false
                        _details.value = null
                        return@collect
                    }

                    _isDetailsLoading.value = true
                    try {
                        _details.value = repository.trackDetails(track)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // The player remains usable even when an enrichment provider
                        // is offline; the loading skeleton simply gives way to the
                        // controls and any data that was already available.
                        _details.value = null
                    } finally {
                        _isDetailsLoading.value = false
                    }
                }
        }
    }

    fun onTogglePlayPause() = player.togglePlayPause()
    fun onNext() = player.next()
    fun onPrevious() = player.previous()
    fun onSeek(fraction: Float) = player.seekToFraction(fraction)
    fun onSeekTo(positionMs: Long) = player.seekTo(positionMs)
    fun onToggleShuffle() = player.toggleShuffle()
    fun onCycleRepeat() = player.cycleRepeatMode()
    fun onToggleSaved() = player.toggleSaved()

    /**
     * Plays a track picked from one of the related-content rows.
     *
     * The surrounding list becomes the queue, so next/previous walks the album or
     * the artist's other work rather than dead-ending on a single track.
     */
    fun onPlayTrack(track: Track) {
        val current = _details.value
        val currentTrack = player.state.value.track
        fun queueWithCurrent(candidates: List<Track>): List<Track> = buildList {
            currentTrack?.let(::add)
            addAll(candidates)
        }.distinctBy { it.id }

        val queue = when {
            current == null -> listOf(track)
            current.albumTracks.any { it.id == track.id } ->
                queueWithCurrent(current.albumTracks)

            current.moreByArtist.any { it.id == track.id } ->
                queueWithCurrent(current.moreByArtist)

            current.recommendations.any { it.id == track.id } ->
                queueWithCurrent(current.recommendations)

            else -> listOf(track)
        }
        player.play(track, queue)
    }
}
