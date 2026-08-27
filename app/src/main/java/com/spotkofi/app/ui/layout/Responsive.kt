package com.spotkofi.app.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout values derived from the current window width.
 *
 * The point is that column counts and card widths are decisions, not constants.
 * A two-column grid that looks right on a 400dp phone leaves absurd whitespace on
 * an unfolded foldable, and a 156dp shelf card is cramped on a small phone. This
 * resolves both from one place so screens never hardcode either.
 */
@Immutable
data class ResponsiveLayout(
    val widthDp: Int,
    /** Columns for the quick-pick and library grids. */
    val gridColumns: Int,
    /** Columns for the large Search category tiles. */
    val tileColumns: Int,
    /** Width of a card in a horizontally scrolling shelf. */
    val shelfCardWidth: Dp,
    /** Width of a large highlighted item. */
    val spotlightWidth: Dp,
    /** Horizontal screen padding. */
    val gutter: Dp,
    /** True on ordinary phone widths. */
    val isCompact: Boolean,
) {
    /** Detail artwork sized against the window so it never overflows. */
    val detailArtwork: Dp
        get() = (widthDp * 0.62f).dp.coerceIn(180.dp, 320.dp)
}

/**
 * Breakpoints follow the usual compact / medium / expanded split, but the values
 * are tuned for a media grid rather than for text columns: the aim is to keep the
 * artwork large enough to be recognisable, not to maximise item count.
 */
@Composable
fun rememberResponsiveLayout(): ResponsiveLayout {
    val widthDp = LocalConfiguration.current.screenWidthDp

    return remember(widthDp) {
        when {
            // Small phones: keep two columns but shrink the cards.
            widthDp < 360 -> ResponsiveLayout(
                widthDp = widthDp,
                gridColumns = 2,
                tileColumns = 2,
                shelfCardWidth = 138.dp,
                spotlightWidth = 168.dp,
                gutter = 14.dp,
                isCompact = true,
            )

            // Ordinary phones.
            widthDp < 600 -> ResponsiveLayout(
                widthDp = widthDp,
                gridColumns = 2,
                tileColumns = 2,
                shelfCardWidth = 156.dp,
                spotlightWidth = 186.dp,
                gutter = 16.dp,
                isCompact = true,
            )

            // Large phones unfolded, small tablets.
            widthDp < 840 -> ResponsiveLayout(
                widthDp = widthDp,
                gridColumns = 3,
                tileColumns = 3,
                shelfCardWidth = 172.dp,
                spotlightWidth = 210.dp,
                gutter = 24.dp,
                isCompact = false,
            )

            // Tablets and desktop-class widths.
            else -> ResponsiveLayout(
                widthDp = widthDp,
                gridColumns = 4,
                tileColumns = 4,
                shelfCardWidth = 188.dp,
                spotlightWidth = 240.dp,
                gutter = 32.dp,
                isCompact = false,
            )
        }
    }
}
