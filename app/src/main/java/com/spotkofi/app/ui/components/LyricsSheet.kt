package com.spotkofi.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotkofi.app.R
import com.spotkofi.app.core.AppConstants
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.TrackLyrics
import com.spotkofi.app.ui.motion.clickableScale
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * The lyrics block inside the player.
 *
 * A preview rather than the whole sheet: the player is a scrolling page and a
 * full-height lyric list inside it would swallow the scroll and bury everything
 * below. Tapping anywhere on the card opens the full-screen reader.
 */
@Composable
fun LyricsCard(
    lyrics: TrackLyrics,
    positionMs: Long,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val cardTint = tint ?: colors.accent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SpotKofiTheme.shapes.card)
            .background(
                Brush.verticalGradient(
                    0f to cardTint.copy(alpha = 0.44f),
                    1f to colors.card,
                ),
            )
            .clickableScale(pressedScale = 0.99f, onClick = onExpand)
            .padding(vertical = dimens.spaceMd),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Lyrics,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(dimens.iconSm),
            )
            Spacer(Modifier.width(dimens.spaceSm))
            Text(
                text = "Lyrics",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = lyrics.syncLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
            Spacer(Modifier.width(dimens.spaceMd))
            Text(
                text = "Show",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent,
            )
        }

        Spacer(Modifier.height(dimens.spaceSm))

        Box(modifier = Modifier.height(220.dp)) {
            LyricsView(
                lyrics = lyrics,
                positionMs = positionMs,
                // No seeking from the preview: the lines are small here and a
                // mis-tap while scrolling the player would jump the song.
                onSeekTo = null,
                lineFontSize = 18.sp,
                contentPadding = PaddingValues(vertical = dimens.spaceSm),
            )
        }

        Spacer(Modifier.height(dimens.spaceSm))

        Text(
            text = "Tap to open \u00b7 ${lyrics.providerName ?: AppConstants.LYRICS_PROVIDER_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = dimens.spaceLg),
        )
    }
}

/**
 * Full-screen lyrics.
 *
 * Tinted from the artwork so the page feels like part of the song rather than a
 * generic text view, and sized large enough to be read at arm's length. Lines are
 * tappable here because the reader is the place where seeking to a lyric is the
 * obvious thing to want.
 */
@Composable
fun LyricsSheet(
    visible: Boolean,
    track: Track?,
    lyrics: TrackLyrics?,
    positionMs: Long,
    onDismiss: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (track == null || lyrics == null) return

    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    val seed = remember(track.id) { artworkSeedColor(track.id) }
    val background = remember(seed) {
        Brush.verticalGradient(
            0f to lerp(seed, colors.base, 0.35f),
            0.55f to lerp(seed, colors.base, 0.82f),
            1f to colors.base,
        )
    }

    BackHandler(enabled = visible, onBack = onDismiss)

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(Motion.gentle()) { it } + fadeIn(Motion.fast()),
        exit = slideOutVertically(Motion.snappy()) { it } + fadeOut(Motion.fast()),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = dimens.spaceXs, vertical = dimens.spaceXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = colors.textPrimary,
                        modifier = Modifier.size(dimens.iconLg),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artistName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(dimens.spaceSm))
                Artwork(id = track.id, size = 40.dp, url = track.artworkUrl)
                Spacer(Modifier.width(dimens.spaceSm))
            }

            Box(modifier = Modifier.weight(1f)) {
                LyricsView(
                    lyrics = lyrics,
                    positionMs = positionMs,
                    onSeekTo = onSeekTo,
                    lineFontSize = 26.sp,
                    // A tall tail so the closing lines can still reach the middle of
                    // the screen instead of stopping at the bottom edge.
                    contentPadding = PaddingValues(
                        top = dimens.spaceXxl,
                        bottom = dimens.spaceHuge * 3,
                    ),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = lyrics.syncLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
                Text(
                    // Named because the words are someone else's work fetched at
                    // runtime, not something this app wrote or ships.
                    text = "Lyrics by ${lyrics.providerName ?: AppConstants.LYRICS_PROVIDER_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
        }
    }
}
