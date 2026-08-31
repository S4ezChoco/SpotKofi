package com.spotkofi.app.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.data.model.HomeQuickPick
import com.spotkofi.app.data.model.HomeSection
import com.spotkofi.app.data.model.MoodCategory
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.asTrackDuration
import com.spotkofi.app.data.repository.previewHomeSections
import com.spotkofi.app.data.repository.previewQuickPicks
import com.spotkofi.app.data.service.DownloadItem
import com.spotkofi.app.ui.components.AppFooter
import com.spotkofi.app.ui.components.HomeSkeleton
import com.spotkofi.app.ui.components.MediaCard
import com.spotkofi.app.ui.components.ErrorState
import com.spotkofi.app.ui.components.SpotKofiScreenHeader
import com.spotkofi.app.ui.components.QuickPickCard
import com.spotkofi.app.ui.components.ReleaseCard
import com.spotkofi.app.ui.components.SectionHeader
import com.spotkofi.app.ui.components.SpotKofiChip
import com.spotkofi.app.ui.components.SpotlightCard
import com.spotkofi.app.ui.components.StationCard
import com.spotkofi.app.ui.components.TrackActionsSheet
import com.spotkofi.app.ui.components.TrackRow
import com.spotkofi.app.ui.components.artworkSeedColor
import com.spotkofi.app.ui.layout.ResponsiveLayout
import com.spotkofi.app.ui.layout.rememberResponsiveLayout
import com.spotkofi.app.ui.motion.staggeredEntry
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme
import com.spotkofi.app.ui.theme.headerWash
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCollectionClick: (String) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel {
        HomeViewModel(
            container.musicRepository,
            container.playerController,
            container.localStore,
            container.settingsStore,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playingTrackId by remember(container.playerController.state) {
        container.playerController.state.map { it.track?.id }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val savedTracks by container.localStore.savedTracks.collectAsStateWithLifecycle()
    val downloads by container.downloadManager.downloads.collectAsStateWithLifecycle()
    val playlists by container.localStore.playlists.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    var showRegionPicker by remember { mutableStateOf(false) }

    // Indexed once per change instead of scanned per row, so a long shelf does not
    // do a linear search for every visible track.
    val downloadsByTrack = remember(downloads) { downloads.associateBy { it.track.id } }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpotKofiTheme.colors.base),
    ) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            HomeContent(
                state = state,
                onChipClick = viewModel::onFilterClick,
                onCollectionClick = onCollectionClick,
                onQuickPickClick = { pick ->
                    pick.track?.let(viewModel::onPlayTrack)
                    pick.collectionId?.let(onCollectionClick)
                },
                onTrackClick = viewModel::onPlayTrack,
                onTrackMore = { selectedTrack = it },
                onMoodClick = viewModel::onMoodClick,
                onRegionClick = { showRegionPicker = true },
                playingTrackId = playingTrackId,
                onOpenProfile = onOpenProfile,
                onRetry = viewModel::retry,
                contentPadding = contentPadding,
                downloads = downloadsByTrack,
            )
        }

        val track = selectedTrack
        TrackActionsSheet(
            visible = track != null,
            track = track,
            isSaved = track?.let { candidate -> savedTracks.any { it.id == candidate.id } } == true,
            playlists = playlists,
            downloadStatus = track?.let { downloadsByTrack[it.id]?.status },
            downloadProgress = track?.let { downloadsByTrack[it.id]?.progress } ?: 0,
            onDismiss = { selectedTrack = null },
            onToggleSaved = {
                track?.let { candidate ->
                    if (savedTracks.any { it.id == candidate.id }) {
                        container.localStore.removeTrack(candidate.id)
                    } else {
                        container.localStore.saveTrack(candidate)
                    }
                }
            },
            onPlayNext = { track?.let(container.queueController::playNext) },
            onAddToQueue = { track?.let(container.queueController::addToQueue) },
            onDownload = { track?.let(container.downloadManager::toggleDownload) },
            onAddToPlaylist = { playlist ->
                track?.let { candidate ->
                    scope.launch { container.localStore.addToPlaylist(playlist.id, candidate) }
                }
            },
        )

        if (showRegionPicker) {
            HomeRegionDialog(
                regions = state.chartRegions,
                selectedCode = state.regionCode,
                onSelect = { code ->
                    viewModel.onRegionSelected(code)
                    showRegionPicker = false
                },
                onDismiss = { showRegionPicker = false },
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeViewModel.UiState,
    onChipClick: (HomeFilter) -> Unit,
    onCollectionClick: (String) -> Unit,
    onQuickPickClick: (HomeQuickPick) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onTrackMore: (Track) -> Unit = {},
    onMoodClick: (MoodCategory) -> Unit = {},
    onRegionClick: () -> Unit = {},
    playingTrackId: String?,
    onOpenProfile: () -> Unit,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    downloads: Map<String, DownloadItem> = emptyMap(),
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val layout = rememberResponsiveLayout()

    val listState = rememberLazyListState()

    // Left as a State object and read only inside the wash's graphicsLayer block.
    //
    // Destructuring it with `by` here read it during composition, which meant this
    // whole function, including the entire LazyColumn declaration, recomposed on
    // every scroll frame. Reading it in the layer block keeps it in the draw phase.
    val washAlpha = remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                0f
            } else {
                (1f - listState.firstVisibleItemScrollOffset / 420f).coerceIn(0f, 1f)
            }
        }
    }

    val headerLifted by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 4
        }
    }
    val headerSurface by animateColorAsState(
        targetValue = if (headerLifted) colors.base else colors.base.copy(alpha = 0.78f),
        animationSpec = Motion.fast(),
        label = "homeStickyHeader",
    )

    // Tinted from the first quick pick so the wash relates to what is on screen.
    val washTint = remember(state.quickPicks.firstOrNull()?.id) {
        state.quickPicks.firstOrNull()?.id?.let(::artworkSeedColor)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (washTint != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .graphicsLayer { alpha = washAlpha.value }
                    .background(headerWash(washTint)),
            )
        }

        // Keep the profile/sidebar action and feed filters attached to the top
        // chrome while the shelves scroll underneath. The surface only becomes
        // opaque once content is behind it, so resting Home still feels airy.
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerSurface)
                    .padding(top = contentPadding.calculateTopPadding()),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HomeHeader(
                        state = state,
                        gutter = layout.gutter,
                        onChipClick = onChipClick,
                        onOpenProfile = onOpenProfile,
                    )
                    Spacer(Modifier.height(dimens.spaceMd))
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    bottom = contentPadding.calculateBottomPadding(),
                ),
            ) {
            if (state.isLoading) {
                item(key = "loading") {
                    HomeSkeleton(
                        gutter = layout.gutter,
                        gridColumns = layout.gridColumns,
                        shelfWidth = layout.shelfCardWidth,
                    )
                }
                return@LazyColumn
            }

            // Checked after loading so a retry replaces the error with a spinner
            // instead of showing both.
            val error = state.error
            if (error != null) {
                item(key = "error") {
                    ErrorState(
                        message = error,
                        onRetry = onRetry,
                        modifier = Modifier.padding(top = dimens.spaceXxl),
                    )
                }
                return@LazyColumn
            }

            // A filter that legitimately has nothing behind it says so, rather than
            // leaving the user staring at a blank screen wondering if it is loading.
            if (state.isEmpty) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = layout.gutter, vertical = dimens.spaceHuge),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Nothing here yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(dimens.spaceXs))
                        Text(
                            text = "The ${state.selectedFilter.label} feed has no content " +
                                "from the catalog right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
                item(key = "empty_footer") { AppFooter() }
                return@LazyColumn
            }

            if (state.showQuickPicks) {
                // Grid built from chunked rows: a LazyVerticalGrid cannot nest in a
                // LazyColumn, and chunking keeps the screen one scroll container.
                // Column count comes from the window width, not a constant.
                state.quickPicks.chunked(layout.gridColumns)
                    .forEachIndexed { rowIndex, row ->
                        item(key = "qp_" + row.first().id) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .staggeredEntry(rowIndex)
                                    .padding(
                                        horizontal = layout.gutter,
                                        vertical = dimens.spaceXs,
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
                            ) {
                                row.forEach { item ->
                                        QuickPickCard(
                                            item = item,
                                            onClick = { onQuickPickClick(item) },
                                            modifier = Modifier.weight(1f),
                                        )
                                }
                                // Keeps a short final row aligned with those above.
                                repeat(layout.gridColumns - row.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                item(key = "qp_spacer") {
                    Spacer(Modifier.height(dimens.shelfSpacing))
                }
            }

            if (
                state.selectedFilter == HomeFilter.All ||
                state.selectedMood != null ||
                state.isMoodLoading
            ) {
                item(key = "home_explore") {
                    HomeExploreSections(
                        state = state,
                        layout = layout,
                        playingTrackId = playingTrackId,
                        onCollectionClick = onCollectionClick,
                        onTrackClick = onTrackClick,
                        onTrackMore = onTrackMore,
                        onMoodClick = onMoodClick,
                        onRegionClick = onRegionClick,
                    )
                }
            }

            if (
                state.selectedFilter == HomeFilter.All &&
                state.selectedMood == null &&
                !state.isMoodLoading
            ) {
                state.sections.forEachIndexed { index, section ->
                    item(key = section.id) {
                        // A LazyColumn item is a single slot, so the block and its
                        // trailing gap need a layout around them.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .staggeredEntry(index + 2),
                        ) {
                            HomeSectionBlock(
                                section = section,
                                layout = layout,
                                onCollectionClick = onCollectionClick,
                                onTrackClick = onTrackClick,
                                onTrackMore = onTrackMore,
                                playingTrackId = playingTrackId,
                                downloads = downloads,
                            )
                            Spacer(Modifier.height(dimens.shelfSpacing))
                        }
                    }
                }
            }

            item(key = "footer") { AppFooter() }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    state: HomeViewModel.UiState,
    gutter: androidx.compose.ui.unit.Dp,
    onChipClick: (HomeFilter) -> Unit,
    onOpenProfile: () -> Unit,
) {
    val dimens = SpotKofiTheme.dimens

    Column(modifier = Modifier.fillMaxWidth()) {
        SpotKofiScreenHeader(
            title = "SpotKofi",
            onLogoClick = onOpenProfile,
            onMenuClick = onOpenProfile,
            gutter = gutter,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = gutter, end = gutter, bottom = dimens.spaceSm),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeFilter.entries.forEach { filter ->
                SpotKofiChip(
                    label = filter.label,
                    selected = state.selectedFilter == filter,
                    onClick = { onChipClick(filter) },
                )
            }
        }
    }
}

