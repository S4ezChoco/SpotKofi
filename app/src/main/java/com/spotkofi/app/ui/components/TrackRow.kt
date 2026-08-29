package com.spotkofi.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spotkofi.app.R
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.service.DownloadManagerStatus
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * A single track line, as used in playlist and album detail screens and in
 * search results.
 *
 * When [isPlaying] is true the title turns green, which is how Spotify marks the
 * active row. Colour alone is not sufficient signal, so the state is also
 * exposed to accessibility services through the row's semantics.
 */
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    showArtwork: Boolean = true,
    trailingText: String? = null,
    /** Offline state for this track, or null when it was never downloaded. */
    downloadStatus: DownloadManagerStatus? = null,
    /** Percent transferred, printed while a download is running or paused. */
    downloadProgress: Int = 0,
    onMoreClick: (() -> Unit)? = null,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArtwork) {
            Artwork(
                id = track.id,
                size = dimens.artworkRow,
                url = track.artworkUrl,
            )
            Spacer(Modifier.width(dimens.spaceMd))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPlaying) colors.accent else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
            ) {
                if (track.isExplicit) {
                    ExplicitBadge()
                }
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (downloadStatus != null) {
            Spacer(Modifier.width(dimens.spaceSm))
            DownloadIndicator(status = downloadStatus, progress = downloadProgress)
        }

        if (trailingText != null) {
            Spacer(Modifier.width(dimens.spaceSm))
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textTertiary,
            )
        }

        if (onMoreClick != null) {
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(dimens.minTouchTarget),
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.cd_more_options),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(dimens.iconMd),
                )
            }
        }
    }
}

@Preview(name = "TrackRow", backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun TrackRowPreview() {
    SpotKofiTheme {
        Column {
            TrackRow(
                track = Track(
                    id = "tr_01",
                    title = "Umaga",
                    artistName = "Mira Solano",
                    albumTitle = "Umaga",
                    durationMs = 214_000,
                ),
                onClick = {},
                trailingText = "3:34",
                onMoreClick = {},
            )
            TrackRow(
                track = Track(
                    id = "tr_06",
                    title = "EDSA Southbound",
                    artistName = "Neon Manila",
                    albumTitle = "Neon Manila",
                    durationMs = 245_000,
                    isExplicit = true,
                ),
                onClick = {},
                isPlaying = true,
                trailingText = "4:05",
                downloadStatus = DownloadManagerStatus.DOWNLOADING,
                downloadProgress = 42,
                onMoreClick = {},
            )
        }
    }
}
