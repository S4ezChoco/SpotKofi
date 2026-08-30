package com.spotkofi.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spotkofi.app.R
import com.spotkofi.app.data.model.PlaybackState
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.ui.theme.SpotKofiTheme
import kotlin.math.abs

/** Upward drag distance, px, that expands into the full player. */
private const val EXPAND_DISTANCE_PX = 36f
private const val EXPAND_VELOCITY_PX = 700f
private const val HORIZONTAL_SWIPE_DISTANCE_PX = 96f
private const val DISMISS_DISTANCE_PX = 120f

/**
 * Collapsed player docked above the bottom navigation.
 *
 * The card is tinted from the artwork rather than using a fixed grey. That is the
 * single most recognisable thing about it, and it is why the mini player reads as
 * "attached" to the track instead of as part of the chrome.
 *
 * It is a gesture surface, not just a button: dragging up expands the full
 * player. A downward drag settles back in place and never stops playback; stopping
 * is an explicit action in the full-player options.
 *
 * Renders nothing when no track is loaded, so callers can place it
 * unconditionally without a wrapping `if`.
 */
@Composable
fun MiniPlayer(
    state: PlaybackState,
    onClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleSaved: () -> Unit,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    /** A deliberate downward swipe on the already-minimized player dismisses it. */
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val track = state.track ?: return
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    // Pushed well towards black so white text clears contrast on every seed, then
    // used as a gradient so the card has some depth instead of reading as a slab.
    val tint = remember(track.id) {
        lerp(artworkSeedColor(track.id), Color.Black, 0.55f)
    }
    val cardBrush = remember(tint) {
        Brush.horizontalGradient(
            0f to lerp(tint, Color.White, 0.06f),
            1f to tint,
        )
    }

    // Smoothing the 500ms ticker into a continuous sweep; without this the bar
    // visibly steps.
    val progress by animateFloatAsState(
        targetValue = state.progress,
        label = "miniPlayerProgress",
    )

    // One gesture surface handles all three directions without making a vertical
    // drag look like a row click: up opens, down dismisses, left/right changes track.
    val dragPx = remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spaceSm)
            .graphicsLayer {
                translationY = dragPx.floatValue.coerceAtLeast(0f) * 0.6f
            }
            .clip(SpotKofiTheme.shapes.miniPlayer)
            .background(cardBrush)
            .pointerInput(Unit) {
                var horizontal = 0f
                var vertical = 0f
                detectDragGestures(
                    onDragStart = {
                        horizontal = 0f
                        vertical = 0f
                        dragPx.floatValue = 0f
                    },
                    onDragCancel = { dragPx.floatValue = 0f },
                    onDragEnd = {
                        val endedHorizontal = horizontal
                        val endedVertical = vertical
                        dragPx.floatValue = 0f
                        when {
                            abs(endedHorizontal) >= HORIZONTAL_SWIPE_DISTANCE_PX -> {
                                if (endedHorizontal < 0f) onNext() else onPrevious()
                            }

                            endedVertical <= -EXPAND_DISTANCE_PX -> onClick()
                            endedVertical >= DISMISS_DISTANCE_PX -> onDismiss()
                        }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        horizontal += amount.x
                        vertical += amount.y
                        dragPx.floatValue = vertical.coerceIn(-EXPAND_DISTANCE_PX * 2f, 240f)
                    },
                )
            }
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
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(1.dp))
                if (state.deviceName != null) {
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
                } else {
                    Text(
                        text = track.artistName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(dimens.spaceSm))

            // Same control as the full player, reading the same state, so a save
            // made in one place is visible in the other.
            SavedToggle(
                isSaved = state.isSaved,
                onToggle = onToggleSaved,
                size = 28.dp,
            )

            IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(44.dp)) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = colors.textPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(dimens.iconMd),
                    )
                } else {
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
                        modifier = Modifier.size(dimens.iconLg),
                    )
                }
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
                .background(Color.White.copy(alpha = 0.18f)),
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    isSaved = true,
                ),
                onClick = {},
                onTogglePlayPause = {},
                onToggleSaved = {},
            )
            MiniPlayer(
                state = PlaybackState(
                    track = Track(
                        id = "tr_06",
                        title = "EDSA Southbound",
                        artistName = "Neon Manila",
                        albumTitle = "Neon Manila",
                        durationMs = 245_000,
                    ),
                    positionMs = 24_000,
                    deviceName = "SpotKofi Web Player",
                ),
                onClick = {},
                onTogglePlayPause = {},
                onToggleSaved = {},
            )
        }
    }
}
