package com.spotkofi.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
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
 * It lives here rather than inside a screen because the full player, the
 * collapsed player bar and the mini player must show the *same* thing: a filled
 * green check when the track is in the library, a hollow plus when it is not.
 * When one of them drew a permanent plus the app looked like it had lost the
 * user's save.
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

    val container by animateColorAsState(
        targetValue = if (isSaved) colors.accent else Color.Transparent,
        animationSpec = Motion.fast(),
        label = "savedContainer",
    )
    val tint by animateColorAsState(
        targetValue = if (isSaved) colors.onAccent else colors.textSecondary,
        animationSpec = Motion.fast(),
        label = "savedTint",
    )
    // The glyph swap is masked by a quick scale pulse, so the check does not
    // simply pop into existence on top of the plus.
    val pulse by animateFloatAsState(
        targetValue = if (isSaved) 1f else 0.92f,
        animationSpec = Motion.bouncy(),
        label = "savedPulse",
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            }
            .background(container, CircleShape)
            .border(
                width = if (isSaved) 0.dp else 1.5.dp,
                color = if (isSaved) Color.Transparent else colors.textSecondary,
                shape = CircleShape,
            )
            .toggleable(
                value = isSaved,
                interactionSource = interaction,
                indication = null,
                onValueChange = { onToggle() },
            )
            .pressScale(interaction, pressedScale = 0.82f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isSaved) Icons.Filled.Check else Icons.Filled.Add,
            contentDescription = stringResource(
                if (isSaved) R.string.cd_unfavourite else R.string.cd_favourite,
            ),
            tint = tint,
            modifier = Modifier.size(size * 0.62f),
        )
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
