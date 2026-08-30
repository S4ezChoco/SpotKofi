package com.spotkofi.app.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.Playlist
import com.spotkofi.app.data.model.SearchResults
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.asTrackDuration
import com.spotkofi.app.ui.components.AppFooter
import com.spotkofi.app.ui.components.Artwork
import com.spotkofi.app.ui.components.ProfileAvatar
import com.spotkofi.app.ui.components.SpotKofiChip
import com.spotkofi.app.ui.components.TrackActionsSheet
import com.spotkofi.app.ui.components.TrackRow
import com.spotkofi.app.ui.theme.SpotKofiTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debounce between the last keystroke and the request.
 *
 * Long enough that typing a word is one search rather than five, short enough
 * that results feel like they arrive while typing.
 */
private const val SearchDebounceMs = 280L

/**
 * Shortest query that is searched.
 *
 * A single letter matches almost everything, so the provider answered with a wide
 * unrelated list - including rows with nothing usable in them - before the user
 * had finished the first word. Below this the landing page stays put.
 */
private const val MinQueryLength = 2

/** The standard result filters, in the order they are shown. */
private enum class SearchFilter(val label: String) {
    All("All"),
    Artists("Artists"),
    Songs("Songs"),
    Albums("Albums"),
    Playlists("Playlists"),
}

private data class SearchCategory(
    val title: String,
    val subtitle: String,
    val color: Color,
)

private val searchCategories = listOf(
    SearchCategory("Music", "Songs, albums and artists", Color(0xFFE41483)),
    SearchCategory("Podcasts", "Episodes and shows", Color(0xFF087A61)),
    SearchCategory("Live Events", "Find music near you", Color(0xFF8B00E8)),
    SearchCategory("K-Pop ON!", "Discover new releases", Color(0xFF354FC4)),
)

/**
 * Search.
 *
 * Results arrive while typing rather than on submit, and the previous results
 * stay on screen while the next request runs: replacing the list with a spinner
 * on every keystroke made the screen flash and hid the very rows the user was
 * reading.
 *
 * Artists lead the results because a name typed into a music app is usually a
 * performer, and the songs that follow are the ones that name produced.
 */
@Composable
fun SearchScreen(
    onCollectionClick: (String) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onOpenProfile: () -> Unit,
    onExploreClick: () -> Unit,
    contentPadding: PaddingValues,
) {
    val container = LocalAppContainer.current
    val repository = container.musicRepository
    val userName = remember { repository.currentUserName() }
    val dimens = SpotKofiTheme.dimens

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(SearchFilter.All) }

    val playlists by container.localStore.playlists.collectAsStateWithLifecycle()

    // Keyed on the query, so a new keystroke cancels the in-flight request for the
    // old one. That replaces the manual job plus generation counter this screen
    // used to carry: the effect itself is the guard, and a cancelled search can no
    // longer publish over a newer one.
    LaunchedEffect(query) {
        val term = query.trim()
        if (term.length < MinQueryLength) {
            results = null
            error = null
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(SearchDebounceMs)
        try {
            results = repository.search(term)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            error = exception.message ?: "Search failed"
        }
        // Deliberately not in a `finally`: on cancellation the newer search owns
        // this flag and must not be told the screen is idle.
        isSearching = false
    }

    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            SearchHeader(userName = userName, onOpenProfile = onOpenProfile)

            SearchField(
                query = query,
                onQueryChange = { query = it },
                onClear = { query = "" },
            )

            // A thin bar rather than a full-screen spinner: the results below stay
            // readable while the next request is in flight.
            AnimatedVisibility(visible = isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.screenGutter)
                        .height(2.dp),
                    color = SpotKofiTheme.colors.accent,
                    trackColor = Color.Transparent,
                )
            }

            val current = results
            when {
                query.trim().length < MinQueryLength ->
                    SearchLanding(
                        onCategoryClick = { query = it.title },
                        onExploreClick = onExploreClick,
                    )

                error != null && current == null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(dimens.spaceXl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }

                current == null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = SpotKofiTheme.colors.accent)
                }

                else -> SearchResultsContent(
                    searchResults = current,
                    // The user's own playlists that match, ahead of the provider's,
                    // because a library hit is the one the user already knows.
                    localPlaylists = playlists.filter {
                        it.title.contains(query.trim(), ignoreCase = true)
                    },
                    filter = filter,
                    onFilterChange = { filter = it },
                    onCollectionClick = onCollectionClick,
                    onTrackClick = onTrackClick,
                )
            }
        }
    }
}

