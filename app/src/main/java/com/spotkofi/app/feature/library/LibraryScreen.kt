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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.ui.components.Artwork
import com.spotkofi.app.ui.components.ProfileAvatar

private enum class LibraryFilter(val label: String) {
    Playlists("Playlists"),
    Albums("Albums"),
    Artists("Artists"),
    Downloaded("Downloaded"),
}

@Composable
fun LibraryScreen(
    onCollectionClick: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onSearchClick: () -> Unit,
    onCreate: () -> Unit,
    contentPadding: PaddingValues,
) {
    val repository = LocalAppContainer.current.musicRepository
    val userName = remember { repository.currentUserName() }
    val visited by repository.library().collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedFilter by remember { mutableStateOf<LibraryFilter?>(null) }
    var gridLayout by remember { mutableStateOf(false) }

    val visibleItems = remember(visited, selectedFilter) {
        when (selectedFilter) {
            null -> visited
            LibraryFilter.Playlists -> visited.filter { it is com.spotkofi.app.data.model.Playlist }
            LibraryFilter.Albums -> visited.filterIsInstance<Album>()
            LibraryFilter.Artists -> visited.filterIsInstance<Artist>()
            // Downloads are not persisted yet; keep the filter honest instead of
            // displaying the same recent items under a false label.
            LibraryFilter.Downloaded -> emptyList()
        }
    }

    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
        LibraryHeader(
            userName = userName,
            onOpenProfile = onOpenProfile,
            onSearchClick = onSearchClick,
            onCreate = onCreate,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryFilter.entries.forEach { filter ->
                val selected = selectedFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = { selectedFilter = if (selected) null else filter },
                    label = { Text(filter.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Recents",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { gridLayout = !gridLayout }) {
                Icon(
                    imageVector = if (gridLayout) Icons.Filled.ViewList else Icons.Filled.GridView,
                    contentDescription = if (gridLayout) "Use list layout" else "Use grid layout",
                )
            }
        }

        if (visibleItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (selectedFilter == null) {
                        "Open an album or artist to add it to your library."
                    } else {
                        "Nothing in ${selectedFilter!!.label.lowercase()} yet."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (gridLayout) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(visibleItems) { collection ->
                    LibraryGridItem(collection, onCollectionClick)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleItems, key = { it.id }) { collection ->
                    LibraryListItem(collection, onCollectionClick)
                }
            }
        }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(
            name = userName,
            onClick = onOpenProfile,
            size = 42.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Your Library",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearchClick) {
            Icon(Icons.Filled.Search, contentDescription = "Search")
        }
        IconButton(onClick = onCreate) {
            Icon(Icons.Filled.Add, contentDescription = "Create playlist")
        }
    }
}

@Composable
private fun LibraryListItem(
    collection: MediaCollection,
    onCollectionClick: (String) -> Unit,
) {
    val shape = if (collection is Artist) CircleShape else RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCollectionClick(collection.id) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            id = collection.id,
            url = collection.artworkUrl,
            shape = shape,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = collection.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = collection.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LibraryGridItem(
    collection: MediaCollection,
    onCollectionClick: (String) -> Unit,
) {
    val shape = if (collection is Artist) CircleShape else RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCollectionClick(collection.id) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            id = collection.id,
            url = collection.artworkUrl,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = collection.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = collection.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
