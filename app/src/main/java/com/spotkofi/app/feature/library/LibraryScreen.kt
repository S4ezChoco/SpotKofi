package com.spotkofi.app.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.Playlist
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.asTrackDuration
import com.spotkofi.app.data.service.DownloadItem
import com.spotkofi.app.data.service.DownloadManagerStatus
import com.spotkofi.app.ui.components.AppFooter
import com.spotkofi.app.ui.components.Artwork
import com.spotkofi.app.ui.components.ProfileAvatar
import com.spotkofi.app.ui.components.SavedToggle
import com.spotkofi.app.ui.components.SpotKofiChip
import com.spotkofi.app.ui.components.TrackActionsSheet
import com.spotkofi.app.ui.components.TrackRow
import com.spotkofi.app.ui.layout.rememberResponsiveLayout
import com.spotkofi.app.ui.theme.SpotKofiTheme
import kotlinx.coroutines.launch

/**
 * Your Library's filters.
 *
 * [All] is a real member rather than a null selection, so the chip row always has
 * exactly one thing lit and the heading always has a name to print.
 */
private enum class LibraryFilter(val label: String) {
    All("All"),
    Playlists("Playlists"),
    Songs("Songs"),
    Albums("Albums"),
    Artists("Artists"),
    Downloaded("Downloaded"),
}

/**
 * Your Library.
 *
 * One scroll container, top to bottom. The previous version stacked a
 * fixed-height song block above a weighted list, which meant the saved songs were
 * capped at twenty rows, could not be scrolled on their own, and squeezed the
 * collections underneath them until they disappeared entirely.
 *
 * Every song row carries the same saved control the player uses, so the green
 * check means one thing everywhere: this track is in your library.
 */
