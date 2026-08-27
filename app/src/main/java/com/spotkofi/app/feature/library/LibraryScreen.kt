package com.spotkofi.app.feature.library

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = dimens.screenGutter,
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
        }

        item(key = "filters") {
            Column(modifier = Modifier.fillMaxWidth()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = dimens.screenGutter),
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
            }
        }

        item(key = "sortrow") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.screenGutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clickable(onClick = onCycleSort)
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
                    Text(
                        text = state.sortMode.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textPrimary,
                    )
                }

                Spacer(Modifier.weight(1f))

                IconButton(onClick = onToggleViewMode) {
                    Icon(
                        imageVector = if (state.viewMode == LibraryViewModel.ViewMode.List) {
                            Icons.Filled.GridView
                        } else {
                            Icons.Filled.ViewList
                        },
                        contentDescription = stringResource(R.string.cd_toggle_view),
                        tint = colors.textPrimary,
                        modifier = Modifier.size(dimens.iconMd),
                    )
                }
            }
        }

        if (state.isLoading) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }
            return@LazyColumn
        }

        val visible = state.visibleItems

        if (visible.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimens.spaceHuge),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Nothing here yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                    )
                }
            }
        } else if (state.viewMode == LibraryViewModel.ViewMode.Grid) {
            items(
                items = visible.chunked(2),
                key = { pair -> "g_" + pair.first().id },
            ) { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = dimens.screenGutter,
                            vertical = dimens.spaceSm,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
                ) {
                    pair.forEach { item ->
                        MediaCard(
                            item = item,
                            onClick = { onCollectionClick(item.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            items(items = visible, key = { it.id }) { item ->
                LibraryRow(item = item, onClick = { onCollectionClick(item.id) })
            }
        }
    }
}

@Composable
private fun LibraryRow(
    item: MediaCollection,
    onClick: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val isPinned = (item as? Playlist)?.isPinned == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            id = item.id,
            size = dimens.artworkRow,
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
            userName = "CHOCO",
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
