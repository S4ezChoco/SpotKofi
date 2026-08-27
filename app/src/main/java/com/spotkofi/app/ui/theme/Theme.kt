package com.spotkofi.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * SpotKofi is dark-only by design, the same way Spotify's mobile app is. There
 * is no light scheme and no dynamic colour: album artwork supplies all the
 * colour, and a user-tinted chrome would fight with it.
 */
private val SpotKofiColorScheme = darkColorScheme(
    primary = KofiGreen,
    onPrimary = OnGreen,
    primaryContainer = KofiGreenDeep,
    onPrimaryContainer = TextPrimary,

    secondary = Surface2,
    onSecondary = TextPrimary,
    secondaryContainer = Surface2,
    onSecondaryContainer = TextPrimary,

    tertiary = KofiGreenDim,
    onTertiary = OnGreen,

    background = Base,
    onBackground = TextPrimary,

    surface = Base,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,

    surfaceContainerLowest = Base,
    surfaceContainerLow = BaseElevated,
    surfaceContainer = BaseElevated,
    surfaceContainerHigh = BaseHighlight,
    surfaceContainerHighest = Surface2,

    inverseSurface = TextPrimary,
    inverseOnSurface = Base,

    outline = Surface3,
    outlineVariant = Surface3,

    error = ErrorRed,
    onError = TextPrimary,

    scrim = Base,
)

@Composable
fun SpotKofiTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSpotKofiColors provides SpotKofiColors(),
        LocalSpotKofiDimens provides SpotKofiDimens(),
        LocalSpotKofiShapes provides SpotKofiShapeTokens(),
    ) {
        MaterialTheme(
            colorScheme = SpotKofiColorScheme,
            typography = SpotKofiTypography,
            shapes = SpotKofiShapes,
            content = content,
        )
    }
}

/**
 * Accessor for the tokens Material 3 has no slot for.
 *
 * Mirrors the `MaterialTheme` function + object pattern, so call sites read as
 * `SpotKofiTheme.colors.textSecondary` / `SpotKofiTheme.dimens.screenGutter`.
 */
object SpotKofiTheme {
    val colors: SpotKofiColors
        @Composable @ReadOnlyComposable
        get() = LocalSpotKofiColors.current

    val dimens: SpotKofiDimens
        @Composable @ReadOnlyComposable
        get() = LocalSpotKofiDimens.current

    val shapes: SpotKofiShapeTokens
        @Composable @ReadOnlyComposable
        get() = LocalSpotKofiShapes.current
}
