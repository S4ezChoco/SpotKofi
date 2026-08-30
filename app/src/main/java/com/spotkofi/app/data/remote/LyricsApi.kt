package com.spotkofi.app.data.remote

import android.text.Html
import com.spotkofi.app.core.AppConstants
import com.spotkofi.app.data.local.LyricsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Runtime lyrics lookup.
 *
 * Providers are deliberately treated as a fallback chain rather than as mutually
 * exclusive endpoints. A provider can be selected as the first choice in Settings,
 * but a timeout, a missing video id, or a plain-only response must not make the
 * lyrics panel silently empty when another source can return timed lyrics.
 */
internal class LyricsApi(
    private val client: OkHttpClient = defaultLyricsClient(),
    private val youtubeSongInfoClient: YouTubeSongInfoClient = YouTubeSongInfoClient(client),
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
        videoId: String? = null,
        provider: LyricsProvider = LyricsProvider.Automatic,
    ): LyricsResult? = withContext(Dispatchers.IO) {
        if (title.isBlank() || artistName.isBlank()) return@withContext null

        // Keep a plain result while continuing: a later source may have the same
        // song with real timings, which is more useful than stopping at plain text.
        var plainFallback: LyricsResult? = null
        for (source in provider.lookupOrder()) {
            val result = runCatching {
                when (source) {
                    LyricsProvider.Automatic -> null
                    LyricsProvider.SimpMusic -> simpMusic(videoId)
                    LyricsProvider.LrcLib -> lrclib(
                        title = title,
                        artistName = artistName,
                        durationMs = durationMs,
                    )

                    LyricsProvider.BetterLyrics -> betterLyrics(
                        title = title,
                        artistName = artistName,
                        durationMs = durationMs,
                    )

                    LyricsProvider.YouTubeCaptions -> youtubeCaptions(videoId)
                    LyricsProvider.LyricsOvh -> lyricsOvh(title, artistName)
                }
            }.getOrNull() ?: continue

            if (!result.hasContent) continue
            if (result.instrumental || !result.synced.isNullOrBlank()) {
                return@withContext result
            }
            if (plainFallback == null) plainFallback = result
        }
        plainFallback
    }

    private fun LyricsProvider.lookupOrder(): List<LyricsProvider> {
        val fallbackOrder = listOf(
            LyricsProvider.SimpMusic,
            LyricsProvider.LrcLib,
            LyricsProvider.BetterLyrics,
            LyricsProvider.YouTubeCaptions,
            LyricsProvider.LyricsOvh,
        )
        return if (this == LyricsProvider.Automatic) {
            fallbackOrder
        } else {
            listOf(this) + fallbackOrder.filterNot { it == this }
        }
    }

    /** SimpMusic's exact video-id lookup, including its rich-sync response. */
    private fun simpMusic(videoId: String?): LyricsResult? {
        val id = videoId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val url = "https://api-lyrics.simpmusic.org".toHttpUrl().newBuilder()
            .addPathSegments("v1")
            .addPathSegment(id)
            .build()
        val body = execute(url.toString()) ?: return null

        val entries = runCatching {
            json.decodeFromString<SimpMusicEnvelope>(body).data
        }.getOrNull()
            ?: runCatching { json.decodeFromString<List<SimpMusicEntry>>(body) }.getOrNull()
            ?: return null
        val entry = entries.firstOrNull() ?: return null

        val synced = entry.syncedLyrics?.trim()?.takeIf { it.isNotEmpty() }
            ?: entry.richSyncLyrics?.let(::richSyncToLrc)
        val plain = entry.plainLyric.trim().takeIf { it.isNotEmpty() }
        val instrumental = entry.trackType.equals("instrumental", ignoreCase = true)
        if (!instrumental && synced == null && plain == null) return null

        return LyricsResult(
            plain = plain,
            synced = synced,
            instrumental = instrumental,
            providerName = LyricsProvider.SimpMusic.displayName,
        )
    }

    /**
     * LRCLIB search, rather than `/api/get` alone. Search results remain usable
     * when an album title differs from the catalog, and duration matching prevents
     * a live/remix result from being shown for the studio track.
     */
    private fun lrclib(
        title: String,
        artistName: String,
        durationMs: Long,
    ): LyricsResult? {
        val url = "https://lrclib.net".toHttpUrl().newBuilder()
            .addPathSegments("api/search")
            .addQueryParameter("q", "$artistName $title")
            .build()
        val body = execute(url.toString()) ?: return null
        val entries = runCatching {
            json.decodeFromString<List<LrcLibEntry>>(body)
        }.getOrNull().orEmpty()
        if (entries.isEmpty()) return null

        val targetSeconds = durationMs.takeIf { it > 0L }?.let { it / 1000f }
        val entry = if (targetSeconds == null) {
            entries.firstOrNull()
        } else {
            entries.minByOrNull { abs(it.duration - targetSeconds) }
                ?.takeIf { abs(it.duration - targetSeconds) <= DURATION_TOLERANCE_SECONDS }
        } ?: return null

        if (entry.instrumental) {
            return LyricsResult(
                plain = null,
                synced = null,
                instrumental = true,
                providerName = LyricsProvider.LrcLib.displayName,
            )
        }

        val plain = entry.plainLyrics?.trim()?.takeIf { it.isNotEmpty() }
        val synced = entry.syncedLyrics?.trim()?.takeIf { it.isNotEmpty() }
        if (plain == null && synced == null) return null
        return LyricsResult(
            plain = plain,
            synced = synced,
            instrumental = false,
            providerName = LyricsProvider.LrcLib.displayName,
        )
    }

    /** BetterLyrics returns TTML; convert its timed paragraphs into line-synced LRC. */
    private fun betterLyrics(
        title: String,
        artistName: String,
        durationMs: Long,
    ): LyricsResult? {
        val url = "https://lyrics-api.boidu.dev".toHttpUrl().newBuilder()
            .addPathSegment("getLyrics")
            .addQueryParameter("s", title)
            .addQueryParameter("a", artistName)
            .addQueryParameter("d", durationMs.takeIf { it > 0L }?.div(1000L)?.toString())
            .build()
        val body = execute(url.toString()) ?: return null
        val ttml = runCatching { json.decodeFromString<BetterLyricsResponse>(body).ttml }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val synced = ttmlToLrc(ttml) ?: return null
        return LyricsResult(
            plain = null,
            synced = synced,
            instrumental = false,
            providerName = LyricsProvider.BetterLyrics.displayName,
        )
    }

    /** YouTube timed captions are the final synced fallback when a video id exists. */
    private suspend fun youtubeCaptions(videoId: String?): LyricsResult? {
        val id = videoId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val transcript = youtubeSongInfoClient.captions(id) ?: return null
        val synced = youtubeTranscriptToLrc(transcript) ?: return null
        return LyricsResult(
            plain = null,
            synced = synced,
            instrumental = false,
            providerName = LyricsProvider.YouTubeCaptions.displayName,
        )
    }

    /** Lyrics.ovh remains useful only as the final unsynchronised fallback. */
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

    /** Turn the reference's rich word-timed format into line-timed text. */
    private fun richSyncToLrc(raw: String): String? {
        val lineStamp = Regex("""\[(\d{1,3}:\d{1,2}(?:[.:,]\d{1,3})?)\]""")
        val wordStamp = Regex("""<\d{1,3}:\d{1,2}(?:[.:,]\d{1,3})?>""")
        val lines = raw.lines().flatMap { line ->
            val stamps = lineStamp.findAll(line).toList()
            if (stamps.isEmpty()) return@flatMap emptyList()
            val text = line.substring(stamps.last().range.last + 1)
                .replace(wordStamp, "")
                .replace(Regex("""^v\d+:"""), "")
                .trim()
            if (text.isEmpty()) return@flatMap emptyList()
            stamps.map { "[${it.groupValues[1]}] $text" }
        }
        return lines.joinToString("\n").takeIf { it.isNotBlank() }
    }

    /** Parse BetterLyrics TTML without pulling an XML dependency into the app. */
    private fun ttmlToLrc(raw: String): String? {
        val paragraphRegex = Regex(
            """<p\b([^>]*)>([\\s\\S]*?)</p\\s*>""",
            RegexOption.IGNORE_CASE,
        )
        val spanRegex = Regex(
            """<span\b([^>]*)>([\\s\\S]*?)</span\\s*>""",
            RegexOption.IGNORE_CASE,
        )
        val lines = paragraphRegex.findAll(raw).mapNotNull { match ->
            val start = attribute(match.groupValues[1], "begin")?.let(::parseTimedValue)
                ?: return@mapNotNull null
            val inner = match.groupValues[2]
            val spans = spanRegex.findAll(inner).toList()
            val textSource = if (spans.isEmpty()) {
                inner
            } else {
                spans.joinToString(" ") { it.groupValues[2] }
            }
            val text = cleanMarkup(textSource)
            if (text.isBlank()) return@mapNotNull null
            "${formatLrcTimestamp(start)} $text"
        }.toList()
        return lines.joinToString("\n").takeIf { it.isNotBlank() }
    }

    /** Parse `M:SS.mmm`, `H:MM:SS`, decimal seconds, and `ms` TTML values. */
    private fun parseTimedValue(raw: String): Long? {
        val value = raw.trim()
        if (value.endsWith("ms", ignoreCase = true)) {
            return value.dropLast(2).toDoubleOrNull()?.roundToLong()
        }
        if (value.endsWith("s", ignoreCase = true) && !value.contains(':')) {
            return value.dropLast(1).toDoubleOrNull()?.times(1_000.0)?.roundToLong()
        }

        val parts = value.split(':')
        val secondsPart: String
        val hourMs: Long
        val minuteMs: Long
        when (parts.size) {
            3 -> {
                hourMs = (parts[0].toLongOrNull() ?: return null) * 3_600_000L
                minuteMs = (parts[1].toLongOrNull() ?: return null) * 60_000L
                secondsPart = parts[2]
            }

            2 -> {
                hourMs = 0L
                minuteMs = (parts[0].toLongOrNull() ?: return null) * 60_000L
                secondsPart = parts[1]
            }

            else -> return value.toDoubleOrNull()?.times(1_000.0)?.roundToLong()
        }
        val secondsMs = secondsPart.toDoubleOrNull()
            ?.times(1_000.0)
            ?.roundToLong()
            ?: return null
        return hourMs + minuteMs + secondsMs
    }

    private fun attribute(attributes: String, name: String): String? = Regex(
        """(?:^|\\s)$name\\s*=\\s*[\"']([^\"']+)[\"']""",
        RegexOption.IGNORE_CASE,
    ).find(attributes)?.groupValues?.getOrNull(1)

    private fun cleanMarkup(raw: String): String = Html.fromHtml(
        raw
            .replace(Regex("""<br\\s*/?>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<[^>]+>"""), " "),
        Html.FROM_HTML_MODE_LEGACY,
    ).toString()
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun youtubeTranscriptToLrc(raw: String): String? {
        val textRegex = Regex(
            """<text\\b([^>]*)>([\\s\\S]*?)</text\\s*>""",
            RegexOption.IGNORE_CASE,
        )
        val lines = textRegex.findAll(raw).mapNotNull { match ->
            val start = attribute(match.groupValues[1], "start")
                ?.toDoubleOrNull()
                ?.times(1_000.0)
                ?.roundToLong()
                ?: return@mapNotNull null
            val text = cleanMarkup(match.groupValues[2])
            if (text.isBlank()) return@mapNotNull null
            "${formatLrcTimestamp(start)} $text"
        }.toList()
        return lines.joinToString("\n").takeIf { it.isNotBlank() }
    }

    private fun formatLrcTimestamp(timestampMs: Long): String {
        val minutes = timestampMs / 60_000L
        val seconds = (timestampMs % 60_000L) / 1_000L
        val centiseconds = (timestampMs % 1_000L) / 10L
        return String.format(
            Locale.US,
            "[%02d:%02d.%02d]",
            minutes,
            seconds,
            centiseconds,
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
        const val DURATION_TOLERANCE_SECONDS = 10f

        fun defaultLyricsClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

/** What one provider had for a track. */
internal data class LyricsResult(
    val plain: String?,
    val synced: String?,
    val instrumental: Boolean,
    val providerName: String,
) {
    val hasContent: Boolean
        get() = instrumental || !plain.isNullOrBlank() || !synced.isNullOrBlank()
}

@Serializable
private data class SimpMusicEnvelope(
    val data: List<SimpMusicEntry>? = null,
)

@Serializable
private data class SimpMusicEntry(
    val plainLyric: String = "",
    val syncedLyrics: String? = null,
    val richSyncLyrics: String? = null,
    val trackType: String? = null,
)

@Serializable
private data class LrcLibEntry(
    val duration: Float = 0f,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

@Serializable
private data class BetterLyricsResponse(
    val ttml: String = "",
)

@Serializable
private data class LyricsOvhResponse(
    val lyrics: String? = null,
)
