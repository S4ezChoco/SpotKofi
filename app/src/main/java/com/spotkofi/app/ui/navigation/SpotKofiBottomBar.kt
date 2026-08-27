package com.spotkofi.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spotkofi.app.R
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * Bottom navigation.
 *
 * Deliberately not Material 3's `NavigationBar`: that component brings its own
 * tonal surface, indicator pill and 80dp height. The real bar is shorter, has no
 * selection indicator, and fades into the content above it, so building the row
 * directly is less work than overriding the defaults.
 */
@Composable
fun SpotKofiBottomBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Order matters here. The background is applied before the inset so it
            // paints all the way through the gesture area, then the inset pushes
            // only the icons and labels up. Padding the bar from the outside
            // instead leaves a visible strip of app background below it.
            .background(
                // Vertical fade so scrolled content dissolves into the bar
                // instead of ending on a hard edge.
                Brush.verticalGradient(
                    listOf(Color.Transparent, colors.base, colors.base),
                ),
            )
            .navigationBarsPadding()
            .height(dimens.bottomBarHeight),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopLevelDestination.entries.forEach { destination ->
            // Create is an action, so it never paints as selected.
            val selected = !destination.isAction && destination == current

            val tint by animateColorAsState(
                targetValue = if (selected) colors.textPrimary else colors.textSecondary,
                animationSpec = tween(200),
                label = "navTint",
            )

            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (pressed) 0.86f else 1f,
                animationSpec = tween(110),
                label = "navPressScale",
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = selected,
                        interactionSource = interaction,
                        // No ripple: the bar has no item background for one to
                        // land on, so the press is expressed by the scale instead.
                        indication = null,
                        onClick = { onSelect(destination) },
                    )
                    .padding(vertical = dimens.spaceSm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(modifier = Modifier.scale(scale)) {
                    NavIcon(destination = destination, selected = selected, tint = tint)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(destination.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                )
            }
        }
    }
}

/**
 * Icon rendering lives here rather than on the enum because Your Library needs a
 * drawable resource while the others use Material vectors, and the enum should
 * not have to model both.
 *
 * `contentDescription` is null throughout: the label directly below already
 * names the item, and announcing both would read it twice.
 */
@Composable
private fun NavIcon(
    destination: TopLevelDestination,
    selected: Boolean,
    tint: Color,
) {
    val size = SpotKofiTheme.dimens.iconMd

    when (destination) {
        TopLevelDestination.Home -> Icon(
            imageVector = if (selected) Icons.Filled.Home else Icons.Outlined.Home,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size),
        )

        TopLevelDestination.Search -> Icon(
            imageVector = if (selected) Icons.Filled.Search else Icons.Outlined.Search,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size),
        )

        TopLevelDestination.Library -> Icon(
            painter = painterResource(
                if (selected) R.drawable.ic_nav_library_filled else R.drawable.ic_nav_library,
            ),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size),
        )

        TopLevelDestination.Create -> Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size),
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
