package com.spotkofi.app.data.service

import com.spotkofi.app.data.local.AudioQuality
import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.PlaybackState
import com.spotkofi.app.data.model.RepeatMode
import com.spotkofi.app.data.model.SearchResults
import com.spotkofi.app.data.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Comprehensive music service for handling search, playback, and download functionality.
 * 
 * This service follows patterns from music apps like SimpMusic and InnerTune
 * to provide a complete music experience.
 */
interface MusicService {
    
    // Search functionality
    suspend fun search(query: String): SearchResults
    suspend fun searchTracks(query: String): List<Track>
    suspend fun searchAlbums(query: String): List<Album>
    suspend fun searchArtists(query: String): List<Artist>
    suspend fun getSearchSuggestions(query: String): List<String>
    
    // Music library and browsing
    suspend fun getTrendingTracks(): List<Track>
    suspend fun getNewReleases(): List<Album>
    suspend fun getTopCharts(): List<Track>
    suspend fun getGenres(): List<String>
    suspend fun getTracksByGenre(genre: String): List<Track>
    
    // Artist and album details
    suspend fun getArtistDetails(artistId: String): ArtistDetails
    suspend fun getAlbumDetails(albumId: String): AlbumDetails
    suspend fun getArtistTopTracks(artistId: String): List<Track>
    suspend fun getArtistAlbums(artistId: String): List<Album>
    suspend fun getRelatedArtists(artistId: String): List<Artist>
    
    // Audio streaming
    suspend fun getStreamUrl(trackId: String, quality: AudioQuality = AudioQuality.Automatic): String?
    
    // Playlist management
    suspend fun getUserPlaylists(): List<MediaCollection>
    suspend fun createPlaylist(name: String, description: String = ""): String
    suspend fun addToPlaylist(playlistId: String, trackId: String)
    suspend fun removeFromPlaylist(playlistId: String, trackId: String)
    
    // Playback queue management
    suspend fun getQueue(): List<Track>
    suspend fun addToQueue(track: Track)
    suspend fun clearQueue()
    suspend fun removeFromQueue(trackId: String)
    suspend fun reorderQueue(from: Int, to: Int)
    
    // Download functionality
    suspend fun downloadTrack(track: Track): DownloadStatus
    suspend fun getDownloadedTracks(): List<Track>
    suspend fun deleteDownload(trackId: String): Boolean
    suspend fun getDownloadStatus(trackId: String): DownloadStatus
    
    // Music metadata
    suspend fun getLyrics(trackId: String): String?
    suspend fun getSimilarTracks(trackId: String): List<Track>
    
    // State flows for UI observation
    val playbackState: StateFlow<PlaybackState>
    val currentQueue: StateFlow<List<Track>>
    val downloadsProgress: StateFlow<Map<String, DownloadProgress>>
}

/**
 * Artist details with comprehensive information
 */
data class ArtistDetails(
    val artist: Artist,
    val biography: String? = null,
    val monthlyListeners: Long? = null,
    val socialLinks: Map<String, String> = emptyMap(),
    val topTracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val singles: List<Track> = emptyList(),
    val relatedArtists: List<Artist> = emptyList()
)

/**
 * Album details with track listing and metadata
 */
data class AlbumDetails(
    val album: Album,
    val tracks: List<Track> = emptyList(),
    val copyright: String? = null,
    val label: String? = null,
    val releaseDate: String? = null,
    val totalDuration: Long = 0L
)

/**
 * Download status tracking
 */
sealed class DownloadStatus {
    object NotDownloaded : DownloadStatus()
    data class Downloading(val progress: Int, val downloadedBytes: Long, val totalBytes: Long) : DownloadStatus()
    data class Downloaded(val filePath: String, val sizeBytes: Long) : DownloadStatus()
    data class Failed(val error: String) : DownloadStatus()
}

/**
 * Download progress for UI updates
 */
data class DownloadProgress(
    val trackId: String,
    val progress: Int, // 0-100
    val downloadedBytes: Long,
    val totalBytes: Long,
    val status: DownloadStatus
)

/**
 * Implementation of MusicService with in-memory state management
 */
class InMemoryMusicService : MusicService {
    
    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    
    private val _currentQueue = MutableStateFlow<List<Track>>(emptyList())
    override val currentQueue: StateFlow<List<Track>> = _currentQueue.asStateFlow()
    
