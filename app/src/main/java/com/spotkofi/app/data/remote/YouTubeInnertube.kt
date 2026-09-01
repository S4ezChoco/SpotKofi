package com.spotkofi.app.data.remote

import com.spotkofi.app.data.model.Track
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Shared YouTube Music (Innertube) request and parsing layer.
 *
 * The search client, the home feed and the explore feeds all speak the same
 * protocol and all have to solve the same three problems: build the client
 * context, walk deeply nested renderers, and work out which run in a subtitle is
 * the artist. Those answers used to be duplicated per client, which is how the
 * feeds ended up disagreeing about what an artist even is - one screen showed
 * "Song" as the performer while another showed a view count.
 *
 * Everything here is provider-shaped on purpose: names mirror the renderer keys
 * so a change in the API maps to one obvious place.
 */
internal object Innertube {

    const val CLIENT_NAME = "WEB_REMIX"
    const val CLIENT_VERSION = "1.20241231.01.00"
    const val API_KEY = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI"

    const val BROWSE_ENDPOINT = "https://music.youtube.com/youtubei/v1/browse"
    const val SEARCH_ENDPOINT = "https://music.youtube.com/youtubei/v1/search"

    // ---- Browse IDs ----
    const val BROWSE_HOME = "FEmusic_home"
    const val BROWSE_NEW_RELEASES = "FEmusic_new_releases"
    const val BROWSE_CHARTS = "FEmusic_charts"
    const val BROWSE_MOODS_AND_GENRES = "FEmusic_moods_and_genres"
    const val BROWSE_MOODS_AND_GENRES_CATEGORY = "FEmusic_moods_and_genres_category"

    /**
     * Songs-only search filter.
     *
     * Stored decoded. It used to be kept in its percent-encoded form
     * ("...EQBA%3D%3D") and posted verbatim inside the JSON body, where percent
     * encoding means nothing - YouTube read it as a different, invalid filter and
     * silently answered with an unfiltered mix of videos, albums and profiles.
     * That is what put blank and mislabelled rows in the results list.
     */
    const val FILTER_SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="

    const val PAGE_TYPE_ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
    const val PAGE_TYPE_ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
    const val PAGE_TYPE_PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"
    const val EXPLICIT_BADGE = "MUSIC_EXPLICIT_BADGE"

    val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    /**
     * Subtitle runs that name a *kind* of thing rather than a person.
     *
     * YouTube puts the item type first in a song's subtitle ("Song • Artist •
     * Album • 3:45"). Taking the first non-duration run as the performer therefore
     * printed "Song" in the artist position on a large share of rows.
     */
    private val TYPE_LABELS = setOf(
        "song", "songs", "video", "videos", "album", "albums", "single", "singles",
        "ep", "eps", "playlist", "playlists", "artist", "artists", "episode",
        "episodes", "podcast", "podcasts", "profile", "station", "radio", "mix",
    )

    private val SEPARATORS = setOf("•", "·", "|", "-", "—", "&")

    /** Matches "1.2M views", "530K plays", "4M subscribers" and similar. */
    private val METRIC_PATTERN = Regex(
        """^[\d.,]+\s*[kmbt]?\s*(views?|plays?|listeners?|subscribers?|monthly listeners)$""",
        RegexOption.IGNORE_CASE,
    )

    /** Values that carry no information and must never reach the UI. */
    private val PLACEHOLDERS = setOf("n/a", "na", "unknown", "null", "-", "--", "—", "?")

    fun isSeparator(value: String): Boolean = value.trim() in SEPARATORS

    fun isTypeLabel(value: String): Boolean = value.trim().lowercase() in TYPE_LABELS

    fun isMetric(value: String): Boolean = METRIC_PATTERN.matches(value.trim())

    fun isPlaceholder(value: String): Boolean = value.trim().lowercase() in PLACEHOLDERS

