package com.spotkofi.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * Icon that morphs between two states instead of swapping instantly.
 *
 * Three things happen at once on a single bouncy spring: the outgoing glyph fades
 * out while shrinking, the incoming one fades in while overshooting past its
 * final size, and the whole thing rotates a few degrees. Sharing one spring is
 * what makes it read as one object deforming rather than two icons cross-fading.
 *
 * Both glyphs stay composed and are driven through [graphicsLayer], so the morph
 * costs no measure or layout work.
 */
@Composable
fun MorphIcon(
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = SpotKofiTheme.dimens.iconMd,
    /** Degrees of twist at the midpoint of the morph. */
    twist: Float = 10f,
) {
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = Motion.bouncy(),
        label = "morphProgress",
    )

    // Peaks at the midpoint so the twist unwinds by the time it settles.
    val midpoint = 1f - kotlin.math.abs(progress - 0.5f) * 2f

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // Outgoing.
        Icon(
            imageVector = unselectedIcon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    alpha = 1f - progress
                    val s = 1f - 0.25f * progress
                    scaleX = s
                    scaleY = s
                    rotationZ = -twist * midpoint
                },
        )
        // Incoming. The spring overshoots past 1f, which is the "pop".
        Icon(
            imageVector = selectedIcon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    alpha = progress
                    val s = 0.75f + 0.25f * progress
                    scaleX = s
                    scaleY = s
                    rotationZ = twist * midpoint
                },
        )
    }
}

/** [MorphIcon] for icons that come from drawable resources rather than vectors. */
@Composable
fun MorphIcon(
    selected: Boolean,
    selectedPainter: Painter,
    unselectedPainter: Painter,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = SpotKofiTheme.dimens.iconMd,
    twist: Float = 10f,
) {
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = Motion.bouncy(),
        label = "morphProgressPainter",
    )
    val midpoint = 1f - kotlin.math.abs(progress - 0.5f) * 2f

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(
            painter = unselectedPainter,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    alpha = 1f - progress
                    val s = 1f - 0.25f * progress
                    scaleX = s
                    scaleY = s
                    rotationZ = -twist * midpoint
                },
        )
        Icon(
            painter = selectedPainter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    alpha = progress
                    val s = 0.75f + 0.25f * progress
                    scaleX = s
                    scaleY = s
                    rotationZ = twist * midpoint
                },
        )
    }
}

/**
 * Play and pause, morphing through a quarter turn.
 *
 * The rotation is the trick: play and pause share no outline, so a plain
 * crossfade looks like a glitch. Spinning through 90 degrees gives the eye a path
 * to follow between the two shapes.
 */
@Composable
fun PlayPauseIcon(
    isPlaying: Boolean,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = SpotKofiTheme.dimens.iconMd,
) {
    val progress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = Motion.bouncy(),
        label = "playPauseProgress",
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) null else contentDescription,
            tint = tint,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    alpha = 1f - progress
                    rotationZ = 90f * progress
                    val s = 1f - 0.2f * progress
                    scaleX = s
                    scaleY = s
                },
        )
        Icon(
            imageVector = Icons.Filled.Pause,
            contentDescription = if (isPlaying) contentDescription else null,
            tint = tint,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    alpha = progress
                    rotationZ = -90f * (1f - progress)
                    val s = 0.8f + 0.2f * progress
                    scaleX = s
                    scaleY = s
                },
        )
    }
}
