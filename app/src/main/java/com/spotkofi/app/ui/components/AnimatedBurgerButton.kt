package com.spotkofi.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme
import kotlinx.coroutines.launch

/**
 * Modern animated hamburger menu button for top screen headers.
 *
 * Renders sleek, staggered pill-capped bars that fluidly morph and twist
 * with a tactile spring animation when pressed or tapped.
 */
@Composable
fun AnimatedBurgerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = SpotKofiTheme.colors.textPrimary,
    contentDescription: String = "Open navigation menu",
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    val clickPop = remember { Animatable(0f) }

    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = Motion.snappy(),
        label = "burgerPress",
    )

    val scale = (1f - 0.12f * pressProgress) * (1f + 0.08f * clickPop.value)
    val rotation = -10f * pressProgress + 15f * clickPop.value

    Box(
        modifier = modifier
            .size(size)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    scope.launch {
                        clickPop.snapTo(1f)
                        clickPop.animateTo(0f, animationSpec = Motion.bouncy())
                    }
                    onClick()
                },
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val strokeWidth = 2.dp.toPx()
            val cap = StrokeCap.Round
            val totalWidth = this.size.width
            val totalHeight = this.size.height

            val left = totalWidth * 0.15f
            val right = totalWidth * 0.85f
            val fullBarWidth = right - left

            val topY = totalHeight * 0.28f
            val midY = totalHeight * 0.50f
            val botY = totalHeight * 0.72f

            // Dynamic morphing:
            // Top bar: full width, slightly compresses on press
            val topEnd = right - (fullBarWidth * 0.15f * pressProgress)
            drawLine(
                color = tint,
                start = Offset(left, topY),
                end = Offset(topEnd, topY),
                strokeWidth = strokeWidth,
                cap = cap,
            )

            // Middle bar: staggered (shorter) by default, stretches to full on press/pop
            val midStart = left + (fullBarWidth * 0.22f * (1f - pressProgress))
            val midEnd = right
            drawLine(
                color = tint,
                start = Offset(midStart, midY),
                end = Offset(midEnd, midY),
                strokeWidth = strokeWidth,
                cap = cap,
            )

            // Bottom bar: medium width by default, expands on press
            val botEnd = right - (fullBarWidth * 0.35f * (1f - pressProgress))
            drawLine(
                color = tint,
                start = Offset(left, botY),
                end = Offset(botEnd, botY),
                strokeWidth = strokeWidth,
                cap = cap,
            )
        }
    }
}
