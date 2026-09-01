package com.spotkofi.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spotkofi.app.data.model.ChartRegion
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.MoodCategory
import com.spotkofi.app.data.model.MoodGroup
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.repository.MoodCategoryContents
import com.spotkofi.app.ui.components.Artwork
import com.spotkofi.app.ui.components.MediaCard
import com.spotkofi.app.ui.components.SectionHeader
import com.spotkofi.app.ui.components.SkeletonBox
import com.spotkofi.app.ui.components.SkeletonShelf
import com.spotkofi.app.ui.components.SkeletonTrackRow
import com.spotkofi.app.ui.components.TrackRow
import com.spotkofi.app.ui.layout.ResponsiveLayout
import com.spotkofi.app.ui.motion.clickableScale
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * Provider-backed blocks that live on Home, in the same order as the reference
 * screenshots: trending playlists, moods and moments, genres, then chart and
 * top artists. All content is passed in from [HomeViewModel]; this file contains
 * no network or fabricated fallback data.
 */
@Composable
fun HomeExploreSections(
    state: HomeViewModel.UiState,
    layout: ResponsiveLayout,
    playingTrackId: String?,
    onCollectionClick: (String) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onTrackMore: (Track) -> Unit,
    onMoodClick: (MoodCategory) -> Unit,
    onRegionClick: () -> Unit,
) {
    val selectedMood = state.selectedMood
    if (selectedMood != null || state.selectedFilter != HomeFilter.All) {
        if (state.isMoodLoading) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SpotKofiTheme.dimens.spaceMd),
            ) {
                SkeletonShelf(gutter = layout.gutter)
                repeat(4) { SkeletonTrackRow() }
            }
        } else if (selectedMood != null) {
            MoodResults(
                content = selectedMood,
                layout = layout,
                playingTrackId = playingTrackId,
                onCollectionClick = onCollectionClick,
                onTrackClick = onTrackClick,
                onTrackMore = onTrackMore,
            )
        } else {
            Text(
                text = "That mood is not available in this region yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = SpotKofiTheme.colors.textSecondary,
                modifier = Modifier.padding(
                    horizontal = layout.gutter,
                    vertical = SpotKofiTheme.dimens.spaceXl,
                ),
            )
        }
        return
    }

    // Deduplicate trending playlists against chart shelves so the same playlist
    // does not appear in both the trending row and the chart section below.
    val chartPlaylistIds = state.chart?.shelves
        ?.flatMap { it.items }
        ?.map { it.id }
        .orEmpty()
        .toSet()
    val uniqueTrending = state.trendingPlaylists.filterNot { it.id in chartPlaylistIds }
    if (uniqueTrending.isNotEmpty()) {
        HomeCollectionShelf(
            title = "Trending playlists",
            items = uniqueTrending.take(8),
            layout = layout,
            onCollectionClick = onCollectionClick,
        )
    }

    if (state.newReleases.isNotEmpty()) {
        HomeCollectionShelf(
            title = "Take it easy",
            items = state.newReleases,
            layout = layout,
            onCollectionClick = onCollectionClick,
        )
    }

    state.moodGroups.forEach { group ->
        if (group.items.isNotEmpty()) {
            HomeMoodGroup(
                group = group,
                onMoodClick = onMoodClick,
            )
        }
    }

    state.chart?.let { chart ->
        HomeChart(
            regionName = state.regionName,
            regionCode = state.regionCode,
            chart = chart,
            layout = layout,
            playingTrackId = playingTrackId,
            isLoading = state.isRegionLoading,
            onRegionClick = onRegionClick,
            onCollectionClick = onCollectionClick,
            onTrackClick = onTrackClick,
            onTrackMore = onTrackMore,
        )
    }
}

@Composable
private fun HomeCollectionShelf(
    title: String,
    items: List<MediaCollection>,
    layout: ResponsiveLayout,
    onCollectionClick: (String) -> Unit,
) {
    val dimens = SpotKofiTheme.dimens
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = layout.gutter),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
        ) {
            items(items.take(12), key = { it.id }) { item ->
                MediaCard(
                    item = item,
                    width = layout.shelfCardWidth,
                    onClick = { onCollectionClick(item.id) },
                )
            }
        }
        Spacer(Modifier.height(dimens.shelfSpacing))
    }
}

@Composable
private fun HomeMoodGroup(
    group: MoodGroup,
    onMoodClick: (MoodCategory) -> Unit,
) {
    val dimens = SpotKofiTheme.dimens
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = group.title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = dimens.screenGutter),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
        ) {
            items(group.items, key = { it.params }) { category ->
                HomeMoodTile(
                    category = category,
                    onClick = { onMoodClick(category) },
                )
            }
        }
        Spacer(Modifier.height(dimens.shelfSpacing))
    }
}

@Composable
private fun HomeMoodTile(
    category: MoodCategory,
    onClick: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val stripe = remember(category.colorArgb) {
        category.colorArgb?.let { Color(it.toInt()) } ?: colors.accent
    }

    Row(
        modifier = Modifier
            .width(184.dp)
            .height(66.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.card)
            .clickableScale(pressedScale = 0.98f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(66.dp)
                .background(stripe),
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
                .padding(horizontal = dimens.spaceMd),
        )
    }
}

