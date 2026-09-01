package com.spotkofi.app.data.remote

import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.ChartRegion
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.MoodCategory
import com.spotkofi.app.data.model.MoodGroup
import com.spotkofi.app.data.model.MusicChart
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
import com.spotkofi.app.data.remote.Innertube.thumbnailUrls
import com.spotkofi.app.data.remote.Innertube.videoId
import com.spotkofi.app.data.remote.Innertube.walkObjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient

/**
 * YouTube Music browse feeds: charts, moods and genres, new releases, and the
 * album/artist/playlist pages the other feeds link to.
 *
 * Detail lookups live here rather than in the search client because a search
 * result is only useful if it opens: a chart carousel, a mood category and a
 * search hit all hand back the same kind of browse ID, and they all need the same
 * page fetched afterwards.
 */
internal class YouTubeMusicBrowseClient(
    private val client: OkHttpClient = Innertube.defaultClient(),
) {

    // ---- Charts ---------------------------------------------------------

    /**
     * The chart page for [region].
     *
     * The region is sent in the request body, not as the client's `gl`: language
     * and "whose chart" are separate questions, and conflating them returned a
     * localised copy of the wrong country's chart.
     */
    suspend fun chart(region: String): MusicChart? = withContext(Dispatchers.IO) {
        val root = browse(browseId = Innertube.BROWSE_CHARTS, country = region)
            ?: return@withContext null

        val shelves = root.shelves()
        val songs = mutableListOf<Track>()
        val artists = mutableListOf<Artist>()
        val playlistShelves = mutableListOf<MusicChart.Shelf>()

        shelves.forEach { shelf ->
            val shelfArtists = shelf.artists()
            val shelfSongs = shelf.songs()
            val shelfPlaylists = shelf.collections()

            when {
                // An artist shelf is identified by what its items link to, not by
                // its heading: the heading is localised and changes per region.
                shelfArtists.isNotEmpty() -> artists += shelfArtists
                shelfSongs.isNotEmpty() -> songs += shelfSongs
                shelfPlaylists.isNotEmpty() -> playlistShelves += MusicChart.Shelf(
                    title = shelf.title,
                    items = shelfPlaylists,
                )
            }
        }

        if (songs.isEmpty() && artists.isEmpty() && playlistShelves.isEmpty()) {
            return@withContext null
        }

        MusicChart(
            region = region,
            topSongs = songs.distinctBy { it.id }.take(MAX_CHART_SONGS),
            topArtists = artists.distinctBy { it.id }.take(MAX_CHART_ARTISTS),
            shelves = playlistShelves.take(MAX_SHELVES),
        )
    }

    // ---- Moods and genres ----------------------------------------------

    suspend fun moodsAndGenres(): List<MoodGroup> = withContext(Dispatchers.IO) {
        val root = browse(browseId = Innertube.BROWSE_MOODS_AND_GENRES)
            ?: return@withContext emptyList()

        root.walkObjects()
            .mapNotNull { it["gridRenderer"] as? JsonObject }
            .mapNotNull { grid ->
                val title = grid["header"]
                    ?.let { it as? JsonObject }
                    ?.walkObjects()
                    ?.mapNotNull { header -> header["title"].runText() }
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                val items = (grid["items"] as? JsonArray)
                    .orEmpty()
                    .mapNotNull { item ->
                        val button = (item as? JsonObject)
                            ?.get("musicNavigationButtonRenderer") as? JsonObject
                            ?: return@mapNotNull null
                        val label = button["buttonText"].runText()?.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        val params = button.walkObjects()
                            .mapNotNull { it.string("params") }
                            .firstOrNull()
                            ?: return@mapNotNull null
                        MoodCategory(
                            title = label,
                            params = params,
                            // The stripe colour is supplied by the API. Generating one
                            // locally made the grid change colours on every recomposition.
                            colorArgb = button.walkObjects()
                                .mapNotNull { it["leftStripeColor"]?.toString()?.toLongOrNull() }
                                .firstOrNull(),
                        )
                    }

                items.takeIf { it.isNotEmpty() }?.let { MoodGroup(title = title, items = it) }
            }
            .toList()
    }

    /**
     * The contents of one mood or genre.
     *
     * Both use the same browse ID and differ only by the opaque `params` the grid
     * item carried, so there is one code path rather than a mood path and a genre
     * path that could drift.
     */
    suspend fun moodCategory(params: String): MoodCategoryPage? = withContext(Dispatchers.IO) {
        val root = browse(
            browseId = Innertube.BROWSE_MOODS_AND_GENRES_CATEGORY,
            params = params,
        ) ?: return@withContext null

        val header = root.walkObjects()
            .mapNotNull { (it["musicHeaderRenderer"] as? JsonObject)?.get("title").runText() }
            .firstOrNull()

        val shelves = root.shelves()
        val songs = shelves.flatMap { it.songs() }.distinctBy { it.id }
        val playlists = shelves.flatMap { it.collections() }.distinctBy { it.id }

        if (songs.isEmpty() && playlists.isEmpty()) return@withContext null
        MoodCategoryPage(
            title = header.orEmpty(),
            songs = songs.take(MAX_CATEGORY_SONGS),
            playlists = playlists.take(MAX_CATEGORY_PLAYLISTS),
        )
    }

    // ---- New releases ---------------------------------------------------

    suspend fun newReleases(): List<MediaCollection> = withContext(Dispatchers.IO) {
        val root = browse(browseId = Innertube.BROWSE_NEW_RELEASES)
            ?: return@withContext emptyList()
        root.shelves()
            .flatMap { it.collections() }
            .distinctBy { it.id }
            .take(MAX_NEW_RELEASES)
    }

    /** Playlists YouTube is currently pushing, used for the trending row. */
    suspend fun trendingPlaylists(region: String): List<Playlist> = withContext(Dispatchers.IO) {
        val root = browse(browseId = Innertube.BROWSE_CHARTS, country = region)
            ?: return@withContext emptyList()
        root.shelves()
            .flatMap { it.collections() }
            .filterIsInstance<Playlist>()
            .distinctBy { it.id }
            .take(MAX_TRENDING)
    }

    // ---- Detail pages ---------------------------------------------------

    /** Resolves an album, artist or playlist page into a collection header. */
    suspend fun collection(id: String): MediaCollection? = withContext(Dispatchers.IO) {
        val rawId = YouTubeIds.rawId(id) ?: return@withContext null
        val kind = YouTubeIds.kindOf(id) ?: return@withContext null
        val root = browse(browseId = rawId) ?: return@withContext null

        val title = root.headerTitle() ?: return@withContext null
        val artwork = root.headerArtwork()
        val subtitleValues = root.headerSubtitles()

        when (kind) {
            YouTubeIds.Kind.Artist -> Artist(
                id = id,
                name = title,
                genre = null,
                artworkUrl = artwork,
            )

            YouTubeIds.Kind.Album -> Album(
                id = id,
                title = title,
                artistName = subtitleValues.firstOrNull { it.isPersonOrArtistName() }.orEmpty(),
                year = subtitleValues.firstNotNullOfOrNull { value ->
                    value.trim().toIntOrNull()?.takeIf { it in 1900..2100 }
                },
                trackCount = subtitleValues.firstNotNullOfOrNull { it.toTrackCount() } ?: 0,
                artworkUrl = artwork,
            )

            YouTubeIds.Kind.Playlist -> Playlist(
                id = id,
                title = title,
                // The header subtitle is owner/type metadata, not a description.
                description = root.headerDescription().orEmpty(),
                ownerName = subtitleValues.firstOrNull { it.isPersonOrArtistName() }.orEmpty(),
                artworkUrl = artwork,
            )
        }
    }

    /** Track listing for an album or playlist, or an artist's top songs. */
    suspend fun collectionTracks(id: String): List<Track> = withContext(Dispatchers.IO) {
        val rawId = YouTubeIds.rawId(id) ?: return@withContext emptyList()
        val root = browse(browseId = rawId) ?: return@withContext emptyList()

        // Album and playlist pages list their songs in a shelf; an artist page
        // leads with a top-songs carousel. Both shapes are read the same way.
        val rows = root.walkObjects()
            .mapNotNull { it["musicResponsiveListItemRenderer"] as? JsonObject }
            .mapNotNull(Innertube::parseSongRow)
            .toList()
        val cards = root.walkObjects()
            .mapNotNull { it["musicTwoRowItemRenderer"] as? JsonObject }
            .mapNotNull(Innertube::parseSongCard)
            .toList()

        (rows + cards).distinctBy { it.id }.take(MAX_TRACKS)
    }

    /** Albums released by an artist, for the artist page's album row. */
    suspend fun artistAlbums(id: String): List<Album> = withContext(Dispatchers.IO) {
        val rawId = YouTubeIds.rawId(id) ?: return@withContext emptyList()
        val root = browse(browseId = rawId) ?: return@withContext emptyList()
        root.shelves()
            .flatMap { it.collections() }
            .filterIsInstance<Album>()
            .distinctBy { it.id }
            .take(MAX_ARTIST_ALBUMS)
    }

    // ---- Internals ------------------------------------------------------

    private fun browse(
        browseId: String?,
        params: String? = null,
        country: String? = null,
    ): JsonObject? {
        val response = Innertube.post(
            client = client,
            endpoint = Innertube.BROWSE_ENDPOINT,
            body = Innertube.browseBody(
                browseId = browseId,
                params = params,
                country = country,
            ),
        ) ?: return null
        return runCatching {
            Innertube.json.parseToJsonElement(response) as? JsonObject
        }.getOrNull()
    }

    private fun JsonObject.headerTitle(): String? = walkObjects()
        .mapNotNull { candidate ->
            (candidate["musicHeaderRenderer"] as? JsonObject)?.get("title").runText()
                ?: (candidate["musicDetailHeaderRenderer"] as? JsonObject)?.get("title").runText()
                ?: (candidate["musicImmersiveHeaderRenderer"] as? JsonObject)?.get("title").runText()
                ?: (candidate["musicResponsiveHeaderRenderer"] as? JsonObject)?.get("title").runText()
        }
        .firstOrNull { it.isNotBlank() }

    private fun JsonObject.headerArtwork(): String? = walkObjects()
        .mapNotNull { candidate ->
            (candidate["musicDetailHeaderRenderer"] as? JsonObject)?.thumbnailUrl()
                ?: (candidate["musicImmersiveHeaderRenderer"] as? JsonObject)?.thumbnailUrl()
                ?: (candidate["musicResponsiveHeaderRenderer"] as? JsonObject)?.thumbnailUrl()
        }
        .firstOrNull()
        ?: thumbnailUrl()

    private fun JsonObject.headerDescription(): String? = walkObjects()
        .mapNotNull { candidate ->
            (candidate["musicDetailHeaderRenderer"] as? JsonObject)?.get("description").runText()
                ?: (candidate["musicResponsiveHeaderRenderer"] as? JsonObject)
                    ?.get("description")
                    .runText()
        }
        .firstOrNull { it.isNotBlank() }

    private fun JsonObject.headerSubtitles(): List<String> = walkObjects()
        .mapNotNull { candidate ->
            (candidate["musicDetailHeaderRenderer"] as? JsonObject)?.get("subtitle")
                ?: (candidate["musicResponsiveHeaderRenderer"] as? JsonObject)?.get("subtitle")
                ?: (candidate["musicImmersiveHeaderRenderer"] as? JsonObject)?.get("subtitle")
        }
        .firstOrNull()
        .runObjects()
        .mapNotNull { it.string("text")?.trim() }
        .filter { isMeaningful(it) }

    /** Every carousel/shelf in a browse response, with its heading. */
    private fun JsonObject.shelves(): List<ParsedShelf> = walkObjects()
        .mapNotNull { candidate ->
            val renderer = (candidate["musicCarouselShelfRenderer"] as? JsonObject)
                ?: (candidate["musicShelfRenderer"] as? JsonObject)
                ?: (candidate["gridRenderer"] as? JsonObject)
                ?: return@mapNotNull null
            val title = renderer["header"]
                ?.let { it as? JsonObject }
                ?.walkObjects()
                ?.mapNotNull { header -> header["title"].runText() }
                ?.firstOrNull()
                ?: renderer["title"].runText()
            val contents = (renderer["contents"] as? JsonArray)
                ?: (renderer["items"] as? JsonArray)
                ?: return@mapNotNull null
            ParsedShelf(title = title.orEmpty(), contents = contents)
        }
        .toList()

    private data class ParsedShelf(
        val title: String,
        val contents: JsonArray,
    )

    private fun ParsedShelf.songs(): List<Track> = contents
        .mapNotNull { it as? JsonObject }
        .mapNotNull { item ->
            (item["musicResponsiveListItemRenderer"] as? JsonObject)
                ?.let(Innertube::parseSongRow)
                ?: (item["musicTwoRowItemRenderer"] as? JsonObject)
                    ?.let(Innertube::parseSongCard)
        }

    private fun ParsedShelf.artists(): List<Artist> = contents
        .mapNotNull { it as? JsonObject }
        .mapNotNull { item ->
            val renderer = (item["musicResponsiveListItemRenderer"] as? JsonObject)
                ?: (item["musicTwoRowItemRenderer"] as? JsonObject)
                ?: return@mapNotNull null
            if (renderer.pageType() != Innertube.PAGE_TYPE_ARTIST) return@mapNotNull null
            val browse = renderer.browseId()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = renderer.itemTitle() ?: return@mapNotNull null
            Artist(
                id = YouTubeIds.artist(browse),
                name = name,
                genre = null,
                artworkUrl = renderer.thumbnailUrl(),
            )
        }

    /** Albums and playlists in a shelf, keeping whichever kind each item is. */
    private fun ParsedShelf.collections(): List<MediaCollection> = contents
        .mapNotNull { it as? JsonObject }
        .mapNotNull { item ->
            val renderer = (item["musicTwoRowItemRenderer"] as? JsonObject)
                ?: (item["musicResponsiveListItemRenderer"] as? JsonObject)
                ?: return@mapNotNull null
            // A card that plays something is a song, not a collection.
            if (renderer.videoId() != null) return@mapNotNull null

            val browse = renderer.browseId()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = renderer.itemTitle() ?: return@mapNotNull null
            val subtitle = renderer.itemSubtitles()

            when (renderer.pageType()) {
                Innertube.PAGE_TYPE_ALBUM -> Album(
                    id = YouTubeIds.album(browse),
                    title = title,
                    artistName = subtitle.firstOrNull { it.isPersonOrArtistName() }.orEmpty(),
                    year = subtitle.firstNotNullOfOrNull { value ->
                        value.trim().toIntOrNull()?.takeIf { it in 1900..2100 }
                    },
                    trackCount = subtitle.firstNotNullOfOrNull { it.toTrackCount() } ?: 0,
                    artworkUrl = renderer.thumbnailUrl(),
                )

                Innertube.PAGE_TYPE_PLAYLIST -> Playlist(
                    id = YouTubeIds.playlist(browse),
                    title = title,
                    description = "",
                    ownerName = subtitle.firstOrNull { it.isPersonOrArtistName() }.orEmpty(),
                    artworkUrl = renderer.thumbnailUrl(),
                    artworkUrls = renderer.thumbnailUrls(),
                )

                else -> null
            }
        }

    private fun JsonObject.itemTitle(): String? {
        val columns = this["flexColumns"] as? JsonArray
        val fromColumn = columns?.firstOrNull().columnRuns().firstOrNull()?.string("text")?.trim()
        return (fromColumn ?: this["title"].runText())
            ?.takeIf { it.isNotEmpty() && !Innertube.isPlaceholder(it) }
    }

    private fun JsonObject.itemSubtitles(): List<String> {
        val columns = this["flexColumns"] as? JsonArray
        val runs = columns?.drop(1)?.flatMap { it.columnRuns() }
            ?: this["subtitle"].runObjects()
        return runs.mapNotNull { it.string("text")?.trim() }.filter { isMeaningful(it) }
    }

    private fun String.toTrackCount(): Int? {
        val parts = trim().split(' ').filter { it.isNotBlank() }
        val count = parts.firstOrNull()?.toIntOrNull() ?: return null
        val label = parts.drop(1).joinToString(" ").lowercase()
        return count.takeIf { it > 0 && (label == "song" || label == "songs" || label == "track" || label == "tracks") }
    }

    private fun String.isPersonOrArtistName(): Boolean =
        isNotBlank() && trim().toIntOrNull()?.let { it in 1900..2100 } != true &&
            toTrackCount() == null &&
            !contains("album", ignoreCase = true) &&
            !contains("playlist", ignoreCase = true) &&
            !equals("YouTube Music", ignoreCase = true)

    private companion object {
        const val MAX_CHART_SONGS = 30
        const val MAX_CHART_ARTISTS = 20
        const val MAX_SHELVES = 6
        const val MAX_CATEGORY_SONGS = 30
        const val MAX_CATEGORY_PLAYLISTS = 24
        const val MAX_NEW_RELEASES = 24
        const val MAX_TRENDING = 20
        const val MAX_TRACKS = 60
        const val MAX_ARTIST_ALBUMS = 20
    }
}

