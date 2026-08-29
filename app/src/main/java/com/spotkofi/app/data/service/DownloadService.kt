package com.spotkofi.app.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.spotkofi.app.R
import com.spotkofi.app.SpotKofiApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the application process foreground while the app-private downloader has
 * queued or active work. The actual queue remains in [DownloadManager], so there
 * is no second download state machine inside this service.
 */
class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private lateinit var downloadManager: DownloadManager

    override fun onCreate() {
        super.onCreate()
        downloadManager = (application as SpotKofiApplication).container.downloadManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(emptyList(), emptyList()))

        observeJob = serviceScope.launch {
            // Give AppContainer/DownloadManager time to restore persisted work
            // before deciding that a newly-created service is idle.
            delay(750L)
            combine(
                downloadManager.downloadQueue,
                downloadManager.activeDownloads,
            ) { queue, active ->
                queue.filter { it.status == DownloadManagerStatus.QUEUED } to
                    active.filter { it.status == DownloadManagerStatus.DOWNLOADING }
            }.collect { (queue, active) ->
                updateNotification(queue, active)
                if (queue.isEmpty() && active.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observeJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun updateNotification(
        queue: List<DownloadItem>,
        active: List<DownloadItem>,
    ) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(queue, active))
    }

    private fun buildNotification(
        queue: List<DownloadItem>,
        active: List<DownloadItem>,
    ): Notification {
        val current = active.firstOrNull()
        val title = current?.track?.title ?: queue.firstOrNull()?.track?.title
        val queuedCount = queue.size
        val text = when {
            current != null && queuedCount > 0 -> "Downloading · $queuedCount queued"
            current != null -> "Downloading ${current.progress}%"
            queue.isNotEmpty() -> "Waiting to download"
            else -> getString(R.string.download_notification_idle)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_library)
            .setContentTitle(title ?: getString(R.string.download_notification_title))
            .setContentText(text)
            .setOngoing(queue.isNotEmpty() || active.isNotEmpty())
            .setOnlyAlertOnce(true)
            .setProgress(100, current?.progress ?: 0, current == null && queue.isNotEmpty())
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.download_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.download_notification_channel_description)
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "spotkofi_downloads"
        const val NOTIFICATION_ID = 7001

        fun intent(context: Context): Intent =
            Intent(context.applicationContext, DownloadService::class.java)
    }
}
