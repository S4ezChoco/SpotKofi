package com.spotkofi.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotkofi.app.R
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
    /** Display name of the selected lyrics provider, shown on the picker badge. */
    providerName: String? = null,
    /** Opens the provider picker; null hides the badge entirely. */
    onChangeProvider: (() -> Unit)? = null,
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
        // The card itself is the expand affordance; the provider chip sits beside
        // the title because the provider is now changed where the lyrics are read
        // (here and in the full-screen reader) rather than in the overflow menu.
        Row(
            modifier = Modifier.padding(horizontal = dimens.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Lyrics",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            if (onChangeProvider != null && providerName != null) {
                LyricsProviderChip(
                    providerName = providerName,
                    onChange = onChangeProvider,
                )
            }
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
    /** Keeps the reader useful while track details and lyrics are being fetched. */
    loading: Boolean = false,
    positionMs: Long,
    onDismiss: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Display name of the selected lyrics provider, shown on the picker badge. */
    providerName: String? = null,
    /** Opens the provider picker; null hides the badge entirely. */
    onChangeProvider: (() -> Unit)? = null,
) {
    if (track == null || (!loading && lyrics == null)) return

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
        Box(modifier = Modifier.fillMaxSize()) {
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
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.cd_back),
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
                    if (loading) {
                        // A provider change is also a reload, and the previous sheet's
                        // text must not sit under the reader while the new one loads:
                        // the skeleton tells the reader the words on screen are about
                        // to be replaced.
                        FullscreenLyricsSkeleton()
                    } else {
                        lyrics?.let { availableLyrics ->
                            LyricsView(
                                lyrics = availableLyrics,
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
                    }
                }
            }

            // The provider switch floats above the reader's tail instead of sharing
            // the header row: the artwork keeps its original position and the chip
            // stays reachable with one thumb while reading. Same chip design as the
            // minimized lyrics card, so the control reads identically in both views.
            if (onChangeProvider != null && providerName != null) {
                LyricsProviderChip(
                    providerName = providerName,
                    onChange = onChangeProvider,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = dimens.spaceXl),
                )
            }
        }
    }
}

/**
 * Compact provider chip for the lyrics provider switch.
 *
 * Matches the app's unselected [SpotKofiChip] language — transparent surface,
 * hairline outline, labelMedium text — without the noise of a leading icon. Used
 * both on the minimized lyrics card and floating over the full-screen reader.
 * The inner clickable consumes the tap so the card's expand gesture never fires.
 */
@Composable
private fun LyricsProviderChip(
    providerName: String,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = modifier
            .clip(SpotKofiTheme.shapes.chip)
            .border(1.dp, colors.textTertiary, SpotKofiTheme.shapes.chip)
            .clickable(onClick = onChange)
            .padding(horizontal = dimens.spaceMd, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = providerName,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = "Change lyrics provider",
            tint = colors.textTertiary,
            modifier = Modifier.size(dimens.iconSm),
        )
    }
}

/**
 * Loading state for the full-screen reader.
 *
 * Mirrors the reader's own geometry — full-width bars at the reader's line height
 * and spacing, dimming the way lyric lines fall off from the active one — so the
 * skeleton occupies the same visual volume as the words that replace it. The
 * compact card skeleton is wrong here: its text-height bars read as a shrunken
 * list, not as incoming lyrics.
 */
@Composable
private fun FullscreenLyricsSkeleton() {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    // Bar lengths wander the way real lyric lines do, and every other bar sits
    // dimmer to echo the reader's distance falloff.
    val lineFractions = listOf(0.92f, 0.58f, 0.78f, 0.44f, 0.86f, 0.34f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceXxl),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        lineFractions.forEachIndexed { index, fraction ->
            val dimmed = index % 2 == 1
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(32.dp),
                shape = RoundedCornerShape(16.dp),
                baseColor = colors.elevated.copy(alpha = if (dimmed) 0.55f else 0.9f),
                highlightColor = colors.highlight.copy(alpha = if (dimmed) 0.55f else 0.9f),
            )
        }
    }
}
