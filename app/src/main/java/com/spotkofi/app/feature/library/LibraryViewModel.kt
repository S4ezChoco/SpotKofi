package com.spotkofi.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.service.DownloadManager
import com.spotkofi.app.data.service.DownloadPriority
import com.spotkofi.app.data.service.DownloadManagerStatus
import com.spotkofi.app.data.service.MusicService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for managing music library, downloads, and user collections
 */
class LibraryViewModel(
    private val musicService: MusicService,
    private val downloadManager: DownloadManager
) : ViewModel() {
    
    // Library sections
    private val _downloadedTracks = MutableStateFlow<List<Track>>(emptyList())
    val downloadedTracks: StateFlow<List<Track>> = _downloadedTracks.asStateFlow()
    
    private val _recentlyPlayed = MutableStateFlow<List<Track>>(emptyList())
    val recentlyPlayed: StateFlow<List<Track>> = _recentlyPlayed.asStateFlow()
    
    private val _favoriteTracks = MutableStateFlow<List<Track>>(emptyList())
    val favoriteTracks: StateFlow<List<Track>> = _favoriteTracks.asStateFlow()
    
    private val _playlists = MutableStateFlow<List<MediaCollection>>(emptyList())
    val playlists: StateFlow<List<MediaCollection>> = _playlists.asStateFlow()
    
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()
    
    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()
    
    // Download management
    private val _downloadQueue = MutableStateFlow<List<com.spotkofi.app.data.service.DownloadItem>>(emptyList())
    val downloadQueue: StateFlow<List<com.spotkofi.app.data.service.DownloadItem>> = _downloadQueue.asStateFlow()
    
    private val _activeDownloads = MutableStateFlow<List<com.spotkofi.app.data.service.DownloadItem>>(emptyList())
    val activeDownloads: StateFlow<List<com.spotkofi.app.data.service.DownloadItem>> = _activeDownloads.asStateFlow()
    
    private val _completedDownloads = MutableStateFlow<List<com.spotkofi.app.data.service.DownloadItem>>(emptyList())
    val completedDownloads: StateFlow<List<com.spotkofi.app.data.service.DownloadItem>> = _completedDownloads.asStateFlow()
    
    private val _failedDownloads = MutableStateFlow<List<com.spotkofi.app.data.service.DownloadItem>>(emptyList())
    val failedDownloads: StateFlow<List<com.spotkofi.app.data.service.DownloadItem>> = _failedDownloads.asStateFlow()
    
    // UI state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _selectedTab = MutableStateFlow(LibraryTab.DOWNLOADS)
    val selectedTab: StateFlow<LibraryTab> = _selectedTab.asStateFlow()
    
    // Combined flows for UI
    val libraryStats = combine(
        downloadedTracks,
        playlists,
        albums,
        artists
    ) { tracks, playlists, albums, artists ->
        LibraryStats(
            downloadedTracks = tracks.size,
            playlists = playlists.size,
            albums = albums.size,
            artists = artists.size
        )
    }
    
    val downloadStats = combine(
        downloadQueue,
        activeDownloads,
        completedDownloads,
        failedDownloads
    ) { queue, active, completed, failed ->
        DownloadStats(
            queued = queue.size,
            active = active.size,
            completed = completed.size,
            failed = failed.size,
            totalBytes = completed.sumOf { it.downloadedBytes }
        )
    }
    
    init {
        loadLibraryData()
        observeDownloads()
    }
    
    fun selectTab(tab: LibraryTab) {
        _selectedTab.value = tab
    }
    
    fun loadLibraryData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                // Load all library data in parallel
                val downloadedTracksDeferred = viewModelScope.launch {
                    _downloadedTracks.value = musicService.getDownloadedTracks()
                }
                
                val playlistsDeferred = viewModelScope.launch {
                    _playlists.value = musicService.getUserPlaylists()
                }
                
                val trendingDeferred = viewModelScope.launch {
                    _recentlyPlayed.value = musicService.getTrendingTracks().take(10)
                }
                
                val favoritesDeferred = viewModelScope.launch {
                    _favoriteTracks.value = musicService.getTopCharts().take(15)
                }
                
                val albumsDeferred = viewModelScope.launch {
                    _albums.value = musicService.getNewReleases()
                }
                
                val artistsDeferred = viewModelScope.launch {
                    // Convert mock data to artists
                    val artistNames = _downloadedTracks.value.map { it.artistName }.distinct()
                    _artists.value = artistNames.mapIndexed { index, name ->
                        Artist(
                            id = "artist_$index",
                            name = name,
                            genre = "Various",
                            artworkUrl = null
                        )
                    }
                }
                
                // Wait for all to complete
                downloadedTracksDeferred.join()
                playlistsDeferred.join()
                trendingDeferred.join()
                favoritesDeferred.join()
                albumsDeferred.join()
                artistsDeferred.join()
                
            } catch (e: Exception) {
                _error.value = "Failed to load library: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun refreshLibrary() {
        loadLibraryData()
    }
    
    // Download management functions
    fun downloadTrack(track: Track, priority: DownloadPriority = DownloadPriority.NORMAL) {
        downloadManager.downloadTrack(track, priority)
    }
    
    fun downloadTracks(tracks: List<Track>, priority: DownloadPriority = DownloadPriority.NORMAL) {
        downloadManager.downloadTracks(tracks, priority)
    }
    
    fun pauseDownload(downloadId: String) {
        downloadManager.pauseDownload(downloadId)
    }
    
    fun resumeDownload(downloadId: String) {
        downloadManager.resumeDownload(downloadId)
    }
    
    fun cancelDownload(downloadId: String) {
        downloadManager.cancelDownload(downloadId)
    }
    
    fun retryDownload(downloadId: String) {
        downloadManager.retryDownload(downloadId)
    }
    
    fun deleteDownload(downloadId: String) {
        viewModelScope.launch {
            val success = downloadManager.deleteDownload(downloadId)
            if (success) {
                // Refresh downloaded tracks
                _downloadedTracks.value = musicService.getDownloadedTracks()
            }
        }
    }
    
    fun clearCompletedDownloads() {
        downloadManager.clearCompletedDownloads()
    }
    
    fun clearFailedDownloads() {
        downloadManager.clearFailedDownloads()
    }
    
    fun getDownloadProgress(downloadId: String): Int {
        return downloadManager.getDownloadItem(downloadId)?.progress ?: 0
    }
    
    fun getDownloadStatus(downloadId: String): DownloadManagerStatus? {
        return downloadManager.getDownloadItem(downloadId)?.status
    }
    
    // Playlist management
    fun createPlaylist(name: String, description: String = "") {
        viewModelScope.launch {
            try {
                val playlistId = musicService.createPlaylist(name, description)
                // Refresh playlists
                _playlists.value = musicService.getUserPlaylists()
            } catch (e: Exception) {
                _error.value = "Failed to create playlist: ${e.message}"
            }
        }
    }
    
    fun addToPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            try {
                musicService.addToPlaylist(playlistId, trackId)
            } catch (e: Exception) {
                _error.value = "Failed to add to playlist: ${e.message}"
            }
        }
    }
    
    fun removeFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            try {
                musicService.removeFromPlaylist(playlistId, trackId)
            } catch (e: Exception) {
                _error.value = "Failed to remove from playlist: ${e.message}"
            }
        }
    }
    
    // Track management
    fun addToFavorites(track: Track) {
        viewModelScope.launch {
            _favoriteTracks.update { current ->
                if (current.any { it.id == track.id }) {
                    current // Already in favorites
                } else {
                    current + track
                }
            }
        }
    }
    
    fun removeFromFavorites(trackId: String) {
        viewModelScope.launch {
            _favoriteTracks.update { current ->
                current.filter { it.id != trackId }
            }
        }
    }
    
    fun isTrackFavorite(trackId: String): Boolean {
        return _favoriteTracks.value.any { it.id == trackId }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    private fun observeDownloads() {
        viewModelScope.launch {
            downloadManager.downloadQueue.collect { queue ->
                _downloadQueue.value = queue
            }
        }
        
        viewModelScope.launch {
            downloadManager.activeDownloads.collect { active ->
                _activeDownloads.value = active
            }
        }
        
        viewModelScope.launch {
            downloadManager.completedDownloads.collect { completed ->
                _completedDownloads.value = completed
                // Refresh downloaded tracks when new downloads complete
                _downloadedTracks.value = musicService.getDownloadedTracks()
            }
        }
        
        viewModelScope.launch {
            downloadManager.failedDownloads.collect { failed ->
                _failedDownloads.value = failed
            }
        }
    }
}

/**
 * Library tabs
 */
enum class LibraryTab {
    DOWNLOADS,
    TRACKS,
    PLAYLISTS,
    ALBUMS,
    ARTISTS,
    RECENT
}

/**
 * Library statistics
 */
data class LibraryStats(
    val downloadedTracks: Int,
    val playlists: Int,
    val albums: Int,
    val artists: Int
)

/**
 * Download statistics for UI
 */
data class DownloadStats(
    val queued: Int,
    val active: Int,
    val completed: Int,
    val failed: Int,
    val totalBytes: Long
)