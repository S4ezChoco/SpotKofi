package com.spotkofi.app.data.repository

import com.spotkofi.app.data.model.BrowseCategory
import com.spotkofi.app.data.model.Conversation
import com.spotkofi.app.data.model.ExploreItem
import com.spotkofi.app.data.model.FriendActivity
import com.spotkofi.app.data.model.HomeSection
import com.spotkofi.app.data.model.HomeTab
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.SearchResults
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.TrackDetails
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * In-memory [MusicRepository] backed by [MockData].
 *
 * Small artificial delays are on purpose: they force every screen to handle a
 * loading state now, so wiring a real network call in a later phase does not
 * surface a class of "we never designed the empty/loading case" bugs.
 */
class FakeMusicRepository : MusicRepository {

    // Placeholder handle, not a real display name. Phase 3 replaces this with the
    // signed-in Supabase profile.
    override fun currentUserName(): String = "kofi_listener"

    override fun quickPicks(): Flow<List<MediaCollection>> = flow {
        delay(LOAD_DELAY_MS)
        emit(
            listOf(
                MockData.playlists[1],
                MockData.playlists[0],
                MockData.playlists[9],
                MockData.playlists[3],
                MockData.playlists[6],
                MockData.playlists[5],
                MockData.playlists[7],
                MockData.artists[0],
            ),
        )
    }

    override fun homeSections(tab: HomeTab): Flow<List<HomeSection>> = flow {
        delay(LOAD_DELAY_MS)
        emit(
            when (tab) {
                HomeTab.All -> listOf(
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
                        items = listOf(
                            MockData.playlists[1],
                            MockData.playlists[2],
                            MockData.playlists[3],
                            MockData.playlists[4],
                            MockData.playlists[10],
                        ),
                    ),
                    HomeSection.Cards(
                        id = "hs_artists",
                        title = "Your favourite artists",
                        items = MockData.artists,
                    ),
                )

                HomeTab.Music -> listOf(
                    HomeSection.Stations(
                        id = "hs_stations",
                        title = "Recommended Stations",
                        items = MockData.stations,
                    ),
                    HomeSection.Cards(
                        id = "hs_yours",
                        title = "Your playlists",
                        items = MockData.playlists.take(6),
                    ),
                    HomeSection.Cards(
                        id = "hs_opm",
                        title = "OPM picks",
                        items = listOf(
                            MockData.playlists[5],
                            MockData.playlists[9],
                            MockData.albums[2],
                            MockData.albums[0],
                            MockData.playlists[11],
                        ),
                    ),
                    HomeSection.Cards(
                        id = "hs_newrelease",
                        title = "New releases for you",
                        items = listOf(
                            MockData.albums[4],
                            MockData.albums[7],
                            MockData.albums[2],
                            MockData.albums[5],
                        ),
                    ),
                )

                // The Following tab is a feed, not a set of shelves.
                HomeTab.Following -> listOf(
                    HomeSection.Releases(
                        id = "hs_latest",
                        title = "Latest releases",
                        items = MockData.latestReleases,
                    ),
                )

                HomeTab.Podcasts -> listOf(
                    HomeSection.Cards(
                        id = "hs_pod_resume",
                        title = "Continue listening",
                        items = listOf(MockData.playlists[10], MockData.playlists[6]),
                    ),
                    HomeSection.Cards(
                        id = "hs_pod_top",
                        title = "Top podcasts",
                        items = MockData.playlists.drop(7).take(4),
                    ),
                )
            },
        )
    }

    override fun library(): Flow<List<MediaCollection>> = flow {
        delay(LOAD_DELAY_MS)
        emit(
            buildList {
                add(MockData.playlists[0])
                add(MockData.playlists[1])
                addAll(MockData.artists.take(3))
                addAll(MockData.playlists.drop(6))
                addAll(MockData.albums.take(3))
            },
        )
    }

    override fun browseCategories(): List<BrowseCategory> = MockData.topCategories

    override fun exploreVideos(): List<ExploreItem> = MockData.exploreVideos

    override fun exploreEpisodes(): List<ExploreItem> = MockData.exploreEpisodes

    override fun friendActivity(): List<FriendActivity> = MockData.friendActivity

    override fun conversations(): List<Conversation> = MockData.conversations

    override suspend fun search(query: String): SearchResults {
        delay(SEARCH_DELAY_MS)
        val q = query.trim()
        if (q.isEmpty()) return SearchResults()

        val tracks = MockData.tracks.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artistName.contains(q, ignoreCase = true) ||
                it.albumTitle.contains(q, ignoreCase = true)
        }
        val collections = (MockData.playlists + MockData.albums + MockData.artists)
            .filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.subtitle.contains(q, ignoreCase = true)
            }

        return SearchResults(tracks = tracks, collections = collections)
    }

    override suspend fun collection(id: String): MediaCollection? {
        delay(LOAD_DELAY_MS)
        return MockData.collection(id)
    }

    override suspend fun tracks(collectionId: String): List<Track> {
        delay(LOAD_DELAY_MS)
        return MockData.tracksFor(collectionId)
    }

    override suspend fun trackDetails(track: Track): TrackDetails {
        delay(LOAD_DELAY_MS)
        return MockData.detailsFor(track)
    }

    private companion object {
        /**
         * Zero on purpose.
         *
         * These were originally 350ms to force every screen to handle a loading
         * state. That job is done, and the cost was real: opening a playlist ran
         * `collection()` then `tracks()` back to back, so a spinner sat on screen
         * for ~700ms on data that is already in memory. Reading mock data should
         * be instant; genuine latency comes back on its own with a real API.
         */
        const val LOAD_DELAY_MS = 0L
        const val SEARCH_DELAY_MS = 0L
    }
}
