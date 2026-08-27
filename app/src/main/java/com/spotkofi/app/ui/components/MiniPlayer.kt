package com.spotkofi.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spotkofi.app.R
import com.spotkofi.app.data.model.PlaybackState
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * Collapsed player docked above the bottom navigation.
 *
 * The card is tinted from the artwork rather than using a fixed grey. That is
 * the single most recognisable thing about it, and it is why the mini player
 * reads as "attached" to the track instead of as part of the chrome.
 *
 * Renders nothing when no track is loaded, so callers can place it
 * unconditionally without a wrapping `if`.
 */
@Composable
fun MiniPlayer(
    state: PlaybackState,
    onClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onAddToLibrary: () -> Unit,
    onConnectDevice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.track ?: return
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    // Pushed well towards black so white text clears contrast on every seed.
    val tint = remember(track.id) {
        lerp(artworkSeedColor(track.id), Color.Black, 0.52f)
    }

    // Smoothing the 500ms ticker into a continuous sweep; without this the bar
    // visibly steps.
    val progress by animateFloatAsState(
        targetValue = state.progress,
        label = "miniPlayerProgress",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spaceSm)
            .clip(SpotKofiTheme.shapes.miniPlayer)
            .background(tint)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = dimens.spaceSm,
                    end = dimens.spaceXs,
                    top = dimens.spaceSm,
                    bottom = dimens.spaceSm,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                id = track.id,
                size = dimens.artworkTiny,
                url = track.artworkUrl,
            )

            Spacer(Modifier.width(dimens.spaceMd))

            Column(modifier = Modifier.weight(1f)) {
                // "Title • Artist" on one line: the bullet form is what the real
                // app uses at this size, and it buys a second line for the device.
                Row {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = " \u2022 ${track.artistName}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Normal,
                        ),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (state.deviceName != null) {
                    Spacer(Modifier.height(1.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(dimens.spaceXs))
                        Text(
                            text = state.deviceName,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            IconButton(onClick = onConnectDevice, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Filled.Computer,
                    contentDescription = stringResource(R.string.cd_connect_device),
                    // Green when a remote device is active, matching the label.
                    tint = if (state.deviceName != null) colors.accent else colors.textPrimary,
                    modifier = Modifier.size(dimens.iconSm),
                )
            }

            IconButton(onClick = onAddToLibrary, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Filled.AddCircleOutline,
                    contentDescription = stringResource(R.string.cd_add_to_library),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(dimens.iconMd),
                )
            }

            IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (state.isPlaying) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.PlayArrow
                    },
                    contentDescription = stringResource(
                        if (state.isPlaying) R.string.cd_pause else R.string.cd_play,
                    ),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(dimens.iconMd),
                )
            }
        }

        // Progress sits flush on the bottom edge of the card, inset slightly, and
        // has no visible remaining-track track behind it.
        Box(
            modifier = Modifier
                .padding(horizontal = dimens.spaceSm)
                .fillMaxWidth()
                .height(2.dp)
                .clip(SpotKofiTheme.shapes.chip)
                .background(Color.White.copy(alpha = 0.22f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(colors.textPrimary),
            )
        }

        Spacer(Modifier.height(dimens.spaceSm))
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun MiniPlayerPreview() {
    SpotKofiTheme {
        MiniPlayer(
            state = PlaybackState(
                track = Track(
                    id = "tr_05",
                    title = "Neon Manila",
                    artistName = "Neon Manila",
                    albumTitle = "Neon Manila",
                    durationMs = 201_000,
                ),
                isPlaying = true,
                positionMs = 74_000,
                deviceName = "SpotKofi Web Player",
            ),
            onClick = {},
            onTogglePlayPause = {},
            onAddToLibrary = {},
            onConnectDevice = {},
        )
    }
}
