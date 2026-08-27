package com.spotkofi.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotkofi.app.data.model.BrowseCategory
import com.spotkofi.app.data.model.ExploreItem
import com.spotkofi.app.data.model.SearchResults
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.repository.MusicRepository
import com.spotkofi.app.player.PlayerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: MusicRepository,
    private val player: PlayerController,
) : ViewModel() {

    data class UiState(
        val userName: String = "",
        val query: String = "",
        val categories: List<BrowseCategory> = emptyList(),
        val videos: List<ExploreItem> = emptyList(),
        val episodes: List<ExploreItem> = emptyList(),
        val results: SearchResults = SearchResults(),
        val isSearching: Boolean = false,
    ) {
        /** Browse content shows while the field is empty; results take over after that. */
        val showBrowse: Boolean get() = query.isBlank()
    }

    private val _uiState = MutableStateFlow(
        UiState(
            userName = repository.currentUserName(),
            categories = repository.browseCategories(),
            videos = repository.exploreVideos(),
            episodes = repository.exploreEpisodes(),
        ),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }

        // Cancel-and-restart debounce: only the last keystroke in a burst
        // actually issues a query.
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(results = SearchResults(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(DEBOUNCE_MS)
            val results = repository.search(query)
            _uiState.update { it.copy(results = results, isSearching = false) }
        }
    }

    fun onClearQuery() = onQueryChange("")

    /** Plays a result, using the rest of the result list as the queue. */
    fun onTrackClick(track: Track) {
        player.play(track, _uiState.value.results.tracks)
    }

    private companion object {
        const val DEBOUNCE_MS = 250L
    }
}
