package com.spotkofi.app.ui.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spotkofi.app.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * Shrinks slightly while pressed.
 *
 * Applied through [graphicsLayer] rather than `scale`/`padding` so the animation
 * runs entirely in the draw phase: no measure, no layout, and no recomposition of
 * anything around it. That is what keeps a grid of cards smooth while one is
 * being pressed.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.94f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.snappy(),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Clickable with press feedback and no ripple.
 *
 * The scale replaces the ripple deliberately: most surfaces here are artwork or
 * tinted cards, where a ripple either vanishes or looks dirty.
 *
 * `clickable` is applied before the scale so hit testing uses the unscaled
 * bounds; otherwise the touch target would shrink under the finger.
 */
@Composable
fun Modifier.clickableScale(
    pressedScale: Float = 0.94f,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
        .pressScale(interaction, pressedScale)
}

/**
 * Fades and lifts an item into place once, offset by its position in the list.
 *
 * [index] drives the delay so a list assembles in sequence instead of all at
 * once. The delay is capped by [Motion.StaggerMaxSteps] so items far down a long
 * list still appear promptly.
 *
 * Like [pressScale] this only touches the draw phase.
 */
@Composable
fun Modifier.staggeredEntry(
    index: Int,
    slide: Dp = 16.dp,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val step = index.coerceIn(0, Motion.StaggerMaxSteps)
        delay((step * Motion.StaggerStepMs).toLong())
        appeared = true
    }

    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = Motion.medium(),
        label = "staggerEntry",
    )

    val slidePx = with(LocalDensity.current) { slide.toPx() }

    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * slidePx
    }
}
