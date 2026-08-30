package com.spotkofi.app.data.remote

import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.Playlist
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.remote.Innertube.browseId
import com.spotkofi.app.data.remote.Innertube.columnRuns
import com.spotkofi.app.data.remote.Innertube.containsString
import com.spotkofi.app.data.remote.Innertube.continuationToken
import com.spotkofi.app.data.remote.Innertube.isMeaningful
import com.spotkofi.app.data.remote.Innertube.pageType
import com.spotkofi.app.data.remote.Innertube.runObjects
import com.spotkofi.app.data.remote.Innertube.runText
import com.spotkofi.app.data.remote.Innertube.string
import com.spotkofi.app.data.remote.Innertube.thumbnailUrl
import com.spotkofi.app.data.remote.Innertube.walkObjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient

/**
 * YouTube Music search.
 *
 * Every result type is fetched from YouTube rather than only songs. Artists and
 * albums used to be borrowed from a different catalog and merged in, which is why
 * a search could show an artist card that had nothing to do with the songs above
 * it and could not be opened.
 *
 * Each result type has its own filter parameter, so the four lists are four small
 * focused requests instead of one unfiltered response that has to be sorted out
 * afterwards.
 */
internal class YouTubeMusicSearchClient(
    private val client: OkHttpClient = Innertube.defaultClient(),
) {

    suspend fun searchSongs(
        query: String,
        limit: Int = DEFAULT_SONG_LIMIT,
        country: String = "US",
    ): List<Track> = withContext(Dispatchers.IO) {
        pagedSearch(
            query = query,
            filter = Innertube.FILTER_SONGS,
            limit = limit,
            country = country,
            key = { it.id },
        ) { root ->
            root.walkObjects()
                .mapNotNull { it["musicResponsiveListItemRenderer"] as? JsonObject }
                .mapNotNull(Innertube::parseSongRow)
                .toList()
        }
    }

    suspend fun searchArtists(
        query: String,
        limit: Int = DEFAULT_COLLECTION_LIMIT,
        country: String = "US",
    ): List<Artist> = withContext(Dispatchers.IO) {
        pagedSearch(
            query = query,
            filter = FILTER_ARTISTS,
            limit = limit,
            country = country,
            key = { it.id },
        ) { root -> parseArtists(root) }
    }

    suspend fun searchAlbums(
        query: String,
        limit: Int = DEFAULT_COLLECTION_LIMIT,
        country: String = "US",
    ): List<Album> = withContext(Dispatchers.IO) {
        pagedSearch(
            query = query,
            filter = FILTER_ALBUMS,
            limit = limit,
            country = country,
            key = { it.id },
        ) { root -> parseAlbums(root) }
    }

    suspend fun searchPlaylists(
        query: String,
        limit: Int = DEFAULT_COLLECTION_LIMIT,
        country: String = "US",
    ): List<Playlist> = withContext(Dispatchers.IO) {
        pagedSearch(
            query = query,
            filter = FILTER_PLAYLISTS,
            limit = limit,
            country = country,
            key = { it.id },
        ) { root -> parsePlaylists(root) }
    }

    /**
     * Runs one filtered search and follows continuations until [limit] is reached.
     *
     * Deduplication is by the caller's [key] rather than by object equality: the
     * same song legitimately appears in more than one shelf of a response, and a
     * results list that repeats a row looks broken.
     */
    private inline fun <T> pagedSearch(
        query: String,
        filter: String,
        limit: Int,
        country: String,
        key: (T) -> String,
        parse: (JsonElement) -> List<T>,
    ): List<T> {
        val term = query.trim()
        if (term.isEmpty() || limit <= 0) return emptyList()

        val results = LinkedHashMap<String, T>()
        var responseText = request(term, filter, continuation = null, country = country)
        var page = 0

        while (responseText != null && page < MAX_PAGES && results.size < limit) {
            val root = runCatching { Innertube.json.parseToJsonElement(responseText) }.getOrNull()
                ?: break
            parse(root).forEach { item ->
                if (results.size < limit) results.putIfAbsent(key(item), item)
            }
            if (results.size >= limit) break

            val continuation = (root as? JsonObject)?.continuationToken() ?: break
            responseText = request(query = null, filter = filter, continuation = continuation, country = country)
            page++
        }

        return results.values.take(limit)
    }

    private fun request(
        query: String?,
        filter: String,
        continuation: String?,
        country: String,
    ): String? = Innertube.post(
        client = client,
        endpoint = Innertube.SEARCH_ENDPOINT,
        body = Innertube.searchBody(
            query = query,
            params = filter.takeIf { continuation == null },
            continuation = continuation,
            country = country,
        ),
    )

    // ---- Collection parsing --------------------------------------------

    private fun parseArtists(root: JsonElement): List<Artist> = root.walkObjects()
        .mapNotNull { it["musicResponsiveListItemRenderer"] as? JsonObject }
        .filter { it.pageType() == Innertube.PAGE_TYPE_ARTIST }
        .mapNotNull { renderer ->
            val id = renderer.browseId()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = renderer.rowTitle() ?: return@mapNotNull null
            Artist(
                id = YouTubeIds.artist(id),
                name = name,
                // The subtitle here is a subscriber count, which is a metric rather
                // than a genre, so it is dropped instead of being printed under the
                // name as if it described the music.
                genre = null,
                artworkUrl = renderer.thumbnailUrl(),
            )
        }
        .toList()

    private fun parseAlbums(root: JsonElement): List<Album> = root.walkObjects()
        .mapNotNull { it["musicResponsiveListItemRenderer"] as? JsonObject }
        .filter { it.pageType() == Innertube.PAGE_TYPE_ALBUM }
        .mapNotNull { renderer ->
            val id = renderer.browseId()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = renderer.rowTitle() ?: return@mapNotNull null
            val subtitle = renderer.subtitleValues()
            Album(
                id = YouTubeIds.album(id),
                title = title,
                artistName = subtitle.firstOrNull { it.isPersonOrArtistName() }.orEmpty(),
                year = subtitle.firstNotNullOfOrNull { it.toReleaseYear() },
                trackCount = subtitle.firstNotNullOfOrNull { it.toTrackCount() } ?: 0,
                artworkUrl = renderer.thumbnailUrl(),
            )
        }
        .toList()

    private fun parsePlaylists(root: JsonElement): List<Playlist> = root.walkObjects()
        .mapNotNull { it["musicResponsiveListItemRenderer"] as? JsonObject }
        .filter { it.pageType() == Innertube.PAGE_TYPE_PLAYLIST }
        .mapNotNull { renderer ->
            val id = renderer.browseId()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = renderer.rowTitle() ?: return@mapNotNull null
            val subtitle = renderer.subtitleValues()
            Playlist(
                id = YouTubeIds.playlist(id),
                title = title,
                // Search subtitles expose the owner, not a playlist description.
                // Keeping it out of description prevents the same string appearing twice.
                description = "",
                ownerName = subtitle.firstOrNull { it.isPersonOrArtistName() }.orEmpty(),
                artworkUrl = renderer.thumbnailUrl(),
            )
        }
        .toList()

    private fun JsonObject.rowTitle(): String? {
        val columns = this["flexColumns"] as? JsonArray
        val fromColumn = columns?.firstOrNull()
            .columnRuns()
            .firstOrNull()
            ?.string("text")
            ?.trim()
        val title = fromColumn ?: this["title"].runText()
        return title?.takeIf { it.isNotEmpty() && !Innertube.isPlaceholder(it) }
    }

    private fun JsonObject.subtitleValues(): List<String> {
        val columns = this["flexColumns"] as? JsonArray
        val runs = columns?.drop(1)?.flatMap { it.columnRuns() }
            ?: this["subtitle"].runObjects()
        return runs.mapNotNull { it.string("text")?.trim() }.filter { isMeaningful(it) }
    }

    private fun String.toReleaseYear(): Int? =
        trim().toIntOrNull()?.takeIf { it in 1900..2100 }

    private fun String.toTrackCount(): Int? {
        val parts = trim().split(' ').filter { it.isNotBlank() }
        val count = parts.firstOrNull()?.toIntOrNull() ?: return null
        val label = parts.drop(1).joinToString(" ").lowercase()
        return count.takeIf { it > 0 && (label == "song" || label == "songs" || label == "track" || label == "tracks") }
    }

    private fun String.isPersonOrArtistName(): Boolean =
        isNotBlank() && toReleaseYear() == null && toTrackCount() == null &&
            !contains("album", ignoreCase = true) &&
            !contains("playlist", ignoreCase = true) &&
            !equals("YouTube Music", ignoreCase = true)

    /** Kept for callers that only need to know whether the row is explicit. */
    @Suppress("unused")
    private fun JsonObject.isExplicit(): Boolean =
        containsString("iconType", Innertube.EXPLICIT_BADGE)

    private companion object {
        const val DEFAULT_SONG_LIMIT = 25
        const val DEFAULT_COLLECTION_LIMIT = 12
        const val MAX_PAGES = 3

        /**
         * Result-type filters, stored decoded.
         *
         * They are the standard YouTube Music search filters; the trailing `==` is
         * real base64 padding and must not be percent-escaped, because the value
         * travels inside a JSON body where `%3D` is just two literal characters.
         */
        const val FILTER_ALBUMS = "EgWKAQIYAWoKEAkQChAFEAMQBA=="
        const val FILTER_ARTISTS = "EgWKAQIgAWoKEAkQChAFEAMQBA=="
        const val FILTER_PLAYLISTS = "EgWKAQIoAWoKEAkQChAFEAMQBA=="
    }
}

