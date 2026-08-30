package com.spotkofi.app.data.service

import com.spotkofi.app.data.local.AudioQuality
import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.PlaybackState
import com.spotkofi.app.data.model.RepeatMode
import com.spotkofi.app.data.model.SearchResults
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.remote.YouTubeMusicClient
import com.spotkofi.app.data.remote.YouTubeStreamExtractor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Simplified YouTube Music service that provides basic functionality
 * to prevent crashes and get the app working
 */
class SimpleYouTubeMusicService : MusicService {
    
    private val youtubeClient = YouTubeMusicClient()
    private val streamExtractor = YouTubeStreamExtractor()
    
    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    
    private val _currentQueue = MutableStateFlow<List<Track>>(emptyList())
    override val currentQueue: StateFlow<List<Track>> = _currentQueue.asStateFlow()
    
    private val _downloadsProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    override val downloadsProgress: StateFlow<Map<String, DownloadProgress>> = _downloadsProgress.asStateFlow()
    
    // Simple in-memory storage
    private val userPlaylists = mutableMapOf<String, MediaCollection>()
    
    override suspend fun search(query: String): SearchResults {
        return try {
            youtubeClient.search(query)
        } catch (e: Exception) {
            // Return empty results to prevent crash
            SearchResults()
        }
    }
    
    override suspend fun searchTracks(query: String): List<Track> {
        return try {
            youtubeClient.search(query).tracks
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun searchAlbums(query: String): List<Album> {
        return try {
            youtubeClient.search(query).collections.filterIsInstance<Album>()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun searchArtists(query: String): List<Artist> {
        return try {
            youtubeClient.search(query).collections.filterIsInstance<Artist>()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun getSearchSuggestions(query: String): List<String> {
        return listOf(
            "$query music",
            "$query songs",
            "popular $query",
            "$query 2024",
            "best of $query"
        ).take(5)
    }
    
    override suspend fun getTrendingTracks(): List<Track> {
        return try {
            youtubeClient.getTrending()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun getNewReleases(): List<Album> {
        // Return mock albums
        return List(10) { index ->
            Album(
                id = "album_$index",
                title = "New Release ${index + 1}",
                artistName = "Artist ${index + 1}",
                year = 2024,
                genre = "Various",
                trackCount = 10 + index,
                artworkUrl = null
            )
        }
    }
    
    override suspend fun getTopCharts(): List<Track> {
        return try {
            youtubeClient.getTrending().take(20)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun getGenres(): List<String> {
        return listOf("Pop", "Rock", "Hip Hop", "R&B", "Jazz")
    }
    
    override suspend fun getTracksByGenre(genre: String): List<Track> {
        return try {
            youtubeClient.getTrending().take(10).map { it.copy(title = "$genre: ${it.title}") }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun getArtistDetails(artistId: String): ArtistDetails {
        return ArtistDetails(
            artist = Artist(artistId, "YouTube Artist", "Various"),
            biography = "Popular artist on YouTube Music",
            monthlyListeners = 1000000L,
            topTracks = getTrendingTracks().take(10),
            albums = getNewReleases().take(3),
            singles = emptyList(),
            relatedArtists = emptyList()
        )
    }
    
    override suspend fun getAlbumDetails(albumId: String): AlbumDetails {
        return AlbumDetails(
            album = Album(albumId, "YouTube Album", "YouTube Artist", 2024, "Various", 10, null),
            tracks = getTrendingTracks().take(10),
            copyright = "© 2024 YouTube Music",
            label = "YouTube Music",
            releaseDate = "2024-01-01",
            totalDuration = 1800000L
        )
    }
    
    override suspend fun getArtistTopTracks(artistId: String): List<Track> {
        return getTrendingTracks().take(10)
    }
    
    override suspend fun getArtistAlbums(artistId: String): List<Album> {
        return getNewReleases().take(5)
    }
    
    override suspend fun getRelatedArtists(artistId: String): List<Artist> {
        return List(3) { index ->
            Artist("related_$index", "Related Artist ${index + 1}", "Various")
        }
    }
    
    override suspend fun getUserPlaylists(): List<MediaCollection> {
        return userPlaylists.values.toList()
    }
    
    override suspend fun createPlaylist(name: String, description: String): String {
        val playlistId = "playlist_${UUID.randomUUID()}"
        userPlaylists[playlistId] = com.spotkofi.app.data.model.Playlist(
            id = playlistId,
            title = name,
            description = description,
            ownerName = "You",
            trackIds = emptyList(),
            artworkUrl = null,
            isPinned = false
        )
        return playlistId
    }
    
    override suspend fun addToPlaylist(playlistId: String, trackId: String) {
        // Simple implementation
    }
    
    override suspend fun removeFromPlaylist(playlistId: String, trackId: String) {
        // Simple implementation
    }
    
    override suspend fun getQueue(): List<Track> {
        return _currentQueue.value
    }
    
    override suspend fun addToQueue(track: Track) {
        _currentQueue.update { it + track }
    }
    
    override suspend fun clearQueue() {
        _currentQueue.update { emptyList() }
    }
    
    override suspend fun removeFromQueue(trackId: String) {
        _currentQueue.update { it.filter { track -> track.id != trackId } }
    }
    
    override suspend fun reorderQueue(from: Int, to: Int) {
        _currentQueue.update { current ->
            val mutable = current.toMutableList()
            val item = mutable.removeAt(from)
            mutable.add(to, item)
            mutable
        }
    }
    
    override suspend fun getStreamUrl(
        trackId: String,
        quality: AudioQuality,
    ): String? = withContext(Dispatchers.IO) {
        // trackId is already the selected YouTube video ID. Re-searching it can
        // select another video and was one of the reasons playback fell back to
        // the unrelated 30-second iTunes preview.
        val extractedUrl = streamExtractor.getAudioUrl(trackId, quality)
        if (!extractedUrl.isNullOrBlank()) {
            Log.d(TAG, "Resolved full-length YouTube audio for $trackId")
            return@withContext extractedUrl
        }

        // Keep the internal player client as a secondary resolver, but never let
        // the controller silently replace a failed YouTube stream with a preview.
        youtubeClient.getStreamUrl(trackId, quality).also { url ->
            if (url.isNullOrBlank()) {
                Log.w(TAG, "No YouTube audio stream resolved for $trackId")
            }
        }
    }

    private companion object {
        const val TAG = "SpotKofiService"
    }
    
    override suspend fun downloadTrack(track: Track): DownloadStatus {
        // Simple mock download
        return DownloadStatus.Downloaded("/storage/emulated/0/Music/${track.id}.mp3", 5000000L)
    }
    
    override suspend fun getDownloadedTracks(): List<Track> {
        return emptyList()
    }
    
    override suspend fun deleteDownload(trackId: String): Boolean {
        return true
    }
    
    override suspend fun getDownloadStatus(trackId: String): DownloadStatus {
        return DownloadStatus.NotDownloaded
    }
    
    override suspend fun getLyrics(trackId: String): String? {
        return null
    }
    
    override suspend fun getSimilarTracks(trackId: String): List<Track> {
        return getTrendingTracks().take(10)
    }
}