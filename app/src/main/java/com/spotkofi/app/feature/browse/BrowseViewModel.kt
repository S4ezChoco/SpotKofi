package com.spotkofi.app.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.service.MusicService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for music browsing, discovery, and home screen content
 */
class BrowseViewModel(
    private val musicService: MusicService
) : ViewModel() {
    
    // Home screen content
    private val _trendingTracks = MutableStateFlow<List<Track>>(emptyList())
    val trendingTracks: StateFlow<List<Track>> = _trendingTracks.asStateFlow()
    
    private val _newReleases = MutableStateFlow<List<Album>>(emptyList())
    val newReleases: StateFlow<List<Album>> = _newReleases.asStateFlow()
    
    private val _topCharts = MutableStateFlow<List<Track>>(emptyList())
    val topCharts: StateFlow<List<Track>> = _topCharts.asStateFlow()
    
    private val _featuredPlaylists = MutableStateFlow<List<MediaCollection>>(emptyList())
    val featuredPlaylists: StateFlow<List<MediaCollection>> = _featuredPlaylists.asStateFlow()
    
    private val _recommendedArtists = MutableStateFlow<List<Artist>>(emptyList())
    val recommendedArtists: StateFlow<List<Artist>> = _recommendedArtists.asStateFlow()
    
    // Genre-based content
    private val _genres = MutableStateFlow<List<String>>(emptyList())
    val genres: StateFlow<List<String>> = _genres.asStateFlow()
    
    private val _genreTracks = MutableStateFlow<Map<String, List<Track>>>(emptyMap())
    val genreTracks: StateFlow<Map<String, List<Track>>> = _genreTracks.asStateFlow()
    
    // Discovery content
    private val _discoverWeekly = MutableStateFlow<List<Track>>(emptyList())
    val discoverWeekly: StateFlow<List<Track>> = _discoverWeekly.asStateFlow()
    
    private val _releaseRadar = MutableStateFlow<List<Track>>(emptyList())
    val releaseRadar: StateFlow<List<Track>> = _releaseRadar.asStateFlow()
    
    // UI state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre: StateFlow<String?> = _selectedGenre.asStateFlow()
    
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    
    init {
        loadBrowseContent()
    }
    
    fun loadBrowseContent() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                // Load all browse content in parallel for better performance
                val trendingDeferred = viewModelScope.async {
                    _trendingTracks.value = musicService.getTrendingTracks().take(15)
                }
                
                val newReleasesDeferred = viewModelScope.async {
                    _newReleases.value = musicService.getNewReleases().take(10)
                }
                
                val topChartsDeferred = viewModelScope.async {
                    _topCharts.value = musicService.getTopCharts().take(20)
                }
                
                val genresDeferred = viewModelScope.async {
                    _genres.value = musicService.getGenres()
                }
                
                val featuredDeferred = viewModelScope.async {
                    // Create featured playlists from trending tracks
                    val playlists = listOf(
                        com.spotkofi.app.data.model.Playlist(
                            id = "featured_1",
                            title = "Today's Top Hits",
                            description = "The biggest songs right now",
                            ownerName = "SpotKofi",
                            trackIds = emptyList(),
                            artworkUrl = null,
                            isPinned = true
                        ),
                        com.spotkofi.app.data.model.Playlist(
                            id = "featured_2",
                            title = "Discover Weekly",
                            description = "Your weekly mixtape of fresh music",
                            ownerName = "SpotKofi",
                            trackIds = emptyList(),
                            artworkUrl = null,
                            isPinned = false
                        ),
                        com.spotkofi.app.data.model.Playlist(
                            id = "featured_3",
                            title = "Release Radar",
                            description = "Catch all the latest music from artists you follow",
                            ownerName = "SpotKofi",
                            trackIds = emptyList(),
                            artworkUrl = null,
                            isPinned = false
                        )
                    )
                    _featuredPlaylists.value = playlists
                }
                
                val artistsDeferred = viewModelScope.async {
                    // Convert trending tracks to recommended artists
                    val artistNames = _trendingTracks.value.map { it.artistName }.distinct().take(8)
                    _recommendedArtists.value = artistNames.mapIndexed { index, name ->
                        Artist(
                            id = "rec_artist_$index",
                            name = name,
                            genre = "Various",
                            artworkUrl = null
                        )
                    }
                }
                
                val discoverDeferred = viewModelScope.async {
                    _discoverWeekly.value = musicService.getTrendingTracks().shuffled().take(30)
                }
                
                val radarDeferred = viewModelScope.async {
                    _releaseRadar.value = musicService.getNewReleases().flatMap { album ->
                        // Simulate tracks from new releases
                        List(album.trackCount) { trackIndex ->
                            Track(
                                id = "${album.id}_track_$trackIndex",
                                title = "New Track ${trackIndex + 1}",
                                artistName = album.artistName,
                                albumTitle = album.title,
                                durationMs = 180000L + (trackIndex * 10000L),
                                isExplicit = trackIndex % 4 == 0,
                                artworkUrl = album.artworkUrl,
                                albumId = album.id,
                                artistId = "artist_${album.id.hashCode()}"
                            )
                        }
                    }.take(20)
                }
                
                // Wait for all async operations to complete
                awaitAll(
                    trendingDeferred,
                    newReleasesDeferred,
                    topChartsDeferred,
                    genresDeferred,
                    featuredDeferred,
                    artistsDeferred,
                    discoverDeferred,
                    radarDeferred
                )
                
                // Load genre tracks for first few genres
                loadGenreTracksForSelectedGenres()
                
            } catch (e: Exception) {
                _error.value = "Failed to load browse content: ${e.message}"
            } finally {
                _isLoading.value = false
                _refreshing.value = false
            }
        }
    }
    
    fun refreshContent() {
        _refreshing.value = true
        loadBrowseContent()
    }
    
    fun selectGenre(genre: String?) {
        _selectedGenre.value = genre
        
        if (genre != null && !_genreTracks.value.containsKey(genre)) {
            loadTracksForGenre(genre)
        }
    }
    
    fun getTracksForCurrentGenre(): List<Track> {
        val genre = _selectedGenre.value ?: return emptyList()
        return _genreTracks.value[genre] ?: emptyList()
    }
    
    fun getGenreColor(genre: String): Long {
        // Map genres to colors (simplified version)
        val genreColors = mapOf(
            "Pop" to 0xFFE91E63,
            "Rock" to 0xFF9C27B0,
            "Hip Hop" to 0xFF3F51B5,
            "Jazz" to 0xFF2196F3,
            "Classical" to 0xFF03A9F4,
            "Electronic" to 0xFF00BCD4,
            "R&B" to 0xFF009688,
            "Country" to 0xFF4CAF50,
            "Reggae" to 0xFF8BC34A,
            "K-Pop" to 0xFFFFC107
        )
        
        return genreColors[genre] ?: 0xFF607D8B
    }
    
    fun getGenreIcon(genre: String): String {
        val genreIcons = mapOf(
            "Pop" to "🎵",
            "Rock" to "🎸",
            "Hip Hop" to "🎤",
            "Jazz" to "🎷",
            "Classical" to "🎻",
            "Electronic" to "🎧",
            "R&B" to "🎶",
            "Country" to "🤠",
            "Reggae" to "🌴",
            "K-Pop" to "🇰🇷"
        )
        
        return genreIcons[genre] ?: "🎵"
    }
    
    fun clearError() {
        _error.value = null
    }
    
    // Statistics for UI
    fun getContentStats(): BrowseStats {
        return BrowseStats(
            trendingTracks = _trendingTracks.value.size,
            newReleases = _newReleases.value.size,
            topCharts = _topCharts.value.size,
            featuredPlaylists = _featuredPlaylists.value.size,
            recommendedArtists = _recommendedArtists.value.size,
            genres = _genres.value.size
        )
    }
    
    private fun loadGenreTracksForSelectedGenres() {
        val genresToLoad = _genres.value.take(4) // Load tracks for first 4 genres
        
        genresToLoad.forEach { genre ->
            if (!_genreTracks.value.containsKey(genre)) {
                loadTracksForGenre(genre)
            }
        }
    }
    
    private fun loadTracksForGenre(genre: String) {
        viewModelScope.launch {
            try {
                val tracks = musicService.getTracksByGenre(genre)
                _genreTracks.update { current ->
                    current + (genre to tracks)
                }
            } catch (e: Exception) {
                // Silently fail for genre tracks - they're not critical
            }
        }
    }
    
    // Content recommendations
    fun getPersonalizedRecommendations(): List<Recommendation> {
        return listOf(
            Recommendation(
                id = "rec_1",
                title = "Based on your listening",
                description = "Songs similar to what you've been playing",
                type = RecommendationType.SIMILAR,
                items = _trendingTracks.value.take(5)
            ),
            Recommendation(
                id = "rec_2",
                title = "New releases you might like",
                description = "Fresh music from artists in your library",
                type = RecommendationType.NEW_RELEASES,
                items = _newReleases.value.take(3)
            ),
            Recommendation(
                id = "rec_3",
                title = "Trending in your area",
                description = "What's hot near you right now",
                type = RecommendationType.LOCAL,
                items = _topCharts.value.take(5)
            )
        )
    }
    
    fun getMoodPlaylists(): List<MediaCollection> {
        return listOf(
            com.spotkofi.app.data.model.Playlist(
                id = "mood_chill",
                title = "Chill Vibes",
                description = "Relax and unwind",
                ownerName = "SpotKofi",
                trackIds = emptyList(),
                artworkUrl = null,
                isPinned = false
            ),
            com.spotkofi.app.data.model.Playlist(
                id = "mood_energy",
                title = "Energy Boost",
                description = "Get pumped up",
                ownerName = "SpotKofi",
                trackIds = emptyList(),
                artworkUrl = null,
                isPinned = false
            ),
            com.spotkofi.app.data.model.Playlist(
                id = "mood_focus",
                title = "Focus Flow",
                description = "Music for concentration",
                ownerName = "SpotKofi",
                trackIds = emptyList(),
                artworkUrl = null,
                isPinned = false
            ),
            com.spotkofi.app.data.model.Playlist(
                id = "mood_workout",
                title = "Workout Mix",
                description = "Pump up your exercise",
                ownerName = "SpotKofi",
                trackIds = emptyList(),
                artworkUrl = null,
                isPinned = false
            )
        )
    }
}

/**
 * Browse statistics
 */
data class BrowseStats(
    val trendingTracks: Int,
    val newReleases: Int,
    val topCharts: Int,
    val featuredPlaylists: Int,
    val recommendedArtists: Int,
    val genres: Int
)

/**
 * Content recommendation
 */
data class Recommendation(
    val id: String,
    val title: String,
    val description: String,
    val type: RecommendationType,
    val items: List<Any> // Can be Tracks, Albums, or Artists
)

/**
 * Recommendation types
 */
enum class RecommendationType {
    SIMILAR,
    NEW_RELEASES,
    LOCAL,
    GENRE,
    ARTIST
}