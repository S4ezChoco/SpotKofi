package com.spotkofi.app.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spotkofi.app.R
import com.spotkofi.app.ui.layout.rememberResponsiveLayout
import com.spotkofi.app.ui.motion.clickableScale
import com.spotkofi.app.ui.motion.staggeredEntry
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme

/** One settings row. The subtitle previews what is inside. */
private data class SettingsEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
)

/**
 * Grouped rather than one flat list.
 *
 * Nine undifferentiated rows is a wall; splitting them into named groups on
 * separate cards gives the eye somewhere to land and makes the screen scannable.
 */
private data class SettingsGroup(
    val heading: String,
    val entries: List<SettingsEntry>,
)

private val SettingsGroups = listOf(
    SettingsGroup(
        heading = "Account",
        entries = listOf(
            SettingsEntry(Icons.Filled.AccountCircle, "Account", "Username \u2022 Subscription"),
            SettingsEntry(Icons.Filled.Lock, "Privacy and social", "Private session \u2022 Public playlists"),
            SettingsEntry(Icons.Filled.Notifications, "Notifications", "Push \u2022 Email"),
        ),
    ),
    SettingsGroup(
        heading = "Playback",
        entries = listOf(
            SettingsEntry(Icons.Filled.VolumeUp, "Playback", "Gapless playback \u2022 Autoplay"),
            SettingsEntry(Icons.Filled.MusicNote, "Content and display", "Music videos \u2022 Allow explicit content"),
            SettingsEntry(Icons.Filled.Equalizer, "Media quality", "Wi-Fi streaming \u2022 Download quality"),
        ),
    ),
    SettingsGroup(
        heading = "Device",
        entries = listOf(
            SettingsEntry(Icons.Filled.PhoneAndroid, "Apps and devices", "SpotKofi Connect control"),
            SettingsEntry(Icons.Filled.Download, "Data-saving and offline", "Data saver \u2022 Offline mode"),
            SettingsEntry(Icons.Filled.Info, "About and support", "Version \u2022 Privacy Policy"),
        ),
    ),
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val layout = rememberResponsiveLayout()
    val listState = rememberLazyListState()

    // The bar only gains a surface once content is behind it, so at rest the
    // screen reads as one plane instead of a bar stuck to a list.
    val lifted by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 4 }
    }
    val barColor by animateColorAsState(
        targetValue = if (lifted) colors.highlight else colors.base,
        animationSpec = Motion.fast(),
        label = "settingsBar",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.base),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(barColor)
                .padding(top = contentPadding.calculateTopPadding())
                .padding(horizontal = dimens.spaceXs, vertical = dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = colors.textPrimary,
                )
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { /* Phase 5: settings search */ }) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.nav_search),
                    tint = colors.textPrimary,
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = dimens.spaceMd,
                bottom = contentPadding.calculateBottomPadding() + dimens.spaceXxl,
            ),
        ) {
            SettingsGroups.forEachIndexed { index, group ->
                item(key = group.heading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredEntry(index)
                            .padding(
                                horizontal = layout.gutter,
                                vertical = dimens.spaceSm,
                            ),
                    ) {
                        Text(
                            text = group.heading,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(
                                start = dimens.spaceXs,
                                bottom = dimens.spaceSm,
                            ),
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SpotKofiTheme.shapes.group)
                                .background(colors.card),
                        ) {
                            group.entries.forEach { entry ->
                                SettingsRow(entry)
                            }
                        }
                    }
                }
            }

            item(key = "logout") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEntry(SettingsGroups.size)
                        .padding(top = dimens.spaceXl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Log out",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Black,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickableScale(pressedScale = 0.95f) {
                                /* Phase 3: Supabase sign-out */
                            }
                            .padding(
                                horizontal = dimens.spaceXxl,
                                vertical = dimens.spaceMd,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(entry: SettingsEntry) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableScale(pressedScale = 0.98f) {
                /* Phase 3+: each group gets its own screen */
            }
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.iconWell),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(dimens.iconSm),
            )
        }
        Spacer(Modifier.width(dimens.spaceMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(dimens.iconSm),
        )
    }
}

@Preview(name = "Settings", backgroundColor = 0xFF121212, showBackground = true, heightDp = 1000)
@Composable
private fun SettingsPreview() {
    SpotKofiTheme {
        SettingsScreen(onBack = {})
    }
}