@Composable
fun LibraryScreen(
    onCollectionClick: (String) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onOpenProfile: () -> Unit,
    onSearchClick: () -> Unit,
    onCreate: () -> Unit,
    contentPadding: PaddingValues,
) {
    val container = LocalAppContainer.current
    val repository = container.musicRepository
    val store = container.localStore
    val dimens = SpotKofiTheme.dimens
    val layout = rememberResponsiveLayout()
    val scope = rememberCoroutineScope()

    val userName = remember { repository.currentUserName() }
    val visited by repository.library().collectAsStateWithLifecycle(initialValue = emptyList())
    val savedCollections by store.savedCollections.collectAsStateWithLifecycle()
    val playlists by store.playlists.collectAsStateWithLifecycle()
    val savedTracks by store.savedTracks.collectAsStateWithLifecycle()
    val downloads by container.downloadManager.downloads.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf(LibraryFilter.All) }
    var gridLayout by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<Track?>(null) }

    val downloadsByTrack = remember(downloads) { downloads.associateBy { it.track.id } }

    // Completed downloads whose file is still on disk. The existence check is done
    // here, once per change, rather than inside a row where it would be filesystem
    // work on every scroll frame.
    val downloadedTracks = remember(downloads) {
        downloads
            .filter { item ->
                item.status == DownloadManagerStatus.COMPLETED &&
                    item.filePath?.let { path -> java.io.File(path).isFile } == true
            }
            .map { it.track }
    }

    val allCollections = remember(playlists, savedCollections, visited) {
        (playlists + savedCollections + visited).distinctBy { it.id }
    }
    val collections = remember(allCollections, filter) {
        when (filter) {
            LibraryFilter.All -> allCollections
            LibraryFilter.Playlists -> allCollections.filterIsInstance<Playlist>()
            LibraryFilter.Albums -> allCollections.filterIsInstance<Album>()
            LibraryFilter.Artists -> allCollections.filterIsInstance<Artist>()
            LibraryFilter.Songs, LibraryFilter.Downloaded -> emptyList()
        }
    }
    val tracks = when (filter) {
        LibraryFilter.Downloaded -> downloadedTracks
        LibraryFilter.Songs -> savedTracks
        else -> emptyList()
    }

    val showsCollections = collections.isNotEmpty()
    val showsTracks = tracks.isNotEmpty()

    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                item(key = "header") {
                    LibraryHeader(
                        userName = userName,
                        onOpenProfile = onOpenProfile,
                        onSearchClick = onSearchClick,
                        onCreate = onCreate,
                    )
                }

                item(key = "filters") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(
                                horizontal = layout.gutter,
                                vertical = dimens.spaceSm,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
                    ) {
                        LibraryFilter.entries.forEach { option ->
                            SpotKofiChip(
                                label = option.label,
                                selected = option == filter,
                                onClick = { filter = option },
                            )
                        }
                    }
                }

                // Liked songs is its own entry point rather than a chip, because it
                // is the one list every listener has and the chip row is for
                // narrowing what is already on screen.
                if (filter == LibraryFilter.All && savedTracks.isNotEmpty()) {
                    item(key = "liked_songs") {
                        LikedSongsRow(
                            count = savedTracks.size,
                            onClick = { filter = LibraryFilter.Songs },
                        )
                    }
                }

                if (showsCollections) {
                    item(key = "collections_heading") {
                        SectionHeading(
                            title = when (filter) {
                                LibraryFilter.All -> "Playlists, albums and artists"
                                else -> filter.label
                            },
                            count = collections.size,
                            trailing = {
                                IconButton(onClick = { gridLayout = !gridLayout }) {
                                    Icon(
                                        imageVector = if (gridLayout) {
                                            Icons.Filled.ViewList
                                        } else {
                                            Icons.Filled.GridView
                                        },
                                        contentDescription = if (gridLayout) {
                                            "Use list layout"
                                        } else {
                                            "Use grid layout"
                                        },
                                        tint = SpotKofiTheme.colors.textSecondary,
                                    )
                                }
                            },
                        )
                    }

                    if (gridLayout) {
                        // Chunked rows rather than a nested LazyVerticalGrid, which
                        // cannot be measured inside a LazyColumn. Column count comes
                        // from the window width.
                        collections.chunked(layout.gridColumns).forEach { row ->
                            item(key = "grid_" + row.first().id) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = layout.gutter,
                                            vertical = dimens.spaceSm,
                                        ),
                                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
                                ) {
                                    row.forEach { collection ->
                                        LibraryGridItem(
                                            collection = collection,
                                            onClick = { onCollectionClick(collection.id) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(layout.gridColumns - row.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        items(collections, key = { "row_" + it.id }) { collection ->
                            LibraryListItem(
                                collection = collection,
                                onClick = { onCollectionClick(collection.id) },
                            )
                        }
                    }
                }

                if (showsTracks) {
                    item(key = "tracks_heading") {
                        SectionHeading(
                            title = if (filter == LibraryFilter.Downloaded) {
                                "Offline songs"
                            } else {
                                "Saved songs"
                            },
                            count = tracks.size,
                        )
                    }

                    items(tracks, key = { "track_" + it.id }) { track ->
                        val download = downloadsByTrack[track.id]
                        val isSaved = savedTracks.any { it.id == track.id }
                        TrackRow(
                            track = track,
                            onClick = { onTrackClick(track, tracks) },
                            trailingText = track.durationMs
                                .takeIf { it > 0L }
                                ?.asTrackDuration(),
                            downloadStatus = download?.status,
                            downloadProgress = download?.progress ?: 0,
                            // The same control the player shows. A row in Your
                            // Library that did not display its own saved state was
                            // the reason the check looked out of step.
                            trailing = {
                                SavedToggle(
                                    isSaved = isSaved,
                                    onToggle = {
                                        if (isSaved) {
                                            store.removeTrack(track.id)
                                        } else {
                                            store.saveTrack(track)
                                        }
                                    },
                                    size = 26.dp,
                                )
                            },
                            onMoreClick = { selectedTrack = track },
                        )
                    }
                }

                if (!showsCollections && !showsTracks) {
                    item(key = "empty") {
                        LibraryEmptyState(filter = filter)
                    }
                }

                item(key = "footer") { AppFooter() }
            }

            val track = selectedTrack
            TrackActionsSheet(
                visible = track != null,
                track = track,
                isSaved = track?.let { candidate ->
                    savedTracks.any { it.id == candidate.id }
                } == true,
                playlists = playlists,
                downloadStatus = track?.let { downloadsByTrack[it.id]?.status },
                downloadProgress = track?.let { downloadsByTrack[it.id]?.progress } ?: 0,
                onDismiss = { selectedTrack = null },
                onToggleSaved = {
                    track?.let { candidate ->
                        if (savedTracks.any { it.id == candidate.id }) {
                            store.removeTrack(candidate.id)
                        } else {
                            store.saveTrack(candidate)
                        }
                    }
                },
                onPlayNext = { track?.let(container.queueController::playNext) },
                onAddToQueue = { track?.let(container.queueController::addToQueue) },
                onDownload = { track?.let(container.downloadManager::toggleDownload) },
                onAddToPlaylist = { playlist ->
                    track?.let { candidate ->
                        scope.launch { store.addToPlaylist(playlist.id, candidate) }
                    }
                },
            )
        }
    }
}

