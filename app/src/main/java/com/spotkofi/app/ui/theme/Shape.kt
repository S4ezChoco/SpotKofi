package com.spotkofi.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Corner radii.
 *
 * Album and playlist art is nearly square (4dp) because it is treated as
 * photography, while interactive chrome (chips, buttons, sheets) is fully
 * rounded. Artist avatars are the one exception: always a circle.
 */
val SpotKofiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Immutable
data class SpotKofiShapeTokens(
    /** Album / playlist / podcast cover art. */
    val artwork: Shape = RoundedCornerShape(4.dp),

    /** Artist avatars and user profile pictures. */
    val avatar: Shape = CircleShape,

    /** Cards in the "quick picks" grid on Home. */
    val quickPick: Shape = RoundedCornerShape(8.dp),

    /**
     * Release cards and the large Search category tiles.
     *
     * Softer than the artwork radius on purpose: chrome is allowed to look
     * modern and rounded, while cover art stays nearly square so it reads as
     * photography rather than as a widget.
     */
    val card: Shape = RoundedCornerShape(14.dp),

    /** Station tiles and Explore thumbnails. */
    val tile: Shape = RoundedCornerShape(10.dp),

    /** Grouped rows, e.g. a settings section. */
    val group: Shape = RoundedCornerShape(16.dp),

    /** Filter pills and genre chips. */
    val chip: Shape = RoundedCornerShape(percent = 50),

    /** Primary CTA buttons. */
    val button: Shape = RoundedCornerShape(percent = 50),

    /** Search input. */
    val searchField: Shape = RoundedCornerShape(8.dp),

    /** Mini player bar. */
    val miniPlayer: Shape = RoundedCornerShape(10.dp),

    /** Bottom sheets and dialogs. */
    val sheet: Shape = RoundedCornerShape(18.dp),
)

val LocalSpotKofiShapes = staticCompositionLocalOf { SpotKofiShapeTokens() }
