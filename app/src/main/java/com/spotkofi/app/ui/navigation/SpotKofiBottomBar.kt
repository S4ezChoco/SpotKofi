package com.spotkofi.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
 *
 * Selection is carried by a pill that grows behind the active item, the same
 * affordance Material uses, rather than by colour alone. Create is drawn as a
 * filled accent button because it is an action, not a place, and the bar should
 * not pretend otherwise.
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
                    elevation = 18.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black,
                    spotColor = Color.Black,
                )
                .clip(shape)
                // A slight vertical gradient instead of a flat fill: it reads as a
                // raised surface without needing a real blur, which would require
                // API 31 while minSdk here is 26.
                .background(
                    Brush.verticalGradient(
                        0f to colors.elevated.copy(alpha = 0.99f),
                        1f to colors.base.copy(alpha = 0.99f),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
                // heightIn, not height: at large accessibility font scales the
                // labels need more room, and a fixed height would clip them.
                .heightIn(min = dimens.floatingBarHeight)
                .padding(horizontal = dimens.spaceSm),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceXxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopLevelDestination.entries.forEach { destination ->
                if (destination.isAction) {
                    CreateItem(
                        expanded = createExpanded,
                        onClick = { onSelect(destination) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    NavItem(
                        destination = destination,
                        selected = destination == current,
                        onClick = { onSelect(destination) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    val tint by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.textSecondary,
        animationSpec = Motion.fast(),
        label = "navTint",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) colors.textPrimary else colors.textSecondary,
        animationSpec = Motion.fast(),
        label = "navLabel",
    )

    // Width, not alpha: an indicator that grows from the icon outwards tracks the
    // selection instead of blinking on top of it.
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 46.dp else 0.dp,
        animationSpec = Motion.bouncy(),
        label = "navIndicatorWidth",
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = Motion.fast(),
        label = "navIndicatorAlpha",
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
                role = Role.Tab,
                onClick = onClick,
            )
            .pressScale(interaction, pressedScale = 0.9f)
            .padding(vertical = dimens.spaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.height(30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(indicatorWidth)
                    .height(30.dp)
                    .graphicsLayer { alpha = indicatorAlpha * 0.16f }
                    .background(colors.accent, CircleShape),
            )

            NavIcon(
                destination = destination,
                selected = selected,
                tint = tint,
            )
        }

        Spacer(Modifier.height(dimens.spaceXs))

        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = labelColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Create is an action, so it is a filled button rather than a tab.
 *
 * A plus turned 45 degrees IS a cross, so the same glyph becomes the close
 * control. That is the whole trick: there is no second button appearing somewhere
 * else, the button you pressed is the button you press again.
 */
@Composable
private fun CreateItem(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val interaction = remember { MutableInteractionSource() }

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = Motion.bouncy(),
        label = "createRotation",
    )
    val container by animateColorAsState(
        targetValue = if (expanded) colors.accent else colors.accent.copy(alpha = 0.18f),
        animationSpec = Motion.fast(),
        label = "createContainer",
    )
    val glyph by animateColorAsState(
        targetValue = if (expanded) colors.onAccent else colors.accent,
        animationSpec = Motion.fast(),
        label = "createGlyph",
    )

    Column(
        modifier = modifier
            .selectable(
                selected = expanded,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .pressScale(interaction, pressedScale = 0.9f)
            .padding(vertical = dimens.spaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = glyph,
                modifier = Modifier
                    .size(dimens.iconSm)
                    .graphicsLayer { rotationZ = rotation },
            )
        }

        Spacer(Modifier.height(dimens.spaceXs))

        Text(
            text = stringResource(TopLevelDestination.Create.labelRes),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (expanded) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (expanded) colors.textPrimary else colors.textSecondary,
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

        // Drawn by CreateItem, which is a filled action button rather than a tab.
        TopLevelDestination.Create -> Unit
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun SpotKofiBottomBarPreview() {
    SpotKofiTheme {
        SpotKofiBottomBar(current = TopLevelDestination.Home, onSelect = {})
    }
}

@Preview(name = "Search selected", backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun SpotKofiBottomBarSearchPreview() {
    SpotKofiTheme {
        SpotKofiBottomBar(current = TopLevelDestination.Search, onSelect = {})
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