    /**
     * True when a subtitle run is real, human metadata.
     *
     * Applied before anything is treated as an artist or album name, so type
     * labels, view counts, separators and placeholder dashes are all rejected in
     * one place instead of leaking into whichever field happened to read them.
     */
    fun isMeaningful(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.isNotEmpty() &&
            !isSeparator(trimmed) &&
            !isTypeLabel(trimmed) &&
            !isMetric(trimmed) &&
            !isPlaceholder(trimmed) &&
            parseDurationMs(trimmed) == null
    }

    // ---- Request bodies -------------------------------------------------

    fun browseBody(
        browseId: String?,
        params: String? = null,
        continuation: String? = null,
        country: String? = null,
        language: String = "en",
    ): String = buildJsonObject {
        put("context", contextObject(country ?: "US", language))
        if (continuation != null) {
            put("continuation", JsonPrimitive(continuation))
        } else {
            put("browseId", JsonPrimitive(browseId.orEmpty()))
            params?.let { put("params", JsonPrimitive(it)) }
            // Charts take their region in the request body rather than as `gl`:
            // the client locale decides language, this decides whose chart.
            country?.let {
                put(
                    "formData",
                    buildJsonObject {
                        put("selectedValues", JsonArray(listOf(JsonPrimitive(it))))
                    },
                )
            }
        }
    }.toString()

    fun searchBody(
        query: String?,
        params: String? = null,
        continuation: String? = null,
        country: String = "US",
        language: String = "en",
    ): String = buildJsonObject {
        put("context", contextObject(country, language))
        if (continuation != null) {
            put("continuation", JsonPrimitive(continuation))
        } else {
            put("query", JsonPrimitive(query.orEmpty()))
            params?.let { put("params", JsonPrimitive(it)) }
        }
    }.toString()

    private fun contextObject(country: String, language: String): JsonObject = buildJsonObject {
        put(
            "client",
            buildJsonObject {
                put("clientName", JsonPrimitive(CLIENT_NAME))
                put("clientVersion", JsonPrimitive(CLIENT_VERSION))
                put("hl", JsonPrimitive(language))
                put("gl", JsonPrimitive(country))
            },
        )
    }

