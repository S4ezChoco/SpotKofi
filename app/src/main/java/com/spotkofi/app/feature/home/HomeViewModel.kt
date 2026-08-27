package com.spotkofi.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotkofi.app.data.model.HomeSection
import com.spotkofi.app.data.model.HomeTab
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.repository.MusicRepository
import com.spotkofi.app.player.PlayerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    private fun load() {
        val state = _uiState.value
        val tab = if (state.followingActive) HomeTab.Following else state.selectedChip

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            combine(
                repository.quickPicks(),
                repository.homeSections(tab),
            ) { picks, sections -> picks to sections }
                .collect { (picks, sections) ->
                    _uiState.update {
                        it.copy(quickPicks = picks, sections = sections, isLoading = false)
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

    fun onPlayTrack(track: Track) = player.play(track, listOf(track))
}
