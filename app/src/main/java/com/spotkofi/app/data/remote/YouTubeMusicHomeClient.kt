package com.spotkofi.app.data.remote

import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.HomeSection
import com.spotkofi.app.data.model.HomeTab
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.Playlist
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.remote.Innertube.browseId
import com.spotkofi.app.data.remote.Innertube.columnRuns
import com.spotkofi.app.data.remote.Innertube.isMeaningful
import com.spotkofi.app.data.remote.Innertube.pageType
import com.spotkofi.app.data.remote.Innertube.runObjects
import com.spotkofi.app.data.remote.Innertube.runText
import com.spotkofi.app.data.remote.Innertube.string
import com.spotkofi.app.data.remote.Innertube.thumbnailUrl
import com.spotkofi.app.data.remote.Innertube.videoId
import com.spotkofi.app.data.remote.Innertube.walkObjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient

/**
 * The YouTube Music home feed.
 *
 * Shelves keep the shape the provider gave them: a shelf of songs stays a list of
 * playable rows, and a shelf of albums or playlists stays a row of cards. It used
 * to coerce every shelf into songs, which silently dropped whole rows of the feed
 * - anything without a video ID simply vanished.
 *
 * Parsing and the request body live in [Innertube] so this feed, search and the
 * explore feeds cannot disagree about what an artist or an album is.
 */
