package com.spotkofi.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spotkofi.app.R
import com.spotkofi.app.ui.motion.pressScale
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * The add-to-library control, shared by every surface that can save a track.
 *
 * It lives here rather than inside a screen because the full player, the collapsed
 * player bar, the mini player and Your Library must show the *same* thing: a
 * filled green check when the track is in the library, a hollow plus when it is
 * not. When one of them drew a permanent plus, the app looked like it had lost the
 * user's save.
 *
 * Saving is the one moment worth animating. The glyph does not simply swap: the
 * ring fills, a halo expands past the edge and fades, and the whole control
 * overshoots and settles. Removing runs the same motion in reverse but damped, so
 * unsaving reads as an undo rather than as its own celebration.
 */
@Composable
fun SavedToggle(
    isSaved: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) {
    val colors = SpotKofiTheme.colors
    val interaction = remember { MutableInteractionSource() }

    val transition = updateTransition(targetState = isSaved, label = "saved")

    // Overshoots on the way in, settles flat on the way out. A symmetric spec made
    // removing a track look like an achievement too.
    val scale by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                spring(dampingRatio = 0.36f, stiffness = 620f)
            } else {
                spring(dampingRatio = 0.9f, stiffness = 700f)
            }
        },
        label = "savedScale",
    ) { saved -> if (saved) 1f else 0.9f }

    /**
     * One-shot halo, driven by its own animation rather than by the transition.
     *
     * A transition interpolates between two resting states, and both "saved" and
     * "not saved" are at rest with no halo - so expressing it that way produced no
     * visible flash at all. This runs once per save and returns to nothing.
     */
    val halo = remember { Animatable(0f) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(isSaved) {
        if (!settled) {
            // Skip the very first pass: a list of already-saved songs must not all
            // flash as it scrolls into view.
            settled = true
            return@LaunchedEffect
        }
        halo.snapTo(0f)
        if (isSaved) {
            halo.animateTo(1f, tween(durationMillis = 460, easing = Motion.Decelerate))
        }
    }

    val container by animateColorAsState(
        targetValue = if (isSaved) colors.accent else Color.Transparent,
        animationSpec = Motion.fast(),
        label = "savedContainer",
    )
    val outline by animateColorAsState(
        targetValue = if (isSaved) Color.Transparent else colors.textSecondary,
        animationSpec = Motion.fast(),
        label = "savedOutline",
    )
    val outlineWidth by animateDpAsState(
        targetValue = if (isSaved) 0.dp else 1.5.dp,
        animationSpec = Motion.fast(),
        label = "savedOutlineWidth",
    )

    Box(
        modifier = modifier
            .size(size)
            .toggleable(
                value = isSaved,
                interactionSource = interaction,
                // No ripple: a ripple centred on the finger fights the pop, and this
                // control sits on artwork where a ripple barely reads anyway.
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .pressScale(interaction, pressedScale = 0.82f),
        contentAlignment = Alignment.Center,
    ) {
        // Expanding halo, behind the ring. Read in the draw phase so the sweep does
        // not recompose the row it sits in.
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val progress = halo.value
                    val grow = 1f + progress * 0.95f
                    scaleX = grow
                    scaleY = grow
                    alpha = (1f - progress) * 0.38f
                }
                .background(colors.accent, CircleShape),
        )

        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(container, CircleShape)
                .border(outlineWidth, outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = isSaved,
                transitionSpec = {
                    // The incoming glyph grows from the centre while the outgoing one
                    // shrinks away, so the two never look stacked.
                    (
                        scaleIn(spring(dampingRatio = 0.45f, stiffness = 700f), initialScale = 0.4f) +
                            fadeIn(tween(120))
                        ) togetherWith (
                        scaleOut(tween(120), targetScale = 0.5f) + fadeOut(tween(90))
                        )
                },
                label = "savedGlyph",
            ) { saved ->
                Icon(
                    imageVector = if (saved) Icons.Filled.Check else Icons.Filled.Add,
                    contentDescription = stringResource(
                        if (saved) R.string.cd_unfavourite else R.string.cd_favourite,
                    ),
                    tint = if (saved) colors.onAccent else colors.textSecondary,
                    modifier = Modifier.size(size * 0.62f),
                )
            }
        }
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun SavedTogglePreview() {
    SpotKofiTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            SavedToggle(isSaved = false, onToggle = {})
            SavedToggle(isSaved = true, onToggle = {})
            SavedToggle(isSaved = true, onToggle = {}, size = 24.dp)
        }
    }
}
