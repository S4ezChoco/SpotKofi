package com.spotkofi.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotkofi.app.data.model.BrowseCategory
import com.spotkofi.app.data.model.ExploreItem
import com.spotkofi.app.data.model.SearchResults
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.repository.MusicRepository
import com.spotkofi.app.player.PlayerController
import kotlinx.coroutines.CancellationException
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
        /** Set when the search request itself failed, as opposed to returning nothing. */
        val error: String? = null,
    ) {
        /** Browse content shows while the field is empty; results take over after that. */
        val showBrowse: Boolean get() = query.isBlank()
    }

    private val _uiState = MutableStateFlow(
        UiState(
            userName = repository.currentUserName(),
            categories = repository.browseCategories(),
        ),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // The browse shelves are network calls now, so they load after first paint
        // rather than blocking construction. A failure here is not worth an error
        // screen: the genre tiles and the search field are still fully usable.
        viewModelScope.launch {
            val videos = runCatching { repository.exploreVideos() }.getOrDefault(emptyList())
            val podcasts = runCatching { repository.explorePodcasts() }.getOrDefault(emptyList())
            _uiState.update { it.copy(videos = videos, episodes = podcasts) }
        }
    }

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }

        // Cancel-and-restart debounce: only the last keystroke in a burst
        // actually issues a query.
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(results = SearchResults(), isSearching = false, error = null)
            }
            return
        }

        searchJob = launchSearch(query)
    }

    /** Re-runs the last query after a failure. */
    fun onRetry() {
        val query = _uiState.value.query
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = launchSearch(query)
    }

    private fun launchSearch(query: String): Job = viewModelScope.launch {
        _uiState.update { it.copy(isSearching = true, error = null) }
        delay(DEBOUNCE_MS)
        try {
            val results = repository.search(query)
            _uiState.update { it.copy(results = results, isSearching = false) }
        } catch (cancelled: CancellationException) {
            // A newer keystroke owns the state now.
            throw cancelled
        } catch (failure: Exception) {
            // Results are cleared as well as flagged: leaving the previous
            // query's hits on screen under a new query would misattribute them.
            _uiState.update {
                it.copy(
                    results = SearchResults(),
                    isSearching = false,
                    error = failure.message ?: "Search failed",
                )
            }
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
