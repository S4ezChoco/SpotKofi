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
    val quickPick: Shape = RoundedCornerShape(4.dp),

    /** Release cards and the large Search category tiles. */
    val card: Shape = RoundedCornerShape(8.dp),

    /** Station tiles and Explore thumbnails. */
    val tile: Shape = RoundedCornerShape(6.dp),

    /** Filter pills and genre chips. */
    val chip: Shape = RoundedCornerShape(percent = 50),

    /** Primary CTA buttons. */
    val button: Shape = RoundedCornerShape(percent = 50),

    /** Search input. */
    val searchField: Shape = RoundedCornerShape(8.dp),

    /** Mini player bar. */
    val miniPlayer: Shape = RoundedCornerShape(6.dp),

    /** Bottom sheets and dialogs. */
    val sheet: Shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
)

val LocalSpotKofiShapes = staticCompositionLocalOf { SpotKofiShapeTokens() }
