package com.spotkofi.app.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.spotkofi.app.MainActivity
import com.spotkofi.app.R
import com.spotkofi.app.SpotKofiApplication
import com.spotkofi.app.data.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Keeps audio alive while the app is minimised and publishes the platform
 * MediaSession.
 *
 * The session is deliberately built over the very same Media3 player the app is
 * already driving, so the system controls, the lock screen and OEM
 * floating/dynamic island surfaces cannot disagree with the in-app player.
 *
 * The notification is built here rather than delegated to `MediaSessionService`
 * so `startForeground` happens immediately in [onCreate]. A service promoted
 * before its first notification exists is the classic source of
 * ForegroundServiceDidNotStartInTime crashes.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var playerController: PlayerController
    private var session: MediaSession? = null
    private var artworkJob: Job? = null
    private var artworkKey: String? = null
    private var artwork: Bitmap? = null

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        val container = (application as SpotKofiApplication).container
        playerController = container.playerController

        createNotificationChannel()
        session = runCatching {
            MediaSession.Builder(this, container.mediaSessionPlayer)
                .setId(SESSION_ID)
                .build()
        }.getOrNull()

        startForeground(NOTIFICATION_ID, buildNotification(playerController.state.value))

        serviceScope.launch {
            playerController.state.collect { state ->
                if (!state.hasTrack) {
                    // Nothing is loaded any more, so there is nothing to keep the
                    // process promoted for.
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }
                updateNotification(state)
            }
        }

        // Artwork is fetched per track, not per tick, so a 500 ms playhead update
        // cannot trigger a network request.
        serviceScope.launch {
            playerController.state
                .map { it.track?.artworkUrl }
                .distinctUntilChanged()
                .collect(::loadArtwork)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAY -> playerController.togglePlayPause()
            ACTION_NEXT -> playerController.next()
            ACTION_PREVIOUS -> playerController.previous()
            ACTION_STOP -> {
                playerController.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away while paused should not leave a dead notification
        // behind; while playing, audio deliberately continues.
        if (!playerController.state.value.isPlaying) {
            playerController.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        artworkJob?.cancel()
        serviceScope.cancel()
        // Releases only the session. The player itself is owned by AppContainer and
        // must survive this service being torn down and started again.
        session?.release()
        session = null
        super.onDestroy()
    }

    private suspend fun loadArtwork(url: String?) {
        artworkKey = url
        artwork = null
        if (url.isNullOrBlank()) {
            updateNotification(playerController.state.value)
            return
        }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body.byteStream().use(BitmapFactory::decodeStream)
                }
            }.getOrNull()
        }
        if (artworkKey != url) return
        artwork = bitmap
        updateNotification(playerController.state.value)
    }

    private fun updateNotification(state: PlaybackState) {
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(state))
        }
    }

    private fun buildNotification(state: PlaybackState): Notification {
        val track = state.track
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_music)
            .setContentTitle(track?.title ?: getString(R.string.playback_notification_title))
            .setContentText(track?.artistName)
            .setSubText(track?.albumTitle?.takeIf { it.isNotBlank() })
            .setLargeIcon(artwork)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(commandIntent(ACTION_STOP))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            // Ongoing only while audio is actually running, so a paused session can
            // be swiped away instead of becoming undismissable chrome.
            .setOngoing(state.isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(
                android.R.drawable.ic_media_previous,
                getString(R.string.cd_previous),
                commandIntent(ACTION_PREVIOUS),
            )
            .addAction(
                if (state.isPlaying) {
                    android.R.drawable.ic_media_pause
                } else {
                    android.R.drawable.ic_media_play
                },
                getString(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                commandIntent(ACTION_TOGGLE_PLAY),
            )
            .addAction(
                android.R.drawable.ic_media_next,
                getString(R.string.cd_next),
                commandIntent(ACTION_NEXT),
            )

        // MediaStyle is what promotes this from a plain notification to a media
        // session: it is the hook the OS reads for the lock screen, the system
        // media panel and OEM dynamic island surfaces.
        session?.let { active ->
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(active)
                    .setShowActionsInCompactView(0, 1, 2),
            )
        }
        return builder.build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN_APP,
        Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun commandIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.playback_notification_channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "spotkofi_playback"
        const val NOTIFICATION_ID = 7002
        private const val SESSION_ID = "spotkofi_session"
        private const val REQUEST_OPEN_APP = 41
        private const val ACTION_TOGGLE_PLAY = "com.spotkofi.app.action.TOGGLE_PLAY"
        private const val ACTION_NEXT = "com.spotkofi.app.action.NEXT"
        private const val ACTION_PREVIOUS = "com.spotkofi.app.action.PREVIOUS"
        private const val ACTION_STOP = "com.spotkofi.app.action.STOP"

        fun intent(context: Context): Intent =
            Intent(context.applicationContext, PlaybackService::class.java)
    }
}
