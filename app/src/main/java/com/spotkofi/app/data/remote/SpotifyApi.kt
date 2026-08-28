package com.spotkofi.app.data.remote

import com.spotkofi.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Optional Spotify Web API client using a caller-supplied access token. */
internal class SpotifyApi(
    private val accessToken: String = BuildConfig.SPOTIFY_ACCESS_TOKEN,
    private val client: OkHttpClient = defaultClient(),
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val market = Locale.getDefault().country
        .uppercase(Locale.ROOT)
        .takeIf { it.length == 2 && it.all(Char::isLetter) }
        ?: "US"

    val isConfigured: Boolean get() = accessToken.isNotBlank()

    /**
     * Matches the iTunes track in Spotify, then asks Spotify for artist metadata
     * and recommendations. A missing/expired token is an optional enrichment
     * failure and never prevents iTunes search from working.
     */
    suspend fun enrich(title: String, artistName: String): SpotifyEnrichment? {
        if (!isConfigured) return null
        val matchedTrack = searchTrack(title, artistName) ?: return null
        val spotifyArtist = matchedTrack.artists.firstOrNull()?.id?.let { artistId ->
            try {
                get<SpotifyArtist>(url("artists/$artistId"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        val recommendations = try {
            get<SpotifyRecommendationsResponse>(
                url(
                    "recommendations",
                    "seed_tracks" to matchedTrack.id,
                    "limit" to "10",
                    "market" to market,
                ),
            ).tracks
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }

        return SpotifyEnrichment(
            artistName = spotifyArtist?.name ?: matchedTrack.artists.firstOrNull()?.name,
            artistGenre = spotifyArtist?.genres?.firstOrNull(),
            recommendations = recommendations,
        )
    }

    private suspend fun searchTrack(title: String, artistName: String): SpotifyTrack? {
        if (title.isBlank() || artistName.isBlank()) return null
        val query = "track:${escapeQuery(title)} artist:${escapeQuery(artistName)}"
        return get<SpotifySearchResponse>(
            url(
                "search",
                "q" to query,
                "type" to "track",
                "limit" to "5",
                "market" to market,
            ),
        ).tracks.items.firstOrNull { track ->
            track.name.isNotBlank() && track.artists.any { it.name.isNotBlank() }
        }
    }

    private fun escapeQuery(value: String): String = value
        .replace("\\", " ")
        .replace("\"", " ")
        .trim()

    private fun url(path: String, vararg parameters: Pair<String, String>): String =
        ("$BASE_URL/$path").toHttpUrl().newBuilder()
            .apply { parameters.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()
            .toString()

    private suspend inline fun <reified T> get(requestUrl: String): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(requestUrl)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", "SpotKofi/1.0")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val message = when (response.code) {
                            401 -> "Spotify access token was rejected"
                            403 -> "Spotify access is not permitted for this token"
                            else -> "Spotify request failed (${response.code})"
                        }
                        throw CatalogException(message)
                    }
                    json.decodeFromString<T>(response.body.string())
                }
            } catch (io: IOException) {
                throw CatalogException("Could not reach Spotify", io)
            } catch (catalog: CatalogException) {
                throw catalog
            } catch (other: Exception) {
                throw CatalogException("Could not read the Spotify response", other)
            }
        }

    private companion object {
        const val BASE_URL = "https://api.spotify.com/v1"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

internal data class SpotifyEnrichment(
    val artistName: String? = null,
    val artistGenre: String? = null,
    val recommendations: List<SpotifyTrack> = emptyList(),
)
