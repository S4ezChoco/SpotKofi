package com.spotkofi.app.feature.library

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.spotkofi.app.ui.components.artworkSeedColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.data.local.AppSettings
import com.spotkofi.app.data.local.LocalMusicStore
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
import com.spotkofi.app.ui.components.SavedToggle
import com.spotkofi.app.ui.components.SpotKofiChip
import com.spotkofi.app.ui.components.SpotKofiScreenHeader
import com.spotkofi.app.ui.components.TrackActionsSheet
import com.spotkofi.app.ui.components.TrackRow
import com.spotkofi.app.ui.layout.rememberResponsiveLayout
import com.spotkofi.app.ui.theme.Motion
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
    MostPlayed("Most Played"),
    Followed("Followed"),
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
    onAnalyticsClick: () -> Unit,
    onCreate: () -> Unit,
    contentPadding: PaddingValues,
) {
    val container = LocalAppContainer.current
    val repository = container.musicRepository
    val store = container.localStore
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle()
    val dimens = SpotKofiTheme.dimens
    val colors = SpotKofiTheme.colors
    val layout = rememberResponsiveLayout()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val headerLifted by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 4
        }
    }
    val headerSurface by animateColorAsState(
        targetValue = if (headerLifted) colors.base else colors.base.copy(alpha = 0.96f),
        animationSpec = Motion.fast(),
        label = "libraryStickyHeader",
    )

    val savedCollections by store.savedCollections.collectAsStateWithLifecycle()
    val playlists by store.playlists.collectAsStateWithLifecycle()
    val savedTracks by store.savedTracks.collectAsStateWithLifecycle()
    val history by store.history.collectAsStateWithLifecycle()
    val historyStats by store.historyStats.collectAsStateWithLifecycle()
    val downloads by container.downloadManager.downloads.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf(LibraryFilter.All) }
    var gridLayout by remember { mutableStateOf(true) }
    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    var showLibrarySearch by remember { mutableStateOf(false) }
    var libraryQuery by remember { mutableStateOf("") }

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
    val searchableTracks = remember(savedTracks, history, downloadedTracks) {
        (savedTracks + history + downloadedTracks).distinctBy { it.id }
    }

    val allCollections = remember(playlists, savedCollections) {
        // Visiting a collection is navigation history, not a library save. It must
        // not make a remote album or artist appear under "Recently Added".
        (playlists + savedCollections).distinctBy { it.id }
    }
    val followedArtists = remember(savedCollections) {
        savedCollections.filterIsInstance<Artist>()
    }
    val collections = remember(allCollections, followedArtists, filter) {
        when (filter) {
            LibraryFilter.All -> allCollections
            LibraryFilter.Playlists -> allCollections.filterIsInstance<Playlist>()
            LibraryFilter.Albums -> allCollections.filterIsInstance<Album>()
            LibraryFilter.Artists -> allCollections.filterIsInstance<Artist>()
            LibraryFilter.Followed -> followedArtists
            LibraryFilter.Songs, LibraryFilter.MostPlayed, LibraryFilter.Downloaded -> emptyList()
        }
    }
    val tracks = when (filter) {
        LibraryFilter.Downloaded -> downloadedTracks
        LibraryFilter.All, LibraryFilter.Songs -> savedTracks
        LibraryFilter.MostPlayed -> historyStats
            .sortedWith(compareByDescending<com.spotkofi.app.data.local.LocalMusicStore.HistoryEntry> { it.playCount }
                .thenByDescending { it.playedAt })
            .map { it.track }
        else -> emptyList()
    }

    val showsCollections = collections.isNotEmpty()
    val showsTracks = tracks.isNotEmpty()

    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpotKofiTheme.colors.base),
        ) {
            if (!showLibrarySearch) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Keep the sidebar/profile action, title, and filters pinned
                    // above the library list. The background lift is animated so
                    // scrolling into content never creates a hard visual seam.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerSurface)
                            .padding(top = contentPadding.calculateTopPadding()),
                    ) {
                        LibraryHeader(
                            onOpenProfile = onOpenProfile,
                            onSearchClick = { showLibrarySearch = true },
                            onAnalyticsClick = onAnalyticsClick,
                            onCreate = onCreate,
                        )

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

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            bottom = contentPadding.calculateBottomPadding(),
                        ),
                    ) {

                if (showsCollections) {
                    item(key = "collections_heading") {
                        SectionHeading(
                            title = when (filter) {
                                LibraryFilter.All -> "Recently Added"
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
                }
            } else {
                LibrarySearchContent(
                    query = libraryQuery,
                    onQueryChange = { libraryQuery = it },
                    onClose = {
                        showLibrarySearch = false
                        libraryQuery = ""
                    },
                    collections = allCollections,
                    tracks = searchableTracks,
                    onCollectionClick = onCollectionClick,
                    onTrackClick = onTrackClick,
                    onTrackMore = { selectedTrack = it },
                    contentPadding = contentPadding,
                )
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
private fun LibrarySearchContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    collections: List<MediaCollection>,
    tracks: List<Track>,
    onCollectionClick: (String) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onTrackMore: (Track) -> Unit,
    contentPadding: PaddingValues,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val term = query.trim().lowercase()
    val matchingCollections = remember(term, collections) {
        if (term.length < 2) emptyList() else collections.filter { collection ->
            collection.title.lowercase().contains(term) ||
                collection.subtitle.lowercase().contains(term)
        }
    }
    val matchingTracks = remember(term, tracks) {
        if (term.length < 2) emptyList() else tracks.filter { track ->
            track.title.lowercase().contains(term) ||
                track.artistName.lowercase().contains(term) ||
                track.albumTitle.lowercase().contains(term)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Close library search",
                    tint = colors.textPrimary,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search your library") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear library search",
                            )
                        }
                    }
                },
            )
        }

        if (term.length < 2) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Search saved songs, playlists, albums, and artists",
                    color = colors.textSecondary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = dimens.spaceHuge),
            ) {
                if (matchingCollections.isEmpty() && matchingTracks.isEmpty()) {
                    item(key = "library_search_empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(dimens.spaceHuge),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Nothing in your library matches \"$query\"", color = colors.textSecondary)
                        }
                    }
                }
                if (matchingCollections.isNotEmpty()) {
                    item(key = "library_search_collections_heading") {
                        Text(
                            text = "Collections",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(
                                horizontal = dimens.screenGutter,
                                vertical = dimens.spaceMd,
                            ),
                        )
                    }
                    items(matchingCollections, key = { "library_search_collection_${it.id}" }) { collection ->
                        LibraryListItem(
                            collection = collection,
                            onClick = { onCollectionClick(collection.id) },
                        )
                    }
                }
                if (matchingTracks.isNotEmpty()) {
                    item(key = "library_search_tracks_heading") {
                        Text(
                            text = "Songs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(
                                horizontal = dimens.screenGutter,
                                vertical = dimens.spaceMd,
                            ),
                        )
                    }
                    items(matchingTracks, key = { "library_search_track_${it.id}" }) { track ->
                        TrackRow(
                            track = track,
                            onClick = { onTrackClick(track, matchingTracks) },
                            onMoreClick = { onTrackMore(track) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    onOpenProfile: () -> Unit,
    onSearchClick: () -> Unit,
    onAnalyticsClick: () -> Unit,
    onCreate: () -> Unit,
) {
    val colors = SpotKofiTheme.colors

    SpotKofiScreenHeader(
        title = "Your Library",
        onLogoClick = onOpenProfile,
        onMenuClick = onOpenProfile,
    ) {
        IconButton(onClick = onAnalyticsClick) {
            Icon(
                imageVector = Icons.Filled.ShowChart,
                contentDescription = "Open listening stats",
                tint = colors.textPrimary,
            )
        }
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

private data class LibraryShortcut(
    val title: String,
    val count: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun LibraryShortcuts(
    favoriteCount: Int,
    followedCount: Int,
    mostPlayedCount: Int,
    downloadedCount: Int,
    onFavorite: () -> Unit,
    onFollowed: () -> Unit,
    onMostPlayed: () -> Unit,
    onDownloaded: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val shortcuts = listOf(
        LibraryShortcut("Favorite", favoriteCount, Icons.Filled.Favorite, onFavorite),
        LibraryShortcut("Followed", followedCount, Icons.Filled.Person, onFollowed),
        LibraryShortcut("Most Played", mostPlayedCount, Icons.Filled.History, onMostPlayed),
        LibraryShortcut("Downloaded", downloadedCount, Icons.Filled.Download, onDownloaded),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceMd),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        shortcuts.forEach { shortcut ->
            LibraryShortcutPill(
                shortcut = shortcut,
                colors = colors,
                dimens = dimens,
            )
        }
    }
}

@Composable
private fun LibraryShortcutPill(
    shortcut: LibraryShortcut,
    colors: com.spotkofi.app.ui.theme.SpotKofiColors,
    dimens: com.spotkofi.app.ui.theme.SpotKofiDimens,
) {
    Row(
        modifier = Modifier
            .height(dimens.minTouchTarget)
            .clip(RoundedCornerShape(50))
            .background(colors.card)
            .clickable(onClick = shortcut.onClick)
            .padding(horizontal = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        Icon(
            imageVector = shortcut.icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(dimens.iconSm),
        )
        Text(
            text = shortcut.title,
            style = MaterialTheme.typography.labelLarge,
            color = colors.textPrimary,
            maxLines = 1,
        )
        Text(
            text = shortcut.count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textTertiary,
        )
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
                LibraryFilter.All -> "Your library is empty"
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
