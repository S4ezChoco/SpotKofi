package com.spotkofi.app.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotkofi.app.R
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.Playlist
import com.spotkofi.app.data.repository.previewLibrary
import com.spotkofi.app.ui.components.Artwork
import com.spotkofi.app.ui.components.MediaCard
import com.spotkofi.app.ui.components.ProfileAvatar
import com.spotkofi.app.ui.components.SpotKofiChip
import com.spotkofi.app.ui.layout.ResponsiveLayout
import com.spotkofi.app.ui.layout.rememberResponsiveLayout
import com.spotkofi.app.ui.motion.clickableScale
import com.spotkofi.app.ui.motion.staggeredEntry
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme

@Composable
fun LibraryScreen(
    onCollectionClick: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val container = LocalAppContainer.current
    val viewModel: LibraryViewModel = viewModel { LibraryViewModel(container.musicRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LibraryContent(
        state = state,
        userName = container.musicRepository.currentUserName(),
        onFilterClick = viewModel::onFilterClick,
        onCycleSort = viewModel::onCycleSort,
        onToggleViewMode = viewModel::onToggleViewMode,
        onCollectionClick = onCollectionClick,
        onOpenProfile = onOpenProfile,
        onCreate = onCreate,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

@Composable
private fun LibraryContent(
    state: LibraryViewModel.UiState,
    userName: String,
    onFilterClick: (LibraryViewModel.Filter) -> Unit,
    onCycleSort: () -> Unit,
    onToggleViewMode: () -> Unit,
    onCollectionClick: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onCreate: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val layout = rememberResponsiveLayout()

    Column(modifier = modifier.fillMaxSize()) {
        // Header, filters and the sort row stay put; only the collection below
        // them animates when the view mode changes.
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
                ProfileAvatar(name = userName, onClick = onOpenProfile, size = 34.dp)
                Spacer(Modifier.width(dimens.spaceMd))
                Text(
                    text = "Your Library",
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { /* Phase 5: search within library */ }) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.nav_search),
                        tint = colors.textPrimary,
                        modifier = Modifier.size(dimens.iconMd),
                    )
                }
                IconButton(onClick = onCreate) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.nav_create),
                        tint = colors.textPrimary,
                        modifier = Modifier.size(dimens.iconMd),
                    )
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = layout.gutter),
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
            ) {
                items(items = LibraryViewModel.Filter.entries, key = { it.name }) { filter ->
                    SpotKofiChip(
                        label = filter.label,
                        selected = state.filter == filter,
                        onClick = { onFilterClick(filter) },
                    )
                }
            }

            Spacer(Modifier.height(dimens.spaceLg))

            SortRow(
                sortMode = state.sortMode,
                viewMode = state.viewMode,
                gutter = layout.gutter,
                onCycleSort = onCycleSort,
                onToggleViewMode = onToggleViewMode,
            )
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent)
            }
            return@Column
        }

        // Switching between list and grid is a genuine layout change, so it gets a
        // crossfade with a small scale rather than items teleporting.
        AnimatedContent(
            targetState = state.viewMode,
            transitionSpec = {
                val enter = fadeIn(Motion.medium()) + scaleIn(Motion.medium(), initialScale = 0.94f)
                val exit = fadeOut(Motion.fast()) + scaleOut(Motion.fast(), targetScale = 1.04f)
                enter togetherWith exit
            },
            label = "libraryViewMode",
            modifier = Modifier.fillMaxSize(),
        ) { viewMode ->
            LibraryCollection(
                items = state.visibleItems,
                viewMode = viewMode,
                layout = layout,
                contentPadding = contentPadding,
                onCollectionClick = onCollectionClick,
            )
        }
    }
}

