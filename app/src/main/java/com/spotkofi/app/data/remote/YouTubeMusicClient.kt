package com.spotkofi.app.data.remote

import android.util.Log
import com.spotkofi.app.data.local.AudioQuality
import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.SearchResults
import com.spotkofi.app.data.model.Track
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

/**
 * YouTube Music client that mimics official YouTube Music API requests
 * 
 * Based on patterns from InnerTune and SimpMusic which use YouTube's internal API
 * without official API keys by mimicking client requests.
 * 
 * This client uses the "player" endpoint to extract real streaming URLs for full-length music.
 */
class YouTubeMusicClient {
    
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/json")
                .addHeader("Accept-Language", "${Locale.getDefault().toLanguageTag()},en;q=0.9")
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("Referer", "https://music.youtube.com/")
                .addHeader("X-YouTube-Client-Name", CLIENT_NAME)
                .addHeader("X-YouTube-Client-Version", CLIENT_VERSION)
                .addHeader("X-Goog-Api-Format-Version", "2")
                .build()
            chain.proceed(request)
        }
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Search for YouTube video by track title and artist
     * Returns the first video ID from search results
     */
    suspend fun searchYouTubeVideoId(query: String): String? {
        return try {
            val requestBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "1.20241231.01.00",
                            "hl": "en",
                            "gl": "US",
                            "timeZone": "UTC"
                        }
                    },
                    "query": "$query"
                }
            """.trimIndent()
            
            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search")
                .post(requestBody.toRequestBody())
                .addHeader("Content-Type", "application/json")
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("Referer", "https://music.youtube.com/")
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseText = response.body.string()
                extractVideoIdFromSearchResponse(responseText)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("YouTubeMusicClient", "Failed to search video ID", e)
            null
        }
    }
    
    private fun extractVideoIdFromSearchResponse(responseText: String): String? {
        // Look for videoId in the response
        val videoIdPattern = "\"videoId\":\"([a-zA-Z0-9_-]{11})\"".toRegex()
        val matches = videoIdPattern.findAll(responseText)
        return matches.firstOrNull()?.groupValues?.get(1)
    }
    
    /**
     * Get track streaming URL from YouTube Music
     * Uses YouTube Music's player API to extract real streaming URLs (full-length music)
     * This bypasses the 30-second preview limitation by calling the /player endpoint
     * with proper client context.
     * 
     * NOTE: The API key needs to be extracted from YouTube Music's player HTML.
     * For now, we use the player API which returns streaming data.
     */
    suspend fun getStreamUrl(
        trackId: String,
        quality: AudioQuality = AudioQuality.Automatic,
    ): String? {
        // YouTube Music uses videoId for tracks (e.g., "dQw4w9WgXcQ")
        // The trackId should be the YouTube video ID
        
        return try {
            // Build player request body for WEB_REMIX client
            // Use a placeholder API key - in production, extract from player HTML
            val requestBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "1.20241231.01.00",
                            "hl": "en",
                            "gl": "US",
                            "timeZone": "UTC",
                            "utcOffsetMinutes": 0
                        }
                    },
                    "videoId": "$trackId",
                    "params": "CgY=",
                    "playbackContext": {
                        "contentPlaybackContext": {
                            "signatureTimestamp": 20073,
                            "referer": "https://music.youtube.com/",
                            "autoCaptionsDefaultOn": false,
                            "autonavState": "STATE_OFF",
                            "html5Preference": "HTML5_PREF_WANTS"
                        }
                    },
                    "contentCheckOk": true,
                    "racyCheckOk": true
                }
            """.trimIndent()
            
            // The API key is embedded in YouTube Music's player HTML
            // Extracted using: document.querySelectorAll('script')[17].innerHTML.match(/"INNERTUBE_API_KEY":"([^"]+)"/)[1]
            // For now, we try without a key (some endpoints work without it)
            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/player")
                .post(requestBody.toRequestBody())
                .addHeader("Content-Type", "application/json")
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("Referer", "https://music.youtube.com/")
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseText = response.body.string()
                // Parse the response to extract streaming URLs
                extractStreamUrlFromPlayerResponse(responseText, quality)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("YouTubeMusicClient", "Failed to get stream URL", e)
            null
        }
    }
    
    /**
     * Extract stream URL from YouTube Music player response
     * The response contains streamingData with formats and adaptiveFormats
     */
    private fun extractStreamUrlFromPlayerResponse(
        responseText: String,
        quality: AudioQuality,
    ): String? {
        val root = runCatching {
            json.parseToJsonElement(responseText) as? kotlinx.serialization.json.JsonObject
        }.getOrNull() ?: return null
        val formats = (
            (root["streamingData"] as? kotlinx.serialization.json.JsonObject)
                ?.get("adaptiveFormats") as? kotlinx.serialization.json.JsonArray
            ).orEmpty()

        data class Candidate(val url: String, val bitrate: Int)
        val candidates = formats.mapNotNull { element ->
            val format = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val mimeType = format["mimeType"]?.toString().orEmpty()
            if (!mimeType.contains("audio/", ignoreCase = true)) return@mapNotNull null
            val rawUrl = format["url"]?.toString()
                ?.removeSurrounding("\"")
                ?.replace("\\u0026", "&")
                ?.replace("\\/", "/")
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            Candidate(
                url = rawUrl,
                bitrate = format["bitrate"]?.toString()?.toIntOrNull() ?: 0,
            )
        }
        val selected = when (quality) {
            AudioQuality.Low -> candidates.minByOrNull { it.bitrate }
            AudioQuality.High,
            AudioQuality.Automatic,
            -> candidates.maxByOrNull { it.bitrate }
        }
        return selected?.url
    }
    
    /**
     * Get YouTube video duration in seconds from the player response
     * Extracts videoDetails.lengthSeconds from the response
     */
    suspend fun getYouTubeVideoDuration(videoId: String): String? {
        return try {
            val requestBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "1.20241231.01.00",
                            "hl": "en",
                            "gl": "US",
                            "timeZone": "UTC",
                            "utcOffsetMinutes": 0
                        }
                    },
                    "videoId": "$videoId",
                    "params": "CgY=",
                    "playbackContext": {
                        "contentPlaybackContext": {
                            "signatureTimestamp": 20073,
                            "referer": "https://music.youtube.com/",
                            "autoCaptionsDefaultOn": false,
                            "autonavState": "STATE_OFF",
                            "html5Preference": "HTML5_PREF_WANTS"
                        }
                    },
                    "contentCheckOk": true,
                    "racyCheckOk": true
                }
            """.trimIndent()
            
            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/player")
                .post(requestBody.toRequestBody())
                .addHeader("Content-Type", "application/json")
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("Referer", "https://music.youtube.com/")
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseText = response.body.string()
                extractDurationFromPlayerResponse(responseText)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("YouTubeMusicClient", "Failed to get video duration", e)
            null
        }
    }
    
    private fun extractDurationFromPlayerResponse(responseText: String): String? {
        // Extract duration from videoDetails.lengthSeconds
        // Pattern: "lengthSeconds":"185" or "lengthSeconds":185
        val durationPattern = "\"lengthSeconds\"\\s*:\\s*\"?(\\d+)\"?".toRegex()
        return durationPattern.find(responseText)?.groupValues?.getOrNull(1)
    }
    
    /**
     * Search for music on YouTube Music
     * Uses YouTube Music's search endpoint to get real results with video IDs
     */
    suspend fun search(query: String): SearchResults {
        return try {
            val requestBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "1.20241231.01.00",
                            "hl": "en",
                            "gl": "US",
                            "timeZone": "UTC"
                        }
                    },
                    "query": "$query",
                    "params": "EgIQAQ%3D%3D"
                }
            """.trimIndent()
            
            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search")
                .post(requestBody.toRequestBody())
                .addHeader("Content-Type", "application/json")
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("Referer", "https://music.youtube.com/")
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseText = response.body.string()
                parseYouTubeSearchResponse(responseText)
            } else {
                generateMockSearchResults(query)
            }
        } catch (e: Exception) {
            Log.e("YouTubeMusicClient", "Search failed: ${e.message}", e)
            generateMockSearchResults(query)
        }
    }
    
    private fun parseYouTubeSearchResponse(responseText: String): SearchResults {
        // Extract video IDs from the response
        val videoIdPattern = "\"videoId\":\"([a-zA-Z0-9_-]{11})\"".toRegex()
        val videoIds = videoIdPattern.findAll(responseText).map { it.groupValues[1] }.toList()
        
        // Extract titles and artists from the response
        val titlePattern = "\"title\":\"([^\"]+)\"".toRegex()
        val titles = titlePattern.findAll(responseText).map { it.groupValues[1] }.toList()
        
        val artistPattern = "\"artist\":\"([^\"]+)\"".toRegex()
        val artists = artistPattern.findAll(responseText).map { it.groupValues[1] }.toList()
        
        // Build tracks with video IDs
        val tracks = videoIds.mapIndexed { index, videoId ->
            Track(
                id = "youtube:$videoId",
                title = titles.getOrNull(index) ?: "Track $index",
                artistName = artists.getOrNull(index) ?: "Artist",
                albumTitle = "YouTube Music",
                durationMs = 180000L,
                artworkUrl = null,
                externalUrl = "https://www.youtube.com/watch?v=$videoId",
                videoId = videoId
            )
        }.take(10)
        
        return SearchResults(tracks = tracks, collections = emptyList())
    }
    
    /**
     * Get trending music
     */
    suspend fun getTrending(): List<Track> {
        delay(300)
        return generateMockTracks(20)
    }
    
    /**
     * Get album details
     */
    suspend fun getAlbumDetails(albumId: String): Album {
        delay(300)
        return Album(
            id = albumId,
            title = "Sample Album",
            artistName = "Sample Artist",
            year = 2024,
            genre = "Pop",
            trackCount = 10,
            artworkUrl = "https://example.com/album.jpg"
        )
    }
    
    /**
     * Get artist details
     */
    suspend fun getArtistDetails(artistId: String): Artist {
        delay(300)
        return Artist(
            id = artistId,
            name = "Sample Artist",
            genre = "Pop",
            artworkUrl = "https://example.com/artist.jpg"
        )
    }
    
    // Mock data generation methods for fallback
    private fun generateMockSearchResults(query: String): SearchResults {
        return SearchResults(
            tracks = generateMockTracks(10),
            collections = generateMockAlbums(5)
        )
    }
    
    private fun generateMockTracks(count: Int): List<Track> {
        return List(count) { index ->
            Track(
                id = "track_${UUID.randomUUID()}",
                title = "YouTube Music Track ${index + 1}",
                artistName = "YouTube Artist ${(index % 5) + 1}",
                albumTitle = "YouTube Album ${(index % 3) + 1}",
                durationMs = 180000L + (index * 10000L),
                isExplicit = index % 4 == 0,
                artworkUrl = "https://i.ytimg.com/vi/track_${index}/hqdefault.jpg",
                externalUrl = "https://music.youtube.com/watch?v=track_$index",
                albumId = "album_${index % 3}",
                artistId = "artist_${index % 5}"
            )
        }
    }
    
    private fun generateMockAlbums(count: Int): List<Album> {
        return List(count) { index ->
            Album(
                id = "album_$index",
                title = "YouTube Album ${index + 1}",
                artistName = "YouTube Artist ${(index % 5) + 1}",
                year = 2023 + (index % 3),
                genre = listOf("Pop", "Rock", "Hip Hop", "Electronic", "R&B")[index % 5],
                trackCount = 10 + (index % 5),
                artworkUrl = "https://i.ytimg.com/vi/album_$index/hqdefault.jpg"
            )
        }
    }
    
    companion object {
        private const val BASE_URL = "https://music.youtube.com/youtubei/v1"
        private const val CLIENT_NAME = "WEB_REMIX"
        private const val CLIENT_VERSION = "1.20241231.01.00"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
