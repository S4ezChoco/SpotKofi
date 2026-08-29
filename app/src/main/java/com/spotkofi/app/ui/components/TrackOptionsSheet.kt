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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spotkofi.app.data.model.RepeatMode
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.asTrackDuration
import com.spotkofi.app.data.service.DownloadManagerStatus
import com.spotkofi.app.ui.motion.staggeredEntry
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * The track options panel behind the player's 3-dot button.
 *
 * Download is a real toggle: the label reflects the persisted record and the
 * callback can pause, resume, retry, cancel, or delete through DownloadManager.
 */
@Composable
fun TrackOptionsSheet(
    visible: Boolean,
    track: Track?,
    isSaved: Boolean,
    isShuffled: Boolean,
    repeatMode: RepeatMode,
    remainingMs: Long,
    onDismiss: () -> Unit,
    onToggleSaved: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onShare: (Track) -> Unit,
    onDownload: ((Track) -> Unit)? = null,
    downloadStatus: DownloadManagerStatus? = null,
    /** Percent transferred, shown while a download is running or paused. */
    downloadProgress: Int = 0,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 0.6f else 0f,
        animationSpec = Motion.fast(),
        label = "trackOptionsScrim",
    )

    if (!visible && scrimAlpha == 0f) return
    if (track == null) return

    val downloadLabel = downloadActionLabel(downloadStatus, downloadProgress)

    BackHandler(enabled = visible, onBack = onDismiss)

    Box(modifier = modifier.fillMaxSize()) {
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(Motion.gentle()) { it } + fadeIn(Motion.fast()),
            exit = slideOutVertically(Motion.snappy()) { it } + fadeOut(Motion.fast()),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(dimens.spaceSm)
                    .background(
                        color = colors.elevated,
                        shape = RoundedCornerShape(dimens.floatingBarRadius),
                    )
                    .padding(vertical = dimens.spaceMd),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(id = track.id, size = 48.dp, url = track.artworkUrl)
                    Spacer(Modifier.width(dimens.spaceMd))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artistName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (remainingMs > 0L) {
                            Text(
                                text = "${remainingMs.asTrackDuration()} left",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textTertiary,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm)
                        .size(width = 0.dp, height = 1.dp)
                        .background(colors.divider),
                )

                var row = 0
                OptionRow(
                    index = row++,
                    icon = if (isSaved) Icons.Filled.Check else Icons.Filled.PlaylistAdd,
                    label = if (isSaved) "Remove from your library" else "Save to your library",
                    tint = if (isSaved) colors.accent else colors.textPrimary,
                    onClick = {
                        onToggleSaved()
                        onDismiss()
                    },
                )
                onDownload?.let { download ->
                    OptionRow(
                        index = row++,
                        icon = if (downloadStatus == DownloadManagerStatus.COMPLETED) {
                            Icons.Filled.Check
                        } else {
                            Icons.Outlined.FileDownload
                        },
                        label = downloadLabel,
                        onClick = {
                            download(track)
                            onDismiss()
                        },
                    )
                }
                OptionRow(
                    index = row++,
                    icon = Icons.Filled.Shuffle,
                    label = if (isShuffled) "Shuffle is on" else "Shuffle this queue",
                    tint = if (isShuffled) colors.accent else colors.textPrimary,
                    onClick = onToggleShuffle,
                )
                OptionRow(
                    index = row++,
                    icon = if (repeatMode == RepeatMode.One) {
                        Icons.Filled.RepeatOne
                    } else {
                        Icons.Filled.Repeat
                    },
                    label = when (repeatMode) {
                        RepeatMode.Off -> "Repeat is off"
                        RepeatMode.All -> "Repeating the queue"
                        RepeatMode.One -> "Repeating this song"
                    },
                    tint = if (repeatMode == RepeatMode.Off) {
                        colors.textPrimary
                    } else {
                        colors.accent
                    },
                    onClick = onCycleRepeat,
                )
                track.albumId?.let { albumId ->
                    OptionRow(
                        index = row++,
                        icon = Icons.Filled.Album,
                        label = "Go to album",
                        onClick = {
                            onDismiss()
                            onOpenAlbum(albumId)
                        },
                    )
                }
                track.artistId?.let { artistId ->
                    OptionRow(
                        index = row++,
                        icon = Icons.Filled.Person,
                        label = "Go to artist",
                        onClick = {
                            onDismiss()
                            onOpenArtist(artistId)
                        },
                    )
                }
                if (track.isExternallyOpenable) {
                    OptionRow(
                        index = row,
                        icon = Icons.Filled.Share,
                        label = "Share",
                        onClick = {
                            onDismiss()
                            onShare(track)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    index: Int,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = SpotKofiTheme.colors.textPrimary,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .staggeredEntry(index, slide = 8.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(dimens.iconMd),
        )
        Spacer(Modifier.width(dimens.spaceLg))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