@Composable
private fun SearchHeader(
    userName: String,
    onOpenProfile: () -> Unit,
) {
    val dimens = SpotKofiTheme.dimens

    // The camera button that used to sit here is gone: it had an empty click
    // handler, and a control that looks live but does nothing is worse than no
    // control at all.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(name = userName, onClick = onOpenProfile, size = 38.dp)
        Spacer(Modifier.width(dimens.spaceMd))
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * The query field.
 *
 * Dark and themed rather than the white slab it used to be: a bright white box
 * directly under a dark header read as an unstyled system widget dropped into the
 * app.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceXs),
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search,
        ),
        // Results already arrive while typing, so the IME action only needs to
        // close the keyboard; there is nothing left to submit.
        keyboardActions = KeyboardActions(onSearch = { }),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(SpotKofiTheme.shapes.chip)
                    .background(colors.chip)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), SpotKofiTheme.shapes.chip)
                    .padding(horizontal = dimens.spaceLg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(dimens.iconMd),
                )
                Spacer(Modifier.width(dimens.spaceMd))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Songs, artists or albums",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(dimens.iconLg)) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear search",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(dimens.iconMd),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun ColumnScope.SearchResultsContent(
    searchResults: SearchResults,
    localPlaylists: List<Playlist>,
    filter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    onCollectionClick: (String) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
) {
    val container = LocalAppContainer.current
    val store = container.localStore
    val dimens = SpotKofiTheme.dimens
    val savedTracks by store.savedTracks.collectAsStateWithLifecycle()
    val downloads by container.downloadManager.downloads.collectAsStateWithLifecycle()
    val allPlaylists by store.playlists.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var selectedTrack by remember { mutableStateOf<Track?>(null) }

    val downloadsByTrack = remember(downloads) { downloads.associateBy { it.track.id } }

    // The catalog returns artists and albums merged into one list, so the concrete
    // types are recovered here. They survive because MediaCollection is sealed.
    val artists = remember(searchResults) {
        searchResults.collections.filterIsInstance<Artist>()
    }
    val albums = remember(searchResults) {
        searchResults.collections.filterIsInstance<Album>()
    }
    val songs = searchResults.tracks

    // Local playlists first, then the provider's, with the user's own kept even if
    // a remote playlist happens to share its title.
    val playlists = remember(localPlaylists, searchResults) {
        (localPlaylists + searchResults.collections.filterIsInstance<Playlist>())
            .distinctBy { it.id }
    }

    val showArtists = filter == SearchFilter.All || filter == SearchFilter.Artists
    val showSongs = filter == SearchFilter.All || filter == SearchFilter.Songs
    val showAlbums = filter == SearchFilter.All || filter == SearchFilter.Albums
    val showPlaylists = filter == SearchFilter.All || filter == SearchFilter.Playlists

    // In All, artists are capped so the songs the user is probably after are not
    // pushed a full screen down.
    val visibleArtists = if (filter == SearchFilter.All) artists.take(3) else artists

    Column(modifier = Modifier.weight(1f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceSm),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
        ) {
            SearchFilter.entries.forEach { option ->
                SpotKofiChip(
                    label = option.label,
                    selected = option == filter,
                    onClick = { onFilterChange(option) },
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = dimens.spaceXl),
            ) {
                if (showArtists && visibleArtists.isNotEmpty()) {
                    item(key = "artists_header") { ResultSectionHeader("Artists") }
                    items(visibleArtists, key = { "artist_" + it.id }) { artist ->
                        CollectionResultRow(
                            item = artist,
                            typeLabel = "Artist",
                            onClick = { onCollectionClick(artist.id) },
                        )
                    }
                }

                if (showSongs && songs.isNotEmpty()) {
                    item(key = "songs_header") { ResultSectionHeader("Songs") }
                    items(songs, key = { "song_" + it.id }) { track ->
                        val download = downloadsByTrack[track.id]
                        TrackRow(
                            track = track,
                            onClick = { onTrackClick(track, songs) },
                            trailingText = track.durationMs
                                .takeIf { it > 0L }
                                ?.asTrackDuration(),
                            downloadStatus = download?.status,
                            downloadProgress = download?.progress ?: 0,
                            onMoreClick = { selectedTrack = track },
                        )
                    }
                }

                if (showAlbums && albums.isNotEmpty()) {
                    item(key = "albums_header") { ResultSectionHeader("Albums") }
                    items(albums, key = { "album_" + it.id }) { album ->
                        CollectionResultRow(
                            item = album,
                            typeLabel = listOfNotNull(
                                "Album",
                                album.artistName.takeIf { it.isNotBlank() },
                                album.year?.toString(),
                            ).joinToString(" · "),
                            onClick = { onCollectionClick(album.id) },
                        )
                    }
                }

                if (showPlaylists && playlists.isNotEmpty()) {
                    item(key = "playlists_header") { ResultSectionHeader("Playlists") }
                    items(playlists, key = { "playlist_" + it.id }) { playlist ->
                        CollectionResultRow(
                            item = playlist,
                            typeLabel = playlist.ownerName
                                .takeIf { it.isNotBlank() }
                                ?.let { "Playlist · $it" }
                                ?: "Playlist",
                            onClick = { onCollectionClick(playlist.id) },
                        )
                    }
                }

                val nothingVisible = (!showArtists || visibleArtists.isEmpty()) &&
                    (!showSongs || songs.isEmpty()) &&
                    (!showAlbums || albums.isEmpty()) &&
                    (!showPlaylists || playlists.isEmpty())
                if (nothingVisible) {
                    item(key = "empty") {
                        Text(
                            text = if (filter == SearchFilter.All) {
                                "No results found"
                            } else {
                                "No ${filter.label.lowercase()} for this search"
                            },
                            color = SpotKofiTheme.colors.textSecondary,
                            modifier = Modifier.padding(dimens.spaceXl),
                        )
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
                playlists = allPlaylists,
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
private fun ResultSectionHeader(title: String) {
    val dimens = SpotKofiTheme.dimens
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = SpotKofiTheme.colors.textPrimary,
        modifier = Modifier.padding(
            start = dimens.screenGutter,
            end = dimens.screenGutter,
            top = dimens.spaceMd,
            bottom = dimens.spaceXs,
        ),
    )
}

/**
 * One artist, album or playlist result.
 *
 * A vertical row rather than the shelf card the results used to reuse: a card is
 * sized for a horizontally scrolling row, so in a vertical list it left most of
 * the line empty and made albums look like a broken grid. Artists get a circular
 * crop, which is the convention that tells them apart from albums at a glance.
 */
@Composable
private fun CollectionResultRow(
    item: MediaCollection,
    typeLabel: String,
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
            Text(
                text = typeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ColumnScope.SearchLanding(
    onCategoryClick: (SearchCategory) -> Unit,
    onExploreClick: () -> Unit,
) {
    val dimens = SpotKofiTheme.dimens
    val browseCategories = LocalAppContainer.current.musicRepository.browseCategories()

    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(
            start = dimens.screenGutter,
            end = dimens.screenGutter,
            top = dimens.spaceLg,
            bottom = dimens.spaceXl,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceLg),
    ) {
        // Explore leads the page.
        //
        // The categories below only prefill the search box, which is a shortcut for
        // typing. Explore is the one entry here that opens real provider content -
        // charts, moods, new releases - so burying it under four coloured tiles
        // would hide the only thing on this screen that is not a search shortcut.
        item { ExploreEntryCard(onClick = onExploreClick) }
        item {
            Text(
                text = "Browse all",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceMd)) {
                searchCategories.chunked(2).forEach { rowCategories ->
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd)) {
                        rowCategories.forEach { category ->
                            SearchCategoryCard(
                                category = category,
                                onClick = { onCategoryClick(category) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowCategories.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Text(
                text = "Explore music",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = dimens.spaceXs),
            )
        }
        items(browseCategories) { category ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SpotKofiTheme.shapes.tile)
                    .background(SpotKofiTheme.colors.elevated)
                    .clickable {
                        onCategoryClick(SearchCategory(category.name, "", Color.Gray))
                    }
                    .padding(dimens.spaceLg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = SpotKofiTheme.colors.textPrimary,
                )
            }
        }

        item { AppFooter() }
    }
}

/** Entry point to charts, moods and moments, genres, new releases and trending. */
@Composable
private fun ExploreEntryCard(onClick: () -> Unit) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SpotKofiTheme.shapes.tile)
            .background(colors.card)
            .clickable(onClick = onClick)
            .padding(dimens.spaceLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        Icon(
            imageVector = Icons.Filled.Explore,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(dimens.iconLg),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Explore",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Text(
                text = "Charts, moods and moments, genres, new releases",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(dimens.iconMd),
        )
    }
}

@Composable
private fun SearchCategoryCard(
    category: SearchCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = SpotKofiTheme.dimens

    Box(
        modifier = modifier
            .height(92.dp)
            .clip(SpotKofiTheme.shapes.card)
            .background(category.color)
            .clickable(onClick = onClick)
            .padding(dimens.spaceMd),
    ) {
        Text(
            text = category.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = category.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.align(Alignment.BottomStart),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
