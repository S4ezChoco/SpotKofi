package com.spotkofi.app.feature.home

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.spotkofi.app.data.model.HomeSection
import com.spotkofi.app.data.model.HomeTab
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.asTrackDuration
import com.spotkofi.app.data.repository.previewHomeSections
import com.spotkofi.app.data.repository.previewQuickPicks
import com.spotkofi.app.data.service.DownloadItem
import com.spotkofi.app.ui.components.AppFooter
import com.spotkofi.app.ui.components.MediaCard
import com.spotkofi.app.ui.components.ErrorState
import com.spotkofi.app.ui.components.ProfileAvatar
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
import com.spotkofi.app.ui.theme.SpotKofiTheme
import com.spotkofi.app.ui.theme.headerWash
import kotlinx.coroutines.launch

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
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val savedTracks by container.localStore.savedTracks.collectAsStateWithLifecycle()
    val downloads by container.downloadManager.downloads.collectAsStateWithLifecycle()
    val playlists by container.localStore.playlists.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selectedTrack by remember { mutableStateOf<Track?>(null) }

    // Indexed once per change instead of scanned per row, so a long shelf does not
    // do a linear search for every visible track.
    val downloadsByTrack = remember(downloads) { downloads.associateBy { it.track.id } }

    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
        HomeContent(
            state = state,
            onChipClick = viewModel::onChipClick,
            onCollectionClick = onCollectionClick,
            onTrackClick = viewModel::onPlayTrack,
            onTrackMore = { selectedTrack = it },
            playingTrackId = playback.track?.id,
            onOpenProfile = onOpenProfile,
            onRetry = viewModel::retry,
            contentPadding = contentPadding,
            downloads = downloadsByTrack,
        )

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
    }
}

@Composable
private fun HomeContent(
    state: HomeViewModel.UiState,
    onChipClick: (HomeTab) -> Unit,
    onCollectionClick: (String) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onTrackMore: (Track) -> Unit = {},
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

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            // Avatar and chips share one row. There is no greeting: the header IS
            // the filter bar.
            item(key = "header") {
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

            if (state.isLoading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.accent)
                    }
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
                            text = "The ${state.selectedChip.label} feed has no content " +
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
                                        onClick = { onCollectionClick(item.id) },
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

            item(key = "footer") { AppFooter() }
        }
    }
}

@Composable
private fun HomeHeader(
    state: HomeViewModel.UiState,
    gutter: androidx.compose.ui.unit.Dp,
    onChipClick: (HomeTab) -> Unit,
    onOpenProfile: () -> Unit,
) {
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimens.spaceMd, bottom = dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(gutter))
        ProfileAvatar(name = state.userName, onClick = onOpenProfile, size = 32.dp)
        Spacer(Modifier.width(dimens.spaceMd))

        // One chip per feed, driven by the enum.
        //
        // This used to be a hand-written list with Music and Following fused into a
        // segmented control. Following had no working feed behind it - every card it
        // drew ignored taps - so the control is gone and the row is a plain,
        // uniform set of filters again.
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(end = gutter),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeTab.entries.forEach { tab ->
                SpotKofiChip(
                    label = tab.label,
                    selected = state.selectedChip == tab,
                    onClick = { onChipClick(tab) },
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
                        onClick = { },
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
                        onClick = { },
                        onPlay = { },
                        onAdd = { },
                        onMore = { },
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
                userName = "kofi_listener",
                quickPicks = previewQuickPicks(),
                sections = previewHomeSections(),
                isLoading = false,
            ),
            onChipClick = {},
            onCollectionClick = {},
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
                userName = "kofi_listener",
                selectedChip = HomeTab.Podcasts,
                isLoading = false,
            ),
            onChipClick = {},
            onCollectionClick = {},
            onTrackClick = { _, _ -> },
            playingTrackId = null,
            onOpenProfile = {},
            onRetry = {},
            contentPadding = PaddingValues(),
        )
    }
}
