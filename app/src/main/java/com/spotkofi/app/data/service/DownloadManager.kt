package com.spotkofi.app.data.service

import com.spotkofi.app.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Download manager for handling music downloads with queue management and progress tracking.
 * Follows patterns from music apps like InnerTune for efficient download handling.
 */
class DownloadManager(
    private val musicService: MusicService,
    private val downloadDirectory: String = "/storage/emulated/0/Music/SpotKofi"
) {
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val maxConcurrentDownloads = 3
    
    private val _downloadQueue = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloadQueue: StateFlow<List<DownloadItem>> = _downloadQueue.asStateFlow()
    
    private val _activeDownloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val activeDownloads: StateFlow<List<DownloadItem>> = _activeDownloads.asStateFlow()
    
    private val _completedDownloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val completedDownloads: StateFlow<List<DownloadItem>> = _completedDownloads.asStateFlow()
    
    private val _failedDownloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val failedDownloads: StateFlow<List<DownloadItem>> = _failedDownloads.asStateFlow()
    
    private val _totalDownloadStats = MutableStateFlow(DownloadStats())
    val totalDownloadStats: StateFlow<DownloadStats> = _totalDownloadStats.asStateFlow()
    
    init {
        // Initialize download directory
        ensureDownloadDirectoryExists()
        
        // Load existing downloads
        loadExistingDownloads()
    }
    
    /**
     * Enqueue a track for download
     */
    fun downloadTrack(track: Track, priority: DownloadPriority = DownloadPriority.NORMAL) {
        val downloadItem = DownloadItem(
            id = "download_${track.id}_${System.currentTimeMillis()}",
            track = track,
            priority = priority,
            status = DownloadManagerStatus.QUEUED,
            progress = 0,
            downloadedBytes = 0L,
            totalBytes = estimateFileSize(track),
            createdAt = System.currentTimeMillis()
        )
        
        _downloadQueue.update { currentQueue ->
            val mutableQueue = currentQueue.toMutableList()
            
            when (priority) {
                DownloadPriority.HIGH -> mutableQueue.add(0, downloadItem)
                DownloadPriority.NORMAL -> {
                    // Add after high priority items
                    val lastHighIndex = mutableQueue.indexOfLast { it.priority == DownloadPriority.HIGH }
                    mutableQueue.add(lastHighIndex + 1, downloadItem)
                }
                DownloadPriority.LOW -> mutableQueue.add(downloadItem)
            }
            
            mutableQueue.toList()
        }
        
        processDownloadQueue()
    }
    
    /**
     * Enqueue multiple tracks for download
     */
    fun downloadTracks(tracks: List<Track>, priority: DownloadPriority = DownloadPriority.NORMAL) {
        tracks.forEach { track ->
            downloadTrack(track, priority)
        }
    }
    
    /**
     * Pause a download
     */
    fun pauseDownload(downloadId: String) {
        downloadJobs[downloadId]?.cancel()
        downloadJobs.remove(downloadId)
        
        updateDownloadManagerStatus(downloadId) { current ->
            current.copy(status = DownloadManagerStatus.PAUSED)
        }
        
        processDownloadQueue()
    }
    
    /**
     * Resume a paused download
     */
    fun resumeDownload(downloadId: String) {
        updateDownloadManagerStatus(downloadId) { current ->
            current.copy(status = DownloadManagerStatus.QUEUED)
        }
        processDownloadQueue()
    }
    
    /**
     * Cancel a download and remove from queue
     */
    fun cancelDownload(downloadId: String) {
        downloadJobs[downloadId]?.cancel()
        downloadJobs.remove(downloadId)
        
        // Remove from all lists
        _downloadQueue.update { it.filterNot { item -> item.id == downloadId } }
        _activeDownloads.update { it.filterNot { item -> item.id == downloadId } }
        
        // Update stats
        _totalDownloadStats.update { stats ->
            stats.copy(cancelled = stats.cancelled + 1)
        }
    }
    
    /**
     * Retry a failed download
     */
    fun retryDownload(downloadId: String) {
        updateDownloadManagerStatus(downloadId) { current ->
            current.copy(
                status = DownloadManagerStatus.QUEUED,
                progress = 0,
                downloadedBytes = 0L,
                error = null
            )
        }
        
        // Remove from failed list
        _failedDownloads.update { it.filterNot { item -> item.id == downloadId } }
        
        processDownloadQueue()
    }
    
    /**
     * Delete a completed download
     */
    fun deleteDownload(downloadId: String): Boolean {
        val downloadItem = findDownloadItem(downloadId) ?: return false
        
        return try {
            if (downloadItem.filePath != null) {
                File(downloadItem.filePath).delete()
            }
            
            // Remove from completed list
            _completedDownloads.update { it.filterNot { item -> item.id == downloadId } }
            
            // Update stats
            _totalDownloadStats.update { stats ->
                stats.copy(deleted = stats.deleted + 1)
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get download item by ID
     */
    fun getDownloadItem(downloadId: String): DownloadItem? {
        return findDownloadItem(downloadId)
    }
    
    /**
     * Clear completed downloads
     */
    fun clearCompletedDownloads() {
        _completedDownloads.value.forEach { downloadItem ->
            if (downloadItem.filePath != null) {
                File(downloadItem.filePath).delete()
            }
        }
        
        _completedDownloads.value = emptyList()
    }
    
    /**
     * Clear failed downloads
     */
    fun clearFailedDownloads() {
        _failedDownloads.value = emptyList()
    }
    
    /**
     * Get total download stats
     */
    fun getStats(): DownloadStats {
        return _totalDownloadStats.value
    }
    
    private fun processDownloadQueue() {
        val activeCount = _activeDownloads.value.size
        val availableSlots = maxConcurrentDownloads - activeCount
        
        if (availableSlots <= 0) return
        
        val queuedItems = _downloadQueue.value
            .filter { it.status == DownloadManagerStatus.QUEUED }
            .sortedByDescending { it.priority.ordinal }
            .take(availableSlots)
        
        queuedItems.forEach { downloadItem ->
            startDownload(downloadItem)
        }
    }
    
    private fun startDownload(downloadItem: DownloadItem) {
        // Move from queue to active
        _downloadQueue.update { it.filterNot { item -> item.id == downloadItem.id } }
        _activeDownloads.update { current ->
            current + downloadItem.copy(status = DownloadManagerStatus.DOWNLOADING)
        }
        
        val downloadJob = coroutineScope.launch {
            try {
                // Simulate download process
                val totalSize = downloadItem.totalBytes
                var downloaded = 0L
                
                while (downloaded < totalSize) {
                    delay(100) // Simulate download chunks
                    
                    // Update progress (simulate 1MB chunks)
                    val chunkSize = minOf(1_000_000L, totalSize - downloaded)
                    downloaded += chunkSize
                    
                    val progress = ((downloaded.toDouble() / totalSize) * 100).toInt()
                    
                    updateDownloadManagerStatus(downloadItem.id) { current ->
                        current.copy(
                            progress = progress,
                            downloadedBytes = downloaded,
                            status = DownloadManagerStatus.DOWNLOADING
                        )
                    }
                    
                    // Check if cancelled
                    if (!downloadJobs.containsKey(downloadItem.id)) {
                        break
                    }
                }
                
                if (downloaded >= totalSize) {
                    // Download completed
                    val filePath = "${downloadDirectory}/${downloadItem.track.id}.mp3"
                    
                    updateDownloadManagerStatus(downloadItem.id) { current ->
                        current.copy(
                            status = DownloadManagerStatus.COMPLETED,
                            progress = 100,
                            downloadedBytes = totalSize,
                            filePath = filePath,
                            completedAt = System.currentTimeMillis()
                        )
                    }
                    
                    // Move from active to completed
                    _activeDownloads.update { it.filterNot { item -> item.id == downloadItem.id } }
                    _completedDownloads.update { current ->
                        current + findDownloadItem(downloadItem.id)!!
                    }
                    
                    // Update stats
                    _totalDownloadStats.update { stats ->
                        stats.copy(
                            completed = stats.completed + 1,
                            totalBytesDownloaded = stats.totalBytesDownloaded + totalSize
                        )
                    }
                }
            } catch (e: Exception) {
                // Download failed
                updateDownloadManagerStatus(downloadItem.id) { current ->
                    current.copy(
                        status = DownloadManagerStatus.FAILED,
                        error = e.message ?: "Download failed",
                        failedAt = System.currentTimeMillis()
                    )
                }
                
                // Move from active to failed
                _activeDownloads.update { it.filterNot { item -> item.id == downloadItem.id } }
                _failedDownloads.update { current ->
                    current + findDownloadItem(downloadItem.id)!!
                }
                
                // Update stats
                _totalDownloadStats.update { stats ->
                    stats.copy(failed = stats.failed + 1)
                }
            } finally {
                downloadJobs.remove(downloadItem.id)
                processDownloadQueue() // Process next in queue
            }
        }
        
        downloadJobs[downloadItem.id] = downloadJob
    }
    
    private fun updateDownloadManagerStatus(downloadId: String, updater: (DownloadItem) -> DownloadItem) {
        // Update in queue
        _downloadQueue.update { queue ->
            queue.map { if (it.id == downloadId) updater(it) else it }
        }
        
        // Update in active downloads
        _activeDownloads.update { active ->
            active.map { if (it.id == downloadId) updater(it) else it }
        }
        
        // Update in completed downloads
        _completedDownloads.update { completed ->
            completed.map { if (it.id == downloadId) updater(it) else it }
        }
        
        // Update in failed downloads
        _failedDownloads.update { failed ->
            failed.map { if (it.id == downloadId) updater(it) else it }
        }
    }
    
    private fun findDownloadItem(downloadId: String): DownloadItem? {
        return (_downloadQueue.value + _activeDownloads.value + _completedDownloads.value + _failedDownloads.value)
            .firstOrNull { it.id == downloadId }
    }
    
    private fun estimateFileSize(track: Track): Long {
        // Estimate file size based on duration (assuming 128kbps)
        val durationMinutes = track.durationMs / 60000.0
        return (durationMinutes * 1_000_000).toLong() // ~1MB per minute
    }
    
    private fun ensureDownloadDirectoryExists() {
        val directory = File(downloadDirectory)
        if (!directory.exists()) {
            directory.mkdirs()
        }
    }
    
    private fun loadExistingDownloads() {
        // In a real implementation, this would load from database or file system
        // For now, we'll simulate by checking the download directory
        coroutineScope.launch {
            val directory = File(downloadDirectory)
            if (directory.exists() && directory.isDirectory) {
                val files = directory.listFiles { file ->
                    file.extension == "mp3"
                } ?: emptyArray()
                
                val completedItems = files.map { file ->
                    DownloadItem(
                        id = "existing_${file.nameWithoutExtension}",
                        track = Track(
                            id = file.nameWithoutExtension,
                            title = file.nameWithoutExtension.replace("_", " "),
                            artistName = "Unknown Artist",
                            albumTitle = "Unknown Album",
                            durationMs = 180000L,
                            isExplicit = false
                        ),
                        priority = DownloadPriority.NORMAL,
                        status = DownloadManagerStatus.COMPLETED,
                        progress = 100,
                        downloadedBytes = file.length(),
                        totalBytes = file.length(),
                        filePath = file.absolutePath,
                        createdAt = file.lastModified(),
                        completedAt = file.lastModified()
                    )
                }
                
                _completedDownloads.value = completedItems
                
                // Update stats
                _totalDownloadStats.update { stats ->
                    stats.copy(
                        completed = completedItems.size,
                        totalBytesDownloaded = completedItems.sumOf { it.downloadedBytes }
                    )
                }
            }
        }
    }
}

/**
 * Download item representing a track download
 */
data class DownloadItem(
    val id: String,
    val track: Track,
    val priority: DownloadPriority,
    val status: DownloadManagerStatus,
    val progress: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val filePath: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val failedAt: Long? = null
)

/**
 * Download priority levels
 */
enum class DownloadPriority {
    HIGH, NORMAL, LOW
}

/**
 * Download status for download manager
 */
enum class DownloadManagerStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

/**
 * Download statistics
 */
data class DownloadStats(
    val completed: Int = 0,
    val failed: Int = 0,
    val cancelled: Int = 0,
    val deleted: Int = 0,
    val totalBytesDownloaded: Long = 0L
)