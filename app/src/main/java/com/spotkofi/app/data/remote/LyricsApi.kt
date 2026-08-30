package com.spotkofi.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.spotkofi.app.core.AppConstants
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Lyrics lookup, backed by the public LRCLIB catalog.
 *
 * Nothing is bundled with the app: lyrics are looked up at runtime for the track
 * that is playing and rendered exactly as the provider returns them. Missing
 * lyrics stay missing rather than being filled with anything invented, which is
 * why every failure path here returns null instead of a placeholder.
 *
 * Both forms are kept when the provider has them. The synced form carries
 * `[mm:ss.xx]` stamps, which is what a highlight-as-it-plays view needs; the
 * plain form is the fallback for tracks that were only ever transcribed.
 */
internal class LyricsApi(
    private val client: OkHttpClient = defaultLyricsClient(),
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun lyrics(
        title: String,
        artistName: String,
        albumTitle: String?,
        durationMs: Long,
    ): LyricsResult? = withContext(Dispatchers.IO) {
        if (title.isBlank() || artistName.isBlank()) return@withContext null

        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegments("api/get")
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artistName)
            .apply {
                albumTitle?.takeIf { it.isNotBlank() }?.let {
                    addQueryParameter("album_name", it)
                }
                // The provider matches on duration in whole seconds when it is
                // known, which is what keeps a cover from resolving to the
                // original's timings.
                durationMs.takeIf { it > 0L }?.let {
                    addQueryParameter("duration", (it / 1000L).toString())
                }
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()

        val body = runCatching {
            client.newCall(request).execute().use { response ->
                // 404 is the normal "no lyrics for this track" answer, not a fault.
                if (!response.isSuccessful) return@use null
                response.body.string()
            }
        }.getOrNull() ?: return@withContext null

        val payload = runCatching {
            json.decodeFromString<LrcLibResponse>(body)
        }.getOrNull() ?: return@withContext null

        if (payload.instrumental) {
            return@withContext LyricsResult(plain = null, synced = null, instrumental = true)
        }

        val plain = payload.plainLyrics?.trim()?.takeIf { it.isNotEmpty() }
        val synced = payload.syncedLyrics?.trim()?.takeIf { it.isNotEmpty() }
        if (plain == null && synced == null) return@withContext null

        LyricsResult(plain = plain, synced = synced, instrumental = false)
    }

    private companion object {
        const val BASE_URL = "https://lrclib.net"

        /**
         * The provider asks clients to identify themselves, so this is the real
         * product string rather than a browser impersonation.
         */
        val USER_AGENT: String = AppConstants.USER_AGENT

        fun defaultLyricsClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

/** What the lyrics provider had for a track. */
internal data class LyricsResult(
    val plain: String?,
    val synced: String?,
    /** True when the provider states the recording has no vocals. */
    val instrumental: Boolean,
)

@Serializable
private data class LrcLibResponse(
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    val instrumental: Boolean = false,
)