@Composable
private fun SortRow(
    sortMode: LibraryViewModel.SortMode,
    viewMode: LibraryViewModel.ViewMode,
    gutter: androidx.compose.ui.unit.Dp,
    onCycleSort: () -> Unit,
    onToggleViewMode: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clickableScale(pressedScale = 0.95f, onClick = onCycleSort)
                .padding(vertical = dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.SwapVert,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(dimens.iconMd),
            )
            Spacer(Modifier.width(dimens.spaceXs))
            // The label slides so the change registers as "the sort moved on",
            // not as a silent text swap.
            AnimatedContent(
                targetState = sortMode,
                transitionSpec = {
                    val enter = slideInVertically(Motion.snappy()) { it } + fadeIn(Motion.fast())
                    val exit = slideOutVertically(Motion.snappy()) { -it } + fadeOut(Motion.fast())
                    enter togetherWith exit
                },
                label = "sortLabel",
            ) { mode ->
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Rotates a quarter turn as it swaps, so the two icons feel like one
        // control changing state.
        val iconSpin by animateFloatAsState(
            targetValue = if (viewMode == LibraryViewModel.ViewMode.Grid) 90f else 0f,
            animationSpec = Motion.bouncy(),
            label = "viewModeSpin",
        )

        IconButton(onClick = onToggleViewMode) {
            Icon(
                imageVector = if (viewMode == LibraryViewModel.ViewMode.List) {
                    Icons.Filled.GridView
                } else {
                    Icons.Filled.ViewList
                },
                contentDescription = stringResource(R.string.cd_toggle_view),
                tint = colors.textPrimary,
                modifier = Modifier
                    .size(dimens.iconMd)
                    .graphicsLayer { rotationZ = iconSpin },
            )
        }
    }
}

@Composable
private fun LibraryCollection(
    items: List<MediaCollection>,
    viewMode: LibraryViewModel.ViewMode,
    layout: ResponsiveLayout,
    contentPadding: PaddingValues,
    onCollectionClick: (String) -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    if (items.isEmpty()) {
        // This list is what the user has opened, not what they have saved, because
        // saving needs an account. The second line says so: an unexplained empty
        // screen on a tab called Your Library reads as a bug.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimens.spaceHuge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Nothing here yet",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(dimens.spaceSm))
            Text(
                text = "Albums and artists you open will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
    ) {
        if (viewMode == LibraryViewModel.ViewMode.Grid) {
            items.chunked(layout.gridColumns).forEachIndexed { rowIndex, row ->
                item(key = "g_" + row.first().id) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredEntry(rowIndex)
                            .padding(horizontal = layout.gutter, vertical = dimens.spaceSm),
                        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
                    ) {
                        row.forEach { item ->
                            MediaCard(
                                item = item,
                                onClick = { onCollectionClick(item.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(layout.gridColumns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            items.forEachIndexed { index, item ->
                item(key = item.id) {
                    LibraryRow(
                        item = item,
                        gutter = layout.gutter,
                        onClick = { onCollectionClick(item.id) },
                        modifier = Modifier.staggeredEntry(index),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(
    item: MediaCollection,
    gutter: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val isPinned = (item as? Playlist)?.isPinned == true

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickableScale(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = gutter, vertical = dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            id = item.id,
            size = dimens.artworkRow,
            url = item.artworkUrl,
            shape = if (item is Artist) {
                SpotKofiTheme.shapes.avatar
            } else {
                SpotKofiTheme.shapes.artwork
            },
        )

        Spacer(Modifier.width(dimens.spaceMd))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPinned) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = stringResource(R.string.cd_pinned),
                        tint = colors.accent,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(dimens.spaceXs))
                }
                Text(
                    text = item.librarySubtitle(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview(name = "Library", backgroundColor = 0xFF121212, showBackground = true, heightDp = 1000)
@Composable
private fun LibraryPreview() {
    SpotKofiTheme {
        LibraryContent(
            state = LibraryViewModel.UiState(items = previewLibrary(), isLoading = false),
            userName = "kofi_listener",
            onFilterClick = {},
            onCycleSort = {},
            onToggleViewMode = {},
            onCollectionClick = {},
            onOpenProfile = {},
            onCreate = {},
            contentPadding = PaddingValues(),
        )
    }
}