    private val _downloadsProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    override val downloadsProgress: StateFlow<Map<String, DownloadProgress>> = _downloadsProgress.asStateFlow()
    
    // In-memory storage for demo purposes
    private val downloadedTracks = mutableMapOf<String, Track>()
    private val userPlaylists = mutableMapOf<String, MediaCollection>()
    private val searchHistory = mutableListOf<String>()
    
    override suspend fun search(query: String): SearchResults {
        searchHistory.add(query)
        // For demo, return mock data
        return SearchResults(
            tracks = generateMockTracks(5),
            collections = generateMockAlbums(3)
        )
    }
    
    override suspend fun searchTracks(query: String): List<Track> {
        return generateMockTracks(10).filter { 
            it.title.contains(query, ignoreCase = true) || 
            it.artistName.contains(query, ignoreCase = true) 
        }
    }
    
    override suspend fun searchAlbums(query: String): List<Album> {
        return generateMockAlbums(5).filter { 
            it.title.contains(query, ignoreCase = true) || 
            it.artistName.contains(query, ignoreCase = true) 
        }
    }
    
    override suspend fun searchArtists(query: String): List<Artist> {
        return generateMockArtists(5).filter { 
            it.name.contains(query, ignoreCase = true) 
        }
    }
    
    override suspend fun getSearchSuggestions(query: String): List<String> {
        return searchHistory.filter { it.startsWith(query, ignoreCase = true) }
            .take(5)
            .plus(listOf("$query songs", "$query album", "$query artist"))
    }
    
    override suspend fun getTrendingTracks(): List<Track> {
        return generateMockTracks(20).shuffled().take(10)
    }
    
    override suspend fun getNewReleases(): List<Album> {
        return generateMockAlbums(10).shuffled().take(5)
    }
    
    override suspend fun getTopCharts(): List<Track> {
        return generateMockTracks(50).shuffled().take(20)
    }
    
    override suspend fun getGenres(): List<String> {
        return listOf("Pop", "Rock", "Hip Hop", "Jazz", "Classical", "Electronic", "R&B", "Country", "Reggae", "K-Pop")
    }
    
    override suspend fun getTracksByGenre(genre: String): List<Track> {
        return generateMockTracks(15).mapIndexed { index, track ->
            track.copy(title = "$genre Track ${index + 1}", artistName = "$genre Artist")
        }
    }
    
    override suspend fun getArtistDetails(artistId: String): ArtistDetails {
        val artist = generateMockArtists(1).first()
        return ArtistDetails(
            artist = artist,
            biography = "This is a sample biography for ${artist.name}. They are known for their contributions to the music industry.",
            monthlyListeners = (1000..1000000).random().toLong(),
            topTracks = generateMockTracks(5),
            albums = generateMockAlbums(3),
            singles = generateMockTracks(3),
            relatedArtists = generateMockArtists(4)
        )
    }
    
    override suspend fun getAlbumDetails(albumId: String): AlbumDetails {
        val album = generateMockAlbums(1).first()
        return AlbumDetails(
            album = album,
            tracks = generateMockTracks(album.trackCount),
            copyright = "© ${album.year} Sample Record Label",
            label = "Sample Record Label",
            releaseDate = "${album.year}-01-01",
            totalDuration = album.trackCount * 180000L // 3 minutes per track
        )
    }
    
    override suspend fun getArtistTopTracks(artistId: String): List<Track> {
        return generateMockTracks(10)
    }
    
    override suspend fun getArtistAlbums(artistId: String): List<Album> {
        return generateMockAlbums(5)
    }
    
    override suspend fun getRelatedArtists(artistId: String): List<Artist> {
        return generateMockArtists(6)
    }
    
    override suspend fun getUserPlaylists(): List<MediaCollection> {
        return userPlaylists.values.toList()
    }
    
    override suspend fun createPlaylist(name: String, description: String): String {
        val playlistId = "playlist_${System.currentTimeMillis()}"
        val playlist = com.spotkofi.app.data.model.Playlist(
            id = playlistId,
            title = name,
            description = description,
            ownerName = "User",
            trackIds = emptyList(),
            artworkUrl = null,
            isPinned = false
        )
        userPlaylists[playlistId] = playlist
        return playlistId
    }
    
    override suspend fun addToPlaylist(playlistId: String, trackId: String) {
        // Implementation would add track to playlist
    }
    
