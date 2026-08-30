package com.spotkofi.app.data.remote

import com.spotkofi.app.core.AppConstants
import com.spotkofi.app.data.local.LyricsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Runtime lyrics lookup through the provider selected in Settings. */
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
        provider: LyricsProvider = LyricsProvider.LrcLib,
    ): LyricsResult? = withContext(Dispatchers.IO) {
        if (title.isBlank() || artistName.isBlank()) return@withContext null
        when (provider) {
            LyricsProvider.LrcLib -> lrclib(
                title = title,
                artistName = artistName,
                albumTitle = albumTitle,
                durationMs = durationMs,
            )

            LyricsProvider.LyricsOvh -> lyricsOvh(title = title, artistName = artistName)
        }
    }

    private fun lrclib(
        title: String,
        artistName: String,
        albumTitle: String?,
        durationMs: Long,
    ): LyricsResult? {
        val url = "https://lrclib.net".toHttpUrl().newBuilder()
            .addPathSegments("api/get")
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artistName)
            .apply {
                albumTitle?.takeIf { it.isNotBlank() }?.let {
                    addQueryParameter("album_name", it)
                }
                durationMs.takeIf { it > 0L }?.let {
                    addQueryParameter("duration", (it / 1000L).toString())
                }
            }
            .build()

        val body = execute(url.toString()) ?: return null
        val payload = runCatching { json.decodeFromString<LrcLibResponse>(body) }.getOrNull()
            ?: return null

        if (payload.instrumental) {
            return LyricsResult(
                plain = null,
                synced = null,
                instrumental = true,
                providerName = LyricsProvider.LrcLib.displayName,
            )
        }

        val plain = payload.plainLyrics?.trim()?.takeIf { it.isNotEmpty() }
        val synced = payload.syncedLyrics?.trim()?.takeIf { it.isNotEmpty() }
        if (plain == null && synced == null) return null
        return LyricsResult(
            plain = plain,
            synced = synced,
            instrumental = false,
            providerName = LyricsProvider.LrcLib.displayName,
        )
    }

    /** Lyrics.ovh is intentionally a fallback source: it provides plain text, not LRC timing. */
    private fun lyricsOvh(title: String, artistName: String): LyricsResult? {
        val url = "https://api.lyrics.ovh".toHttpUrl().newBuilder()
            .addPathSegment("v1")
            .addPathSegment(artistName)
            .addPathSegment(title)
            .build()
        val body = execute(url.toString()) ?: return null
        val lyrics = runCatching { json.decodeFromString<LyricsOvhResponse>(body).lyrics }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return LyricsResult(
            plain = lyrics,
            synced = null,
            instrumental = false,
            providerName = LyricsProvider.LyricsOvh.displayName,
        )
    }

    private fun execute(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", AppConstants.USER_AGENT)
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body.string()
            }
        }.getOrNull()
    }

    private companion object {
        fun defaultLyricsClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

/** What the selected lyrics provider had for a track. */
internal data class LyricsResult(
    val plain: String?,
    val synced: String?,
    val instrumental: Boolean,
    val providerName: String,
)

@Serializable
private data class LrcLibResponse(
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    val instrumental: Boolean = false,
)

@Serializable
private data class LyricsOvhResponse(
    val lyrics: String? = null,
)
