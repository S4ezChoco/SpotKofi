package com.spotkofi.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spotkofi.app.data.service.DownloadManagerStatus
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * Compact download state for a track row.
 *
 * A spinner alone answers "something is happening" but not "how far along", which
 * is the only question worth asking of a download, so the percentage is printed
 * next to the ring. Completed and failed states are glyph-only because a number
 * would carry no extra information.
 *
 * Renders nothing when the track has no download record, so callers can place it
 * unconditionally.
 */
@Composable
fun DownloadIndicator(
    status: DownloadManagerStatus?,
    progress: Int,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    if (status == null) return

    val fraction by animateFloatAsState(
        targetValue = (progress.coerceIn(0, 100)) / 100f,
        label = "downloadProgress",
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        when (status) {
            DownloadManagerStatus.COMPLETED -> Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(colors.accent, SpotKofiTheme.shapes.avatar),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Downloaded",
                    tint = colors.onAccent,
                    modifier = Modifier.size(12.dp),
                )
            }

            DownloadManagerStatus.DOWNLOADING -> {
                Text(
                    text = "$progress%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.accent,
                )
                Spacer(Modifier.width(6.dp))
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.size(16.dp),
                    color = colors.accent,
                    trackColor = colors.trackInactive,
                    strokeWidth = 2.dp,
                )
            }

            DownloadManagerStatus.PAUSED -> {
                Text(
                    text = "$progress%",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = "Download paused",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }

            DownloadManagerStatus.QUEUED -> Icon(
                imageVector = Icons.Outlined.FileDownload,
                contentDescription = "Waiting to download",
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )

            DownloadManagerStatus.FAILED -> Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = "Download failed",
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * The label for the download row in a track menu.
 *
 * Shared by both sheets so the wording, and the percentage, cannot drift between
 * the one opened from a list and the one opened from the player.
 */
fun downloadActionLabel(status: DownloadManagerStatus?, progress: Int): String = when (status) {
    DownloadManagerStatus.COMPLETED -> "Remove offline download"
    DownloadManagerStatus.DOWNLOADING -> "Pause download · $progress%"
    DownloadManagerStatus.PAUSED -> "Resume download · $progress%"
    DownloadManagerStatus.QUEUED -> "Cancel queued download"
    DownloadManagerStatus.FAILED -> "Retry download"
    null -> "Download for offline"
}

@Preview(backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun DownloadIndicatorPreview() {
    SpotKofiTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DownloadIndicator(status = DownloadManagerStatus.DOWNLOADING, progress = 42)
            DownloadIndicator(status = DownloadManagerStatus.PAUSED, progress = 42)
            DownloadIndicator(status = DownloadManagerStatus.QUEUED, progress = 0)
            DownloadIndicator(status = DownloadManagerStatus.COMPLETED, progress = 100)
            DownloadIndicator(status = DownloadManagerStatus.FAILED, progress = 12)
        }
    }
}
