package com.spotkofi.app.data.remote

import com.spotkofi.app.data.model.HomeSection
import com.spotkofi.app.data.model.HomeTab
import com.spotkofi.app.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * Anonymous YouTube Music browse feed modeled after the reference app's
 * Innertube layer. It keeps provider identity and metadata together, so every
 * Home row points at the video ID that produced that row.
 */
internal class YouTubeMusicHomeClient(
    private val client: OkHttpClient = defaultClient(),
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun sections(tab: HomeTab): List<HomeSection> = withContext(Dispatchers.IO) {
        val browseIds = when (tab) {
            HomeTab.All -> listOf(HOME_BROWSE_ID, NEW_RELEASES_BROWSE_ID, CHARTS_BROWSE_ID)
            HomeTab.Music -> listOf(HOME_BROWSE_ID, CHARTS_BROWSE_ID)
            HomeTab.Podcasts -> emptyList()
        }
        if (browseIds.isEmpty()) return@withContext emptyList()

        browseIds.flatMapIndexed { index, browseId ->
            requestPages(browseId)
                .mapIndexed { shelfIndex, shelf ->
                    shelf.copy(
                        id = "youtube_${tab.name.lowercase()}_${index}_$shelfIndex",
                    )
                }
        }
            .distinctBy { it.title.lowercase() to (it as HomeSection.Songs).items.map(Track::id).toSet() }
            .take(MAX_SHELVES)
    }

    private fun requestPages(browseId: String): List<HomeSection.Songs> {
        val sections = LinkedHashMap<String, LinkedHashMap<String, Track>>()
        var response = requestBrowse(browseId = browseId, continuation = null)
        var page = 0

        while (response != null && page < MAX_PAGES) {
            val root = runCatching { json.parseToJsonElement(response) }.getOrNull() ?: break
            parseShelves(root).forEach { shelf ->
                val tracks = sections.getOrPut(shelf.title) { LinkedHashMap() }
                shelf.items.forEach { tracks.putIfAbsent(it.id, it) }
            }
            val continuation = continuationToken(root) ?: break
            response = requestBrowse(browseId = null, continuation = continuation)
            page++
        }

        return sections.mapNotNull { (title, tracks) ->
            tracks.values.toList()
                .take(MAX_TRACKS_PER_SHELF)
                .takeIf { it.isNotEmpty() }
                ?.let { HomeSection.Songs("youtube_${title.hashCode()}", title, it) }
        }
    }

    private fun parseShelves(root: JsonElement): List<ParsedShelf> =
        root.walkObjects()
            .mapNotNull { objectValue ->
                val carousel = objectValue["musicCarouselShelfRenderer"] as? JsonObject
                val shelf = objectValue["musicShelfRenderer"] as? JsonObject
                when {
                    carousel != null -> parseShelf(carousel)
                    shelf != null -> parseShelf(shelf)
                    else -> null
                }
            }
            .filter { it.items.isNotEmpty() }
            .toList()

    private fun parseShelf(renderer: JsonObject): ParsedShelf? {
        val title = shelfTitle(renderer)?.takeIf { it.isNotBlank() } ?: return null
        val contents = renderer["contents"] as? JsonArray ?: return null
        val tracks = contents
            .flatMap { item ->
                item.walkObjects()
                    .mapNotNull { objectValue ->
                        parseResponsiveSong(objectValue) ?: parseTwoRowSong(objectValue)
                    }
                    .toList()
            }
            .distinctBy { it.id }
        return ParsedShelf(title, tracks)
    }

    private fun shelfTitle(renderer: JsonObject): String? {
        val header = renderer["header"] as? JsonObject
        val headerTitle = header?.walkObjects()
            ?.mapNotNull { it["title"]?.runText() }
            ?.firstOrNull()
        if (!headerTitle.isNullOrBlank()) return headerTitle
        return renderer["title"]?.runText()
    }

    private fun parseResponsiveSong(renderer: JsonObject): Track? {
        val item = renderer["musicResponsiveListItemRenderer"] as? JsonObject ?: return null
        val videoId = (item["playlistItemData"] as? JsonObject)?.string("videoId")
            ?: item.string("videoId")
            ?: item.walkObjects()
                .mapNotNull { objectValue ->
                    (objectValue["watchEndpoint"] as? JsonObject)?.string("videoId")
                }
                .firstOrNull()
            ?: return null
        val columns = item["flexColumns"] as? JsonArray ?: return null
        val title = columns.firstOrNull().columnRuns().firstOrNull()?.string("text") ?: return null
        val values = columns.getOrNull(1)
            .columnRuns()
            .mapNotNull { it.string("text")?.trim() }
            .filter { it.isNotBlank() && it !in setOf("•", "·", "|") }
        val duration = values.asReversed().firstNotNullOfOrNull(::parseDurationMs) ?: 0L
        val artist = values.firstOrNull { parseDurationMs(it) == null } ?: "YouTube Music"
        val album = values.drop(1).firstOrNull { parseDurationMs(it) == null } ?: "YouTube Music"
        return Track(
            id = "youtube:$videoId",
            title = title.trim(),
            artistName = artist,
            albumTitle = album,
            durationMs = duration,
            artworkUrl = item.thumbnailUrl(),
            externalUrl = "https://music.youtube.com/watch?v=$videoId",
            videoId = videoId,
        )
    }

    private fun parseTwoRowSong(renderer: JsonObject): Track? {
        val item = renderer["musicTwoRowItemRenderer"] as? JsonObject ?: return null
        val endpoint = item.walkObjects()
            .mapNotNull { objectValue ->
                (objectValue["watchEndpoint"] as? JsonObject)?.string("videoId")
            }
            .firstOrNull()
            ?: return null
        val title = item["title"]?.runText()?.takeIf { it.isNotBlank() } ?: return null
        val subtitleValues = item["subtitle"]?.runTexts().orEmpty()
        val duration = subtitleValues.asReversed().firstNotNullOfOrNull(::parseDurationMs) ?: 0L
        val artist = subtitleValues.firstOrNull { parseDurationMs(it) == null } ?: "YouTube Music"
        val album = subtitleValues.drop(1).firstOrNull { parseDurationMs(it) == null } ?: "YouTube Music"
        return Track(
            id = "youtube:$endpoint",
            title = title,
            artistName = artist,
            albumTitle = album,
            durationMs = duration,
            artworkUrl = item.thumbnailUrl(),
            externalUrl = "https://music.youtube.com/watch?v=$endpoint",
            videoId = endpoint,
        )
    }

    private fun requestBrowse(browseId: String?, continuation: String?): String? {
        val body = buildJsonObject {
            put("context", buildJsonObject {
                put("client", buildJsonObject {
                    put("clientName", JsonPrimitive(CLIENT_NAME))
                    put("clientVersion", JsonPrimitive(CLIENT_VERSION))
                    put("hl", JsonPrimitive("en"))
                    put("gl", JsonPrimitive("US"))
                })
            })
            if (continuation != null) {
                put("continuation", JsonPrimitive(continuation))
            } else {
                put("browseId", JsonPrimitive(browseId.orEmpty()))
            }
        }.toString()
        val url = ENDPOINT.toHttpUrl().newBuilder()
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

    private fun continuationToken(root: JsonElement): String? = root.walkObjects()
        .mapNotNull { objectValue ->
            (objectValue["continuationCommand"] as? JsonObject)?.string("token")
                ?: (objectValue["nextContinuationData"] as? JsonObject)?.string("continuation")
                ?: (objectValue["reloadContinuationData"] as? JsonObject)?.string("continuation")
        }
        .firstOrNull()

    private fun JsonElement?.runText(): String? = runTexts().firstOrNull()

    private fun JsonElement?.runTexts(): List<String> =
        (this as? JsonObject)?.get("runs")
            .let { it as? JsonArray }
            ?.mapNotNull { (it as? JsonObject)?.string("text") }
            .orEmpty()

    private fun JsonObject.thumbnailUrl(): String? = walkObjects()
        .mapNotNull { it["thumbnails"] as? JsonArray }
        .flatMap { it.asSequence() }
        .mapNotNull { it as? JsonObject }
        .mapNotNull { it.string("url") }
        .lastOrNull()

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonElement?.columnRuns(): List<JsonObject> {
        val column = this as? JsonObject ?: return emptyList()
        val renderer = column["musicResponsiveListItemFlexColumnRenderer"] as? JsonObject
            ?: return emptyList()
        return renderer["text"]?.let { text ->
            (text as? JsonObject)?.get("runs") as? JsonArray
        }?.mapNotNull { it as? JsonObject }.orEmpty()
    }

    private fun JsonElement.walkObjects(): Sequence<JsonObject> = sequence {
        when (val element = this@walkObjects) {
            is JsonObject -> {
                yield(element)
                element.values.forEach { child -> yieldAll(child.walkObjects()) }
            }
            is JsonArray -> element.forEach { child -> yieldAll(child.walkObjects()) }
            else -> Unit
        }
    }

    private fun parseDurationMs(value: String): Long? {
        val parts = value.split(':').map { it.toLongOrNull() ?: return null }
        if (parts.size !in 2..3) return null
        val seconds = if (parts.size == 2) parts[0] * 60L + parts[1]
        else parts[0] * 3_600L + parts[1] * 60L + parts[2]
        return seconds.takeIf { it > 0L }?.times(1000L)
    }

    private data class ParsedShelf(val title: String, val items: List<Track>)

    private companion object {
        const val HOME_BROWSE_ID = "FEmusic_home"
        const val NEW_RELEASES_BROWSE_ID = "FEmusic_new_releases"
        const val CHARTS_BROWSE_ID = "FEmusic_charts"
        const val CLIENT_NAME = "WEB_REMIX"
        const val CLIENT_VERSION = "1.20241231.01.00"
        const val API_KEY = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI"
        const val ENDPOINT = "https://music.youtube.com/youtubei/v1/browse"
        const val MAX_PAGES = 2
        const val MAX_SHELVES = 12
        const val MAX_TRACKS_PER_SHELF = 12
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
