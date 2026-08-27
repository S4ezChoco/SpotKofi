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
import kotlinx.coroutines.flow.Flow

/**
 * The single seam between the UI and wherever music data comes from.
 *
 * This is the whole point of building the UI first: every screen depends on this
 * interface and never on a concrete implementation. Phase 1 binds it to
 * [FakeMusicRepository]. When a real catalog API is wired up, only the binding
 * in `AppContainer` changes; no screen, component, or ViewModel is touched.
 */
interface MusicRepository {

    /** Display name of the signed-in user. Replaced by the Supabase profile in Phase 3. */
    fun currentUserName(): String

    /** The 2-column grid pinned to the top of Home. */
    fun quickPicks(): Flow<List<MediaCollection>>

    /**
     * Home content for [tab].
     *
     * Content differs per tab, not just filtered: the Following tab is a release
     * feed rather than a set of card shelves.
     */
    fun homeSections(tab: HomeTab): Flow<List<HomeSection>>

    /** Everything the user has saved, for Your Library. */
    fun library(): Flow<List<MediaCollection>>

    /** The four large colour tiles at the top of Search. */
    fun browseCategories(): List<BrowseCategory>

    fun exploreVideos(): List<ExploreItem>

    fun exploreEpisodes(): List<ExploreItem>

    /** Friend avatar strip in the profile drawer. */
    fun friendActivity(): List<FriendActivity>

    /** DM threads in the profile drawer. Backed by Supabase Realtime in Phase 4. */
    fun conversations(): List<Conversation>

    suspend fun search(query: String): SearchResults

    /** Resolves a collection by id, or null when it does not exist. */
    suspend fun collection(id: String): MediaCollection?

    /** Track listing for an album or playlist, in running order. */
    suspend fun tracks(collectionId: String): List<Track>

    /** Lyrics, bio, contributors and related content for the Now Playing page. */
    suspend fun trackDetails(track: Track): TrackDetails
}
