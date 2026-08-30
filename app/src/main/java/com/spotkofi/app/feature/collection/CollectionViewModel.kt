package com.spotkofi.app.feature.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.repository.MusicRepository
import com.spotkofi.app.player.PlayerController
import kotlinx.coroutines.CancellationException
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
        val isLoading: Boolean = true,
        /** Set when the lookup failed, or when the catalog has no such entry. */
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val playbackState = player.state

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Concurrent, not sequential. The two lookups are independent, so
                // awaiting them one after the other doubled the time to first paint
                // for no reason.
                val collection = async { repository.collection(collectionId) }
                val tracks = async { repository.tracks(collectionId) }
                val resolved = collection.await()
                if (resolved == null) {
                    // A null result is not an empty screen, it means the id does
                    // not exist. Treated as an error so the user gets an
                    // explanation instead of a permanent spinner.
                    _uiState.update {
                        it.copy(isLoading = false, error = "This isn't in the catalog")
                    }
                    return@launch
                }
                // Recorded here rather than at the tap site, because only a
                // successful lookup proves the collection is real, and Your
                // Library is built from this list.
                repository.recordVisited(resolved)
                _uiState.update {
                    it.copy(
                        collection = resolved,
                        tracks = tracks.await(),
                        isLoading = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = failure.message ?: "Could not load this",
                    )
                }
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

}
