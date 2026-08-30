package com.spotkofi.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
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
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * Minimal save control shared by the player, lists, and Library.
 *
 * A bookmark communicates "keep this" more clearly than a plus that suddenly
 * becomes a check. The selection is shown by the filled glyph and accent tint;
 * the short scale pulse gives immediate feedback without a large decorative halo.
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
    val pulse = remember { Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(isSaved) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        pulse.snapTo(0.78f)
        pulse.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 720f))
    }

    val background by animateColorAsState(
        targetValue = if (isSaved) colors.accent.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(160),
        label = "savedBackground",
    )
    val border by animateColorAsState(
        targetValue = if (isSaved) colors.accent.copy(alpha = 0.45f) else colors.divider,
        animationSpec = tween(160),
        label = "savedBorder",
    )
    val glyphScale by animateFloatAsState(
        targetValue = if (isSaved) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "savedGlyphScale",
    )

    Box(
        modifier = modifier
            .size(size)
            .toggleable(
                value = isSaved,
                interactionSource = interaction,
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .pressScale(interaction, pressedScale = 0.82f)
            .background(background, CircleShape)
            .border(1.dp, border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val scale = glyphScale * pulse.value
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = isSaved,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = 0.6f), initialScale = 0.55f) + fadeIn(tween(120)))
                        .togetherWith(scaleOut(tween(100), targetScale = 0.7f) + fadeOut(tween(80)))
                },
                label = "savedGlyph",
            ) { saved ->
                Icon(
                    imageVector = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = stringResource(
                        if (saved) R.string.cd_unfavourite else R.string.cd_favourite,
                    ),
                    tint = if (saved) colors.accent else colors.textSecondary,
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
        }
    }
}
