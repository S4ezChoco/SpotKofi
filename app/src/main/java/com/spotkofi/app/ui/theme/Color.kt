package com.spotkofi.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * SpotKofi palette.
 *
 * The neutral ramp is deliberately narrow and very dark: in a media app the
 * artwork should be the only saturated thing on screen, so every surface stays
 * within a few steps of #121212 and hierarchy is carried by small luminance
 * jumps rather than by colour.
 */

// ---------- Brand ----------
/**
 * Primary accent. The brighter #1ED760 is correct for the current app: selected
 * chips, play buttons and the cast indicator all use it. The older, darker
 * #1DB954 survives only as [KofiGreenDim] for large filled areas.
 */
val KofiGreen = Color(0xFF1ED760)
val KofiGreenDim = Color(0xFF1DB954)
val KofiGreenPressed = Color(0xFF169C46)
val KofiGreenDeep = Color(0xFF117032)

// ---------- Neutral ramp ----------
/** App background. Everything else sits on top of this. */
val Base = Color(0xFF121212)

/** Default card / row background, one step up from [Base]. */
val BaseElevated = Color(0xFF181818)

/** Card in hovered / pressed state. */
val BaseHighlight = Color(0xFF1F1F1F)

/** Chips, text fields, filter pills. */
val Surface2 = Color(0xFF242424)

/**
 * Quick-pick cards, station captions, sheet rows.
 *
 * One step brighter than [Surface2] because these cards must read as raised
 * against [Base] without a border or shadow.
 */
val CardSurface = Color(0xFF2A2A2A)

/** Circular icon chips inside the Create sheet. */
val IconWell = Color(0xFF333333)

/** Hairline dividers and card outlines. */
val Surface3 = Color(0xFF2E2E2E)

/** Inactive slider track, scrubber background. */
val Surface4 = Color(0xFF4D4D4D)

// ---------- Text ----------
val TextPrimary = Color(0xFFFFFFFF)

/** Subtitles, metadata, inactive nav items. ~4.6:1 on [Base]. */
val TextSecondary = Color(0xFFB3B3B3)

/** Lowest-emphasis text. Use only for non-essential info. */
val TextTertiary = Color(0xFF8A8A8A)

/** Text/icons drawn on top of the green accent. */
val OnGreen = Color(0xFF000000)

// ---------- Status ----------
val ErrorRed = Color(0xFFE22134)
val ExplicitGrey = Color(0xFF9A9A9A)

/**
 * Seed palette for generated artwork placeholders.
 *
 * Phase 1 has no music API, so cover art is a deterministic gradient derived
 * from an item's id. These are the gradient tops.
 */
val ArtworkSeeds: List<Color> = listOf(
    Color(0xFF1E3264),
    Color(0xFF8D67AB),
    Color(0xFFE8115B),
    Color(0xFF1DB954),
    Color(0xFFF037A5),
    Color(0xFF148A08),
    Color(0xFFE1118C),
    Color(0xFF503750),
    Color(0xFFB02897),
    Color(0xFF477D95),
    Color(0xFFD84000),
    Color(0xFFAF2896),
    Color(0xFF056952),
    Color(0xFF7358FF),
    Color(0xFFBA5D07),
    Color(0xFF0D73EC),
)

/**
 * Design tokens that Material 3's [androidx.compose.material3.ColorScheme] has
 * no slot for. Reach for these through `SpotKofiTheme.colors`.
 */
@Immutable
data class SpotKofiColors(
    val base: Color = Base,
    val elevated: Color = BaseElevated,
    val highlight: Color = BaseHighlight,
    val card: Color = CardSurface,
    val iconWell: Color = IconWell,
    val chip: Color = Surface2,
    val divider: Color = Surface3,
    val trackInactive: Color = Surface4,
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary,
    val textTertiary: Color = TextTertiary,
    val accent: Color = KofiGreen,
    val accentDim: Color = KofiGreenDim,
    val onAccent: Color = OnGreen,
    val explicit: Color = ExplicitGrey,
)

val LocalSpotKofiColors = staticCompositionLocalOf { SpotKofiColors() }
