package com.spotkofi.app.feature.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotkofi.app.R
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.data.model.BrowseCategory
import com.spotkofi.app.data.model.ExploreItem
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.asTrackDuration
import com.spotkofi.app.data.repository.previewExploreItems
import com.spotkofi.app.data.repository.previewTopCategories
import com.spotkofi.app.ui.components.Artwork
import com.spotkofi.app.ui.components.ErrorState
import com.spotkofi.app.ui.components.MediaCard
import com.spotkofi.app.ui.components.ProfileAvatar
import com.spotkofi.app.ui.components.SectionHeader
import com.spotkofi.app.ui.components.TrackRow
import com.spotkofi.app.ui.components.artworkSeedColor
import com.spotkofi.app.ui.layout.ResponsiveLayout
import com.spotkofi.app.ui.layout.rememberResponsiveLayout
import com.spotkofi.app.ui.motion.clickableScale
import com.spotkofi.app.ui.motion.staggeredEntry
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme

@Composable
fun SearchScreen(
    onCollectionClick: (String) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val container = LocalAppContainer.current
    val viewModel: SearchViewModel = viewModel {
        SearchViewModel(container.musicRepository, container.playerController)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SearchContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::onClearQuery,
        onCollectionClick = onCollectionClick,
        onTrackClick = viewModel::onTrackClick,
        onOpenProfile = onOpenProfile,
        onRetry = viewModel::onRetry,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

@Composable
private fun SearchContent(
    state: SearchViewModel.UiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onCollectionClick: (String) -> Unit,
    onTrackClick: (Track) -> Unit,
    onOpenProfile: () -> Unit,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val dimens = SpotKofiTheme.dimens
    val layout = rememberResponsiveLayout()

    Column(modifier = modifier.fillMaxSize()) {
        // Header and field are pinned rather than scrolled away. Once someone is
        // typing, having the field leave the screen is the single most annoying
        // thing a search UI can do.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = contentPadding.calculateTopPadding()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = layout.gutter,
                        end = dimens.spaceSm,
                        top = dimens.spaceLg,
                        bottom = dimens.spaceMd,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(name = state.userName, onClick = onOpenProfile, size = 34.dp)
                Spacer(Modifier.width(dimens.spaceMd))
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.displaySmall,
                    color = SpotKofiTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { /* Phase 5: scan a code or cover */ }) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = stringResource(R.string.cd_camera_search),
                        tint = SpotKofiTheme.colors.textPrimary,
                        modifier = Modifier.size(dimens.iconMd),
                    )
                }
            }

            SearchField(
                query = state.query,
                gutter = layout.gutter,
                onQueryChange = onQueryChange,
                onClear = onClearQuery,
            )

            Spacer(Modifier.height(dimens.spaceLg))
        }

        // Browse and results are different screens conceptually, so they cross-
        // fade with a slight vertical offset instead of items popping in place.
        AnimatedContent(
            targetState = state.showBrowse,
            transitionSpec = {
                val enter = fadeIn(Motion.medium()) +
                    slideInVertically(Motion.medium()) { it / 14 }
                val exit = fadeOut(Motion.fast()) +
                    slideOutVertically(Motion.fast()) { -it / 14 }
                enter togetherWith exit
            },
            label = "searchMode",
            modifier = Modifier.fillMaxSize(),
        ) { browsing ->
            if (browsing) {
                BrowseList(
                    state = state,
                    layout = layout,
                    contentPadding = contentPadding,
                    onTrackClick = onTrackClick,
                )
            } else {
                ResultsList(
                    state = state,
                    layout = layout,
                    contentPadding = contentPadding,
                    onCollectionClick = onCollectionClick,
                    onTrackClick = onTrackClick,
                    onRetry = onRetry,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ browse -- */

@Composable
private fun BrowseList(
    state: SearchViewModel.UiState,
    layout: ResponsiveLayout,
    contentPadding: PaddingValues,
    onTrackClick: (Track) -> Unit,
) {
    val dimens = SpotKofiTheme.dimens

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
    ) {
        item(key = "browse_header") { SectionHeader(title = "Browse all") }

        state.categories.chunked(layout.tileColumns).forEachIndexed { rowIndex, row ->
            item(key = "tc_" + row.first().id) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEntry(rowIndex)
                        .padding(horizontal = layout.gutter, vertical = dimens.spaceXs),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
                ) {
                    row.forEach { category ->
                        TopCategoryTile(
                            category = category,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(layout.tileColumns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        item(key = "videos") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredEntry(4),
            ) {
                Spacer(Modifier.height(dimens.shelfSpacing))
                SectionHeader(title = "Explore music videos")
                ExploreShelf(
                    items = state.videos,
                    tall = true,
                    gutter = layout.gutter,
                    onTrackClick = onTrackClick,
                )
            }
        }

        item(key = "episodes") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredEntry(5),
            ) {
                Spacer(Modifier.height(dimens.shelfSpacing))
                SectionHeader(title = "Explore episodes for you")
                ExploreShelf(
                    items = state.episodes,
                    tall = false,
                    gutter = layout.gutter,
                    onTrackClick = onTrackClick,
                )
                Spacer(Modifier.height(dimens.shelfSpacing))
            }
        }
    }
}

/* ----------------------------------------------------------------- results -- */

@Composable
private fun ResultsList(
    state: SearchViewModel.UiState,
    layout: ResponsiveLayout,
    contentPadding: PaddingValues,
    onCollectionClick: (String) -> Unit,
    onTrackClick: (Track) -> Unit,
    onRetry: () -> Unit,
) {
    val dimens = SpotKofiTheme.dimens
    val results = state.results
    val error = state.error

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
    ) {
        // A failed request and a query with genuinely no matches look identical
        // from the result list alone, so the error is checked first. Telling
        // someone their search found nothing when the network is down sends them
        // looking for a different spelling.
        if (error != null) {
            item(key = "error") {
                ErrorState(
                    message = error,
                    onRetry = onRetry,
                    title = "Search didn't go through",
                )
            }
            return@LazyColumn
        }

        if (results.isEmpty && !state.isSearching) {
            item(key = "empty") { EmptyResults(query = state.query) }
        }

        if (results.collections.isNotEmpty()) {
            item(key = "col_header") { SectionHeader(title = "Playlists, albums & artists") }
            item(key = "col_row") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = layout.gutter),
                        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
                    ) {
                        items(items = results.collections, key = { it.id }) { item ->
                            MediaCard(
                                item = item,
                                onClick = { onCollectionClick(item.id) },
                                width = layout.shelfCardWidth,
                            )
                        }
                    }
                    Spacer(Modifier.height(dimens.spaceLg))
                }
            }
        }

        if (results.tracks.isNotEmpty()) {
            item(key = "tr_header") { SectionHeader(title = "Songs") }
            results.tracks.forEachIndexed { index, track ->
                item(key = track.id) {
                    Box(modifier = Modifier.staggeredEntry(index)) {
                        TrackRow(
                            track = track,
                            onClick = { onTrackClick(track) },
                            trailingText = track.durationMs.asTrackDuration(),
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------- field -- */

@Composable
private fun SearchField(
    query: String,
    gutter: androidx.compose.ui.unit.Dp,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    // A growing accent ring is the focus affordance. It does not rely on colour
    // alone, so it still reads for anyone who cannot distinguish the green.
    val ringWidth by animateDpAsState(
        targetValue = if (focused) 2.5.dp else 0.dp,
        animationSpec = Motion.snappy(),
        label = "searchRing",
    )
    val ringColor by animateColorAsState(
        targetValue = if (focused) colors.accent else Color.Transparent,
        animationSpec = Motion.fast(),
        label = "searchRingColor",
    )

    TextField(
        value = query,
        onValueChange = onQueryChange,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = gutter)
            .border(
                width = ringWidth,
                color = ringColor,
                shape = SpotKofiTheme.shapes.searchField,
            ),
        placeholder = {
            Text(
                text = "What do you want to listen to?",
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = colors.base,
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = colors.base,
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = SpotKofiTheme.shapes.searchField,
        // The one inverted surface in the app: a white field on a dark screen.
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedTextColor = colors.base,
            unfocusedTextColor = colors.base,
            focusedPlaceholderColor = colors.base.copy(alpha = 0.66f),
            unfocusedPlaceholderColor = colors.base.copy(alpha = 0.66f),
            cursorColor = colors.base,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

/* ------------------------------------------------------------------- tiles -- */

/**
 * Fixed hues for the four headline categories.
 *
 * These are brand-ish rather than generated: the real app uses a specific,
 * recognisable colour per category, so deriving them from a hash would lose the
 * association. Anything unmapped falls back to the generated palette.
 */
private fun categoryColor(id: String): Color = when (id) {
    "tc_music" -> Color(0xFFD6216E)
    "tc_podcasts" -> Color(0xFF0C5C4C)
    "tc_live" -> Color(0xFF7A3DF0)
    "tc_kpop" -> Color(0xFF1D4FD8)
    else -> artworkSeedColor(id)
}

/** One of the four large tiles, with artwork tilted into the trailing corner. */
@Composable
private fun TopCategoryTile(
    category: BrowseCategory,
    modifier: Modifier = Modifier,
) {
    val dimens = SpotKofiTheme.dimens

    Box(
        modifier = modifier
            .aspectRatio(1.75f)
            .clip(SpotKofiTheme.shapes.card)
            .background(categoryColor(category.id))
            .clickableScale(pressedScale = 0.96f) { /* Phase 5: category browse */ },
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(dimens.spaceMd)
                .fillMaxWidth(0.72f),
        )

        Artwork(
            id = category.id,
            size = 52.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 8.dp)
                .rotate(26f),
        )
    }
}

@Composable
private fun ExploreShelf(
    items: List<ExploreItem>,
    tall: Boolean,
    gutter: androidx.compose.ui.unit.Dp,
    onTrackClick: (Track) -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    LazyRow(
        contentPadding = PaddingValues(horizontal = gutter),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        items(items = items, key = { it.id }) { item ->
            Column(
                modifier = Modifier
                    .width(148.dp)
                    .clickableScale { item.track?.let(onTrackClick) },
            ) {
                Box {
                    Artwork(
                        id = item.id,
                        url = item.artworkUrl,
                        shape = SpotKofiTheme.shapes.tile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(if (tall) 0.78f else 1f),
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(dimens.spaceSm),
                    )
                }
                if (item.caption != null) {
                    Spacer(Modifier.height(dimens.spaceSm))
                    Text(
                        text = item.caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyResults(query: String) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimens.spaceHuge),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No results for \"$query\"",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
        )
    }
}

@Preview(name = "Search", backgroundColor = 0xFF121212, showBackground = true, heightDp = 1000)
@Composable
private fun SearchPreview() {
    SpotKofiTheme {
        SearchContent(
            state = SearchViewModel.UiState(
                userName = "kofi_listener",
                categories = previewTopCategories(),
                videos = previewExploreItems(),
                episodes = previewExploreItems(),
            ),
            onQueryChange = {},
            onClearQuery = {},
            onCollectionClick = {},
            onTrackClick = {},
            onOpenProfile = {},
            onRetry = {},
            contentPadding = PaddingValues(),
        )
    }
}
