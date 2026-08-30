package com.spotkofi.app.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotkofi.app.data.local.SettingsStore
import com.spotkofi.app.data.model.ChartRegion
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.MoodGroup
import com.spotkofi.app.data.model.MusicChart
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.repository.MusicRepository
import com.spotkofi.app.player.PlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * The Explore page: charts for a chosen region, moods and genres, new releases and
 * the playlists the provider is currently pushing.
 *
 * Region-dependent and region-independent content are loaded by two separate jobs.
 * Changing the region has to refetch the chart and the trending shelves, but the
 * moods grid and the new-release list are the same in every country, and refetching
 * them would make a region change cost three extra round trips and blank out
 * content that was already correct.
 */
class ExploreViewModel(
    private val repository: MusicRepository,
    private val player: PlayerController,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    data class UiState(
        val regionCode: String = "",
        val regions: List<ChartRegion> = emptyList(),
        val chart: MusicChart? = null,
        val moodGroups: List<MoodGroup> = emptyList(),
        val newReleases: List<MediaCollection> = emptyList(),
        val trendingPlaylists: List<MediaCollection> = emptyList(),
        /** True while the region-dependent half is in flight. */
        val isLoadingRegion: Boolean = true,
        /** True while the region-independent half is in flight. */
        val isLoadingCatalog: Boolean = true,
        val error: String? = null,
    ) {
        val regionName: String
            get() = regions.firstOrNull { it.code == regionCode }?.name ?: regionCode

        val isLoading: Boolean get() = isLoadingRegion || isLoadingCatalog

        /** Nothing came back at all, as opposed to one shelf being short. */
        val isEmpty: Boolean
            get() = !isLoading &&
                error == null &&
                chart == null &&
                moodGroups.isEmpty() &&
                newReleases.isEmpty() &&
                trendingPlaylists.isEmpty()
    }

    private val _uiState = MutableStateFlow(
        UiState(
            // Seeded from Settings so the page opens on the user's own region
            // instead of making them pick one every time.
            regionCode = settingsStore.current.contentRegion,
            regions = repository.chartRegions(),
        ),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Only row highlighting needs playback identity; progress belongs to the player. */
    val playingTrackId: Flow<String?> = player.state
        .map { it.track?.id }
        .distinctUntilChanged()

    private var regionJob: Job? = null
    private var catalogJob: Job? = null

    init {
        loadRegion()
        loadCatalog()
    }

    fun retry() {
        loadRegion()
        loadCatalog()
    }

    /**
     * Switches region and remembers the choice.
     *
     * The pick is written back to Settings rather than kept on this screen: the
     * region also decides which catalogue search and Home ask for, and a listener
     * who sets it here means it for the whole app, not just this page.
     */
    fun onRegionSelected(code: String) {
        if (code == _uiState.value.regionCode) return
        settingsStore.setContentRegion(code)
        _uiState.update { it.copy(regionCode = settingsStore.current.contentRegion) }
        loadRegion()
    }

    fun onPlayTrack(track: Track, queue: List<Track>) {
        player.play(track, queue.ifEmpty { listOf(track) })
    }

    private fun loadRegion() {
        val region = _uiState.value.regionCode

        regionJob?.cancel()
        regionJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRegion = true, error = null) }
            try {
                supervisorScope {
                    val chart = async { repository.chart(region) }
                    val trending = async { repository.trendingPlaylists(region) }
                    val loadedChart = chart.await()
                    _uiState.update {
                        it.copy(
                            // A chart with nothing in it is treated as no chart, so
                            // the screen shows its empty state rather than three
                            // empty headings.
                            chart = loadedChart?.takeUnless { c -> c.isEmpty },
                            trendingPlaylists = trending.await(),
                            isLoadingRegion = false,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingRegion = false,
                        error = failure.message ?: "Could not load charts",
                    )
                }
            }
        }
    }

    private fun loadCatalog() {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCatalog = true) }
            try {
                supervisorScope {
                    val moods = async { repository.moodsAndGenres() }
                    val releases = async { repository.newReleases() }
                    _uiState.update {
                        it.copy(
                            moodGroups = moods.await(),
                            newReleases = releases.await(),
                            isLoadingCatalog = false,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // The error surface belongs to the region half, which is the part
                // the user just acted on. This half only stops claiming to load, so
                // a failed moods request cannot wipe a chart that arrived fine.
                _uiState.update {
                    it.copy(
                        isLoadingCatalog = false,
                        error = it.error ?: failure.message,
                    )
                }
            }
        }
    }
}
