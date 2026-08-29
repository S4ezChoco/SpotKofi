package com.spotkofi.app.data.remote

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
 * A song-only YouTube Music search result. All fields come from the same
 * musicResponsiveListItemRenderer, so a video ID can never be paired with
 * unrelated catalog metadata merely because it was the first global match.
 */
internal data class YouTubeSongCandidate(
    val videoId: String,
    val title: String,
    val artistName: String,
    val albumTitle: String? = null,
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val isExplicit: Boolean = false,
)

/**
 * Structured YouTube Music search modeled after the song-filtered path used by
 * InnerTune and SimpMusic. It follows a small number of continuations so the
 * search screen gets a useful list instead of only the first hit.
 */
internal class YouTubeMusicSearchClient(
    private val client: OkHttpClient = defaultClient(),
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun searchSongs(query: String, limit: Int = DEFAULT_LIMIT): List<YouTubeSongCandidate> =
        withContext(Dispatchers.IO) {
            val term = query.trim()
            if (term.isEmpty()) return@withContext emptyList()

            val results = LinkedHashMap<String, YouTubeSongCandidate>()
            var responseText = requestSearch(term, continuation = null)
            var page = 0

            while (responseText != null && page < MAX_PAGES && results.size < limit) {
                val root = runCatching { json.parseToJsonElement(responseText) }.getOrNull()
                    ?: break
                parseSongs(root).forEach { candidate ->
                    if (results.size < limit) results.putIfAbsent(candidate.videoId, candidate)
                }
                if (results.size >= limit) break

                val continuation = continuationToken(root) ?: break
                responseText = requestSearch(query = null, continuation = continuation)
                page++
            }

            results.values.take(limit)
        }

    private fun requestSearch(query: String?, continuation: String?): String? {
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
                put("query", JsonPrimitive(query.orEmpty()))
                put("params", JsonPrimitive(SONG_FILTER))
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

    private fun parseSongs(root: JsonElement): List<YouTubeSongCandidate> =
        root.walkObjects()
            .mapNotNull { it["musicResponsiveListItemRenderer"] as? JsonObject }
            .mapNotNull(::parseSongRenderer)
            .toList()

    private fun parseSongRenderer(renderer: JsonObject): YouTubeSongCandidate? {
        val videoId = (renderer["playlistItemData"] as? JsonObject)
            ?.string("videoId")
            ?: renderer.string("videoId")
            ?: return null

        val columns = renderer["flexColumns"] as? JsonArray ?: return null
        val title = columns.firstOrNull()
            .columnRuns()
            .firstOrNull()
            ?.string("text")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val secondaryValues = columns.getOrNull(1)
            .columnRuns()
            .mapNotNull { it.string("text")?.trim() }
            .filterNot(::isSeparator)
            .filter { it.isNotEmpty() }
        val durationMs = secondaryValues.asReversed()
            .firstNotNullOfOrNull(::parseDurationMs)
            ?: 0L
        val artistName = secondaryValues
            .firstOrNull { parseDurationMs(it) == null }
            ?: return null
        val albumTitle = secondaryValues
            .drop(1)
            .firstOrNull { parseDurationMs(it) == null }

        return YouTubeSongCandidate(
            videoId = videoId,
            title = title,
            artistName = artistName,
            albumTitle = albumTitle,
            durationMs = durationMs,
            artworkUrl = renderer.thumbnailUrl(),
            isExplicit = renderer.containsString("iconType", EXPLICIT_BADGE),
        )
    }

    private fun continuationToken(root: JsonElement): String? = root.walkObjects()
        .mapNotNull { objectValue ->
            (objectValue["nextContinuationData"] as? JsonObject)
                ?.string("continuation")
                ?: (objectValue["continuationCommand"] as? JsonObject)
                    ?.string("token")
        }
        .firstOrNull()

    private fun JsonElement?.columnRuns(): List<JsonObject> {
        val column = this as? JsonObject ?: return emptyList()
        val renderer = column["musicResponsiveListItemFlexColumnRenderer"] as? JsonObject
            ?: return emptyList()
        val text = renderer["text"] as? JsonObject ?: return emptyList()
        return (text["runs"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    }

    private fun JsonObject.thumbnailUrl(): String? = walkObjects()
        .mapNotNull { it["thumbnails"] as? JsonArray }
        .flatMap { it.asSequence() }
        .mapNotNull { it as? JsonObject }
        .mapNotNull { it.string("url") }
        .lastOrNull()

    private fun JsonObject.containsString(key: String, expected: String): Boolean =
        walkObjects().any { it.string(key) == expected }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

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
        val seconds = if (parts.size == 2) {
            parts[0] * 60L + parts[1]
        } else {
            parts[0] * 3_600L + parts[1] * 60L + parts[2]
        }
        return seconds.takeIf { it > 0L }?.times(1000L)
    }

    private fun isSeparator(value: String): Boolean =
        value == "•" || value == "·" || value == "|"

    private companion object {
        const val DEFAULT_LIMIT = 25
        const val MAX_PAGES = 3
        const val CLIENT_NAME = "WEB_REMIX"
        const val CLIENT_VERSION = "1.20241231.01.00"
        const val SONG_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        const val API_KEY = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI"
        const val ENDPOINT = "https://music.youtube.com/youtubei/v1/search"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        const val EXPLICIT_BADGE = "MUSIC_EXPLICIT_BADGE"
    }
}
