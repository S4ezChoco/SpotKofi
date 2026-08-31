package com.spotkofi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spotkofi.app.ui.theme.SpotKofiBrandStyle
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * Unified screen header used on every top-level tab.
 *
 * Shows the SpotKofi logo followed by the screen title in the brand typeface,
 * an optional trailing action slot (library search, analytics, add, etc.),
 * and a modern animated burger menu button on the far right.
 *
 * Using the same component across Home, Search and Library keeps the top chrome
 * visually consistent — the logo and typography are always aligned, and the burger
 * menu button provides a unified drawer entry point on all tabs.
 */
@Composable
fun SpotKofiScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onLogoClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    gutter: Dp = SpotKofiTheme.dimens.screenGutter,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val dimens = SpotKofiTheme.dimens
    val colors = SpotKofiTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = gutter, vertical = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        SpotKofiLogo(onClick = onLogoClick, size = 28.dp)
        Spacer(Modifier.width(dimens.spaceSm))
        Text(
            text = title,
            style = SpotKofiBrandStyle,
            color = colors.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            trailing()
            if (onMenuClick != null) {
                Spacer(Modifier.width(dimens.spaceXs))
            }
        }
        if (onMenuClick != null) {
            AnimatedBurgerButton(onClick = onMenuClick)
        }
    }
}