internal class YouTubeMusicHomeClient(
    private val client: OkHttpClient = Innertube.defaultClient(),
) {

    suspend fun sections(
        tab: HomeTab,
        country: String = "US",
    ): List<HomeSection> = withContext(Dispatchers.IO) {
        val browseIds = when (tab) {
            HomeTab.All -> listOf(
                Innertube.BROWSE_HOME,
                Innertube.BROWSE_NEW_RELEASES,
                Innertube.BROWSE_CHARTS,
            )

            HomeTab.Music -> listOf(Innertube.BROWSE_HOME, Innertube.BROWSE_CHARTS)
            HomeTab.Podcasts -> emptyList()
        }
        if (browseIds.isEmpty()) return@withContext emptyList()

        browseIds
            .flatMapIndexed { index, browseId ->
                requestPages(browseId, country).mapIndexed { shelfIndex, shelf ->
                    shelf.toSection("youtube_${tab.name.lowercase()}_${index}_$shelfIndex")
                }
            }
            .filterNotNull()
            // Deduplicated by heading plus contents, so the same shelf arriving from
            // two browse IDs is shown once.
            .distinctBy { section -> section.title.lowercase() to section.contentKey() }
            .take(MAX_SHELVES)
    }

    private fun requestPages(browseId: String, country: String): List<ParsedShelf> {
        val shelves = LinkedHashMap<String, ParsedShelf>()
        var responseText = request(browseId = browseId, continuation = null, country = country)
        var page = 0

        while (responseText != null && page < MAX_PAGES) {
            val root = runCatching {
                Innertube.json.parseToJsonElement(responseText) as? JsonObject
            }.getOrNull() ?: break

            parseShelves(root).forEach { parsed ->
                val existing = shelves[parsed.title]
                shelves[parsed.title] = existing?.merge(parsed) ?: parsed
            }

            val continuation = root.walkObjects()
                .mapNotNull { objectValue ->
                    (objectValue["continuationCommand"] as? JsonObject)?.string("token")
                        ?: (objectValue["nextContinuationData"] as? JsonObject)
                            ?.string("continuation")
                }
                .firstOrNull() ?: break
            responseText = request(browseId = null, continuation = continuation, country = country)
            page++
        }

        return shelves.values.toList()
    }

    private fun request(browseId: String?, continuation: String?, country: String): String? =
        Innertube.post(
            client = client,
            endpoint = Innertube.BROWSE_ENDPOINT,
            body = Innertube.browseBody(
                browseId = browseId,
                continuation = continuation,
                // Charts need the region in the body; the other browse IDs ignore it.
                country = country.takeIf { browseId == Innertube.BROWSE_CHARTS },
            ),
        )

    private fun parseShelves(root: JsonObject): List<ParsedShelf> = root.walkObjects()
        .mapNotNull { candidate ->
            val renderer = (candidate["musicCarouselShelfRenderer"] as? JsonObject)
                ?: (candidate["musicShelfRenderer"] as? JsonObject)
                ?: return@mapNotNull null
            val title = shelfTitle(renderer)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val contents = renderer["contents"] as? JsonArray ?: return@mapNotNull null

            val items = contents.mapNotNull { it as? JsonObject }
            val songs = items.mapNotNull { item ->
                (item["musicResponsiveListItemRenderer"] as? JsonObject)
                    ?.let(Innertube::parseSongRow)
                    ?: (item["musicTwoRowItemRenderer"] as? JsonObject)
                        ?.let(Innertube::parseSongCard)
            }
            val collections = items.mapNotNull(::parseCollectionCard)

            ParsedShelf(
                title = title,
                songs = songs.distinctBy { it.id }.take(MAX_ITEMS_PER_SHELF),
                collections = collections.distinctBy { it.id }.take(MAX_ITEMS_PER_SHELF),
            ).takeIf { it.songs.isNotEmpty() || it.collections.isNotEmpty() }
        }
        .toList()

    private fun shelfTitle(renderer: JsonObject): String? {
        val header = renderer["header"] as? JsonObject
        val headerTitle = header?.walkObjects()
            ?.mapNotNull { it["title"].runText() }
            ?.firstOrNull()
        if (!headerTitle.isNullOrBlank()) return headerTitle
        return renderer["title"].runText()
    }

    /** An album or playlist card, or null when the item is something else. */
    private fun parseCollectionCard(item: JsonObject): MediaCollection? {
        val renderer = (item["musicTwoRowItemRenderer"] as? JsonObject)
            ?: (item["musicResponsiveListItemRenderer"] as? JsonObject)
            ?: return null
        // Anything playable is a song and is parsed as one.
        if (renderer.videoId() != null) return null

        val browse = renderer.browseId()?.takeIf { it.isNotBlank() } ?: return null
        val title = renderer.cardTitle() ?: return null
        val subtitle = renderer.cardSubtitles()

        return when (renderer.pageType()) {
            Innertube.PAGE_TYPE_ALBUM -> Album(
                id = YouTubeIds.album(browse),
                title = title,
                artistName = subtitle.firstOrNull { it.toIntOrNull() == null }.orEmpty(),
                year = subtitle.firstNotNullOfOrNull { value ->
                    value.trim().toIntOrNull()?.takeIf { it in 1900..2100 }
                },
                artworkUrl = renderer.thumbnailUrl(),
            )

            Innertube.PAGE_TYPE_PLAYLIST -> Playlist(
                id = YouTubeIds.playlist(browse),
                title = title,
                description = subtitle.firstOrNull().orEmpty(),
                ownerName = subtitle.firstOrNull().orEmpty().ifEmpty { "YouTube Music" },
                artworkUrl = renderer.thumbnailUrl(),
            )

            else -> null
        }
    }

    private fun JsonObject.cardTitle(): String? {
        val columns = this["flexColumns"] as? JsonArray
        val fromColumn = columns?.firstOrNull().columnRuns().firstOrNull()?.string("text")?.trim()
        return (fromColumn ?: this["title"].runText())
            ?.takeIf { it.isNotEmpty() && !Innertube.isPlaceholder(it) }
    }

    private fun JsonObject.cardSubtitles(): List<String> {
        val columns = this["flexColumns"] as? JsonArray
        val runs = columns?.drop(1)?.flatMap { it.columnRuns() }
            ?: this["subtitle"].runObjects()
        return runs.mapNotNull { it.string("text")?.trim() }.filter { isMeaningful(it) }
    }

    /**
     * One shelf of the feed before it is given an id.
     *
     * Songs and collections are held separately so the section type is decided by
     * what the shelf actually contained rather than by a guess.
     */
    private data class ParsedShelf(
        val title: String,
        val songs: List<Track>,
        val collections: List<MediaCollection>,
    ) {
        fun merge(other: ParsedShelf): ParsedShelf = ParsedShelf(
            title = title,
            songs = (songs + other.songs).distinctBy { it.id }.take(MAX_ITEMS_PER_SHELF),
            collections = (collections + other.collections)
                .distinctBy { it.id }
                .take(MAX_ITEMS_PER_SHELF),
        )

        fun toSection(id: String): HomeSection? = when {
            // A song shelf wins when a shelf somehow holds both: songs are playable
            // in place, which is what a home feed is for.
            songs.isNotEmpty() -> HomeSection.Songs(id, title, songs)
            collections.isNotEmpty() -> HomeSection.Cards(id, title, collections)
            else -> null
        }
    }

    private fun HomeSection.contentKey(): Set<String> = when (this) {
        is HomeSection.Songs -> items.map { it.id }.toSet()
        is HomeSection.Cards -> items.map { it.id }.toSet()
        is HomeSection.Stations -> items.map { it.id }.toSet()
        is HomeSection.Releases -> items.map { it.id }.toSet()
        is HomeSection.Spotlight -> setOf(item.id)
    }

    private companion object {
        const val MAX_PAGES = 2
        const val MAX_SHELVES = 14
        const val MAX_ITEMS_PER_SHELF = 12
    }
}