/**
 * Dispatches on section type. A new block shape forces a new branch here.
 *
 * Emits several siblings, so every call site must place it inside a layout
 * (a Column), not directly in a LazyColumn `item` slot.
 */
@Composable
private fun HomeSectionBlock(
    section: HomeSection,
    layout: ResponsiveLayout,
    onCollectionClick: (String) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onTrackMore: (Track) -> Unit,
    playingTrackId: String?,
    downloads: Map<String, DownloadItem>,
) {
    val dimens = SpotKofiTheme.dimens

    when (section) {
        is HomeSection.Cards -> {
            SectionHeader(title = section.title)
            LazyRow(
                contentPadding = PaddingValues(horizontal = layout.gutter),
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
            ) {
                items(items = section.items, key = { it.id }) { item ->
                    MediaCard(
                        item = item,
                        onClick = { onCollectionClick(item.id) },
                        width = layout.shelfCardWidth,
                    )
                }
            }
        }

        is HomeSection.Songs -> {
            SectionHeader(title = section.title)
            // Plain rows rather than a LazyRow of cards: these are songs, and a
            // song row shows the artist and the length, which is what tells the
            // user this is playable rather than another screen to open.
            section.items.forEachIndexed { index, track ->
                Box(modifier = Modifier.staggeredEntry(index)) {
                    val download = downloads[track.id]
                    TrackRow(
                        track = track,
                        onClick = { onTrackClick(track, section.items) },
                        isPlaying = track.id == playingTrackId,
                        trailingText = track.durationMs
                            .takeIf { it > 0L }
                            ?.asTrackDuration(),
                        downloadStatus = download?.status,
                        downloadProgress = download?.progress ?: 0,
                        onMoreClick = { onTrackMore(track) },
                    )
                }
            }
        }

        is HomeSection.Stations -> {
            SectionHeader(title = section.title)
            LazyRow(
                contentPadding = PaddingValues(horizontal = layout.gutter),
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
            ) {
                items(items = section.items, key = { it.id }) { station ->
                    StationCard(
                        station = station,
                        onClick = null,
                        width = layout.shelfCardWidth,
                    )
                }
            }
        }

        is HomeSection.Spotlight -> {
            SectionHeader(title = section.title)
            Box(modifier = Modifier.padding(horizontal = layout.gutter)) {
                SpotlightCard(
                    item = section.item,
                    onClick = { onCollectionClick(section.item.id) },
                    width = layout.spotlightWidth,
                )
            }
        }

        is HomeSection.Releases -> {
            // The feed's own heading is a screen title, not a shelf label.
            Text(
                text = section.title,
                style = MaterialTheme.typography.displaySmall,
                color = SpotKofiTheme.colors.textPrimary,
                modifier = Modifier.padding(
                    horizontal = layout.gutter,
                    vertical = dimens.spaceSm,
                ),
            )
            section.items.forEachIndexed { index, release ->
                Box(
                    modifier = Modifier
                        .staggeredEntry(index)
                        .padding(
                            horizontal = layout.gutter,
                            vertical = dimens.spaceSm,
                        ),
                ) {
                    ReleaseCard(
                        release = release,
                        onClick = null,
                        onPlay = null,
                        onAdd = null,
                        onMore = null,
                    )
                }
            }
        }
    }
}

