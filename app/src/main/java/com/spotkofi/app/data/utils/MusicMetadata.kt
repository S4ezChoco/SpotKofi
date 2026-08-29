package com.spotkofi.app.data.utils

import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.Track
import java.util.UUID

/**
 * Utilities for music metadata and ID generation
 */
object MusicMetadata {
    
    /**
     * Generate a unique ID for music entities
     */
    fun generateId(prefix: String = ""): String {
        return if (prefix.isEmpty()) {
            UUID.randomUUID().toString().replace("-", "").substring(0, 12)
        } else {
            "${prefix}_${UUID.randomUUID().toString().replace("-", "").substring(0, 8)}"
        }
    }
    
    /**
     * Format duration in milliseconds to human-readable format
     */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
    
    /**
     * Format file size in bytes to human-readable format
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
    
    /**
     * Estimate file size based on duration and bitrate
     */
    fun estimateFileSize(durationMs: Long, bitrateKbps: Int = 128): Long {
        val durationSeconds = durationMs / 1000.0
        return (durationSeconds * bitrateKbps * 1024 / 8).toLong()
    }
    
    /**
     * Extract year from release date string
     */
    fun extractYear(releaseDate: String?): Int? {
        if (releaseDate.isNullOrEmpty()) return null
        
        return try {
            // Try to extract year from various date formats
            val yearPattern = Regex("\\b(19|20)\\d{2}\\b")
            yearPattern.find(releaseDate)?.value?.toInt()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get genre color for UI display
     */
    fun getGenreColor(genre: String?): Long {
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
        
        return genreColors[genre] ?: 0xFF607D8B // Default gray
    }
    
    /**
     * Get genre icon resource
     */
    fun getGenreIcon(genre: String?): String {
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
    
    /**
     * Validate music metadata
     */
    fun validateTrack(track: Track): Boolean {
        return track.id.isNotBlank() &&
                track.title.isNotBlank() &&
                track.artistName.isNotBlank() &&
                track.durationMs > 0
    }
    
    fun validateAlbum(album: Album): Boolean {
        return album.id.isNotBlank() &&
                album.title.isNotBlank() &&
                album.artistName.isNotBlank() &&
                album.trackCount >= 0
    }
    
    fun validateArtist(artist: Artist): Boolean {
        return artist.id.isNotBlank() &&
                artist.name.isNotBlank()
    }
    
    /**
     * Generate a fallback artwork URL based on entity type and ID
     */
    fun generateFallbackArtworkUrl(entityId: String, entityType: String): String {
        // In a real app, this might use a service like ui-avatars.com or generate colors
        val colors = listOf("FF6B6B", "4ECDC4", "45B7D1", "96CEB4", "FFEAA7", "DDA0DD", "98D8C8")
        val colorIndex = entityId.hashCode().mod(colors.size)
        val color = colors[colorIndex]
        
        return when (entityType) {
            "track" -> "https://ui-avatars.com/api/?name=${entityId}&background=$color&color=fff&size=256"
            "album" -> "https://ui-avatars.com/api/?name=Album&background=$color&color=fff&size=512"
            "artist" -> "https://ui-avatars.com/api/?name=Artist&background=$color&color=fff&size=512"
            else -> "https://ui-avatars.com/api/?name=Music&background=$color&color=fff&size=256"
        }
    }
    
    /**
     * Calculate audio quality based on bitrate
     */
    fun getAudioQuality(bitrateKbps: Int): String {
        return when {
            bitrateKbps >= 320 -> "Hi-Fi"
            bitrateKbps >= 256 -> "High"
            bitrateKbps >= 192 -> "Medium"
            bitrateKbps >= 128 -> "Standard"
            else -> "Low"
        }
    }
    
    /**
     * Parse lyrics from various formats
     */
    fun parseLyrics(rawLyrics: String): List<LyricsLine> {
        val lines = rawLyrics.lines()
        val lyricsLines = mutableListOf<LyricsLine>()
        
        var currentTime = 0L
        var currentText = ""
        
        val timePattern = Regex("\\[(\\d+):(\\d+\\.?\\d*)\\]")
        
        for (line in lines) {
            val timeMatch = timePattern.find(line)
            if (timeMatch != null) {
                // Save previous line if exists
                if (currentText.isNotBlank()) {
                    lyricsLines.add(LyricsLine(currentTime, currentText))
                }
                
                // Parse new time
                val minutes = timeMatch.groupValues[1].toInt()
                val seconds = timeMatch.groupValues[2].toDouble()
                currentTime = (minutes * 60 + seconds).toLong() * 1000
                
                // Extract text after time tag
                currentText = line.substringAfter("]").trim()
            } else if (line.isNotBlank()) {
                // Continuation of previous line or standalone line
                if (currentText.isNotEmpty()) {
                    currentText += "\n$line"
                } else {
                    // Standalone line without timestamp
                    lyricsLines.add(LyricsLine(0, line))
                }
            }
        }
        
        // Add last line
        if (currentText.isNotBlank()) {
            lyricsLines.add(LyricsLine(currentTime, currentText))
        }
        
        return lyricsLines
    }
    
    /**
     * Calculate BPM (beats per minute) from audio analysis (simplified)
     */
    fun estimateBPM(durationMs: Long, genre: String?): Int {
        val baseBPM = when (genre?.lowercase()) {
            "rock", "pop" -> 120
            "hip hop", "r&b" -> 90
            "jazz" -> 130
            "electronic" -> 128
            "classical" -> 60
            "reggae" -> 80
            "country" -> 100
            else -> 120
        }
        
        // Add some variation based on duration
        val durationMinutes = durationMs / 60000.0
        val variation = (durationMinutes * 10).toInt() % 20
        
        return (baseBPM + variation - 10).coerceIn(60, 180)
    }
}

/**
 * Lyrics line with timestamp
 */
data class LyricsLine(
    val timestampMs: Long,
    val text: String
)

/**
 * Audio format information
 */
data class AudioFormat(
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val channels: Int,
    val codec: String
) {
    val quality: String get() = MusicMetadata.getAudioQuality(bitrateKbps)
    val isHighQuality: Boolean get() = bitrateKbps >= 256
}