/**
 * Identity scheme for YouTube-sourced collections.
 *
 * A YouTube browse ID is opaque and can collide with the other catalog's numeric
 * ids, so every id carries the provider and the kind it came from. Detail lookups
 * read the prefix to decide which endpoint to call, which is what lets a search
 * result actually open instead of resolving to nothing.
 */
internal object YouTubeIds {

    const val ARTIST_PREFIX = "ytartist:"
    const val ALBUM_PREFIX = "ytalbum:"
    const val PLAYLIST_PREFIX = "ytplaylist:"

    fun artist(browseId: String): String = ARTIST_PREFIX + browseId

    fun album(browseId: String): String = ALBUM_PREFIX + browseId

    fun playlist(browseId: String): String = PLAYLIST_PREFIX + browseId

    fun rawId(id: String): String? = when {
        id.startsWith(ARTIST_PREFIX) -> id.removePrefix(ARTIST_PREFIX)
        id.startsWith(ALBUM_PREFIX) -> id.removePrefix(ALBUM_PREFIX)
        id.startsWith(PLAYLIST_PREFIX) -> id.removePrefix(PLAYLIST_PREFIX)
        else -> null
    }?.takeIf { it.isNotBlank() }

    fun isYouTube(id: String): Boolean = rawId(id) != null

    fun kindOf(id: String): Kind? = when {
        id.startsWith(ARTIST_PREFIX) -> Kind.Artist
        id.startsWith(ALBUM_PREFIX) -> Kind.Album
        id.startsWith(PLAYLIST_PREFIX) -> Kind.Playlist
        else -> null
    }

    enum class Kind { Artist, Album, Playlist }
}

/** Convenience so callers can treat any collection uniformly. */
internal fun MediaCollection.isYouTubeSourced(): Boolean = YouTubeIds.isYouTube(id)
