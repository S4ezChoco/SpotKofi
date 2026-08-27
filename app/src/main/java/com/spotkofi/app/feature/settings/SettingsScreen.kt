package com.spotkofi.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spotkofi.app.R
import com.spotkofi.app.ui.theme.SpotKofiTheme

/** One settings group. The subtitle previews what is inside. */
private data class SettingsEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
)

private val SettingsEntries = listOf(
    SettingsEntry(Icons.Filled.AccountCircle, "Account", "Username \u2022 Refer friends to Premium"),
    SettingsEntry(Icons.Filled.MusicNote, "Content and display", "Music videos \u2022 Allow explicit content"),
    SettingsEntry(Icons.Filled.Lock, "Privacy and social", "Private session \u2022 Public playlists"),
    SettingsEntry(Icons.Filled.VolumeUp, "Playback", "Gapless playback \u2022 Autoplay"),
    SettingsEntry(Icons.Filled.Notifications, "Notifications", "Push \u2022 Email"),
    SettingsEntry(Icons.Filled.PhoneAndroid, "Apps and devices", "Amazon Alexa \u2022 SpotKofi Connect control"),
    SettingsEntry(Icons.Filled.Download, "Data-saving and offline", "Data saver mode \u2022 Offline mode"),
    SettingsEntry(Icons.Filled.Equalizer, "Media quality", "Wi-Fi streaming quality \u2022 Audio download quality"),
    SettingsEntry(Icons.Filled.Info, "About and support", "Version \u2022 Privacy Policy"),
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Column(modifier = modifier.fillMaxSize()) {
        // Raised top bar, matching the app's own settings chrome.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.highlight)
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = dimens.spaceSm,
                bottom = contentPadding.calculateBottomPadding() + dimens.spaceXl,
            ),
        ) {
            items(items = SettingsEntries, key = { it.title }) { entry ->
                SettingsRow(entry)
            }

            item(key = "logout") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
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
                            .clickable { /* Phase 3: Supabase sign-out */ }
                            .padding(horizontal = dimens.spaceXxl, vertical = dimens.spaceMd),
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
            .clickable { /* Phase 3+: each group gets its own screen */ }
            .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(dimens.iconMd),
        )
        Spacer(Modifier.width(dimens.spaceLg))
        Column {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Preview(name = "Settings", backgroundColor = 0xFF121212, showBackground = true, heightDp = 1000)
@Composable
private fun SettingsPreview() {
    SpotKofiTheme {
        SettingsScreen(onBack = {})
    }
}
