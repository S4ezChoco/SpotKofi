package com.spotkofi.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.data.model.Playlist
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.service.DownloadManagerStatus
import com.spotkofi.app.ui.motion.staggeredEntry
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme
import kotlinx.coroutines.launch

/** Shared track menu for Home/Search/Library/album rows. */
@Composable
fun TrackActionsSheet(
    visible: Boolean,
    track: Track?,
    isSaved: Boolean,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onToggleSaved: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownload: () -> Unit,
    downloadStatus: DownloadManagerStatus? = null,
    /** Percent transferred, shown while a download is running or paused. */
    downloadProgress: Int = 0,
    onAddToPlaylist: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    var showPlaylists by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var playlistMembership by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 0.62f else 0f,
        animationSpec = Motion.fast(),
        label = "trackActionsScrim",
    )

    LaunchedEffect(visible) {
        if (!visible) {
            showPlaylists = false
            showCreatePlaylist = false
            playlistMembership = emptyMap()
        }
    }

    // The relation table is the source of truth. Loading it when the playlist
    // picker opens lets the sheet show a Spotify-style check state instead of
    // silently accepting duplicate adds.
    LaunchedEffect(showPlaylists, track?.id, playlists) {
        val selected = track
        if (!showPlaylists || selected == null) {
            playlistMembership = emptyMap()
            return@LaunchedEffect
        }
        val selectedId = selected.id
        playlistMembership = playlists.associate { playlist ->
            playlist.id to container.localStore
                .playlistTracks(playlist.id)
                .any { it.id == selectedId }
        }
    }

    if ((!visible && scrimAlpha == 0f) || track == null) return
    BackHandler(enabled = visible, onBack = onDismiss)

    val downloadLabel = downloadActionLabel(downloadStatus, downloadProgress)

    // Rendered in a Popup so the sheet owns the whole window and cannot be
    // covered by the bottom navigation or the minimized player.
    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(Motion.player()) { it } + fadeIn(Motion.fast()),
                exit = slideOutVertically(Motion.player()) { it } + fadeOut(Motion.fast()),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(dimens.spaceSm)
                        .background(colors.elevated, RoundedCornerShape(dimens.floatingBarRadius))
                        .padding(vertical = dimens.spaceMd),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showPlaylists) {
                            ActionRow(
                                index = 0,
                                icon = Icons.Filled.ArrowBack,
                                label = "Back to song actions",
                                onClick = { showPlaylists = false },
                            )
                        } else {
                            Artwork(id = track.id, url = track.artworkUrl, size = 48.dp)
                            Spacer(Modifier.width(dimens.spaceMd))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    track.artistName,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    if (showPlaylists) {
                        if (playlists.isEmpty()) {
                            Text(
                                text = "Create your first playlist to save this song.",
                                color = colors.textSecondary,
                                modifier = Modifier.padding(
                                    horizontal = dimens.spaceLg,
                                    vertical = dimens.spaceLg,
                                ),
                            )
                        } else {
                            playlists.forEachIndexed { index, playlist ->
                                val alreadyAdded = playlistMembership[playlist.id] == true
                                ActionRow(
                                    index = index + 1,
                                    icon = if (alreadyAdded) {
                                        Icons.Filled.Check
                                    } else {
                                        Icons.Filled.PlaylistAdd
                                    },
                                    label = if (alreadyAdded) {
                                        "${playlist.title}  ·  Added"
                                    } else {
                                        playlist.title
                                    },
                                    enabled = !alreadyAdded,
                                    onClick = {
                                        onAddToPlaylist(playlist)
                                        onDismiss()
                                    },
                                )
                            }
                        }
                        ActionRow(
                            index = playlists.size + 1,
                            icon = Icons.Filled.AddCircleOutline,
                            label = "Create playlist",
                            onClick = { showCreatePlaylist = true },
                        )
                    } else {
                        ActionRow(
                            index = 0,
                            icon = if (isSaved) Icons.Filled.Check else Icons.Filled.PlaylistAdd,
                            label = if (isSaved) "Remove from your library" else "Save to your library",
                            onClick = {
                                onToggleSaved()
                                onDismiss()
                            },
                        )
                        ActionRow(
                            index = 1,
                            icon = Icons.Filled.SkipNext,
                            label = "Play next",
                            onClick = {
                                onPlayNext()
                                onDismiss()
                            },
                        )
                        ActionRow(
                            index = 2,
                            icon = Icons.Filled.AddToQueue,
                            label = "Add to queue",
                            onClick = {
                                onAddToQueue()
                                onDismiss()
                            },
                        )
                        ActionRow(
                            index = 3,
                            icon = if (downloadStatus == DownloadManagerStatus.COMPLETED) {
                                Icons.Filled.Check
                            } else {
                                Icons.Filled.Download
                            },
                            label = downloadLabel,
                            onClick = {
                                onDownload()
                                onDismiss()
                            },
                        )
                        ActionRow(
                            index = 4,
                            icon = Icons.Filled.PlaylistAdd,
                            label = "Add to playlist",
                            onClick = { showPlaylists = true },
                        )
                    }
                }
            }
        }
    }

    CreatePlaylistDialog(
        visible = showCreatePlaylist,
        onDismiss = { showCreatePlaylist = false },
        onCreate = { name, description ->
            scope.launch {
                val created = container.localStore.createPlaylist(name, description)
                track?.let { container.localStore.addToPlaylist(created.id, it) }
                showCreatePlaylist = false
                onDismiss()
            }
        },
    )
}

@Composable
private fun ActionRow(
    index: Int,
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val tint = if (enabled) colors.textPrimary else colors.textTertiary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .staggeredEntry(index, slide = 8.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(dimens.iconMd))
        Spacer(Modifier.width(dimens.spaceLg))
        Text(label, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