@Composable
private fun LibraryHeader(
    userName: String,
    onOpenProfile: () -> Unit,
    onSearchClick: () -> Unit,
    onCreate: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(name = userName, onClick = onOpenProfile, size = 38.dp)
        Spacer(Modifier.width(dimens.spaceMd))
        Text(
            text = "Your Library",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search your library",
                tint = colors.textPrimary,
            )
        }
        IconButton(onClick = onCreate) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Create playlist",
                tint = colors.textPrimary,
            )
        }
    }
}

/**
 * A section title with the number of things under it.
 *
 * The count is the point: "Songs" alone does not tell you whether the list below
 * is three rows or three hundred.
 */
@Composable
private fun SectionHeading(
    title: String,
    count: Int,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = dimens.screenGutter,
                end = dimens.spaceSm,
                top = dimens.spaceMd,
                bottom = dimens.spaceXs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
        Spacer(Modifier.width(dimens.spaceSm))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textTertiary,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** The standing entry point to everything the user has saved. */
@Composable
private fun LikedSongsRow(
    count: Int,
    onClick: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(dimens.artworkRow)
                .clip(SpotKofiTheme.shapes.artwork)
                .background(
                    Brush.linearGradient(
                        listOf(colors.accent, colors.accentDim),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(dimens.iconMd),
            )
        }
        Spacer(Modifier.width(dimens.spaceMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Liked songs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Text(
                text = if (count == 1) "1 song" else "$count songs",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun LibraryEmptyState(filter: LibraryFilter) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceHuge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when (filter) {
                LibraryFilter.All -> "Nothing saved yet"
                LibraryFilter.Downloaded -> "No offline songs"
                else -> "No ${filter.label.lowercase()} yet"
            },
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(dimens.spaceXs))
        Text(
            text = when (filter) {
                LibraryFilter.Downloaded ->
                    "Download a song and it will play without a connection."

                LibraryFilter.All ->
                    "Save songs, playlists, albums or artists and they appear here."

                else -> "Save some ${filter.label.lowercase()} and they appear here."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun LibraryListItem(
    collection: MediaCollection,
    onClick: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            id = collection.id,
            url = collection.artworkUrl,
            // Circular for artists, square for everything else: it is the fastest
            // way to tell a performer from a record without reading a label.
            shape = if (collection is Artist) {
                SpotKofiTheme.shapes.avatar
            } else {
                SpotKofiTheme.shapes.artwork
            },
            modifier = Modifier.size(dimens.artworkRow),
        )
        Spacer(Modifier.width(dimens.spaceMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = collection.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = collection.libraryCaption(),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LibraryGridItem(
    collection: MediaCollection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = if (collection is Artist) {
            Alignment.CenterHorizontally
        } else {
            Alignment.Start
        },
    ) {
        Artwork(
            id = collection.id,
            url = collection.artworkUrl,
            shape = if (collection is Artist) {
                SpotKofiTheme.shapes.avatar
            } else {
                SpotKofiTheme.shapes.artwork
            },
            // A square aspect ratio rather than a fixed height, so a tile is the
            // same shape on a phone and on a tablet.
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(dimens.spaceSm))
        Text(
            text = collection.title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = collection.libraryCaption(),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The caption under a library entry.
 *
 * Names the kind first, because Your Library mixes playlists, albums and artists
 * in one list and the subtitle is the only thing that says which is which.
 */
private fun MediaCollection.libraryCaption(): String = when (this) {
    is Playlist -> listOfNotNull(
        "Playlist",
        ownerName.takeIf { it.isNotBlank() },
    ).joinToString(" \u2022 ")

    is Album -> listOfNotNull(
        "Album",
        artistName.takeIf { it.isNotBlank() },
        year?.toString(),
    ).joinToString(" \u2022 ")

    is Artist -> listOfNotNull(
        "Artist",
        genre?.takeIf { it.isNotBlank() },
    ).joinToString(" \u2022 ")
}