@Preview(name = "Home / All", backgroundColor = 0xFF121212, showBackground = true, heightDp = 1000)
@Composable
private fun HomePreview() {
    SpotKofiTheme {
        HomeContent(
            state = HomeViewModel.UiState(
                userName = "SpotKofi_User",
                quickPicks = previewQuickPicks().map(HomeQuickPick::fromCollection),
                sections = previewHomeSections(),
                isLoading = false,
            ),
            onChipClick = {},
            onCollectionClick = {},
            onQuickPickClick = {},
            onTrackClick = { _, _ -> },
            playingTrackId = null,
            onOpenProfile = {},
            onRetry = {},
            contentPadding = PaddingValues(),
        )
    }
}

@Preview(name = "Home / Empty", backgroundColor = 0xFF121212, showBackground = true, heightDp = 600)
@Composable
private fun HomeEmptyPreview() {
    SpotKofiTheme {
        HomeContent(
            state = HomeViewModel.UiState(
                userName = "SpotKofi_User",
                selectedFilter = HomeFilter.Sad,
                isLoading = false,
            ),
            onChipClick = {},
            onCollectionClick = {},
            onQuickPickClick = {},
            onTrackClick = { _, _ -> },
            playingTrackId = null,
            onOpenProfile = {},
            onRetry = {},
            contentPadding = PaddingValues(),
        )
    }
}
