package com.spotkofi.app.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.MoodCategory
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.repository.MusicRepository
import com.spotkofi.app.player.PlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Loads the songs and playlists behind one mood, moment or genre tile. */
class MoodCategoryViewModel(
    private val category: MoodCategory,
    private val repository: MusicRepository,
    private val player: PlayerController,
) : ViewModel() {

    data class UiState(
        val title: String = "",
        val songs: List<Track> = emptyList(),
        val playlists: List<MediaCollection> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
    ) {
        val isEmpty: Boolean
            get() = !isLoading && error == null && songs.isEmpty() && playlists.isEmpty()
    }

    private val _uiState = MutableStateFlow(UiState(title = category.title))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Track rows only need identity; progress and buffering stay out of this page. */
    val playingTrackId: Flow<String?> = player.state
        .map { it.track?.id }
        .distinctUntilChanged()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    fun onPlayTrack(track: Track, queue: List<Track>) {
        player.play(track, queue.ifEmpty { listOf(track) })
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val page = repository.moodCategory(category)
                _uiState.update {
                    it.copy(
                        // A null page means the request came back with nothing
                        // usable. That is the empty state, not an error: the
                        // category exists, the provider just has nothing in it.
                        title = page?.title ?: category.title,
                        songs = page?.songs.orEmpty(),
                        playlists = page?.playlists.orEmpty(),
                        isLoading = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = failure.message ?: "Could not load this category",
                    )
                }
            }
        }
    }
}
