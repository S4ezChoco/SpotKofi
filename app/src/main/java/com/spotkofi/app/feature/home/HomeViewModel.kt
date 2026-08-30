package com.spotkofi.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotkofi.app.data.local.LocalMusicStore
import com.spotkofi.app.data.local.SettingsStore
import com.spotkofi.app.data.model.ChartRegion
import com.spotkofi.app.data.model.HomeQuickPick
import com.spotkofi.app.data.model.HomeSection
import com.spotkofi.app.data.model.HomeTab
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.MoodCategory
import com.spotkofi.app.data.model.MoodGroup
import com.spotkofi.app.data.model.MusicChart
import com.spotkofi.app.data.model.PlaybackState
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.repository.MoodCategoryContents
import com.spotkofi.app.data.repository.MusicRepository
import com.spotkofi.app.player.PlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/** The compact Home filters shown below the app greeting. */
enum class HomeFilter(val label: String) {
    All("All"),
    Relax("Relax"),
    Sleep("Sleep"),
    Energize("Energize"),
    Sad("Sad"),
}

class HomeViewModel(
    private val repository: MusicRepository,
    private val player: PlayerController,
    private val localStore: LocalMusicStore? = null,
    private val settingsStore: SettingsStore? = null,
) : ViewModel() {

    data class UiState(
        val userName: String = "",
        val selectedFilter: HomeFilter = HomeFilter.All,
        val quickPicks: List<HomeQuickPick> = emptyList(),
        val sections: List<HomeSection> = emptyList(),
        val trendingPlaylists: List<MediaCollection> = emptyList(),
        val newReleases: List<MediaCollection> = emptyList(),
        val moodGroups: List<MoodGroup> = emptyList(),
        val chart: MusicChart? = null,
        val chartRegions: List<ChartRegion> = emptyList(),
        val regionCode: String = "PH",
        val selectedMood: MoodCategoryContents? = null,
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val isRegionLoading: Boolean = false,
        val isMoodLoading: Boolean = false,
        val error: String? = null,
    ) {
        val showQuickPicks: Boolean get() = quickPicks.isNotEmpty()

        val isEmpty: Boolean
            get() = !isLoading && error == null && !isMoodLoading &&
                quickPicks.isEmpty() && sections.isEmpty() &&
                trendingPlaylists.isEmpty() && newReleases.isEmpty() &&
                moodGroups.isEmpty() && chart == null && selectedMood == null

        val regionName: String
            get() = chartRegions.firstOrNull { it.code == regionCode }?.name ?: regionCode
    }

    private val _uiState = MutableStateFlow(
        UiState(
            userName = repository.currentUserName(),
            chartRegions = repository.chartRegions(),
            regionCode = settingsStore?.current?.contentRegion ?: "PH",
        ),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var remoteQuickPicks: List<MediaCollection> = emptyList()
    private var remoteSections: List<HomeSection> = emptyList()
    private var loadJob: Job? = null
    private var moodJob: Job? = null

    init {
        // Settings can be changed on the Settings screen while Home is kept alive
        // in the saved tab stack. Observe the persisted value rather than relying
        // on the Home region dialog to be the only writer.
        settingsStore?.let { store ->
            viewModelScope.launch {
                store.settings
                    .map { it.contentRegion }
                    .distinctUntilChanged()
                    .collect { region ->
                        if (region != _uiState.value.regionCode) {
                            _uiState.update { it.copy(regionCode = region) }
                            load(regionOverride = region)
                        }
                    }
            }
        }

        // Playback history is durable and changes after the player records a play.
        // Updating the derived list here means the first eight tiles evolve while
        // the listener uses the app, without requiring a Home reload.
        localStore?.let { store ->
            viewModelScope.launch {
                store.history.collect(::updateLocalHistory)
            }
        }

        load()
    }

    fun retry() = load()

    /** Pull-to-refresh reloads every Home shelf, including region-sensitive data. */
    fun refresh() {
        repository.invalidateHomeCache()
        load(refreshing = true)
    }

    /** Loads the real Home feed and the provider discovery blocks together. */
    private fun load(
        regionOverride: String? = null,
        refreshing: Boolean = false,
    ) {
        val region = regionOverride
            ?: settingsStore?.current?.contentRegion
            ?: _uiState.value.regionCode
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    regionCode = region,
                    isLoading = if (refreshing) it.isLoading else true,
                    isRefreshing = refreshing,
                    error = null,
                    isMoodLoading = false,
                    selectedMood = null,
                )
            }
            try {
                supervisorScope {
                    val picks = async {
                        runCatching { repository.quickPicks() }
                            .getOrDefault(remoteQuickPicks)
                    }
                    val sections = async {
                        runCatching { repository.homeSections(HomeTab.All) }
                            .getOrDefault(remoteSections)
                    }
                    val trending = async {
                        runCatching { repository.trendingPlaylists(region) }
                            .getOrDefault(emptyList())
                    }
                    val releases = async {
                        runCatching { repository.newReleases() }.getOrDefault(emptyList())
                    }
                    val moods = async {
                        runCatching { repository.moodsAndGenres() }.getOrDefault(emptyList())
                    }
                    val chart = async {
                        runCatching { repository.chart(region) }.getOrNull()
                    }

                    val loadedPicks = picks.await()
                    val loadedSections = sections.await()
                    val loadedTrending = trending.await()
                    val loadedReleases = releases.await()
                    val loadedMoods = moods.await()
                    val loadedChart = chart.await()

                    remoteQuickPicks = loadedPicks
                    remoteSections = loadedSections
                    val history = localStore?.history?.value.orEmpty()

                    _uiState.update {
                        it.copy(
                            quickPicks = buildQuickPicks(history),
                            sections = sectionsWithHistory(history),
                            trendingPlaylists = loadedTrending,
                            newReleases = loadedReleases,
                            moodGroups = loadedMoods,
                            chart = loadedChart?.takeUnless { value -> value.isEmpty },
                            isLoading = false,
                            isRefreshing = false,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = failure.message ?: "Could not load music",
                    )
                }
            }
        }
    }

    private fun updateLocalHistory(history: List<Track>) {
        _uiState.update {
            it.copy(
                quickPicks = buildQuickPicks(history),
                sections = sectionsWithHistory(history),
            )
        }
    }

    private fun buildQuickPicks(history: List<Track>): List<HomeQuickPick> {
        val recent = history
            .asSequence()
            .distinctBy { it.id }
            .take(8)
            .map(HomeQuickPick::fromTrack)
            .toList()
        val remote = remoteQuickPicks
            .asSequence()
            .map(HomeQuickPick::fromCollection)
            .filterNot { pick -> recent.any { it.id == pick.id } }
            .toList()
        return (recent + remote).distinctBy { it.id }.take(8)
    }

    private fun sectionsWithHistory(history: List<Track>): List<HomeSection> =
        if (history.isEmpty()) {
            remoteSections
        } else {
            listOf(
                HomeSection.Songs(
                    id = "local_recently_played",
                    title = "Recently played",
                    items = history.take(6),
                ),
            ) + remoteSections
        }

    /** Changes the top filter; mood chips open the provider's matching category. */
    fun onFilterClick(filter: HomeFilter) {
        if (_uiState.value.selectedFilter == filter) return
        _uiState.update { it.copy(selectedFilter = filter, selectedMood = null) }
        if (filter == HomeFilter.All) return

        val category = findCategory(filter)
        if (category == null) return

        moodJob?.cancel()
        moodJob = viewModelScope.launch {
            _uiState.update { it.copy(isMoodLoading = true, error = null) }
            try {
                val content = repository.moodCategory(category)
                _uiState.update {
                    it.copy(selectedMood = content, isMoodLoading = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update {
                    it.copy(
                        isMoodLoading = false,
                        error = failure.message ?: "Could not load this mood",
                    )
                }
            }
        }
    }

    /** Opens any provider tile without requiring a title-to-search detour. */
    fun onMoodClick(category: MoodCategory) {
        moodJob?.cancel()
        moodJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isMoodLoading = true,
                    selectedFilter = HomeFilter.All,
                    selectedMood = null,
                    error = null,
                )
            }
            try {
                val content = repository.moodCategory(category)
                _uiState.update {
                    it.copy(selectedMood = content, isMoodLoading = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update {
                    it.copy(
                        isMoodLoading = false,
                        error = failure.message ?: "Could not load this category",
                    )
                }
            }
        }
    }

    /** Persists the region; the settings observer owns the single reload. */
    fun onRegionSelected(code: String) {
        val normalized = code.trim().uppercase().take(2)
        if (normalized.isBlank() || normalized == _uiState.value.regionCode) return
        if (settingsStore != null) {
            settingsStore.setContentRegion(normalized)
        } else {
            _uiState.update { it.copy(regionCode = normalized) }
            load(regionOverride = normalized)
        }
    }

    private fun findCategory(filter: HomeFilter): MoodCategory? {
        val categories = _uiState.value.moodGroups.flatMap { it.items }
        val aliases = when (filter) {
            HomeFilter.Relax -> listOf("relax", "chill", "feel good")
            HomeFilter.Sleep -> listOf("sleep", "sleepy", "rest")
            HomeFilter.Energize -> listOf("energize", "energy", "workout")
            HomeFilter.Sad -> listOf("sad", "heartbreak", "melancholy")
            HomeFilter.All -> emptyList()
        }
        return categories.firstOrNull { category ->
            aliases.any { alias -> category.title.contains(alias, ignoreCase = true) }
        }
    }

    fun onPlayTrack(track: Track, queue: List<Track> = listOf(track)) {
        player.play(track, queue.ifEmpty { listOf(track) })
    }

    val playbackState: StateFlow<PlaybackState> = player.state
}
