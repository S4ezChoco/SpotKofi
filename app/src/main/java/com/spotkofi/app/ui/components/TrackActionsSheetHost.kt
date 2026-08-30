package com.spotkofi.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.data.model.Track
import kotlinx.coroutines.launch

/**
 * [TrackActionsSheet] with its actions already wired to the app container.
 *
 * The sheet itself takes every action as a parameter, which is right for a
 * component: it should not know where saved songs or downloads live. But the
 * wiring is identical on every screen that shows it, and a screen that reproduced
 * it slightly differently - saving without recording the download, say - would
 * behave differently from the rest of the app for no visible reason.
 *
 * Pass the tapped [track], or null when the sheet should be closed.
 */
@Composable
fun TrackActionsSheetHost(
    track: Track?,
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    val savedTracks by container.localStore.savedTracks.collectAsStateWithLifecycle()
    val playlists by container.localStore.playlists.collectAsStateWithLifecycle()
    val downloads by container.downloadManager.downloads.collectAsStateWithLifecycle()

    val download = remember(downloads, track) {
        track?.let { candidate -> downloads.firstOrNull { it.track.id == candidate.id } }
    }
    val isSaved = remember(savedTracks, track) {
        track != null && savedTracks.any { it.id == track.id }
    }

    TrackActionsSheet(
        visible = track != null,
        track = track,
        isSaved = isSaved,
        playlists = playlists,
        downloadStatus = download?.status,
        downloadProgress = download?.progress ?: 0,
        onDismiss = onDismiss,
        onToggleSaved = {
            track?.let { candidate ->
                if (isSaved) {
                    container.localStore.removeTrack(candidate.id)
                } else {
                    container.localStore.saveTrack(candidate)
                }
            }
        },
        onPlayNext = { track?.let(container.queueController::playNext) },
        onAddToQueue = { track?.let(container.queueController::addToQueue) },
        onDownload = { track?.let(container.downloadManager::toggleDownload) },
        onAddToPlaylist = { playlist ->
            track?.let { candidate ->
                scope.launch { container.localStore.addToPlaylist(playlist.id, candidate) }
            }
        },
    )
}
