package com.spotkofi.app.data.repository

import com.spotkofi.app.data.model.BrowseCategory
import com.spotkofi.app.data.model.ChartRegion
import com.spotkofi.app.data.model.Conversation
import com.spotkofi.app.data.model.ExploreItem
import com.spotkofi.app.data.model.FriendActivity
import com.spotkofi.app.data.model.HomeSection
import com.spotkofi.app.data.model.HomeTab
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.MoodCategory
import com.spotkofi.app.data.model.MoodGroup
import com.spotkofi.app.data.model.MusicChart
import com.spotkofi.app.data.model.SearchResults
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.TrackDetails
import kotlinx.coroutines.flow.Flow

/** A mood or genre page as the UI consumes it. */
data class MoodCategoryContents(
    val title: String,
    val songs: List<Track>,
    val playlists: List<MediaCollection>,
)

/**
 * The music catalog, as the UI sees it.
 *
 * The feed and lookup calls are `suspend` rather than `Flow`-returning because
 * they are one-shot network requests that can fail. A Flow that emits exactly once
 * and then has to smuggle an error through hides that; a suspend function that
 * throws lets each ViewModel decide what a failure means for its screen.
 *
 * [library] is the exception and stays a Flow, because it is genuinely observable
 * local state that grows while the app is open.
 */
interface MusicRepository {

    /** Display name of the signed-in user. Becomes a real profile with accounts. */
    fun currentUserName(): String

    // ---------------------------------------------------------------- feeds

    /** The compact grid pinned to the top of Home. */
    suspend fun quickPicks(): List<MediaCollection>

    /** Home content for [tab]. */
    suspend fun homeSections(tab: HomeTab): List<HomeSection>

    /**
     * What the user has opened, most recent first.
     *
     * Local saved songs are handled by [com.spotkofi.app.data.local.LocalMusicStore];
     * this repository flow remains the durable recent-collections view.
     */
    fun library(): Flow<List<MediaCollection>>

    /** Records that a collection was opened, for [library]. */
    fun recordVisited(collection: MediaCollection)

    // --------------------------------------------------------------- search

    /** The genre tiles shown before anything is typed. */
    fun browseCategories(): List<BrowseCategory>

    suspend fun search(query: String): SearchResults

    suspend fun exploreVideos(): List<ExploreItem>

    suspend fun explorePodcasts(): List<ExploreItem>

    // -------------------------------------------------------------- explore

    /** The regions a chart can be requested for. */
    fun chartRegions(): List<ChartRegion>

    /** Top songs, top artists and playlist shelves for [regionCode]. */
    suspend fun chart(regionCode: String): MusicChart?

    /** "Moods & moments" and "Genres", as the provider groups them. */
    suspend fun moodsAndGenres(): List<MoodGroup>

    /** The contents of one mood or genre tile. */
    suspend fun moodCategory(category: MoodCategory): MoodCategoryContents?

    /** Albums and singles the provider is currently featuring. */
    suspend fun newReleases(): List<MediaCollection>

    /** Playlists the provider is currently pushing in [regionCode]. */
    suspend fun trendingPlaylists(regionCode: String): List<MediaCollection>

    // --------------------------------------------------------------- detail

    /** Resolves a collection by id, or null when the catalog has no such entry. */
    suspend fun collection(id: String): MediaCollection?

    /** Track listing for an album, or an artist's top tracks. */
    suspend fun tracks(collectionId: String): List<Track>

    /** Related content for the Now Playing page. */
    suspend fun trackDetails(track: Track): TrackDetails

    // --------------------------------------------------------------- social
    // Not backed by the catalog API: these are account features and stay empty
    // until sign-in exists. Returning empty lists is honest; the screens render
    // their own empty states.

    fun friendActivity(): List<FriendActivity>

    fun conversations(): List<Conversation>
}
