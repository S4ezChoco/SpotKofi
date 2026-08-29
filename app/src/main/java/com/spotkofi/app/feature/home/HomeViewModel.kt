package com.spotkofi.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotkofi.app.data.model.HomeSection
import com.spotkofi.app.data.model.HomeTab
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.PlaybackState
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.repository.MusicRepository
import com.spotkofi.app.player.PlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class HomeViewModel(
    private val repository: MusicRepository,
    private val player: PlayerController,
) : ViewModel() {

    data class UiState(
        val userName: String = "",
        val selectedChip: HomeTab = HomeTab.All,
        /**
         * Following is a sub-filter of Music rather than a peer chip: it only
         * appears once Music is active, and when both are lit the feed replaces
         * the shelves entirely.
         */
        val followingActive: Boolean = false,
        val quickPicks: List<MediaCollection> = emptyList(),
        val sections: List<HomeSection> = emptyList(),
        val isLoading: Boolean = true,
        /**
         * Set when the catalog request failed.
         *
         * Needed now that this is real network: mock data could not fail, so a
         * spinner was the only state a screen ever had to show.
         */
        val error: String? = null,
    ) {
        /** Following only exists as an option while Music is the active chip. */
        val followingVisible: Boolean get() = selectedChip == HomeTab.Music

        /** The quick-pick grid is not part of the Following feed. */
        val showQuickPicks: Boolean get() = !followingActive
    }

    private val _uiState = MutableStateFlow(UiState(userName = repository.currentUserName()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        val state = _uiState.value
        val tab = if (state.followingActive) HomeTab.Following else state.selectedChip

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Both are network calls now, so they run concurrently rather than
                // stacking two round trips before Home can paint. A supervisor keeps
                // one failed catalog request from cancelling the whole ViewModel
                // before the surrounding error state can be updated.
                supervisorScope {
                    val picks = async { repository.quickPicks() }
                    val sections = async { repository.homeSections(tab) }
                    _uiState.update {
                        it.copy(
                            quickPicks = picks.await(),
                            sections = sections.await(),
                            isLoading = false,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                // A tab switch or ViewModel teardown cancelled this load; the new
                // load or lifecycle owns the state now.
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = failure.message ?: "Could not load music",
                    )
                }
            }
        }
    }

    fun onChipClick(chip: HomeTab) {
        _uiState.update { current ->
            when (chip) {
                // Toggling Following keeps Music selected underneath.
                HomeTab.Following -> current.copy(followingActive = !current.followingActive)
                // Leaving Music also clears Following, otherwise the feed would
                // stay visible with no chip explaining why.
                else -> current.copy(selectedChip = chip, followingActive = false)
            }
        }
        load()
    }

    /** Playback state, so a Home song row can mark itself as the active track. */
    val playbackState: StateFlow<PlaybackState> = player.state

    /**
     * Plays a Home recommendation, keeping the rest of its shelf as the queue so
     * the song rolls into related tracks instead of stopping after one.
     */
    fun onPlayTrack(track: Track, queue: List<Track> = listOf(track)) {
        player.play(track, queue.ifEmpty { listOf(track) })
    }
}
