package com.spotkofi.app.feature.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotkofi.app.R
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.data.model.ChartRegion
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.MoodCategory
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.ui.components.AppFooter
import com.spotkofi.app.ui.components.Artwork
import com.spotkofi.app.ui.components.ErrorState
import com.spotkofi.app.ui.components.ExploreSkeleton
import com.spotkofi.app.ui.components.MediaCard
import com.spotkofi.app.ui.components.SectionHeader
import com.spotkofi.app.ui.components.SkeletonBox
import com.spotkofi.app.ui.components.SkeletonShelf
import com.spotkofi.app.ui.components.SkeletonTrackRow
import com.spotkofi.app.ui.components.TrackActionsSheetHost
import com.spotkofi.app.ui.motion.clickableScale
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * The provider's own discovery surfaces, in one page: charts for a region, moods
 * and moments, genres, new releases, and the playlists it is currently featuring.
 *
 * Everything here is fetched. There are no house-made shelves and no invented
 * mood tiles, because a tile the provider does not know about cannot be opened,
 * and a shelf with made-up contents is worse than an absent one.
 */
@Composable
fun ExploreScreen(
    onBack: () -> Unit,
    onCollectionClick: (String) -> Unit,
    onCategoryClick: (MoodCategory) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val container = LocalAppContainer.current
    val viewModel: ExploreViewModel = viewModel {
        ExploreViewModel(
            repository = container.musicRepository,
            player = container.playerController,
            settingsStore = container.settingsStore,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playingTrackId by viewModel.playingTrackId.collectAsStateWithLifecycle(initialValue = null)

    var showRegionPicker by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<Track?>(null) }

    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val listState = rememberLazyListState()

    val lifted by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 4
        }
    }
    val barColor by animateColorAsState(
        targetValue = if (lifted) colors.highlight else colors.base,
        animationSpec = Motion.fast(),
        label = "exploreBar",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.base),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(barColor)
                    .padding(top = contentPadding.calculateTopPadding())
                    .padding(horizontal = dimens.spaceXs, vertical = dimens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = colors.textPrimary,
                    )
                }
                Text(
                    text = "Explore",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }

            when {
                state.error != null && state.chart == null && state.moodGroups.isEmpty() ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ErrorState(
                            message = state.error.orEmpty(),
                            onRetry = viewModel::retry,
                        )
                    }

                state.isLoading && state.chart == null && state.moodGroups.isEmpty() ->
                    ExploreSkeleton(modifier = Modifier.fillMaxSize())

                else -> ExploreContent(
                    state = state,
                    listState = listState,
                    playingTrackId = playingTrackId,
                    onRegionClick = { showRegionPicker = true },
                    onCollectionClick = onCollectionClick,
                    onCategoryClick = onCategoryClick,
                    onTrackClick = viewModel::onPlayTrack,
                    onTrackMore = { selectedTrack = it },
                    contentPadding = contentPadding,
                )
            }
        }

        TrackActionsSheetHost(
            track = selectedTrack,
            onDismiss = { selectedTrack = null },
        )
    }

    if (showRegionPicker) {
        RegionDialog(
            regions = state.regions,
            selectedCode = state.regionCode,
            onSelect = { code ->
                viewModel.onRegionSelected(code)
                showRegionPicker = false
            },
            onDismiss = { showRegionPicker = false },
        )
    }
}

@Composable
private fun ExploreContent(
    state: ExploreViewModel.UiState,
    listState: LazyListState,
    playingTrackId: String?,
    onRegionClick: () -> Unit,
    onCollectionClick: (String) -> Unit,
    onCategoryClick: (MoodCategory) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onTrackMore: (Track) -> Unit,
    contentPadding: PaddingValues,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val chart = state.chart

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = contentPadding.calculateBottomPadding() + dimens.spaceLg,
        ),
    ) {
        item(key = "region") {
            RegionButton(
                regionName = state.regionName,
                regionCode = state.regionCode,
                loading = state.isLoadingRegion,
                onClick = onRegionClick,
            )
        }

        if (state.isEmpty) {
            item(key = "empty") {
                Text(
                    text = "Nothing to explore for this region yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(
                        horizontal = dimens.screenGutter,
                        vertical = dimens.spaceXl,
                    ),
                )
            }
        }

        // ---- Top songs: a ranked list, so rows rather than a carousel ----
        if (chart != null && chart.topSongs.isNotEmpty()) {
            item(key = "top_songs_header") {
                SectionHeader(title = "Top songs")
            }
            itemsIndexed(
                items = chart.topSongs,
                key = { _, track -> "chart_song_${track.id}" },
            ) { index, track ->
                RankedTrackRow(
                    rank = index + 1,
                    track = track,
                    isPlaying = track.id == playingTrackId,
                    onClick = { onTrackClick(track, chart.topSongs) },
                    onMoreClick = { onTrackMore(track) },
                )
            }
        }

        if (chart != null && chart.topArtists.isNotEmpty()) {
            item(key = "top_artists") {
                Column {
                    SectionHeader(title = "Top artists")
                    CollectionShelf(
                        items = chart.topArtists,
                        onClick = onCollectionClick,
                    )
                }
            }
        }

        if (state.newReleases.isNotEmpty()) {
            item(key = "new_releases") {
                Column {
                    SectionHeader(title = "New releases")
                    CollectionShelf(
                        items = state.newReleases,
                        onClick = onCollectionClick,
                    )
                }
            }
        }

        if (state.trendingPlaylists.isNotEmpty()) {
            item(key = "trending") {
                Column {
                    SectionHeader(title = "Trending playlists")
                    CollectionShelf(
                        items = state.trendingPlaylists,
                        onClick = onCollectionClick,
                    )
                }
            }
        }

        // The provider's own extra chart shelves, keeping its headings.
        chart?.shelves?.forEach { shelf ->
            if (shelf.items.isEmpty()) return@forEach
            item(key = "shelf_${shelf.title}") {
                Column {
                    SectionHeader(title = shelf.title)
                    CollectionShelf(
                        items = shelf.items,
                        onClick = onCollectionClick,
                    )
                }
            }
        }

        // ---- Moods & moments, Genres: the provider's own groupings ----
        state.moodGroups.forEach { group ->
            if (group.items.isEmpty()) return@forEach
            item(key = "mood_${group.title}") {
                Column {
                    SectionHeader(title = group.title)
                    MoodGrid(
                        items = group.items,
                        onClick = onCategoryClick,
                    )
                }
            }
        }

        if (state.isLoadingCatalog && state.moodGroups.isEmpty()) {
            item(key = "catalog_loading") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.spaceLg),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceMd),
            ) {
                SkeletonShelf(gutter = dimens.screenGutter)
                repeat(3) { SkeletonTrackRow() }
            }
            }
        }

        item(key = "footer") { AppFooter() }
    }
}

