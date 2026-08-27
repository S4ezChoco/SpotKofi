package com.spotkofi.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spotkofi.app.ui.motion.clickableScale
import com.spotkofi.app.ui.motion.staggeredEntry
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme

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
 * The Create panel.
 *
 * Deliberately not a `ModalBottomSheet`. A bottom sheet is anchored to the screen
 * edge, but this panel has to appear anchored to the Create button in the floating
 * nav bar: it scales up out of that button's corner and sits directly above it,
 * and the button itself rotates into the close control. A sheet cannot express
 * that relationship, and the previous version's separate floating X read as an
 * unrelated third button.
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
    // Kept separate from [visible] so the scrim can ease in over its own duration
    // rather than snapping to full black the instant the panel appears.
    var scrimShown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { scrimShown = visible }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (scrimShown) 0.66f else 0f,
        animationSpec = Motion.medium(),
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
            // Scaled from the bottom-right corner, which is where the Create button
            // sits in the bar. That origin is what sells "this grew out of that
            // button" rather than "a sheet slid up from the bottom".
            enter = scaleIn(
                animationSpec = Motion.gentle(),
                initialScale = 0.82f,
                transformOrigin = TransformOrigin(0.88f, 1f),
            ) + fadeIn(animationSpec = Motion.fast()),
            exit = scaleOut(
                animationSpec = Motion.snappy(),
                targetScale = 0.86f,
                transformOrigin = TransformOrigin(0.88f, 1f),
            ) + fadeOut(animationSpec = Motion.fast()),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val shape = RoundedCornerShape(dimens.floatingBarRadius)

            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(
                        start = dimens.floatingBarMargin,
                        end = dimens.floatingBarMargin,
                        // Clears the bar, then leaves a small visible gap so the two
                        // surfaces read as a pair rather than one merged block.
                        bottom = dimens.floatingBarGap +
                            dimens.floatingBarHeight +
                            dimens.spaceSm,
                    )
                    .fillMaxWidth()
                    .shadow(
                        elevation = 24.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black,
                        spotColor = Color.Black,
                    )
                    .clip(shape)
                    .background(colors.elevated.copy(alpha = 0.98f))
                    .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
                    .padding(vertical = dimens.spaceMd),
            ) {
                CreateOption.entries.forEachIndexed { index, option ->
                    CreateRow(
                        option = option,
                        onClick = { onOptionClick(option) },
                        modifier = Modifier.staggeredEntry(index, slide = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateRow(
    option: CreateOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickableScale(pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(colors.iconWell, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(dimens.iconSm),
            )
        }

        Spacer(Modifier.width(dimens.spaceLg))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                if (option.isBeta) {
                    Spacer(Modifier.width(dimens.spaceSm))
                    BetaBadge()
                }
            }
            Spacer(Modifier.height(1.dp))
            Text(
                text = option.description,
                style = MaterialTheme.typography.bodySmall,
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
