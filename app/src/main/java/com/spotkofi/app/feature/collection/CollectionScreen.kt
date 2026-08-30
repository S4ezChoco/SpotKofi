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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
import com.spotkofi.app.data.model.Playlist
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.asTrackDuration
import com.spotkofi.app.data.repository.previewCollection
import com.spotkofi.app.data.repository.previewTracks
import com.spotkofi.app.data.service.DownloadItem
import com.spotkofi.app.ui.components.Artwork
import com.spotkofi.app.ui.components.CollectionSkeleton
import com.spotkofi.app.ui.components.ErrorState
import com.spotkofi.app.ui.components.PlayButton
import com.spotkofi.app.ui.components.TrackActionsSheet
import com.spotkofi.app.ui.components.TrackRow
import com.spotkofi.app.ui.components.artworkSeedColor
import com.spotkofi.app.ui.theme.SpotKofiTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
    val playingTrackId by remember(container.playerController.state) {
        container.playerController.state.map { it.track?.id }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val isPlaying by remember(container.playerController.state) {
        container.playerController.state.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val savedTracks by container.localStore.savedTracks.collectAsStateWithLifecycle()
    val downloads by container.downloadManager.downloads.collectAsStateWithLifecycle()
    val savedCollections by container.localStore.savedCollections.collectAsStateWithLifecycle()
    val playlists by container.localStore.playlists.collectAsStateWithLifecycle()
    LaunchedEffect(playlists) {
        // Local playlist membership is stored in a relation table. Re-read the
        // open detail whenever that playlist list changes so an add from another
        // screen updates this screen without navigating away and back.
        if (collectionId.startsWith("local:playlist:") && state.collection != null) {
            viewModel.retry()
        }
    }
    val scope = rememberCoroutineScope()
    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    val downloadsByTrack = remember(downloads) { downloads.associateBy { it.track.id } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpotKofiTheme.colors.base),
    ) {
        CollectionContent(
            state = state,
            downloads = downloadsByTrack,
            playingTrackId = playingTrackId,
            isPlaying = isPlaying,
            onBack = onBack,
            onPlayAll = viewModel::onPlayAll,
            onShuffle = viewModel::onShuffle,
            onTrackClick = viewModel::onTrackClick,
            onTrackMore = { selectedTrack = it },
            isCollectionSaved = state.collection?.let { collection ->
                savedCollections.any { it.id == collection.id }
            } == true,
            onToggleCollectionSave = {
                state.collection?.let(container.localStore::toggleCollection)
            },
            onDownloadAll = { container.downloadManager.downloadTracks(state.tracks) },
            onRetry = viewModel::retry,
            contentPadding = contentPadding,
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
                    scope.launch {
                        container.localStore.addToPlaylist(playlist.id, candidate)
                        if (collectionId.startsWith("local:playlist:")) viewModel.retry()
                    }
                }
            },
        )
    }
}

