package com.spotkofi.app.feature.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.repository.MusicRepository
import com.spotkofi.app.player.PlayerController
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CollectionViewModel(
    private val collectionId: String,
    private val repository: MusicRepository,
    private val player: PlayerController,
) : ViewModel() {

    data class UiState(
        val collection: MediaCollection? = null,
        val tracks: List<Track> = emptyList(),
        val isSaved: Boolean = false,
        val isLoading: Boolean = true,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val playbackState = player.state

    init {
        viewModelScope.launch {
            // Concurrent, not sequential. The two lookups are independent, so
            // awaiting them one after the other doubled the time to first paint
            // for no reason.
            val collection = async { repository.collection(collectionId) }
            val tracks = async { repository.tracks(collectionId) }
            _uiState.update {
                it.copy(
                    collection = collection.await(),
                    tracks = tracks.await(),
                    isLoading = false,
                )
            }
        }
    }

    /** Plays the whole collection from the top. */
    fun onPlayAll() {
        val tracks = _uiState.value.tracks
        val first = tracks.firstOrNull() ?: return
        if (player.state.value.track?.id in tracks.map { it.id }) {
            player.togglePlayPause()
        } else {
            player.play(first, tracks)
        }
    }

    /** Plays one track, keeping the rest of the collection as the queue. */
    fun onTrackClick(track: Track) {
        player.play(track, _uiState.value.tracks)
    }

    fun onToggleSave() {
        _uiState.update { it.copy(isSaved = !it.isSaved) }
    }
}