/**
 * The region control.
 *
 * A button rather than a row of country chips: there are dozens of regions, and a
 * horizontally scrolling strip of them would bury the one that is selected.
 */
@Composable
private fun RegionButton(
    regionName: String,
    regionCode: String,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .padding(
                horizontal = dimens.screenGutter,
                vertical = dimens.spaceMd,
            )
            .clip(SpotKofiTheme.shapes.chip)
            .background(colors.chip)
            .clickableScale(pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        Icon(
            imageVector = Icons.Filled.Public,
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(dimens.iconSm),
        )
        Text(
            text = regionName,
            style = MaterialTheme.typography.labelLarge,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (loading) {
            SkeletonBox(
                modifier = Modifier.size(width = 32.dp, height = 16.dp),
                shape = SpotKofiTheme.shapes.chip,
            )
        } else {
            Text(
                text = regionCode,
                style = MaterialTheme.typography.labelMedium,
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun CollectionShelf(
    items: List<MediaCollection>,
    onClick: (String) -> Unit,
) {
    val dimens = SpotKofiTheme.dimens

    LazyRow(
        contentPadding = PaddingValues(horizontal = dimens.screenGutter),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        items(items, key = { it.id }) { item ->
            MediaCard(item = item, onClick = { onClick(item.id) })
        }
    }
}

/**
 * A ranked chart row.
 *
 * The number is part of the content here, not decoration: a chart entry without
 * its position is just a song, and the position is the reason the row is where it
 * is. It is given a fixed width so the titles line up down the column.
 */
@Composable
private fun RankedTrackRow(
    rank: Int,
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableScale(pressedScale = 0.99f, onClick = onClick)
            .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textTertiary,
            modifier = Modifier.width(28.dp),
        )
        Artwork(
            id = track.id,
            url = track.artworkUrl,
            modifier = Modifier.size(dimens.artworkTiny),
        )
        Spacer(Modifier.width(dimens.spaceMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPlaying) colors.accent else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistName,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.cd_more_options),
                tint = colors.textSecondary,
            )
        }
    }
}

/**
 * Mood and genre tiles, two to a row.
 *
 * Laid out as chunked rows inside one list item rather than a nested grid: a
 * vertical grid inside a vertical list has no bounded height, and giving it one
 * would either clip the last row or leave dead space.
 */
@Composable
private fun MoodGrid(
    items: List<MoodCategory>,
    onClick: (MoodCategory) -> Unit,
) {
    val dimens = SpotKofiTheme.dimens

    Column(
        modifier = Modifier.padding(horizontal = dimens.screenGutter),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd)) {
                row.forEach { category ->
                    MoodTile(
                        category = category,
                        onClick = { onClick(category) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MoodTile(
    category: MoodCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    // The provider ships a stripe colour per category. When it sends none the tile
    // keeps the app's own accent rather than a colour invented per tile, so a tile
    // is never given a meaning the catalogue did not give it.
    val stripe = remember(category.colorArgb) {
        category.colorArgb?.let { Color(it.toInt()) }
    }

    Row(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(SpotKofiTheme.shapes.tile)
            .background(colors.card)
            .clickableScale(pressedScale = 0.98f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(64.dp)
                .background(stripe ?: colors.accent),
        )
        Text(
            text = category.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(dimens.spaceMd),
        )
    }
}

@Composable
private fun RegionDialog(
    regions: List<ChartRegion>,
    selectedCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    // Selected first, so the current region is visible without scrolling dozens of
    // countries to find it.
    val ordered = remember(regions, selectedCode) {
        regions.filter { it.code == selectedCode } +
            regions.filterNot { it.code == selectedCode }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.highlight,
        titleContentColor = colors.textPrimary,
        title = { Text("Chart region") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceXxs),
            ) {
                items(ordered, key = { it.code }) { region ->
                    val isSelected = region.code == selectedCode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SpotKofiTheme.shapes.chip)
                            .clickableScale(pressedScale = 0.99f) { onSelect(region.code) }
                            .padding(
                                horizontal = dimens.spaceMd,
                                vertical = dimens.spaceSm,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = region.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) colors.accent else colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(dimens.iconSm),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = colors.accent)
            }
        },
    )
}