@Composable
private fun HomeChart(
    regionName: String,
    regionCode: String,
    chart: com.spotkofi.app.data.model.MusicChart,
    layout: ResponsiveLayout,
    playingTrackId: String?,
    isLoading: Boolean,
    onRegionClick: () -> Unit,
    onCollectionClick: (String) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onTrackMore: (Track) -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "WHAT IS BEST CHOICE TODAY",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(
                start = layout.gutter,
                end = layout.gutter,
                top = dimens.spaceSm,
            ),
        )
        Text(
            text = "Chart",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                horizontal = layout.gutter,
                vertical = dimens.spaceXs,
            ),
        )
        RegionButton(
            regionName = regionName,
            regionCode = regionCode,
            loading = isLoading,
            onClick = onRegionClick,
            modifier = Modifier.padding(horizontal = layout.gutter),
        )

        if (chart.topSongs.isNotEmpty()) {
            SectionHeader(title = "Top songs")
            chart.topSongs.take(5).forEach { track ->
                TrackRow(
                    track = track,
                    isPlaying = track.id == playingTrackId,
                    onClick = { onTrackClick(track, chart.topSongs) },
                    onMoreClick = { onTrackMore(track) },
                )
            }
        }

        // Video charts section removed - this was a duplicate, chart shelves are handled elsewhere

        if (chart.topArtists.isNotEmpty()) {
            SectionHeader(title = "Top artists")
            chart.topArtists.take(5).forEachIndexed { index, artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableScale(
                            pressedScale = 0.98f,
                            onClick = { onCollectionClick(artist.id) },
                        )
                        .padding(
                            horizontal = layout.gutter,
                            vertical = dimens.spaceSm,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.width(32.dp),
                    )
                    Artwork(
                        id = artist.id,
                        url = artist.artworkUrl,
                        shape = SpotKofiTheme.shapes.avatar,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.width(dimens.spaceMd))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = artist.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(dimens.shelfSpacing))
    }
}

@Composable
private fun MoodResults(
    content: MoodCategoryContents,
    layout: ResponsiveLayout,
    playingTrackId: String?,
    onCollectionClick: (String) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onTrackMore: (Track) -> Unit,
) {
    if (content.playlists.isNotEmpty()) {
        HomeCollectionShelf(
            title = content.title,
            items = content.playlists,
            layout = layout,
            onCollectionClick = onCollectionClick,
        )
    }
    if (content.songs.isNotEmpty()) {
        SectionHeader(title = "Songs")
        content.songs.forEach { track ->
            TrackRow(
                track = track,
                isPlaying = track.id == playingTrackId,
                onClick = { onTrackClick(track, content.songs) },
                onMoreClick = { onTrackMore(track) },
            )
        }
    }
}

@Composable
private fun RegionButton(
    regionName: String,
    regionCode: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(colors.brown.copy(alpha = 0.15f))
            .clickableScale(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        Icon(
            imageVector = Icons.Filled.Public,
            contentDescription = null,
            tint = colors.brown,
            modifier = Modifier.size(dimens.iconSm),
        )
        Text(
            text = regionName,
            style = MaterialTheme.typography.titleMedium,
            color = colors.brown,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
        if (loading) {
            SkeletonBox(
                modifier = Modifier.size(26.dp, 18.dp),
                shape = RoundedCornerShape(9.dp),
            )
        } else {
            Text(
                text = regionCode,
                style = MaterialTheme.typography.labelMedium,
                color = colors.brown,
            )
        }
    }
}

/** Region picker used by Home's chart section. */
@Composable
fun HomeRegionDialog(
    regions: List<ChartRegion>,
    selectedCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    var query by remember { mutableStateOf("") }
    val ordered = remember(regions, selectedCode, query) {
        val filtered = regions.filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true) ||
                it.code.contains(query, ignoreCase = true)
        }
        filtered.sortedWith(compareByDescending<ChartRegion> { it.code == selectedCode }.thenBy { it.name })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.highlight,
        titleContentColor = colors.textPrimary,
        title = { Text("Chart region") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.base)
                        .border(1.dp, colors.textTertiary, RoundedCornerShape(18.dp))
                        .padding(horizontal = dimens.spaceMd, vertical = dimens.spaceSm),
                    decorationBox = { innerTextField ->
                        if (query.isBlank()) {
                            Text(
                                text = "Search countries",
                                color = colors.textTertiary,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        innerTextField()
                    },
                )

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.height(360.dp),
                ) {
                    items(ordered, key = { it.code }) { region ->
                        val selected = region.code == selectedCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableScale(pressedScale = 0.99f) { onSelect(region.code) }
                                .padding(horizontal = dimens.spaceMd, vertical = dimens.spaceSm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = region.name,
                                color = if (selected) colors.brown else colors.textPrimary,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = colors.brown,
                                    modifier = Modifier.size(dimens.iconSm),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = colors.brown)
            }
        },
    )
}
