package com.spotkofi.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotkofi.app.data.model.SearchResults
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.service.MusicService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for search functionality with debouncing and suggestions
 */
class SearchViewModel(
    private val musicService: MusicService
) : ViewModel() {
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _searchResults = MutableStateFlow<SearchResults?>(null)
    val searchResults: StateFlow<SearchResults?> = _searchResults.asStateFlow()
    
    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    
    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()
    
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()
    
    private var searchJob: Job? = null
    private var suggestionsJob: Job? = null
    
    init {
        loadRecentSearches()
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        
        // Cancel previous search job
        searchJob?.cancel()
        
        if (query.isNotEmpty()) {
            // Update suggestions immediately
            updateSuggestions(query)
            
            // Debounce search execution
            searchJob = viewModelScope.launch {
                delay(300) // 300ms debounce
                performSearch(query)
            }
        } else {
            _searchResults.value = null
            _searchSuggestions.value = emptyList()
        }
    }
    
    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = null
        _searchSuggestions.value = emptyList()
        _searchError.value = null
        searchJob?.cancel()
        suggestionsJob?.cancel()
    }
    
    fun performSearch(query: String) {
        if (query.isEmpty()) return
        
        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            
            try {
                val results = musicService.search(query)
                _searchResults.value = results
                
                // Save to recent searches if we got results
                if (!results.isEmpty) {
                    saveToRecentSearches(query)
                }
            } catch (e: Exception) {
                _searchError.value = "Search failed: ${e.message}"
                _searchResults.value = null
            } finally {
                _isSearching.value = false
            }
        }
    }
    
    fun searchTracks(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val tracks = musicService.searchTracks(query)
                _searchResults.value = SearchResults(tracks = tracks)
                
                if (tracks.isNotEmpty()) {
                    saveToRecentSearches(query)
                }
            } catch (e: Exception) {
                _searchError.value = "Failed to search tracks: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }
    
    fun searchAlbums(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val albums = musicService.searchAlbums(query)
                _searchResults.value = SearchResults(collections = albums)
                
                if (albums.isNotEmpty()) {
                    saveToRecentSearches(query)
                }
            } catch (e: Exception) {
                _searchError.value = "Failed to search albums: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }
    
    fun searchArtists(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val artists = musicService.searchArtists(query)
                _searchResults.value = SearchResults(collections = artists)
                
                if (artists.isNotEmpty()) {
                    saveToRecentSearches(query)
                }
            } catch (e: Exception) {
                _searchError.value = "Failed to search artists: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }
    
    fun selectSuggestion(suggestion: String) {
        _searchQuery.value = suggestion
        performSearch(suggestion)
    }
    
    fun clearRecentSearches() {
        viewModelScope.launch {
            _recentSearches.value = emptyList()
            // In a real app, you would clear from persistence here
        }
    }
    
    private fun updateSuggestions(query: String) {
        suggestionsJob?.cancel()
        
        suggestionsJob = viewModelScope.launch {
            delay(150) // 150ms debounce for suggestions
            
            if (query.isNotEmpty()) {
                try {
                    val suggestions = musicService.getSearchSuggestions(query)
                    _searchSuggestions.value = suggestions
                } catch (e: Exception) {
                    _searchSuggestions.value = emptyList()
                }
            } else {
                _searchSuggestions.value = emptyList()
            }
        }
    }
    
    private fun loadRecentSearches() {
        viewModelScope.launch {
            // In a real app, you would load from persistence
            // For now, use mock recent searches
            _recentSearches.value = listOf(
                "pop music",
                "rock classics",
                "jazz piano",
                "hip hop 2024"
            )
        }
    }
    
    private fun saveToRecentSearches(query: String) {
        viewModelScope.launch {
            val current = _recentSearches.value.toMutableList()
            
            // Remove if already exists
            current.remove(query)
            
            // Add to beginning
            current.add(0, query)
            
            // Keep only last 10 searches
            if (current.size > 10) {
                current.removeLast()
            }
            
            _recentSearches.value = current
            
            // In a real app, you would save to persistence here
        }
    }
    
    // Helper functions for UI state
    fun hasSearchResults(): Boolean {
        return searchResults.value?.let { !it.isEmpty } ?: false
    }
    
    fun getTrackCount(): Int {
        return searchResults.value?.tracks?.size ?: 0
    }
    
    fun getCollectionCount(): Int {
        return searchResults.value?.collections?.size ?: 0
    }
    
    fun getTracks(): List<Track> {
        return searchResults.value?.tracks ?: emptyList()
    }
    
    fun getCollections(): List<com.spotkofi.app.data.model.MediaCollection> {
        return searchResults.value?.collections ?: emptyList()
    }
}