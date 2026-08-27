package com.spotkofi.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing and sizing tokens.
 *
 * Everything is a multiple of 4dp. Named tokens exist so that changing, say,
 * the screen gutter is a one-line edit instead of a find-and-replace across
 * every screen.
 */
@Immutable
data class SpotKofiDimens(
    // ---- Spacing scale ----
    val spaceXxs: Dp = 2.dp,
    val spaceXs: Dp = 4.dp,
    val spaceSm: Dp = 8.dp,
    val spaceMd: Dp = 12.dp,
    val spaceLg: Dp = 16.dp,
    val spaceXl: Dp = 24.dp,
    val spaceXxl: Dp = 32.dp,
    val spaceHuge: Dp = 48.dp,

    // ---- Layout ----
    /** Left/right padding for screen content. */
    val screenGutter: Dp = 16.dp,

    /** Vertical gap between two horizontal shelves. */
    val shelfSpacing: Dp = 28.dp,

    // ---- Artwork ----
    val artworkTiny: Dp = 40.dp,
    val artworkSmall: Dp = 48.dp,
    val artworkRow: Dp = 56.dp,
    val artworkCard: Dp = 156.dp,
    val artworkHeader: Dp = 220.dp,

    // ---- Floating navigation bar ----
    /**
     * Minimum height of the bar's content area, excluding its outer margins.
     *
     * Treated as a floor, not a fixed height. The item stack is roughly
     * 34dp glow + 4dp gap + label line + 8dp padding, which already sits close to
     * this budget at the default font scale and exceeds it once the user enlarges
     * text. Applied with `heightIn(min = ...)` the bar grows instead of clipping
     * the labels.
     */
    val floatingBarHeight: Dp = 72.dp,

    /** Inset from the left and right screen edges. */
    val floatingBarMargin: Dp = 14.dp,

    /** Gap between the bar and the navigation-bar inset below it. */
    val floatingBarGap: Dp = 10.dp,

    /** Corner radius. Large enough to read as a pill at [floatingBarHeight]. */
    val floatingBarRadius: Dp = 26.dp,

    // ---- Components ----
    val miniPlayerHeight: Dp = 56.dp,
    val chipHeight: Dp = 34.dp,
    val iconSm: Dp = 18.dp,
    val iconMd: Dp = 24.dp,
    val iconLg: Dp = 32.dp,
    val playButtonSm: Dp = 48.dp,
    val playButtonLg: Dp = 64.dp,

    /**
     * Minimum touch target. Anything tappable must be at least this in both
     * axes, even when the painted shape is smaller.
     */
    val minTouchTarget: Dp = 48.dp,
)

val LocalSpotKofiDimens = staticCompositionLocalOf { SpotKofiDimens() }
