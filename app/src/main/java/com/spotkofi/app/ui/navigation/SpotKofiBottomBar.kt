package com.spotkofi.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
 * The three persistent destinations, docked to the bottom edge above the system
 * navigation inset. There is no Create action here: playlist creation belongs to
 * the Library header where it has a clear, local purpose.
 */
@Composable
fun SpotKofiBottomBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    // The outer Box owns the background all the way through the system inset.
    // Applying navigationBarsPadding to the same bottom-aligned surface made the
    // bar look like a floating card with a dark gap below it.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.base),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = dimens.floatingBarHeight)
                .padding(horizontal = dimens.spaceLg),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopLevelDestination.entries.forEach { destination ->
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
        targetValue = if (selected) colors.textPrimary else colors.textSecondary,
        animationSpec = Motion.fast(),
        label = "navTint",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) colors.textPrimary else colors.textSecondary,
        animationSpec = Motion.fast(),
        label = "navLabel",
    )
    val pillColor by animateColorAsState(
        targetValue = if (selected) colors.highlight else Color.Transparent,
        animationSpec = Motion.fast(),
        label = "navPill",
    )

    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = Motion.medium(),
        label = "navSelectionScale",
    )

    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = selectionScale
                scaleY = selectionScale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(pillColor)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .pressScale(interaction, pressedScale = 0.94f)
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NavIcon(
            destination = destination,
            selected = selected,
            tint = tint,
        )

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
 * Icon rendering lives here rather than on the enum because Library uses a
 * drawable resource while Home and Search use Material vectors.
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
