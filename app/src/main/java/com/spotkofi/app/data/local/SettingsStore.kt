package com.spotkofi.app.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Every user preference in the app, as one immutable snapshot.
 *
 * Only settings that change real behaviour are modelled. A switch that persists a
 * value nothing reads is worse than no switch: it tells the user the app does
 * something it does not.
 */
data class AppSettings(
    // ---- Playback ----
    /** Restore the previous queue and now-playing track on launch. */
    val restoreQueueOnStart: Boolean = true,
    /** Open the full player when a track is tapped, rather than staying collapsed. */
    val openPlayerOnPlay: Boolean = true,
    /** Swiping the player away also stops the audio. */
    val stopOnPlayerDismiss: Boolean = true,

    // ---- Content ----
    /**
     * Two-letter region used for provider requests and as the default chart region.
     *
     * It decides which catalogue the provider answers from, so a listener in Manila
     * gets Manila's results rather than California's.
     */
    val contentRegion: String = "PH",
    /** Hide results the provider marked explicit. */
    val hideExplicitContent: Boolean = false,

    // ---- Lyrics ----
    /** Look up lyrics for the playing track. */
    val lyricsEnabled: Boolean = true,

    // ---- Downloads ----
    /** Preferred download ordering when several are queued. */
    val downloadHighPriority: Boolean = false,
)

/**
 * Durable settings, backed by `SharedPreferences`.
 *
 * Preferences rather than a database: this is a handful of scalars read on almost
 * every screen, and the whole set is small enough to hold in memory and publish as
 * one snapshot. Publishing a snapshot rather than a flow per key means a screen
 * cannot see two settings from different moments in time.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** The current values, for callers outside composition. */
    val current: AppSettings get() = _settings.value

    fun setRestoreQueueOnStart(value: Boolean) = update { it.copy(restoreQueueOnStart = value) }

    fun setOpenPlayerOnPlay(value: Boolean) = update { it.copy(openPlayerOnPlay = value) }

    fun setStopOnPlayerDismiss(value: Boolean) = update { it.copy(stopOnPlayerDismiss = value) }

    fun setContentRegion(code: String) = update {
        it.copy(contentRegion = code.trim().uppercase().take(2).ifBlank { "PH" })
    }

    fun setHideExplicitContent(value: Boolean) = update { it.copy(hideExplicitContent = value) }

    fun setLyricsEnabled(value: Boolean) = update { it.copy(lyricsEnabled = value) }

    fun setDownloadHighPriority(value: Boolean) = update { it.copy(downloadHighPriority = value) }

    /** Restores defaults, for the "reset settings" action. */
    fun reset() {
        prefs.edit().clear().apply()
        _settings.value = AppSettings()
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        write(next)
        // Written before publishing, so a screen that reacts by reading the store
        // again can never observe the old value.
        _settings.value = next
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            restoreQueueOnStart = prefs.getBoolean(KEY_RESTORE_QUEUE, defaults.restoreQueueOnStart),
            openPlayerOnPlay = prefs.getBoolean(KEY_OPEN_PLAYER, defaults.openPlayerOnPlay),
            stopOnPlayerDismiss = prefs.getBoolean(KEY_STOP_ON_DISMISS, defaults.stopOnPlayerDismiss),
            contentRegion = prefs.getString(KEY_REGION, defaults.contentRegion)
                ?.takeIf { it.isNotBlank() }
                ?: defaults.contentRegion,
            hideExplicitContent = prefs.getBoolean(KEY_HIDE_EXPLICIT, defaults.hideExplicitContent),
            lyricsEnabled = prefs.getBoolean(KEY_LYRICS, defaults.lyricsEnabled),
            downloadHighPriority = prefs.getBoolean(
                KEY_DOWNLOAD_PRIORITY,
                defaults.downloadHighPriority,
            ),
        )
    }

    private fun write(value: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_RESTORE_QUEUE, value.restoreQueueOnStart)
            .putBoolean(KEY_OPEN_PLAYER, value.openPlayerOnPlay)
            .putBoolean(KEY_STOP_ON_DISMISS, value.stopOnPlayerDismiss)
            .putString(KEY_REGION, value.contentRegion)
            .putBoolean(KEY_HIDE_EXPLICIT, value.hideExplicitContent)
            .putBoolean(KEY_LYRICS, value.lyricsEnabled)
            .putBoolean(KEY_DOWNLOAD_PRIORITY, value.downloadHighPriority)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "spotkofi_settings"
        const val KEY_RESTORE_QUEUE = "restore_queue_on_start"
        const val KEY_OPEN_PLAYER = "open_player_on_play"
        const val KEY_STOP_ON_DISMISS = "stop_on_player_dismiss"
        const val KEY_REGION = "content_region"
        const val KEY_HIDE_EXPLICIT = "hide_explicit_content"
        const val KEY_LYRICS = "lyrics_enabled"
        const val KEY_DOWNLOAD_PRIORITY = "download_high_priority"
    }
}
