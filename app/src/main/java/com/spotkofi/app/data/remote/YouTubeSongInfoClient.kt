package com.spotkofi.app.data.remote

import com.spotkofi.app.core.AppConstants
import com.spotkofi.app.data.model.TrackCredits
import com.spotkofi.app.data.remote.Innertube.string
import com.spotkofi.app.data.remote.Innertube.walkObjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

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

    suspend fun credits(videoId: String, country: String = "US"): TrackCredits? =
        withContext(Dispatchers.IO) {
            if (videoId.isBlank()) return@withContext null

            val response = Innertube.post(client, PLAYER_ENDPOINT, playerBody(videoId, country))
                ?: return@withContext null
            val root = parseRoot(response) ?: return@withContext null

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

    /**
     * Fetches the first usable YouTube caption track. The raw transcript is kept
     * as XML because the lyrics layer also understands the provider timestamps
     * and can turn them into the same LRC model as every other source.
     */
    suspend fun captions(
        videoId: String,
        preferredLanguage: String = "en",
    ): String? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null

        val response = Innertube.post(client, PLAYER_ENDPOINT, playerBody(videoId))
            ?: return@withContext null
        val root = parseRoot(response) ?: return@withContext null
        val renderer = root.walkObjects()
            .mapNotNull { it["playerCaptionsTracklistRenderer"] as? JsonObject }
            .firstOrNull()
            ?: return@withContext null
        val tracks = renderer["captionTracks"] as? JsonArray ?: return@withContext null

        data class CaptionTrack(
            val url: String,
            val languageCode: String?,
            val kind: String?,
        )

        val candidates = tracks.mapNotNull { element ->
            val track = element as? JsonObject ?: return@mapNotNull null
            CaptionTrack(
                url = track.string("baseUrl") ?: return@mapNotNull null,
                languageCode = track.string("languageCode"),
                kind = track.string("kind"),
            )
        }
        val preferred = preferredLanguage.trim().lowercase()
        val selected = candidates.minByOrNull { track ->
            when {
                track.languageCode.equals(preferred, ignoreCase = true) -> 0
                track.languageCode?.lowercase()?.startsWith("$preferred-") == true -> 1
                track.kind == "asr" -> 3
                else -> 2
            }
        } ?: return@withContext null

        val request = Request.Builder()
            .url(selected.url)
            .header("Accept", "text/xml, application/xml, application/json")
            .header("User-Agent", AppConstants.USER_AGENT)
            .build()
        runCatching {
            client.newCall(request).execute().use { captionResponse ->
                if (!captionResponse.isSuccessful) null else captionResponse.body.string()
            }
        }.getOrNull()
    }

    private fun parseRoot(response: String): JsonObject? = runCatching {
        Innertube.json.parseToJsonElement(response) as? JsonObject
    }.getOrNull()

    private fun playerBody(videoId: String, country: String = "US"): String = buildJsonObject {
        put("context", playerContext(country))
        put("videoId", JsonPrimitive(videoId))
        // Without these the endpoint refuses age-gated and region-flagged items
        // outright, and both metadata and captions disappear for those songs.
        put("contentCheckOk", JsonPrimitive(true))
        put("racyCheckOk", JsonPrimitive(true))
    }.toString()

    private fun playerContext(country: String): JsonObject = buildJsonObject {
        put(
            "client",
            buildJsonObject {
                put("clientName", JsonPrimitive("ANDROID_MUSIC"))
                put("clientVersion", JsonPrimitive("6.33.52"))
                put("androidSdkVersion", JsonPrimitive(30))
                put("hl", JsonPrimitive("en"))
                put("gl", JsonPrimitive(normalizeCountry(country)))
            },
        )
    }

    private fun normalizeCountry(country: String): String = country
        .trim()
        .uppercase()
        .take(2)
        .ifBlank { "US" }

    private companion object {
        const val PLAYER_ENDPOINT = "https://music.youtube.com/youtubei/v1/player"
    }
}
