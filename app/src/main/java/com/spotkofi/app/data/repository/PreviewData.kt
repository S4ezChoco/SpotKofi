package com.spotkofi.app.data.repository

import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.BrowseCategory
import com.spotkofi.app.data.model.Conversation
import com.spotkofi.app.data.model.ExploreItem
import com.spotkofi.app.data.model.FriendActivity
import com.spotkofi.app.data.model.HomeSection
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.ReleaseItem
import com.spotkofi.app.data.model.Station
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.TrackDetails

/*
 * Fixtures for @Preview only.
 *
 * `@Preview` renders inside the IDE with no network, so the screens still need
 * something to draw. This is the ONLY remaining hardcoded catalog content in the
 * app: everything the running app shows now comes from the live API.
 *
 * Titles are deliberately generic placeholders rather than plausible song names,
 * so a preview can never be mistaken for real data. No artwork URLs are set, which
 * exercises the gradient fallback that also covers a failed image load at runtime.
 */

private fun sampleTrack(index: Int, explicit: Boolean = false) = Track(
    id = "preview_track_$index",
    title = "Preview Track $index",
    artistName = "Preview Artist",
    albumTitle = "Preview Album",
    durationMs = 30_000L + index * 4_000L,
    isExplicit = explicit,
    albumId = "preview_album_1",
)

private fun sampleAlbum(index: Int) = Album(
    id = "preview_album_$index",
    title = "Preview Album $index",
    artistName = "Preview Artist $index",
    year = 2020 + index,
    genre = "Pop",
    trackCount = 10,
)

private fun sampleArtist(index: Int) = Artist(
    id = "preview_artist_$index",
    name = "Preview Artist $index",
    genre = "Pop",
)

fun previewTrack(): Track = sampleTrack(1)

fun previewTracks(): List<Track> = List(8) { sampleTrack(it + 1, explicit = it % 4 == 0) }

fun previewCollection(): MediaCollection = sampleAlbum(1)

fun previewQuickPicks(): List<MediaCollection> = List(8) { sampleAlbum(it + 1) }

fun previewLibrary(): List<MediaCollection> =
    List(4) { sampleAlbum(it + 1) } + List(3) { sampleArtist(it + 1) }

fun previewHomeSections(): List<HomeSection> = listOf(
    HomeSection.Spotlight(
        id = "preview_spotlight",
        title = "New for you",
        item = sampleAlbum(1),
    ),
    HomeSection.Stations(
        id = "preview_stations",
        title = "Stations for you",
        items = List(4) { index ->
            Station(
                id = "preview_station_$index",
                name = "Preview Artist $index",
                seedArtists = "Preview Track A, Preview Track B",
            )
        },
    ),
    HomeSection.Cards(
        id = "preview_cards",
        title = "Albums",
        items = List(5) { sampleAlbum(it + 1) },
    ),
)

fun previewReleaseSections(): List<HomeSection> = listOf(
    HomeSection.Releases(
        id = "preview_releases",
        title = "Latest releases",
        items = List(4) { index ->
            ReleaseItem(
                id = "preview_release_$index",
                artistName = "Preview Artist $index",
                title = "Preview Album $index",
                releasedLabel = "2026",
                songCount = index + 1,
            )
        },
    ),
)

fun previewTopCategories(): List<BrowseCategory> = listOf(
    BrowseCategory("tc_music", "Music"),
    BrowseCategory("tc_podcasts", "Podcasts"),
    BrowseCategory("tc_opm", "OPM"),
    BrowseCategory("tc_kpop", "K-Pop"),
)

fun previewExploreItems(): List<ExploreItem> = List(4) { index ->
    ExploreItem(
        id = "preview_explore_$index",
        title = "Preview Item $index",
        caption = "Preview Artist",
    )
}

fun previewTrackDetails(): TrackDetails = TrackDetails(
    contextLabel = "Preview Album",
    albumTracks = previewTracks().take(4),
    moreByArtist = previewTracks().drop(4),
    artistAlbums = List(3) { sampleAlbum(it + 1) },
)

/*
 * Social features have no data source until accounts exist, so these are empty to
 * match what the running app shows. That way the previews exercise the same empty
 * states the user actually sees.
 */

fun previewFriends(): List<FriendActivity> = emptyList()

fun previewConversations(): List<Conversation> = emptyList()


// Mock search functionality
fun mockSearch(query: String): com.spotkofi.app.data.model.SearchResults {
    val tracks = previewTracks().filter { 
        it.title.contains(query, ignoreCase = true) || 
        it.artistName.contains(query, ignoreCase = true)
    }.take(10)
    
    val albums = listOf(sampleAlbum(1), sampleAlbum(2), sampleAlbum(3)).filter {
        it.title.contains(query, ignoreCase = true) ||
        it.artistName.contains(query, ignoreCase = true)
    }.take(5)
    
    return com.spotkofi.app.data.model.SearchResults(
        tracks = tracks,
        collections = albums
    )
}

// Mock search suggestions
fun mockSearchSuggestions(query: String): List<String> {
    val suggestions = mutableListOf<String>()
    
    if (query.isNotEmpty()) {
        suggestions.add("$query music")
        suggestions.add("$query hits")
        suggestions.add("$query 2024")
        suggestions.add("best of $query")
    }
    
    return suggestions
}