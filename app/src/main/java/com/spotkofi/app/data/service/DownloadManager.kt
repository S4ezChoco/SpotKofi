package com.spotkofi.app.data.service

import android.content.Context
import androidx.core.content.ContextCompat
import com.spotkofi.app.data.local.AppSettings
import com.spotkofi.app.data.local.AudioQuality
import com.spotkofi.app.data.local.LocalMusicStore
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.remote.YouTubeTrackResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Real app-private downloader for full-length audio streams.
 *
 * Signed provider URLs are resolved for each attempt and are never persisted.
 * The stable track metadata and progress live in [LocalMusicStore], while the
 * bytes live under app-private files storage. A completed file is therefore
 * playable with no network connection through [MusicPlayerController].
 *
 * The downloader deliberately remains the one source of truth for SpotKofi.
 * It is owned by [AppContainer], and [DownloadService] keeps the application
 * process foreground while queued work is being transferred.
 */
class DownloadManager(
    context: Context,
    private val musicService: MusicService,
    private val localStore: LocalMusicStore,
    private val settingsProvider: () -> AppSettings = { AppSettings() },
) {

    private val appContext = context.applicationContext
    private val downloadDirectory = File(appContext.filesDir, "downloads")
    private val temporaryDirectory = File(downloadDirectory, ".partial")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationLock = Any()
    private val operationGenerations = ConcurrentHashMap<String, Long>()
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val resetPartialOnStart = ConcurrentHashMap.newKeySet<String>()
    private val cleanupAfterOperation = ConcurrentHashMap.newKeySet<String>()
    private val cleanupDestinationAfterOperation = ConcurrentHashMap.newKeySet<String>()
    private val nextQueueSequence = AtomicLong(0L)
    private val persistenceQueue = Channel<PersistenceOperation>(Channel.UNLIMITED)
    private val persistenceWorker = scope.launch {
        for (operation in persistenceQueue) {
            runCatching {
                when (operation) {
                    is PersistenceOperation.Upsert -> {
                        if (isCurrentOperation(operation.item.id, operation.generation)) {
                            localStore.upsertDownload(
                                LocalMusicStore.LocalDownload(
                                    track = operation.item.track,
                                    status = operation.item.status.storageValue,
                                    progress = operation.item.progress,
                                    downloadedBytes = operation.item.downloadedBytes,
                                    totalBytes = operation.item.totalBytes,
                                    filePath = operation.item.filePath,
                                    error = operation.item.error,
                                    priority = operation.item.priority.storageValue,
                                    queueSequence = operation.item.queueSequence,
                                ),
                            )
                        }
                    }

                    is PersistenceOperation.Remove -> localStore.removeDownload(operation.trackId)
                }
            }
        }
    }
    @Volatile
    private var applicationReady = false
    @Volatile
    private var closed = false

    private val resolver = YouTubeTrackResolver()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val maxConcurrentDownloads = 2

    private val _downloadQueue = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloadQueue: StateFlow<List<DownloadItem>> = _downloadQueue.asStateFlow()

    private val _activeDownloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val activeDownloads: StateFlow<List<DownloadItem>> = _activeDownloads.asStateFlow()

    private val _completedDownloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val completedDownloads: StateFlow<List<DownloadItem>> = _completedDownloads.asStateFlow()

    private val _failedDownloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val failedDownloads: StateFlow<List<DownloadItem>> = _failedDownloads.asStateFlow()

    /** Immediate in-memory view used by UI actions; persistence is a durable mirror. */
    val downloads: StateFlow<List<DownloadItem>> = combine(
        _downloadQueue,
        _activeDownloads,
        _completedDownloads,
        _failedDownloads,
    ) { queue, active, completed, failed ->
        (queue + active + completed + failed).distinctBy { it.id }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _totalDownloadStats = MutableStateFlow(DownloadStats())
    val totalDownloadStats: StateFlow<DownloadStats> = _totalDownloadStats.asStateFlow()

    /**
     * Priority applied when a caller does not name one.
     *
     * Kept here rather than threaded through every UI call site: a screen that
     * forgot to pass the user's preference would silently queue at the wrong
     * priority, and there is no way to notice that from the outside.
     */
    @Volatile
    var defaultPriority: DownloadPriority = DownloadPriority.NORMAL

    init {
        downloadDirectory.mkdirs()
        temporaryDirectory.mkdirs()
        restorePersistedDownloads(localStore.downloadRecords.value)
        processDownloadQueue()
    }

    /**
     * Called by [SpotKofiApplication] only after its [AppContainer] property is
     * assigned. This avoids a service callback observing a half-built container
     * during application construction.
     */
    fun onApplicationReady() {
        if (closed) return
        applicationReady = true
        processDownloadQueue()
    }

    /** Performs the action appropriate for the current persisted state. */
    fun toggleDownload(track: Track, priority: DownloadPriority = defaultPriority) {
        when (val item = findByTrackId(track.id)) {
            null -> downloadTrack(track, priority)
            else -> when (item.status) {
                DownloadManagerStatus.COMPLETED -> deleteDownload(item.id)
                DownloadManagerStatus.DOWNLOADING -> pauseDownload(item.id)
                DownloadManagerStatus.PAUSED -> resumeDownload(item.id)
                DownloadManagerStatus.QUEUED -> cancelDownload(item.id)
                DownloadManagerStatus.FAILED -> retryDownload(item.id)
            }
        }
    }

    /** Enqueues a track, or resumes/retries its existing record instead of duplicating it. */
    fun downloadTrack(track: Track, priority: DownloadPriority = defaultPriority) {
        val existing = findByTrackId(track.id)
        when {
            existing?.status == DownloadManagerStatus.COMPLETED &&
                existing.filePath?.let(::File)?.isFile == true -> return

            existing?.status == DownloadManagerStatus.DOWNLOADING ||
                existing?.status == DownloadManagerStatus.QUEUED -> return

            existing?.status == DownloadManagerStatus.PAUSED -> {
                resumeDownload(existing.id)
                return
            }

            existing?.status == DownloadManagerStatus.FAILED -> {
                retryDownload(existing.id)
                return
            }
        }

        val id = downloadId(track)
        val generation = beginOperation(id).generation
        resetPartialOnStart += id
        synchronized(operationLock) {
            removeItemFromAllLocked(id)
            val item = DownloadItem(
                id = id,
                track = track,
                priority = priority,
                status = DownloadManagerStatus.QUEUED,
                progress = 0,
                downloadedBytes = 0L,
                totalBytes = estimateFileSize(track),
                queueSequence = nextQueueSequence.incrementAndGet(),
                createdAt = System.currentTimeMillis(),
            )
            _downloadQueue.update { current -> sortQueue(current + item) }
            persist(item, generation)
        }
        ensureDownloadService()
        processDownloadQueue()
    }

    fun downloadTracks(tracks: List<Track>, priority: DownloadPriority = defaultPriority) {
        tracks.forEach { downloadTrack(it, priority) }
    }

    fun pauseDownload(downloadId: String) {
        val item = findById(downloadId) ?: return
        val operation = beginOperation(downloadId, CleanupMode.DESTINATION)
        if (!operation.hadJob) fileFor(item.track).delete()
        synchronized(operationLock) {
            removeItemFromAllLocked(downloadId)
            val paused = item.copy(
                status = DownloadManagerStatus.PAUSED,
                filePath = null,
            )
            _downloadQueue.update { current -> sortQueue(current + paused) }
            persist(paused, operation.generation)
        }
    }

    fun resumeDownload(downloadId: String) {
        val item = findById(downloadId) ?: return
        val operation = beginOperation(downloadId, CleanupMode.DESTINATION)
        if (!operation.hadJob) fileFor(item.track).delete()
        synchronized(operationLock) {
            removeItemFromAllLocked(downloadId)
            val queued = item.copy(
                status = DownloadManagerStatus.QUEUED,
                error = null,
                filePath = null,
            )
            _downloadQueue.update { current -> sortQueue(current + queued) }
            persist(queued, operation.generation)
        }
        ensureDownloadService()
        processDownloadQueue()
    }

    fun cancelDownload(downloadId: String) {
        val item = findById(downloadId)
        val operation = beginOperation(downloadId, CleanupMode.ALL)
        if (item != null && !operation.hadJob) cleanupDownloadFiles(item)
        synchronized(operationLock) {
            removeItemFromAllLocked(downloadId)
            item?.track?.id?.let { trackId -> removePersisted(trackId) }
            _totalDownloadStats.update { it.copy(cancelled = it.cancelled + 1) }
        }
        processDownloadQueue()
    }

    fun retryDownload(downloadId: String) {
        val item = findById(downloadId) ?: return
        val operation = beginOperation(downloadId)
        resetPartialOnStart += downloadId
        synchronized(operationLock) {
            removeItemFromAllLocked(downloadId)
            val retry = item.copy(
                status = DownloadManagerStatus.QUEUED,
                progress = 0,
                downloadedBytes = 0L,
                error = null,
                filePath = null,
            )
            _downloadQueue.update { current -> sortQueue(current + retry) }
            persist(retry, operation.generation)
        }
        ensureDownloadService()
        processDownloadQueue()
    }

    fun deleteDownload(downloadId: String): Boolean {
        val item = findById(downloadId) ?: return false
        val operation = beginOperation(downloadId, CleanupMode.ALL)
        if (!operation.hadJob) cleanupDownloadFiles(item)
        synchronized(operationLock) {
            removeItemFromAllLocked(downloadId)
            removePersisted(item.track.id)
            _totalDownloadStats.update { it.copy(deleted = it.deleted + 1) }
        }
        processDownloadQueue()
        return true
    }

    fun getDownloadItem(downloadId: String): DownloadItem? = findById(downloadId)

    fun getDownloadItemForTrack(trackId: String): DownloadItem? = findByTrackId(trackId)

    /**
     * Deletes every finished download.
     *
     * The file work is handed to this manager's IO scope rather than run inline.
     * Deleting one file from a row tap is quick enough to do on the spot, but a
     * library-wide clear is an unbounded number of deletes, and doing that on the
     * caller's thread would freeze the screen that asked for it.
     */
    fun clearCompletedDownloads() {
        val items = _completedDownloads.value
        scope.launch {
            items.forEach { item ->
                cleanupDownloadFiles(item)
                removeItemFromAll(item.id)
                removePersisted(item.track.id)
            }
            updateStats()
        }
    }

    /** As [clearCompletedDownloads], for the entries that failed. */
    fun clearFailedDownloads() {
        val items = _failedDownloads.value
        scope.launch {
            items.forEach { item ->
                cleanupDownloadFiles(item)
                removeItemFromAll(item.id)
                removePersisted(item.track.id)
            }
            updateStats()
        }
    }

    fun getStats(): DownloadStats = _totalDownloadStats.value

    fun close() {
        synchronized(operationLock) {
            closed = true
            operationGenerations.keys.toList().forEach(::nextGenerationLocked)
            activeCalls.values.forEach(Call::cancel)
            downloadJobs.values.forEach(Job::cancel)
        }
        persistenceQueue.close()
        scope.cancel()
        httpClient.connectionPool.evictAll()
        httpClient.dispatcher.executorService.shutdown()
    }

    private fun restorePersistedDownloads(records: List<LocalMusicStore.LocalDownload>) {
        val queue = mutableListOf<DownloadItem>()
        val completed = mutableListOf<DownloadItem>()
        val failed = mutableListOf<DownloadItem>()

        nextQueueSequence.set(records.maxOfOrNull { it.queueSequence } ?: 0L)
        records.forEach { record ->
            val restoredSequence = record.queueSequence.takeIf { it > 0L }
                ?: nextQueueSequence.incrementAndGet()
            val item = toDownloadItem(record).copy(queueSequence = restoredSequence)
            when (item.status) {
                DownloadManagerStatus.COMPLETED -> {
                    if (item.filePath?.let(::File)?.isFile == true) {
                        completed += item
                    } else {
                        // A process can die between writing bytes and committing the
                        // final record. Treat a missing destination as resumable.
                        item.filePath?.let(::File)?.delete()
                        queue += item.copy(
                            status = DownloadManagerStatus.QUEUED,
                            progress = 0,
                            downloadedBytes = 0L,
                            filePath = null,
                            error = null,
                        )
                    }
                }

                DownloadManagerStatus.FAILED -> failed += item
                DownloadManagerStatus.PAUSED -> queue += item
                DownloadManagerStatus.QUEUED,
                DownloadManagerStatus.DOWNLOADING,
                -> {
                    // DOWNLOADING means the previous process was interrupted;
                    // automatically continue it from the .part file.
                    queue += item.copy(status = DownloadManagerStatus.QUEUED, error = null)
                }
            }
        }

        _downloadQueue.value = sortQueue(queue)
        _completedDownloads.value = completed
        _failedDownloads.value = failed
        updateStats()
    }

    private fun processDownloadQueue() {
        if (closed) return
        val slots = maxConcurrentDownloads - _activeDownloads.value
            .count { it.status == DownloadManagerStatus.DOWNLOADING }
        if (slots > 0) {
            _downloadQueue.value
                .filter { it.status == DownloadManagerStatus.QUEUED }
                .take(slots)
                .forEach(::startDownload)
        }
        ensureDownloadService()
    }

    private fun startDownload(item: DownloadItem) {
        synchronized(operationLock) {
            if (downloadJobs.containsKey(item.id)) return
            if (findById(item.id)?.status != DownloadManagerStatus.QUEUED) return

            val generation = nextGenerationLocked(item.id)
            // Capture quality for the entire transfer. If the setting changes while
            // a signed URL is being fetched, this operation must not change stream
            // identity halfway through its partial file.
            val quality = settingsProvider().audioQuality
            if (resetPartialOnStart.remove(item.id)) {
                partialFilesFor(item.track).forEach(File::delete)
                fileFor(item.track).delete()
            }

            removeItemFromAllLocked(item.id)
            val partialBytes = temporaryFileFor(item.track, quality).length().coerceAtLeast(0L)
            val active = item.copy(
                status = DownloadManagerStatus.DOWNLOADING,
                progress = progressOf(partialBytes, item.totalBytes),
                downloadedBytes = partialBytes,
                filePath = null,
                error = null,
            )
            _activeDownloads.update { it + active }
            persist(active, generation)

            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    ensureOperationCurrent(item.id, generation)
                    val resolved = resolveStream(item.track, quality)
                        ?: throw IOException("No full-length stream was found")
                    ensureOperationCurrent(item.id, generation)
                    val result = downloadToFile(active, resolved.url, generation, quality)
                    ensureOperationCurrent(item.id, generation)
                    val destination = fileFor(item.track)
                    val completed = active.copy(
                        status = DownloadManagerStatus.COMPLETED,
                        progress = 100,
                        downloadedBytes = result.downloadedBytes,
                        totalBytes = result.totalBytes.coerceAtLeast(result.downloadedBytes),
                        filePath = destination.absolutePath,
                        error = null,
                        completedAt = System.currentTimeMillis(),
                    )
                    synchronized(operationLock) {
                        if (!isCurrentOperationLocked(item.id, generation)) return@launch
                        removeItemFromAllLocked(item.id)
                        _completedDownloads.update { it + completed }
                        persist(completed, generation)
                        updateStats()
                    }
                } catch (cancelled: CancellationException) {
                    // Pause/cancel owns the visible state transition. A partial
                    // file remains so resume can send a Range request from its size.
                } catch (failure: Exception) {
                    if (!currentCoroutineContext().isActive ||
                        !isCurrentOperation(item.id, generation)
                    ) {
                        return@launch
                    }
                    val partialBytes = temporaryFileFor(item.track, quality).length()
                    val failed = active.copy(
                        status = DownloadManagerStatus.FAILED,
                        progress = progressOf(partialBytes, active.totalBytes),
                        downloadedBytes = partialBytes,
                        error = failure.message ?: "Download failed",
                        failedAt = System.currentTimeMillis(),
                    )
                    synchronized(operationLock) {
                        if (!isCurrentOperationLocked(item.id, generation)) return@launch
                        removeItemFromAllLocked(item.id)
                        _failedDownloads.update { it + failed }
                        persist(failed, generation)
                        updateStats()
                    }
                } finally {
                    val runningJob = currentCoroutineContext()[Job]
                    synchronized(operationLock) {
                        if (runningJob != null) downloadJobs.remove(item.id, runningJob)
                        if (cleanupAfterOperation.remove(item.id)) {
                            cleanupDownloadFiles(item)
                        }
                        if (cleanupDestinationAfterOperation.remove(item.id)) {
                            fileFor(item.track).delete()
                        }
                    }
                    processDownloadQueue()
                }
            }
            downloadJobs[item.id] = job
            job.start()
        }
    }

    /** Resolves a fresh signed URL, with the same direct-URL fallback as playback. */
    private suspend fun resolveStream(
        track: Track,
        quality: AudioQuality,
    ): ResolvedDownload? {
        val videoId = withContext(Dispatchers.IO) { resolver.resolveVideoId(track) }
        if (videoId != null) {
            val url = withContext(Dispatchers.IO) {
                musicService.getStreamUrl(videoId, quality)
            }
            url?.trim()?.takeIf { it.isNotEmpty() }?.let {
                return ResolvedDownload(videoId, it)
            }
        }

        return track.audioUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { ResolvedDownload(videoId = null, url = it) }
    }

    /**
     * Transfers a stream into a private .part file. A fresh provider URL is used
     * for every invocation, while the byte offset survives pause/process death.
     */
    private suspend fun downloadToFile(
        item: DownloadItem,
        url: String,
        generation: Long,
        quality: AudioQuality,
    ): TransferResult {
        val partial = temporaryFileFor(item.track, quality)
        val destination = fileFor(item.track)
        partial.parentFile?.mkdirs()
        destination.parentFile?.mkdirs()

        var existingBytes = partial.length().coerceAtLeast(0L)
        var retriedFromStart = false

        while (true) {
            ensureOperationCurrent(item.id, generation)
            val requestedOffset = existingBytes
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Encoding", "identity")
            if (requestedOffset > 0L) {
                requestBuilder.header("Range", "bytes=$requestedOffset-")
            }

            val step = executeHttpCall(item.id, generation, requestBuilder.build()) { response ->
                if (response.code == 416 && requestedOffset > 0L && !retriedFromStart) {
                    // The signed URL/server rejected the old offset. Re-request
                    // from zero rather than publishing a corrupt offline file.
                    partial.delete()
                    existingBytes = 0L
                    retriedFromStart = true
                    return@executeHttpCall TransferStep.RestartFromZero
                }

                if (!response.isSuccessful) {
                    throw IOException("Audio request failed (${response.code})")
                }
                val body = response.body
                    ?: throw IOException("Audio response had no body")
                val contentRange = if (response.code == 206) {
                    parseContentRange(response.header("Content-Range"))
                } else {
                    null
                }
                val responseBytes = body.contentLength().takeIf { it >= 0L }
                if (response.code == 206) {
                    val expectedRangeBytes = contentRange?.let { it.end - it.start + 1L }
                    val validRange = contentRange != null &&
                        contentRange.totalBytes != null &&
                        contentRange.start == requestedOffset &&
                        contentRange.end >= contentRange.start &&
                        contentRange.totalBytes > contentRange.end &&
                        (responseBytes == null || responseBytes == expectedRangeBytes)
                    if (!validRange) {
                        if (!retriedFromStart) {
                            partial.delete()
                            existingBytes = 0L
                            retriedFromStart = true
                            return@executeHttpCall TransferStep.RestartFromZero
                        }
                        throw IOException("Invalid Content-Range in audio response")
                    }
                }

                val append = requestedOffset > 0L && response.code == 206
                if (!append) {
                    existingBytes = 0L
                    partial.delete()
                }

                val declaredTotal = when {
                    contentRange?.totalBytes != null -> contentRange.totalBytes
                    responseBytes != null -> if (append) {
                        requestedOffset + responseBytes
                    } else {
                        responseBytes
                    }
                    else -> null
                }
                val progressTotal = declaredTotal ?: item.totalBytes
                var downloadedBytes = existingBytes
                var lastPublished = 0L
                publishProgress(item, generation, downloadedBytes, progressTotal, force = true)

                FileOutputStream(partial, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (isCurrentOperation(item.id, generation)) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            val now = System.currentTimeMillis()
                            if (now - lastPublished >= PROGRESS_INTERVAL_MS) {
                                publishProgress(item, generation, downloadedBytes, progressTotal)
                                lastPublished = now
                            }
                        }
                    }
                    if (!isCurrentOperation(item.id, generation)) throw CancellationException()
                    output.flush()
                }

                if (contentRange != null) {
                    val expectedRangeBytes = contentRange.end - contentRange.start + 1L
                    if (downloadedBytes - requestedOffset != expectedRangeBytes) {
                        throw IOException("Audio response length did not match Content-Range")
                    }
                }
                if (downloadedBytes <= 0L) throw IOException("No audio bytes were downloaded")
                publishProgress(item, generation, downloadedBytes, progressTotal, force = true)

                val needsMore = contentRange?.totalBytes?.let { downloadedBytes < it } == true ||
                    (contentRange == null && responseBytes != null && downloadedBytes < responseBytes)
                if (needsMore) {
                    existingBytes = downloadedBytes
                    return@executeHttpCall TransferStep.ContinueWithRange
                }

                if (!promotePartialFile(partial, destination)) {
                    throw IOException("Could not finalize the downloaded audio file")
                }
                TransferStep.Complete(
                    TransferResult(
                        downloadedBytes = downloadedBytes,
                        totalBytes = declaredTotal?.coerceAtLeast(downloadedBytes)
                            ?: downloadedBytes,
                    ),
                )
            }

            when (step) {
                TransferStep.RestartFromZero -> continue
                TransferStep.ContinueWithRange -> continue
                is TransferStep.Complete -> return step.result
            }
        }
    }

    private fun publishProgress(
        item: DownloadItem,
        generation: Long,
        downloadedBytes: Long,
        totalBytes: Long,
        force: Boolean = false,
    ) {
        synchronized(operationLock) {
            if (!isCurrentOperationLocked(item.id, generation)) return
            val current = findById(item.id)
                ?.takeIf { it.status == DownloadManagerStatus.DOWNLOADING }
                ?: return
            val progress = progressOf(downloadedBytes, totalBytes)
            if (!force && current.progress == progress &&
                current.downloadedBytes == downloadedBytes
            ) return
            val updated = current.copy(
                progress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes.coerceAtLeast(current.totalBytes),
            )
            replaceItemLocked(item.id, updated)
            persist(updated, generation)
        }
    }

    private fun promotePartialFile(partial: File, destination: File): Boolean = try {
        try {
            Files.move(
                partial.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                partial.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        destination.isFile && destination.length() > 0L
    } catch (_: Exception) {
        false
    }

    private fun persist(item: DownloadItem, generation: Long) {
        persistenceQueue.trySend(PersistenceOperation.Upsert(item, generation))
    }

    private fun removePersisted(trackId: String) {
        persistenceQueue.trySend(PersistenceOperation.Remove(trackId))
    }

    private fun replaceItemLocked(id: String, replacement: DownloadItem) {
        _downloadQueue.update { list -> list.map { if (it.id == id) replacement else it } }
        _activeDownloads.update { list -> list.map { if (it.id == id) replacement else it } }
        _completedDownloads.update { list -> list.map { if (it.id == id) replacement else it } }
        _failedDownloads.update { list -> list.map { if (it.id == id) replacement else it } }
    }

    private fun removeItemFromAll(id: String) {
        synchronized(operationLock) {
            removeItemFromAllLocked(id)
        }
    }

    private fun removeItemFromAllLocked(id: String) {
        _downloadQueue.update { it.filterNot { item -> item.id == id } }
        _activeDownloads.update { it.filterNot { item -> item.id == id } }
        _completedDownloads.update { it.filterNot { item -> item.id == id } }
        _failedDownloads.update { it.filterNot { item -> item.id == id } }
    }

    private fun findById(id: String): DownloadItem? =
        (_downloadQueue.value + _activeDownloads.value + _completedDownloads.value + _failedDownloads.value)
            .firstOrNull { it.id == id }

    private fun findByTrackId(trackId: String): DownloadItem? =
        (_downloadQueue.value + _activeDownloads.value + _completedDownloads.value + _failedDownloads.value)
            .firstOrNull { it.track.id == trackId }

    private fun toDownloadItem(download: LocalMusicStore.LocalDownload): DownloadItem = DownloadItem(
        id = downloadId(download.track),
        track = download.track,
        priority = download.priority.toDownloadPriority(),
        status = download.status.toDownloadManagerStatusOrNull() ?: DownloadManagerStatus.QUEUED,
        progress = download.progress,
        downloadedBytes = download.downloadedBytes,
        totalBytes = download.totalBytes,
        filePath = download.filePath,
        error = download.error,
        queueSequence = download.queueSequence,
    )

    private fun updateStats() {
        _totalDownloadStats.value = DownloadStats(
            completed = _completedDownloads.value.size,
            failed = _failedDownloads.value.size,
            cancelled = _totalDownloadStats.value.cancelled,
            deleted = _totalDownloadStats.value.deleted,
            totalBytesDownloaded = _completedDownloads.value.sumOf { it.downloadedBytes },
        )
    }

    private fun hasTransferWork(): Boolean =
        _downloadQueue.value.any { it.status == DownloadManagerStatus.QUEUED } ||
            _activeDownloads.value.any { it.status == DownloadManagerStatus.DOWNLOADING }

    private fun ensureDownloadService() {
        if (closed || !applicationReady || !hasTransferWork()) return
        runCatching {
            ContextCompat.startForegroundService(
                appContext,
                DownloadService.intent(appContext),
            )
        }
    }

    private fun beginOperation(
        downloadId: String,
        cleanupMode: CleanupMode = CleanupMode.NONE,
    ): OperationStart = synchronized(operationLock) {
        val hadJob = downloadJobs.containsKey(downloadId)
        val generation = nextGenerationLocked(downloadId)
        if (hadJob) {
            when (cleanupMode) {
                CleanupMode.ALL -> cleanupAfterOperation += downloadId
                CleanupMode.DESTINATION -> cleanupDestinationAfterOperation += downloadId
                CleanupMode.NONE -> Unit
            }
        }
        OperationStart(generation, hadJob)
    }

    private fun nextGenerationLocked(downloadId: String): Long {
        val generation = (operationGenerations[downloadId] ?: 0L) + 1L
        operationGenerations[downloadId] = generation
        activeCalls.remove(downloadId)?.cancel()
        downloadJobs[downloadId]?.cancel()
        return generation
    }

    private fun isCurrentOperation(downloadId: String, generation: Long): Boolean =
        synchronized(operationLock) { isCurrentOperationLocked(downloadId, generation) }

    private fun isCurrentOperationLocked(downloadId: String, generation: Long): Boolean =
        operationGenerations[downloadId] == generation

    private suspend fun ensureOperationCurrent(downloadId: String, generation: Long) {
        currentCoroutineContext().ensureActive()
        if (!isCurrentOperation(downloadId, generation)) {
            throw CancellationException("Download operation was superseded")
        }
    }

    private fun cleanupDownloadFiles(item: DownloadItem) {
        item.filePath?.let(::File)?.delete()
        fileFor(item.track).delete()
        partialFilesFor(item.track).forEach(File::delete)
    }

    private fun sortQueue(items: List<DownloadItem>): List<DownloadItem> = items.sortedWith(
        compareBy<DownloadItem> { it.priority.queueRank }
            .thenBy { it.queueSequence }
            .thenBy { it.createdAt },
    )

    private fun parseContentRange(value: String?): ContentRange? {
        val match = CONTENT_RANGE_PATTERN.matchEntire(value?.trim().orEmpty()) ?: return null
        val total = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull()
            ?: return null
        return ContentRange(
            start = match.groupValues[1].toLongOrNull() ?: return null,
            end = match.groupValues[2].toLongOrNull() ?: return null,
            totalBytes = total,
        )
    }

    private fun progressOf(downloadedBytes: Long, totalBytes: Long): Int =
        if (totalBytes <= 0L) 0 else ((downloadedBytes * 100L) / totalBytes)
            .toInt()
            .coerceIn(0, 99)

    private fun downloadId(track: Track): String = "download_${track.id}"

    private fun fileFor(track: Track): File = File(downloadDirectory, "${safeKey(track)}.audio")

    private fun temporaryFileFor(track: Track, quality: AudioQuality): File = File(
        temporaryDirectory,
        "${safeKey(track)}.${quality.key}.part",
    )

    /** Includes the legacy unqualified path so cancellation cleans old app data too. */
    private fun partialFilesFor(track: Track): List<File> =
        AudioQuality.entries.map { temporaryFileFor(track, it) } +
            File(temporaryDirectory, "${safeKey(track)}.part")

    private fun safeKey(track: Track): String = (track.videoId ?: track.id)
        .replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun estimateFileSize(track: Track): Long =
        ((track.durationMs.coerceAtLeast(60_000L) / 60_000.0) * 1_000_000L).toLong()

    private val DownloadManagerStatus.storageValue: String
        get() = name.lowercase()

    private val DownloadPriority.storageValue: Int
        get() = when (this) {
            DownloadPriority.HIGH -> 0
            DownloadPriority.NORMAL -> 1
            DownloadPriority.LOW -> 2
        }

    private val DownloadPriority.queueRank: Int
        get() = storageValue

    private fun Int.toDownloadPriority(): DownloadPriority = when (this) {
        0 -> DownloadPriority.HIGH
        2 -> DownloadPriority.LOW
        else -> DownloadPriority.NORMAL
    }

    private enum class CleanupMode {
        NONE,
        DESTINATION,
        ALL,
    }

    private data class OperationStart(
        val generation: Long,
        val hadJob: Boolean,
    )

    private sealed interface PersistenceOperation {
        data class Upsert(val item: DownloadItem, val generation: Long) : PersistenceOperation
        data class Remove(val trackId: String) : PersistenceOperation
    }

    private sealed interface TransferStep {
        data object RestartFromZero : TransferStep
        data object ContinueWithRange : TransferStep
        data class Complete(val result: TransferResult) : TransferStep
    }

    private data class ContentRange(
        val start: Long,
        val end: Long,
        val totalBytes: Long?,
    )

    private data class ResolvedDownload(
        val videoId: String?,
        val url: String,
    )

    private data class TransferResult(
        val downloadedBytes: Long,
        val totalBytes: Long,
    )

    private companion object {
        val CONTENT_RANGE_PATTERN = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)", RegexOption.IGNORE_CASE)
        const val USER_AGENT = "SpotKofi/1.0 (Android)"
        const val BUFFER_SIZE = 32 * 1024
        const val PROGRESS_INTERVAL_MS = 350L
    }

    /** Keeps an OkHttp call cancellable for the entire response-body transfer. */
    private suspend inline fun <T> executeHttpCall(
        downloadId: String,
        generation: Long,
        request: Request,
        block: (Response) -> T,
    ): T {
        val call = httpClient.newCall(request)
        if (!registerCall(downloadId, generation, call)) {
            call.cancel()
            throw CancellationException("Download operation was superseded")
        }
        return try {
            call.execute().use(block)
        } finally {
            activeCalls.remove(downloadId, call)
        }
    }

    private fun registerCall(downloadId: String, generation: Long, call: Call): Boolean =
        synchronized(operationLock) {
            if (!isCurrentOperationLocked(downloadId, generation)) {
                false
            } else {
                activeCalls[downloadId] = call
                if (isCurrentOperationLocked(downloadId, generation)) {
                    true
                } else {
                    activeCalls.remove(downloadId, call)
                    call.cancel()
                    false
                }
            }
        }
}

/** Converts the store's durable lowercase status into the UI/domain enum. */
fun String.toDownloadManagerStatusOrNull(): DownloadManagerStatus? = when (lowercase()) {
    "completed" -> DownloadManagerStatus.COMPLETED
    "downloading" -> DownloadManagerStatus.DOWNLOADING
    "paused" -> DownloadManagerStatus.PAUSED
    "failed" -> DownloadManagerStatus.FAILED
    "queued" -> DownloadManagerStatus.QUEUED
    else -> null
}

/** Download item exposed to Library and download-management UI. */
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
    val queueSequence: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val failedAt: Long? = null,
)

enum class DownloadPriority { HIGH, NORMAL, LOW }

enum class DownloadManagerStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

data class DownloadStats(
    val completed: Int = 0,
    val failed: Int = 0,
    val cancelled: Int = 0,
    val deleted: Int = 0,
    val totalBytesDownloaded: Long = 0L,
)
