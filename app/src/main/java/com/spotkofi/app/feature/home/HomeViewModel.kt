package com.spotkofi.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotkofi.app.data.local.LocalMusicStore
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
    private val localStore: LocalMusicStore? = null,
) : ViewModel() {

    data class UiState(
        val userName: String = "",
        val selectedChip: HomeTab = HomeTab.All,
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
        /** The grid only makes sense while there is something in it. */
        val showQuickPicks: Boolean get() = quickPicks.isNotEmpty()

        /** True when the selected feed came back with nothing at all. */
        val isEmpty: Boolean
            get() = !isLoading && error == null && quickPicks.isEmpty() && sections.isEmpty()
    }

    private val _uiState = MutableStateFlow(UiState(userName = repository.currentUserName()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        val tab = _uiState.value.selectedChip

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
                    val remoteSections = sections.await()
                    val recentTracks = localStore?.history?.value.orEmpty().take(6)
                    val displaySections = if (recentTracks.isEmpty()) {
                        remoteSections
                    } else {
                        listOf(
                            HomeSection.Songs(
                                id = "local_recently_played",
                                title = "Recently played",
                                items = recentTracks,
                            ),
                        ) + remoteSections
                    }
                    _uiState.update {
                        it.copy(
                            quickPicks = picks.await(),
                            sections = displaySections,
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
        // Re-tapping the active chip is a no-op rather than a reload: the feed is
        // already the one being asked for.
        if (_uiState.value.selectedChip == chip) return
        _uiState.update { current -> current.copy(selectedChip = chip) }
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
