package com.spotkofi.app.feature.collection

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotkofi.app.R
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.PlaybackState
import com.spotkofi.app.data.model.Playlist
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.asTrackDuration
import com.spotkofi.app.data.repository.previewPlaylist
import com.spotkofi.app.data.repository.previewTracks
import com.spotkofi.app.ui.components.Artwork
import com.spotkofi.app.ui.components.PlayButton
import com.spotkofi.app.ui.components.TrackRow
import com.spotkofi.app.ui.components.artworkSeedColor
import com.spotkofi.app.ui.theme.SpotKofiTheme

@Composable
fun CollectionScreen(
    collectionId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val container = LocalAppContainer.current
    val viewModel: CollectionViewModel = viewModel(key = "collection_$collectionId") {
        CollectionViewModel(
            collectionId = collectionId,
            repository = container.musicRepository,
            player = container.playerController,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()

    CollectionContent(
        state = state,
        playback = playback,
        onBack = onBack,
        onPlayAll = viewModel::onPlayAll,
        onTrackClick = viewModel::onTrackClick,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

@Composable
private fun CollectionContent(
    state: CollectionViewModel.UiState,
    playback: PlaybackState,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onTrackClick: (Track) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    val collection = state.collection
    if (state.isLoading || collection == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.accent)
        }
        return
    }

    val seed = remember(collection.id) { artworkSeedColor(collection.id) }
    // Desaturated towards the background so the track list below stays legible
    // rather than sitting on saturated colour.
    val headerBrush = remember(seed) {
        Brush.verticalGradient(
            listOf(
                lerp(seed, colors.base, 0.42f),
                lerp(seed, colors.base, 0.78f),
                colors.base,
            ),
        )
    }

    val listState = rememberLazyListState()
    val showTitleInBar by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    val barColor by animateColorAsState(
        targetValue = if (showTitleInBar) colors.base else Color.Transparent,
        animationSpec = tween(200),
        label = "collectionBarColor",
    )

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBrush)
                        .padding(top = contentPadding.calculateTopPadding() + 56.dp),
                ) {
                    // Artwork is centred, but everything under it is left aligned.
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Artwork(
                            id = collection.id,
                            size = 250.dp,
                            shape = if (collection is Artist) {
                                SpotKofiTheme.shapes.avatar
                            } else {
                                SpotKofiTheme.shapes.artwork
                            },
                        )
                    }

                    Spacer(Modifier.height(dimens.spaceXl))

                    Text(
                        text = collection.title,
                        style = MaterialTheme.typography.displayMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(horizontal = dimens.screenGutter),
                    )

                    Spacer(Modifier.height(dimens.spaceMd))

                    OwnerRow(collection = collection)

                    Spacer(Modifier.height(dimens.spaceSm))

                    MetaRow(collection = collection, tracks = state.tracks)

                    Spacer(Modifier.height(dimens.spaceMd))

                    ActionRow(
                        collectionId = collection.id,
                        isPlaying = playback.isPlaying &&
                            playback.track?.id in state.tracks.map { it.id },
                        onPlayAll = onPlayAll,
                    )

                    Spacer(Modifier.height(dimens.spaceMd))

                    ActionPills()

                    Spacer(Modifier.height(dimens.spaceLg))
                }
            }

            items(items = state.tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    onClick = { onTrackClick(track) },
                    isPlaying = playback.track?.id == track.id,
                    onMoreClick = { /* Phase 5: track context sheet */ },
                )
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(contentPadding.calculateBottomPadding() + dimens.spaceHuge))
            }
        }

        // Floating top bar so back is always reachable.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(barColor)
                .padding(top = contentPadding.calculateTopPadding())
                .padding(horizontal = dimens.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = colors.textPrimary,
                )
            }
            if (showTitleInBar) {
                Text(
                    text = collection.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Add-circle, owner avatar and owner name. */
@Composable
private fun OwnerRow(collection: MediaCollection) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val owner = collection.ownerName()

    Row(
        modifier = Modifier.padding(horizontal = dimens.screenGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.AddCircleOutline,
            contentDescription = stringResource(R.string.cd_add_to_library),
            tint = colors.textSecondary,
            modifier = Modifier
                .size(24.dp)
                .clickable { /* Phase 5: save collection */ },
        )
        Spacer(Modifier.width(dimens.spaceSm))
        Artwork(id = owner, size = 22.dp, shape = CircleShape)
        Spacer(Modifier.width(dimens.spaceSm))
        Text(
            text = owner,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Globe plus "N saves - total duration". */
@Composable
private fun MetaRow(collection: MediaCollection, tracks: List<Track>) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier.padding(horizontal = dimens.screenGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Public,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(dimens.spaceXs))
        Text(
            text = collection.metaLine(tracks),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

/**
 * Download, share and more on the left; shuffle and the big play button on the
 * right. The leading thumbnail previews the first track in the running order.
 */
@Composable
private fun ActionRow(
    collectionId: String,
    isPlaying: Boolean,
    onPlayAll: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(id = collectionId + "_thumb", size = 30.dp)

        Spacer(Modifier.width(dimens.spaceMd))

        Icon(
            imageVector = Icons.Outlined.FileDownload,
            contentDescription = "Download",
            tint = colors.textSecondary,
            modifier = Modifier
                .size(26.dp)
                .clickable { /* Phase 5: offline download */ },
        )

        Spacer(Modifier.width(dimens.spaceLg))

        Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = "Share",
            tint = colors.textSecondary,
            modifier = Modifier
                .size(22.dp)
                .clickable { /* Phase 4: share sheet */ },
        )

        Spacer(Modifier.width(dimens.spaceLg))

        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.cd_more_options),
            tint = colors.textSecondary,
            modifier = Modifier
                .size(22.dp)
                .clickable { /* Phase 5: collection context sheet */ },
        )

        Spacer(Modifier.weight(1f))

        Icon(
            imageVector = Icons.Filled.Shuffle,
            contentDescription = stringResource(R.string.cd_shuffle),
            tint = colors.textSecondary,
            modifier = Modifier
                .size(26.dp)
                .clickable { /* Phase 5: shuffle the queue */ },
        )

        Spacer(Modifier.width(dimens.spaceLg))

        PlayButton(
            isPlaying = isPlaying,
            onClick = onPlayAll,
            size = dimens.playButtonLg,
        )
    }
}

/** Add / Mix / Video / Edit pills. */
@Composable
private fun ActionPills() {
    val dimens = SpotKofiTheme.dimens

    LazyRow(
        contentPadding = PaddingValues(horizontal = dimens.screenGutter),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        item { ActionPill(Icons.Filled.Add, "Add") }
        item { ActionPill(Icons.Filled.Tune, "Mix") }
        item { ActionPill(Icons.Filled.VideoLibrary, "Video") }
        item { ActionPill(Icons.Filled.Edit, "Edit") }
    }
}

@Composable
private fun ActionPill(icon: ImageVector, label: String) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .height(dimens.chipHeight + 4.dp)
            .background(colors.chip, SpotKofiTheme.shapes.chip)
            .clickable { /* Phase 5 */ }
            .padding(horizontal = dimens.spaceLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(dimens.iconSm),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.textPrimary,
        )
    }
}

private fun MediaCollection.ownerName(): String = when (this) {
    is Playlist -> ownerName
    is Album -> artistName
    is Artist -> name
}

/** The line under the owner row. */
private fun MediaCollection.metaLine(tracks: List<Track>): String {
    val totalMs = tracks.sumOf { it.durationMs }
    val minutes = totalMs / 60_000
    val duration = if (minutes >= 60) {
        "${minutes / 60}h ${minutes % 60}min"
    } else {
        "${minutes}min"
    }

    return when (this) {
        is Playlist -> "$saves saves \u2022 $duration"
        is Album -> "$year \u2022 ${tracks.size} songs \u2022 $duration"
        is Artist -> "%,d monthly listeners".format(monthlyListeners)
    }
}

@Preview(name = "Collection", backgroundColor = 0xFF121212, showBackground = true, heightDp = 1100)
@Composable
private fun CollectionPreview() {
    SpotKofiTheme {
        CollectionContent(
            state = CollectionViewModel.UiState(
                collection = previewPlaylist(),
                tracks = previewTracks(),
                isLoading = false,
            ),
            playback = PlaybackState(),
            onBack = {},
            onPlayAll = {},
            onTrackClick = {},
            contentPadding = PaddingValues(),
        )
    }
}
