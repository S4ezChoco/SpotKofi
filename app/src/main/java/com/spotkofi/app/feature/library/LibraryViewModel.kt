package com.spotkofi.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.Playlist
import com.spotkofi.app.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: MusicRepository,
) : ViewModel() {

    enum class Filter(val label: String) {
        Playlists("Playlists"),
        Albums("Albums"),
        Artists("Artists"),
        Downloaded("Downloaded"),
    }

    enum class SortMode(val label: String) {
        Recents("Recents"),
        RecentlyAdded("Recently added"),
        Alphabetical("Alphabetical"),
        Creator("Creator"),
    }

    enum class ViewMode { List, Grid }

    data class UiState(
        val items: List<MediaCollection> = emptyList(),
        /** Null means no filter chip is active, which is the default state. */
        val filter: Filter? = null,
        val sortMode: SortMode = SortMode.Recents,
        val viewMode: ViewMode = ViewMode.List,
        val isLoading: Boolean = true,
    ) {
        /**
         * Filtering and sorting are applied at read time so toggling a chip never
         * refetches, and pinned entries always float to the top the way they do
         * in the real app.
         */
        val visibleItems: List<MediaCollection>
            get() {
                val filtered = when (filter) {
                    null -> items
                    Filter.Playlists -> items.filterIsInstance<Playlist>()
                    Filter.Albums -> items.filterIsInstance<Album>()
                    Filter.Artists -> items.filterIsInstance<Artist>()
                    // Nothing is downloaded in Phase 1; the chip still has to work.
                    Filter.Downloaded -> emptyList()
                }

                val sorted = when (sortMode) {
                    SortMode.Recents, SortMode.RecentlyAdded -> filtered
                    SortMode.Alphabetical -> filtered.sortedBy { it.title.lowercase() }
                    SortMode.Creator -> filtered.sortedBy { it.ownerLabel().lowercase() }
                }

                return sorted.sortedByDescending { (it as? Playlist)?.isPinned == true }
            }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.library().collect { items ->
                _uiState.update { it.copy(items = items, isLoading = false) }
            }
        }
    }

    /** Tapping the active chip clears it, matching the real app's toggle behaviour. */
    fun onFilterClick(filter: Filter) {
        _uiState.update { it.copy(filter = if (it.filter == filter) null else filter) }
    }

    /** Steps through the sort modes; the real app opens a sheet. */
    fun onCycleSort() {
        _uiState.update {
            val next = SortMode.entries[(it.sortMode.ordinal + 1) % SortMode.entries.size]
            it.copy(sortMode = next)
        }
    }

    fun onToggleViewMode() {
        _uiState.update {
            it.copy(
                viewMode = if (it.viewMode == ViewMode.List) ViewMode.Grid else ViewMode.List,
            )
        }
    }
}

/** Creator shown in the row subtitle. */
internal fun MediaCollection.ownerLabel(): String = when (this) {
    is Playlist -> ownerName
    is Album -> artistName
    is Artist -> name
}

/** "Playlist \u2022 owner" style subtitle used by the library rows. */
internal fun MediaCollection.librarySubtitle(): String = when (this) {
    is Playlist -> "Playlist \u2022 $ownerName"
    is Album -> "Album \u2022 $artistName"
    is Artist -> "Artist"
}