    override suspend fun removeFromPlaylist(playlistId: String, trackId: String) {
        // Implementation would remove track from playlist
    }
    
    override suspend fun getQueue(): List<Track> {
        return _currentQueue.value
    }
    
    override suspend fun addToQueue(track: Track) {
        _currentQueue.update { current ->
            current + track
        }
    }
    
    override suspend fun clearQueue() {
        _currentQueue.update { emptyList() }
    }
    
    override suspend fun removeFromQueue(trackId: String) {
        _currentQueue.update { current ->
            current.filter { it.id != trackId }
        }
    }
    
    override suspend fun reorderQueue(from: Int, to: Int) {
        _currentQueue.update { current ->
            val mutable = current.toMutableList()
            val item = mutable.removeAt(from)
            mutable.add(to, item)
            mutable.toList()
        }
    }
    
    override suspend fun getStreamUrl(
        trackId: String,
        quality: AudioQuality,
    ): String? {
        // No real audio URL is available from this in-memory service.
        return null
    }
    
    override suspend fun downloadTrack(track: Track): DownloadStatus {
        // Simulate download process
        val downloadId = "download_${track.id}"
        val progress = DownloadProgress(
            trackId = track.id,
            progress = 0,
            downloadedBytes = 0L,
            totalBytes = 5000000L, // 5MB
            status = DownloadStatus.Downloading(0, 0L, 5000000L)
        )
        
        _downloadsProgress.update { current ->
            current + (downloadId to progress)
        }
        
        // Simulate download completion
        downloadedTracks[track.id] = track
        
        val completedStatus = DownloadStatus.Downloaded("/storage/emulated/0/Music/${track.title}.mp3", 5000000L)
        _downloadsProgress.update { current ->
            current - downloadId
        }
        
        return completedStatus
    }
    
    override suspend fun getDownloadedTracks(): List<Track> {
        return downloadedTracks.values.toList()
    }
    
    override suspend fun deleteDownload(trackId: String): Boolean {
        downloadedTracks.remove(trackId)
        return true
    }
    
    override suspend fun getDownloadStatus(trackId: String): DownloadStatus {
        return if (downloadedTracks.containsKey(trackId)) {
            DownloadStatus.Downloaded("/storage/emulated/0/Music/track_$trackId.mp3", 5000000L)
        } else {
            DownloadStatus.NotDownloaded
        }
    }
    
    override suspend fun getLyrics(trackId: String): String? {
        // Deliberately null. Lyrics come from a real provider through
        // MusicRepository.trackDetails; inventing text here would put fabricated
        // words on screen under a real song's title.
        return null
    }
    
    override suspend fun getSimilarTracks(trackId: String): List<Track> {
        return generateMockTracks(10)
    }
    
    // Helper methods for generating mock data
    private fun generateMockTracks(count: Int): List<Track> {
        return List(count) { index ->
            Track(
                id = "track_$index",
                title = "Sample Track ${index + 1}",
                artistName = "Sample Artist ${(index % 5) + 1}",
                albumTitle = "Sample Album ${(index % 3) + 1}",
                durationMs = 180000L + (index * 10000L), // 3 minutes plus variation
                isExplicit = index % 4 == 0,
                artworkUrl = "https://example.com/artwork_$index.jpg",
                externalUrl = "https://example.com/track/$index",
                albumId = "album_${index % 3}",
                artistId = "artist_${index % 5}"
            )
        }
    }
    
    private fun generateMockAlbums(count: Int): List<Album> {
        return List(count) { index ->
            Album(
                id = "album_$index",
                title = "Sample Album ${index + 1}",
                artistName = "Sample Artist ${(index % 5) + 1}",
                year = 2020 + (index % 5),
                genre = listOf("Pop", "Rock", "Hip Hop", "Jazz", "Electronic")[index % 5],
                trackCount = 10 + (index % 5),
                artworkUrl = "https://example.com/album_artwork_$index.jpg"
            )
        }
    }
    
    private fun generateMockArtists(count: Int): List<Artist> {
        return List(count) { index ->
            Artist(
                id = "artist_$index",
                name = "Sample Artist ${index + 1}",
                genre = listOf("Pop", "Rock", "Hip Hop", "Jazz", "Electronic")[index % 5],
                artworkUrl = "https://example.com/artist_avatar_$index.jpg"
            )
        }
    }
}