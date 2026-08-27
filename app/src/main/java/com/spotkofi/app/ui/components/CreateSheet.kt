package com.spotkofi.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spotkofi.app.R
import com.spotkofi.app.ui.theme.SpotKofiTheme

/** How long the sheet takes to appear or leave. */
private const val SHEET_ANIM_MS = 260

/** The options offered by the Create action in the bottom bar. */
enum class CreateOption(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isBeta: Boolean = false,
) {
    Playlist(
        title = "Playlist",
        description = "Create a playlist with songs or episodes",
        icon = Icons.Filled.MusicNote,
    ),
    Collaborative(
        title = "Collaborative playlist",
        description = "Create a playlist together with friends",
        icon = Icons.Filled.People,
    ),
    Mixed(
        title = "Mixed playlist",
        description = "Mix songs with smooth transitions",
        icon = Icons.Filled.Tune,
        isBeta = true,
    ),
    Blend(
        title = "Blend",
        description = "Combine your friends' tastes into a playlist",
        icon = Icons.Filled.Contrast,
    ),
    AiPlaylist(
        title = "AI Playlist",
        description = "Turn your ideas into playlists with AI",
        icon = Icons.Filled.AutoAwesome,
        isBeta = true,
    ),
    Jam(
        title = "Jam",
        description = "Listen together from anywhere",
        icon = Icons.Filled.Podcasts,
    ),
}

/**
 * The Create sheet.
 *
 * Hand-rolled rather than `ModalBottomSheet` for one reason: the close button
 * floats on the scrim *below* the panel's rounded bottom edge, which a real
 * bottom sheet cannot express because its content is clipped to the sheet.
 *
 * [visible] drives the animation rather than the caller adding or removing this
 * composable, because a removed composable cannot play an exit transition.
 */
@Composable
fun CreateSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOptionClick: (CreateOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Drives the scrim independently of the panel so the background darkens
    // progressively instead of snapping to full black.
    var scrimShown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { scrimShown = visible }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (scrimShown) 0.62f else 0f,
        animationSpec = tween(SHEET_ANIM_MS),
        label = "createScrimAlpha",
    )

    // Nothing to draw and nothing to hit-test once fully hidden.
    if (!visible && scrimAlpha == 0f) return

    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    BackHandler(enabled = visible, onBack = onDismiss)

    Box(modifier = modifier.fillMaxSize()) {
        // Scrim. `indication = null` so tapping to dismiss does not ripple across
        // the whole screen.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(animationSpec = tween(SHEET_ANIM_MS)) { it / 3 } +
                fadeIn(animationSpec = tween(SHEET_ANIM_MS)),
            exit = slideOutVertically(animationSpec = tween(SHEET_ANIM_MS)) { it / 3 } +
                fadeOut(animationSpec = tween(SHEET_ANIM_MS)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 84.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.highlight)
                    .padding(vertical = dimens.spaceSm),
            ) {
                CreateOption.entries.forEach { option ->
                    CreateRow(option = option, onClick = { onOptionClick(option) })
                }
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(SHEET_ANIM_MS)),
            exit = fadeOut(animationSpec = tween(SHEET_ANIM_MS)),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            CloseButton(onClick = onDismiss)
        }
    }
}

/** Floating close button, sitting clear of the panel. */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    val dimens = SpotKofiTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = tween(120),
        label = "closeButtonScale",
    )

    Box(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(end = dimens.screenGutter, bottom = dimens.spaceLg)
            .scale(scale)
            .size(dimens.minTouchTarget)
            .background(Color.White, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.cd_close),
            tint = Color.Black,
            modifier = Modifier.size(dimens.iconMd),
        )
    }
}

@Composable
private fun CreateRow(
    option: CreateOption,
    onClick: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(120),
        label = "createRowScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(colors.iconWell, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(dimens.iconMd),
            )
        }

        Spacer(Modifier.width(dimens.spaceLg))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                if (option.isBeta) {
                    Spacer(Modifier.width(dimens.spaceSm))
                    BetaBadge()
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = option.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun BetaBadge() {
    Text(
        text = "Beta",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = Color.Black,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(SpotKofiTheme.colors.accent)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@Preview(backgroundColor = 0xFF121212, showBackground = true, heightDp = 900)
@Composable
private fun CreateSheetPreview() {
    SpotKofiTheme {
        CreateSheet(visible = true, onDismiss = {}, onOptionClick = {})
    }
}