@Composable
private fun CollectionContent(
    state: CollectionViewModel.UiState,
    downloads: Map<String, DownloadItem>,
    playingTrackId: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit = {},
    onTrackClick: (Track) -> Unit,
    onTrackMore: (Track) -> Unit = {},
    isCollectionSaved: Boolean = false,
    onToggleCollectionSave: () -> Unit = {},
    onDownloadAll: () -> Unit = {},
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    if (state.isLoading) {
        CollectionSkeleton(modifier = modifier.fillMaxSize())
        return
    }

    val collection = state.collection
    if (collection == null) {
        // Back is still reachable while this is showing: the header is gone, so
        // without it the only way out would be the system gesture.
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = colors.textPrimary,
                )
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorState(
                    message = state.error ?: "Could not load this",
                    onRetry = onRetry,
                )
            }
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
                            url = collection.artworkUrl,
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

                    val owner = collection.ownerName()
                    if (owner != null) {
                        OwnerRow(
                            owner = owner,
                            isSaved = isCollectionSaved,
                            onToggleSave = onToggleCollectionSave,
                        )
                    } else {
                        SaveOnlyRow(
                            isSaved = isCollectionSaved,
                            onToggleSave = onToggleCollectionSave,
                        )
                    }

                    Spacer(Modifier.height(dimens.spaceSm))

                    MetaRow(collection = collection, tracks = state.tracks)

                    CollectionDescription(collection = collection)

                    if (state.tracks.isNotEmpty()) {
                        Spacer(Modifier.height(dimens.spaceMd))

                        ActionRow(
                            isPlaying = isPlaying &&
                                playingTrackId in state.tracks.map { it.id },
                            onPlayAll = onPlayAll,
                            onShuffle = onShuffle,
                            onDownloadAll = onDownloadAll,
                        )
                    }

                    Spacer(Modifier.height(dimens.spaceLg))
                }
            }

            items(items = state.tracks, key = { it.id }) { track ->
                val download = downloads[track.id]
                TrackRow(
                    track = track,
                    onClick = { onTrackClick(track) },
                    isPlaying = playingTrackId == track.id,
                    downloadStatus = download?.status,
                    downloadProgress = download?.progress ?: 0,
                    onMoreClick = { onTrackMore(track) },
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

/** Save control plus a provider-backed owner. Albums do not use their artist as an owner. */
@Composable
private fun OwnerRow(
    owner: String,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier.padding(horizontal = dimens.screenGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaveIcon(
            isSaved = isSaved,
            onClick = onToggleSave,
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

@Composable
private fun SaveOnlyRow(
    isSaved: Boolean,
    onToggleSave: () -> Unit,
) {
    val dimens = SpotKofiTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenGutter),
    ) {
        SaveIcon(isSaved = isSaved, onClick = onToggleSave)
    }
}

@Composable
private fun SaveIcon(
    isSaved: Boolean,
    onClick: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    Icon(
        imageVector = if (isSaved) Icons.Filled.Check else Icons.Filled.AddCircleOutline,
        contentDescription = if (isSaved) "Remove from library" else "Add to library",
        tint = if (isSaved) colors.accent else colors.textSecondary,
        modifier = Modifier
            .size(24.dp)
            .clickable(onClick = onClick),
    )
}

/** Only provider-backed playlist owners are shown; an album artist is metadata, not an owner. */
private fun MediaCollection.ownerName(): String? = when (this) {
    is Playlist -> ownerName.trim().takeIf { it.isNotEmpty() }
    is Album, is Artist -> null
}

/** Metadata that the provider actually returned. No public/globe state is invented here. */
@Composable
private fun MetaRow(collection: MediaCollection, tracks: List<Track>) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val line = collection.metaLine(tracks).takeIf { it.isNotBlank() } ?: return

    Text(
        text = line,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
        modifier = Modifier.padding(horizontal = dimens.screenGutter),
    )
}

@Composable
private fun CollectionDescription(collection: MediaCollection) {
    val description = (collection as? Playlist)?.description?.takeIf { it.isNotBlank() }
        ?: return
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = dimens.screenGutter),
    )
}

/** Download, shuffle and play controls for the collection. */
@Composable
private fun ActionRow(
    isPlaying: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onDownloadAll: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDownloadAll) {
            Icon(
                imageVector = Icons.Outlined.FileDownload,
                contentDescription = "Download",
                tint = colors.textSecondary,
                modifier = Modifier.size(dimens.iconMd),
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onShuffle) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = stringResource(R.string.cd_shuffle),
                tint = colors.textPrimary,
                modifier = Modifier.size(dimens.iconMd),
            )
        }
        Spacer(Modifier.width(dimens.spaceSm))
        PlayButton(
            isPlaying = isPlaying,
            onClick = onPlayAll,
            size = dimens.playButtonLg,
        )
    }
}

/** The line under the owner row, composed only from real collection fields. */
private fun MediaCollection.metaLine(tracks: List<Track>): String {
    val totalMs = tracks.sumOf { it.durationMs }
    val minutes = totalMs / 60_000
    val duration = if (minutes >= 60) {
        "${minutes / 60}h ${minutes % 60}min"
    } else {
        "${minutes}min"
    }

    return when (this) {
        is Playlist -> listOfNotNull(
            "Playlist".takeIf { title.isNotBlank() },
            "${tracks.size} tracks".takeIf { tracks.isNotEmpty() },
            duration.takeIf { tracks.isNotEmpty() && totalMs > 0L },
        ).joinToString(" \u2022 ")

        is Album -> listOfNotNull(
            artistName.trim().takeIf { it.isNotEmpty() },
            year?.toString(),
            "${tracks.size} tracks".takeIf { tracks.isNotEmpty() },
            "${trackCount} tracks".takeIf { tracks.isEmpty() && trackCount > 0 },
            duration.takeIf { tracks.isNotEmpty() && totalMs > 0L },
        ).distinct().joinToString(" \u2022 ")

        is Artist -> listOfNotNull(
            genre?.trim()?.takeIf { it.isNotEmpty() },
            "${tracks.size} tracks".takeIf { tracks.isNotEmpty() },
        ).joinToString(" \u2022 ")
    }
}

@Preview(name = "Collection", backgroundColor = 0xFF121212, showBackground = true, heightDp = 1100)
@Composable
private fun CollectionPreview() {
    SpotKofiTheme {
        CollectionContent(
            state = CollectionViewModel.UiState(
                collection = previewCollection(),
                tracks = previewTracks(),
                isLoading = false,
            ),
            downloads = emptyMap(),
            playingTrackId = null,
            isPlaying = false,
            onBack = {},
            onPlayAll = {},
            onTrackClick = {},
            onRetry = {},
            contentPadding = PaddingValues(),
        )
    }
}