/** One mood or genre page: its songs and its playlists. */
internal data class MoodCategoryPage(
    val title: String,
    val songs: List<Track>,
    val playlists: List<MediaCollection>,
)

/** The regions YouTube Music publishes charts for. */
internal object ChartRegions {

    val all: List<ChartRegion> = listOf(
        ChartRegion("ZZ", "Global"),
        ChartRegion("PH", "Philippines"),
        ChartRegion("US", "United States"),
        ChartRegion("GB", "United Kingdom"),
        ChartRegion("AR", "Argentina"),
        ChartRegion("AU", "Australia"),
        ChartRegion("AT", "Austria"),
        ChartRegion("BE", "Belgium"),
        ChartRegion("BO", "Bolivia"),
        ChartRegion("BR", "Brazil"),
        ChartRegion("CA", "Canada"),
        ChartRegion("CL", "Chile"),
        ChartRegion("CO", "Colombia"),
        ChartRegion("CR", "Costa Rica"),
        ChartRegion("CZ", "Czechia"),
        ChartRegion("DK", "Denmark"),
        ChartRegion("DO", "Dominican Republic"),
        ChartRegion("EC", "Ecuador"),
        ChartRegion("EG", "Egypt"),
        ChartRegion("SV", "El Salvador"),
        ChartRegion("EE", "Estonia"),
        ChartRegion("FI", "Finland"),
        ChartRegion("FR", "France"),
        ChartRegion("DE", "Germany"),
        ChartRegion("GT", "Guatemala"),
        ChartRegion("HN", "Honduras"),
        ChartRegion("HK", "Hong Kong"),
        ChartRegion("HU", "Hungary"),
        ChartRegion("IS", "Iceland"),
        ChartRegion("IN", "India"),
        ChartRegion("ID", "Indonesia"),
        ChartRegion("IE", "Ireland"),
        ChartRegion("IL", "Israel"),
        ChartRegion("IT", "Italy"),
        ChartRegion("JP", "Japan"),
        ChartRegion("KE", "Kenya"),
        ChartRegion("LU", "Luxembourg"),
        ChartRegion("MY", "Malaysia"),
        ChartRegion("MX", "Mexico"),
        ChartRegion("NL", "Netherlands"),
        ChartRegion("NZ", "New Zealand"),
        ChartRegion("NI", "Nicaragua"),
        ChartRegion("NG", "Nigeria"),
        ChartRegion("NO", "Norway"),
        ChartRegion("PA", "Panama"),
        ChartRegion("PY", "Paraguay"),
        ChartRegion("PE", "Peru"),
        ChartRegion("PL", "Poland"),
        ChartRegion("PT", "Portugal"),
        ChartRegion("RO", "Romania"),
        ChartRegion("RU", "Russia"),
        ChartRegion("SA", "Saudi Arabia"),
        ChartRegion("RS", "Serbia"),
        ChartRegion("SG", "Singapore"),
        ChartRegion("ZA", "South Africa"),
        ChartRegion("KR", "South Korea"),
        ChartRegion("ES", "Spain"),
        ChartRegion("SE", "Sweden"),
        ChartRegion("CH", "Switzerland"),
        ChartRegion("TW", "Taiwan"),
        ChartRegion("TZ", "Tanzania"),
        ChartRegion("TH", "Thailand"),
        ChartRegion("TR", "Turkey"),
        ChartRegion("UG", "Uganda"),
        ChartRegion("UA", "Ukraine"),
        ChartRegion("AE", "United Arab Emirates"),
        ChartRegion("UY", "Uruguay"),
        ChartRegion("VN", "Vietnam"),
        ChartRegion("ZW", "Zimbabwe"),
    )

    const val DEFAULT_CODE = "ZZ"

    fun nameOf(code: String): String =
        all.firstOrNull { it.code == code }?.name ?: code
}
