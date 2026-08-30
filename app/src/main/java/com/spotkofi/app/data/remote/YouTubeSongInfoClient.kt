package com.spotkofi.app.data.remote

import com.spotkofi.app.data.model.TrackCredits
import com.spotkofi.app.data.remote.Innertube.string
import com.spotkofi.app.data.remote.Innertube.walkObjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient

/**
 * Per-song information published alongside a recording.
 *
 * This is the honest ceiling of what is available: the provider exposes the
 * uploading channel, a play count, a publish date and a description. It does not
 * expose songwriters, producers, labels or ISRCs, so those are not modelled and
 * not shown - a credits panel that invents a songwriter is worse than one that
 * admits it only knows the channel.
 */
internal class YouTubeSongInfoClient(
    private val client: OkHttpClient = Innertube.defaultClient(),
) {

    suspend fun credits(videoId: String): TrackCredits? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null

        val body = buildJsonObject {
            put("context", Innertube.json.parseToJsonElement(CONTEXT_BODY))
            put("videoId", JsonPrimitive(videoId))
            // Without these the endpoint refuses age-gated and region-flagged items
            // outright, and the panel would be empty for exactly the songs whose
            // details are most worth showing.
            put("contentCheckOk", JsonPrimitive(true))
            put("racyCheckOk", JsonPrimitive(true))
        }.toString()

        val response = Innertube.post(client, PLAYER_ENDPOINT, body) ?: return@withContext null
        val root = runCatching {
            Innertube.json.parseToJsonElement(response) as? JsonObject
        }.getOrNull() ?: return@withContext null

        val details = root.walkObjects()
            .firstOrNull { it.containsKey("videoDetails") }
            ?.get("videoDetails") as? JsonObject

        val microformat = root.walkObjects()
            .mapNotNull { it["playerMicroformatRenderer"] as? JsonObject }
            .firstOrNull()

        val channel = details?.string("author")?.trim()?.takeIf { it.isNotEmpty() }
        val plays = details?.string("viewCount")?.trim()?.toLongOrNull()
        val description = details?.string("shortDescription")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val published = microformat?.string("publishDate")
            ?: microformat?.string("uploadDate")

        // Nothing usable came back; the caller shows what it already knew instead of
        // an empty panel.
        if (channel == null && plays == null && description == null && published == null) {
            return@withContext null
        }

        TrackCredits(
            channelName = channel,
            plays = plays,
            publishedOn = published?.take(10),
            description = description,
        )
    }

    private companion object {
        const val PLAYER_ENDPOINT = "https://music.youtube.com/youtubei/v1/player"

        /**
         * The player endpoint wants an Android-style client for a bare metadata
         * read; the web client answers with a challenge for the same request.
         */
        val CONTEXT_BODY = """
            {
              "client": {
                "clientName": "ANDROID_MUSIC",
                "clientVersion": "6.33.52",
                "androidSdkVersion": 30,
                "hl": "en",
                "gl": "US"
              }
            }
        """.trimIndent()
    }
}
