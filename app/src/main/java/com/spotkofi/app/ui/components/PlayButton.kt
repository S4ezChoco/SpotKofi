package com.spotkofi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.spotkofi.app.R
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * The primary green play/pause affordance.
 *
 * The content description flips with [isPlaying] so the button always announces
 * the action it will perform, not its current state.
 */
@Composable
fun PlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = SpotKofiTheme.dimens.playButtonSm,
) {
    val colors = SpotKofiTheme.colors

    Box(
        modifier = modifier
            .size(size)
            .background(colors.accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(size)) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (isPlaying) R.string.cd_pause else R.string.cd_play,
                ),
                tint = colors.onAccent,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

/**
 * Borderless transport control for skip / shuffle / repeat.
 *
 * Painted at [iconSize] but always given a 48dp touch target, which is the
 * minimum accessible size regardless of how small the glyph looks.
 */
@Composable
fun TransportIconButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = SpotKofiTheme.colors.textPrimary,
    iconSize: Dp = SpotKofiTheme.dimens.iconLg,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(SpotKofiTheme.dimens.minTouchTarget),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun PlayButtonPreview() {
    SpotKofiTheme {
        PlayButton(isPlaying = false, onClick = {}, size = SpotKofiTheme.dimens.playButtonLg)
    }
}
