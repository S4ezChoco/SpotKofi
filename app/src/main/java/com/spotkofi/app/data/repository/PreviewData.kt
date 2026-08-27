package com.spotkofi.app.data.repository

import com.spotkofi.app.data.model.BrowseCategory
import com.spotkofi.app.data.model.Conversation
import com.spotkofi.app.data.model.ExploreItem
import com.spotkofi.app.data.model.FriendActivity
import com.spotkofi.app.data.model.HomeSection
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.Playlist
import com.spotkofi.app.data.model.ReleaseItem
import com.spotkofi.app.data.model.Station
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.TrackDetails

/*
 * Sample data for `@Preview` only.
 *
 * These wrappers exist so UI code never reaches into [MockData] directly. That
 * keeps one rule true: nothing under `ui/` or `feature/` knows the fake catalog
 * exists, so deleting it in Phase 5 cannot break a screen, only these previews.
 */

fun previewQuickPicks(): List<MediaCollection> = listOf(
    MockData.playlists[1],
    MockData.playlists[0],
    MockData.playlists[9],
    MockData.playlists[3],
    MockData.playlists[6],
    MockData.playlists[5],
    MockData.playlists[7],
    MockData.artists[0],
)

fun previewHomeSections(): List<HomeSection> = listOf(
    HomeSection.Spotlight(
        id = "hs_presave",
        title = "Pre-save upcoming releases",
        item = MockData.albums[4],
    ),
    HomeSection.Stations(
        id = "hs_stations",
        title = "Recommended Stations",
        items = MockData.stations,
    ),
    HomeSection.Cards(
        id = "hs_madefor",
        title = "Made for you",
        items = MockData.playlists.drop(1).take(4),
    ),
)

fun previewReleaseSections(): List<HomeSection> = listOf(
    HomeSection.Releases(
        id = "hs_latest",
        title = "Latest releases",
        items = MockData.latestReleases,
    ),
)

fun previewStations(): List<Station> = MockData.stations

fun previewReleases(): List<ReleaseItem> = MockData.latestReleases

fun previewPlaylist(): Playlist = MockData.playlists[5]

fun previewLibrary(): List<MediaCollection> = buildList {
    add(MockData.playlists[0])
    add(MockData.playlists[1])
    addAll(MockData.artists.take(2))
    addAll(MockData.playlists.drop(6).take(3))
}

fun previewTracks(): List<Track> = MockData.tracksFor(MockData.playlists[5].id)

fun previewTrack(): Track = MockData.tracks[4]

fun previewTopCategories(): List<BrowseCategory> = MockData.topCategories

fun previewGenres(): List<BrowseCategory> = MockData.browseCategories

fun previewExploreVideos(): List<ExploreItem> = MockData.exploreVideos

fun previewExploreEpisodes(): List<ExploreItem> = MockData.exploreEpisodes

fun previewFriends(): List<FriendActivity> = MockData.friendActivity

fun previewConversations(): List<Conversation> = MockData.conversations

fun previewTrackDetails(): TrackDetails = MockData.detailsFor(previewTrack())