    fun post(client: OkHttpClient, endpoint: String, body: String): String? {
        val url = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("key", API_KEY)
            .build()
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body.string()
            }
        }.getOrNull()
    }

    // ---- JSON walking ---------------------------------------------------

    fun JsonElement.walkObjects(): Sequence<JsonObject> = sequence {
        when (val element = this@walkObjects) {
            is JsonObject -> {
                yield(element)
                element.values.forEach { child -> yieldAll(child.walkObjects()) }
            }

            is JsonArray -> element.forEach { child -> yieldAll(child.walkObjects()) }
            else -> Unit
        }
    }

    /**
     * Like [walkObjects], but skips `thumbnailCornerOverlay` subtrees.
     *
     * Community-playlist cards carry a corner overlay holding the *owner's channel
     * avatar* - a tiny letter tile on most channels. Walking it used to leak that
     * avatar into the item's artwork: `thumbnailUrl` picked it as the last nested
     * thumbnail (a stretched, blurry "J"), and the multi-image grid filled its
     * remaining cells with the same avatar at two sizes.
     */
    fun JsonElement.walkPrimaryObjects(): Sequence<JsonObject> = sequence {
        when (val element = this@walkPrimaryObjects) {
            is JsonObject -> {
                yield(element)
                element.forEach { (key, child) ->
                    if (key != "thumbnailCornerOverlay") yieldAll(child.walkPrimaryObjects())
                }
            }

            is JsonArray -> element.forEach { child -> yieldAll(child.walkPrimaryObjects()) }
            else -> Unit
        }
    }

    fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    fun JsonElement?.runObjects(): List<JsonObject> =
        ((this as? JsonObject)?.get("runs") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            .orEmpty()

    fun JsonElement?.runText(): String? = runObjects().firstOrNull()?.string("text")?.trim()

    fun JsonElement?.runTexts(): List<String> =
        runObjects().mapNotNull { it.string("text")?.trim() }

    /** The flex-column runs of a `musicResponsiveListItemRenderer` column. */
    fun JsonElement?.columnRuns(): List<JsonObject> {
        val column = this as? JsonObject ?: return emptyList()
        val renderer = column["musicResponsiveListItemFlexColumnRenderer"] as? JsonObject
            ?: return emptyList()
        return renderer["text"].runObjects()
    }

    /** The item's own largest cover thumbnail, ignoring corner-overlay avatars. */
    fun JsonObject.thumbnailUrl(): String? = walkPrimaryObjects()
        .mapNotNull { it["thumbnails"] as? JsonArray }
        .flatMap { it.asSequence() }
        .mapNotNull { it as? JsonObject }
        .mapNotNull { it.string("url") }
        .lastOrNull()

    /** Matches the size suffix of a thumbnail URL (e.g. `=w544-h544-p-l90-rj` or `-w60-h60`). */
    private val thumbnailSizeSuffix = Regex("[-=]w\\d+-h\\d+[^/]*$")

    /**
     * Distinct cover images, ignoring corner-overlay avatars.
     *
     * Community playlists ship a single server-composed collage cover served at
     * several sizes, so after size-deduplication this usually holds exactly one
     * image - and the UI falls back to showing that collage full-size, which is
     * what YouTube Music itself renders.
     */
    fun JsonObject.thumbnailUrls(): List<String> = walkPrimaryObjects()
        .mapNotNull { it["thumbnails"] as? JsonArray }
        .flatMap { it.asSequence() }
        .mapNotNull { it as? JsonObject }
        .mapNotNull { thumb ->
            val url = thumb.string("url") ?: return@mapNotNull null
            url to (thumb.string("width")?.toIntOrNull() ?: 0)
        }
        .groupBy { (url, _) -> thumbnailSizeSuffix.replace(url, "").substringBefore("=") }
        .map { (_, variants) -> variants.maxBy { it.second }.first }
        .take(4)
        .toList()

    fun JsonObject.containsString(key: String, expected: String): Boolean =
        walkObjects().any { it.string(key) == expected }

    /**
     * The kind of page a run or item links to.
     *
     * This is what makes artist detection reliable: an artist run carries a browse
     * endpoint marked ARTIST, so it can be identified structurally instead of by
     * guessing at its position in the subtitle.
     */
    fun JsonObject.pageType(): String? = walkObjects()
        .mapNotNull { it.string("pageType") }
        .firstOrNull()

    fun JsonObject.browseId(): String? = walkObjects()
        .mapNotNull { (it["browseEndpoint"] as? JsonObject)?.string("browseId") }
        .firstOrNull()

    fun JsonObject.videoId(): String? =
        (this["playlistItemData"] as? JsonObject)?.string("videoId")
            ?: string("videoId")
            ?: walkObjects()
                .mapNotNull { (it["watchEndpoint"] as? JsonObject)?.string("videoId") }
                .firstOrNull()

    fun JsonObject.continuationToken(): String? = walkObjects()
        .mapNotNull { objectValue ->
            (objectValue["continuationCommand"] as? JsonObject)?.string("token")
                ?: (objectValue["nextContinuationData"] as? JsonObject)?.string("continuation")
                ?: (objectValue["reloadContinuationData"] as? JsonObject)?.string("continuation")
        }
        .firstOrNull()

    fun parseDurationMs(value: String): Long? {
        val parts = value.trim().split(':')
        if (parts.size !in 2..3) return null
        val numbers = parts.map { it.toLongOrNull() ?: return null }
        val seconds = if (numbers.size == 2) {
            numbers[0] * 60L + numbers[1]
        } else {
            numbers[0] * 3_600L + numbers[1] * 60L + numbers[2]
        }
        return seconds.takeIf { it > 0L }?.times(1000L)
    }

    // ---- Song parsing ---------------------------------------------------

    /**
     * Artist and album read from the subtitle runs of a song row.
     *
     * Runs that link to an artist or album page are trusted first; the text
     * heuristic is only a fallback for rows YouTube returns without endpoints.
     */
    data class SongCredits(
        val artistName: String?,
        val albumTitle: String?,
    )

    fun creditsFrom(runs: List<JsonObject>): SongCredits {
        val artists = runs
            .filter { it.pageType() == PAGE_TYPE_ARTIST }
            .mapNotNull { it.string("text")?.trim() }
            .filter { isMeaningful(it) }
            .distinct()

        val albumFromEndpoint = runs
            .firstOrNull { it.pageType() == PAGE_TYPE_ALBUM }
            ?.string("text")
            ?.trim()
            ?.takeIf { isMeaningful(it) }

        val plainValues = runs
            .mapNotNull { it.string("text")?.trim() }
            .filter { isMeaningful(it) }

        val artistName = artists.takeIf { it.isNotEmpty() }?.joinToString(", ")
            ?: plainValues.firstOrNull()

        val albumTitle = albumFromEndpoint
            ?: plainValues.firstOrNull { it != artistName }

        return SongCredits(artistName = artistName, albumTitle = albumTitle)
    }

    /**
     * A song row, or null when the renderer is not a playable song.
     *
     * Returning null for a missing title or performer is deliberate: a row with no
     * artist is not a song the user can recognise, and rendering it produced the
     * blank entries in the results list.
     */
    fun parseSongRow(renderer: JsonObject): Track? {
        val videoId = renderer.videoId()?.takeIf { it.isNotBlank() } ?: return null
        val columns = renderer["flexColumns"] as? JsonArray ?: return null

        val title = columns.firstOrNull()
            .columnRuns()
            .firstOrNull()
            ?.string("text")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !isPlaceholder(it) }
            ?: return null

        // Every column after the title can hold credits; YouTube moves the artist
        // between column 1 and 2 depending on the shelf.
        val creditRuns = columns.drop(1).flatMap { it.columnRuns() }
        val credits = creditsFrom(creditRuns)
        val artistName = credits.artistName ?: "Unknown artist"

        val durationMs = creditRuns
            .mapNotNull { it.string("text") }
            .asReversed()
            .firstNotNullOfOrNull(::parseDurationMs)
            ?: 0L

        return Track(
            id = "youtube:$videoId",
            title = title,
            artistName = artistName,
            // Blank rather than a stand-in string. The row hides an empty album,
            // whereas a filler value like "YouTube Music" looked like real data.
            albumTitle = credits.albumTitle.orEmpty(),
            durationMs = durationMs,
            isExplicit = renderer.containsString("iconType", EXPLICIT_BADGE),
            artworkUrl = renderer.thumbnailUrl(),
            externalUrl = "https://music.youtube.com/watch?v=$videoId",
            videoId = videoId,
        )
    }

    /** A song laid out as a card (`musicTwoRowItemRenderer`) rather than a row. */
    fun parseSongCard(renderer: JsonObject): Track? {
        val videoId = renderer.videoId()?.takeIf { it.isNotBlank() } ?: return null
        val title = renderer["title"].runText()?.takeIf {
            it.isNotEmpty() && !isPlaceholder(it)
        } ?: return null

        val subtitleRuns = renderer["subtitle"].runObjects()
        val credits = creditsFrom(subtitleRuns)
        val artistName = credits.artistName ?: "Unknown artist"
        val durationMs = subtitleRuns
            .mapNotNull { it.string("text") }
            .asReversed()
            .firstNotNullOfOrNull(::parseDurationMs)
            ?: 0L

        return Track(
            id = "youtube:$videoId",
            title = title,
            artistName = artistName,
            albumTitle = credits.albumTitle.orEmpty(),
            durationMs = durationMs,
            isExplicit = renderer.containsString("iconType", EXPLICIT_BADGE),
            artworkUrl = renderer.thumbnailUrl(),
            externalUrl = "https://music.youtube.com/watch?v=$videoId",
            videoId = videoId,
        )
    }
}
