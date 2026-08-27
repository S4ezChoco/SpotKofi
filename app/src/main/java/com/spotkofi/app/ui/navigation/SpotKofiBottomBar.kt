package com.spotkofi.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spotkofi.app.R
import com.spotkofi.app.ui.components.MorphIcon
import com.spotkofi.app.ui.motion.pressScale
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * Floating navigation island.
 *
 * Detached from every screen edge rather than docked. Two reasons beyond looks:
 * a floating bar cannot have its labels clipped by the gesture area, and it makes
 * the sheet that grows out of the Create button read as attached to that button
 * instead of to the bottom of the screen.
 *
 * Not Material 3's `NavigationBar`, which is edge-to-edge by construction and
 * brings its own tonal surface, indicator pill and 80dp height.
 */
@Composable
fun SpotKofiBottomBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    /** True while the Create sheet is open, which turns the plus into a close. */
    createExpanded: Boolean = false,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val shape = RoundedCornerShape(dimens.floatingBarRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = dimens.floatingBarMargin,
                end = dimens.floatingBarMargin,
                bottom = dimens.floatingBarGap,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Shadow before clip so the halo falls outside the pill. On a dark
                // background it is barely visible as a shadow, but it separates the
                // island from artwork scrolling underneath.
                .shadow(
                    elevation = 20.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black,
                    spotColor = Color.Black,
                )
                .clip(shape)
                // Slightly translucent so content scrolling behind tints it. A real
                // blur would need API 31+, and minSdk here is 26.
                .background(colors.elevated.copy(alpha = 0.97f))
                .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
                // heightIn, not height: at large accessibility font scales the
                // labels need more room, and a fixed height would clip them.
                .heightIn(min = dimens.floatingBarHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopLevelDestination.entries.forEach { destination ->
                // Create is an action. It paints as active while its sheet is open,
                // which is the visual link between the button and the panel.
                val selected = if (destination.isAction) {
                    createExpanded
                } else {
                    destination == current
                }

                NavItem(
                    destination = destination,
                    selected = selected,
                    createExpanded = createExpanded,
                    onClick = { onSelect(destination) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    destination: TopLevelDestination,
    selected: Boolean,
    createExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    val tint by animateColorAsState(
        targetValue = if (selected) colors.textPrimary else colors.textSecondary,
        animationSpec = Motion.fast(),
        label = "navTint",
    )

    val emphasis by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = Motion.bouncy(),
        label = "navEmphasis",
    )

    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                interactionSource = interaction,
                // No ripple: the press is expressed by scale, which survives on
                // artwork and tinted surfaces where a ripple would not.
                indication = null,
                onClick = onClick,
            )
            .pressScale(interaction, pressedScale = 0.86f)
            .padding(vertical = dimens.spaceXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Soft glow behind the active icon. Cheap depth cue that avoids the
            // heavy pill indicator Material 3 would draw.
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .graphicsLayer { alpha = emphasis * 0.18f }
                    .background(colors.accent, SpotKofiTheme.shapes.avatar),
            )

            NavIcon(
                destination = destination,
                selected = selected,
                createExpanded = createExpanded,
                tint = tint,
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Icon rendering lives here rather than on the enum because Your Library needs a
 * drawable resource while the others use Material vectors, and the enum should
 * not have to model both.
 *
 * `contentDescription` is null throughout: the label directly below already names
 * the item, and announcing both would read it twice.
 */
@Composable
private fun NavIcon(
    destination: TopLevelDestination,
    selected: Boolean,
    createExpanded: Boolean,
    tint: Color,
) {
    val size = SpotKofiTheme.dimens.iconMd

    when (destination) {
        TopLevelDestination.Home -> MorphIcon(
            selected = selected,
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
            contentDescription = null,
            tint = tint,
            size = size,
        )

        TopLevelDestination.Search -> MorphIcon(
            selected = selected,
            selectedIcon = Icons.Filled.Search,
            unselectedIcon = Icons.Outlined.Search,
            contentDescription = null,
            tint = tint,
            size = size,
        )

        TopLevelDestination.Library -> MorphIcon(
            selected = selected,
            selectedPainter = painterResource(R.drawable.ic_nav_library_filled),
            unselectedPainter = painterResource(R.drawable.ic_nav_library),
            contentDescription = null,
            tint = tint,
            size = size,
        )

        // A plus turned 45 degrees IS a cross, so the same glyph becomes the close
        // control. That is the whole trick: there is no second button appearing
        // somewhere else, the button you pressed is the button you press again.
        TopLevelDestination.Create -> {
            val rotation by animateFloatAsState(
                targetValue = if (createExpanded) 45f else 0f,
                animationSpec = Motion.bouncy(),
                label = "createRotation",
            )
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(size)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun SpotKofiBottomBarPreview() {
    SpotKofiTheme {
        SpotKofiBottomBar(current = TopLevelDestination.Home, onSelect = {})
    }
}

@Preview(name = "Create open", backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun SpotKofiBottomBarCreateOpenPreview() {
    SpotKofiTheme {
        SpotKofiBottomBar(
            current = TopLevelDestination.Home,
            onSelect = {},
            createExpanded = true,
        )
    }
}
