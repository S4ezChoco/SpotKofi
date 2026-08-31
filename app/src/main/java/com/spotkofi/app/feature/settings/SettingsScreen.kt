package com.spotkofi.app.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotkofi.app.R
import com.spotkofi.app.core.AppConstants
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.data.model.ChartRegion
import com.spotkofi.app.data.local.AudioQuality
import com.spotkofi.app.data.local.LyricsProvider
import com.spotkofi.app.ui.components.AppFooter
import com.spotkofi.app.ui.components.SpotKofiLogo
import com.spotkofi.app.ui.layout.rememberResponsiveLayout
import com.spotkofi.app.ui.motion.clickableScale
import com.spotkofi.app.ui.motion.staggeredEntry
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiBrandStyle
import com.spotkofi.app.ui.theme.SpotKofiTheme
import kotlinx.coroutines.launch

/**
 * Soft brown accents local to the Settings page.
 *
 * A coffee shade suits the app's name without repainting the whole product:
 * playback keeps the brand green, while Settings wears a muted caramel. Both
 * stay soft on purpose — saturated brown would read as error red on a dark
 * background.
 */
private val SettingsBrown = Color(0xFFB08D6A)
private val SettingsBrownDim = Color(0xFF8F6B4F)

/**
 * The app's real preferences.
 *
 * Every row here changes behaviour the user can observe. Rows for features the app
 * does not have (equalizer, proxy, third-party accounts, themes) are deliberately
 * absent: a switch that persists a value nothing reads is a promise the app breaks
 * the moment someone flips it.
 */
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val container = LocalAppContainer.current
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle()
    val downloadStats by container.downloadManager.totalDownloadStats
        .collectAsStateWithLifecycle()
    val history by container.localStore.history.collectAsStateWithLifecycle()
    val savedTracks by container.localStore.savedTracks.collectAsStateWithLifecycle()
    val savedCollections by container.localStore.savedCollections.collectAsStateWithLifecycle()
    val visitedCollections by container.localStore.visitedCollections.collectAsStateWithLifecycle()
    val playlists by container.localStore.playlists.collectAsStateWithLifecycle()

    var showRegionPicker by remember { mutableStateOf(false) }
    var showLyricsProviderPicker by remember { mutableStateOf(false) }
    var showAudioQualityPicker by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<ConfirmAction?>(null) }

    // Cache size is read on demand rather than observed: the value only moves while
    // audio streams, and polling it every frame would hit the filesystem for a
    // subtitle nobody is watching.
    var cacheBytes by remember { mutableStateOf(container.playbackCache.sizeBytes) }

    // The bar only gains a surface once content is behind it, so at rest the
    // screen reads as one plane instead of a bar stuck to a list.
    val lifted by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 4
        }
    }
    val barColor by animateColorAsState(
        targetValue = if (lifted) colors.highlight else Color.Transparent,
        animationSpec = Motion.fast(),
        label = "settingsBar",
    )

    val regionName = remember(settings.contentRegion) {
        container.musicRepository.chartRegions()
            .firstOrNull { it.code == settings.contentRegion }
            ?.name
            ?: settings.contentRegion
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // A quiet brand wash fading out of the top of the page — the same
            // treatment every other SpotKofi screen gives its header. Blended
            // into the base colour rather than painted over it, so the scrolled
            // bar above can stay opaque while the resting bar stays clear.
            .background(
                Brush.verticalGradient(
                    0f to lerp(colors.base, SettingsBrownDim, 0.30f),
                    0.10f to lerp(colors.base, SettingsBrownDim, 0.12f),
                    0.22f to colors.base,
                ),
            ),
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
            Spacer(Modifier.width(dimens.spaceXxs))
            SpotKofiLogo(size = 26.dp)
            Spacer(Modifier.width(dimens.spaceSm))
            Text(
                text = "Settings",
                style = SpotKofiBrandStyle,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = dimens.spaceMd,
                bottom = contentPadding.calculateBottomPadding() + dimens.spaceXl,
            ),
        ) {
            settingsGroup(
                index = 0,
                heading = "Playback",
                gutter = layout.gutter,
            ) {
                ToggleRow(
                    icon = Icons.Filled.Replay,
                    title = "Resume where you left off",
                    subtitle = "Restore the last queue when the app opens",
                    checked = settings.restoreQueueOnStart,
                    onCheckedChange = container.settingsStore::setRestoreQueueOnStart,
                )
                RowDivider()
                ToggleRow(
                    icon = Icons.Filled.OpenInFull,
                    title = "Open the player on play",
                    subtitle = "Expand the full player when you start a song",
                    checked = settings.openPlayerOnPlay,
                    onCheckedChange = container.settingsStore::setOpenPlayerOnPlay,
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.HighQuality,
                    title = "Audio quality",
                    subtitle = settings.audioQuality.description,
                    trailingText = settings.audioQuality.displayName,
                    onClick = { showAudioQualityPicker = true },
                )
            }

            settingsGroup(
                index = 1,
                heading = "Content",
                gutter = layout.gutter,
            ) {
                ActionRow(
                    icon = Icons.Filled.Public,
                    title = "Content region",
                    subtitle = regionName,
                    trailingText = settings.contentRegion,
                    onClick = { showRegionPicker = true },
                )
                RowDivider()
                ToggleRow(
                    icon = Icons.Filled.Explicit,
                    title = "Hide explicit content",
                    subtitle = "Leave out songs the provider marked explicit",
                    checked = settings.hideExplicitContent,
                    onCheckedChange = container.settingsStore::setHideExplicitContent,
                )
            }

            settingsGroup(
                index = 2,
                heading = "Downloads",
                gutter = layout.gutter,
            ) {
                ToggleRow(
                    icon = Icons.Filled.Speed,
                    title = "Download at high priority",
                    subtitle = "New downloads jump ahead of the queue",
                    checked = settings.downloadHighPriority,
                    onCheckedChange = container.settingsStore::setDownloadHighPriority,
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.Download,
                    title = "Clear finished downloads",
                    subtitle = "${downloadStats.completed} finished " +
                        "\u2022 ${formatBytes(downloadStats.totalBytesDownloaded)}",
                    onClick = { confirm = ConfirmAction.ClearCompleted },
                    enabled = downloadStats.completed > 0,
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.ErrorOutline,
                    title = "Clear failed downloads",
                    subtitle = if (downloadStats.failed > 0) {
                        "${downloadStats.failed} failed"
                    } else {
                        "Nothing failed"
                    },
                    onClick = { confirm = ConfirmAction.ClearFailed },
                    enabled = downloadStats.failed > 0,
                )
            }

            settingsGroup(
                index = 3,
                heading = "Storage",
                gutter = layout.gutter,
            ) {
                ActionRow(
                    icon = Icons.Filled.SdStorage,
                    title = "Clear streaming cache",
                    subtitle = "${formatBytes(cacheBytes)} held for instant replay",
                    onClick = { confirm = ConfirmAction.ClearCache },
                    enabled = cacheBytes > 0,
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.History,
                    title = "Clear listening history",
                    subtitle = if (history.isEmpty()) {
                        "No plays recorded"
                    } else {
                        "${history.size} ${if (history.size == 1) "song" else "songs"} remembered"
                    },
                    onClick = { confirm = ConfirmAction.ClearHistory },
                    enabled = history.isNotEmpty(),
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.DeleteSweep,
                    title = "Clear library data",
                    subtitle = "Remove saved songs, collections, playlists and recents",
                    onClick = { confirm = ConfirmAction.ClearLibrary },
                    enabled = savedTracks.isNotEmpty() ||
                        savedCollections.isNotEmpty() ||
                        visitedCollections.isNotEmpty() ||
                        playlists.isNotEmpty(),
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.RestartAlt,
                    title = "Reset settings",
                    subtitle = "Put every option back to its default",
                    onClick = { confirm = ConfirmAction.ResetSettings },
                )
            }

            settingsGroup(
                index = 4,
                heading = "About",
                gutter = layout.gutter,
            ) {
                InfoRow(
                    icon = Icons.Filled.Info,
                    title = AppConstants.APP_NAME,
                    subtitle = "Version ${AppConstants.VERSION_NAME} " +
                        "(${AppConstants.VERSION_CODE})",
                )
                RowDivider()
                InfoRow(
                    icon = Icons.Filled.Code,
                    title = AppConstants.DEVELOPER,
                    subtitle = AppConstants.DEVELOPER_ROLE,
                    onClick = { context.openLink(AppConstants.DEVELOPER_URL) },
                )
            }

            item(key = "footer") { AppFooter() }
        }
    }

    if (showRegionPicker) {
        RegionPickerDialog(
            regions = container.musicRepository.chartRegions(),
            selectedCode = settings.contentRegion,
            onSelect = { code ->
                container.settingsStore.setContentRegion(code)
                showRegionPicker = false
            },
            onDismiss = { showRegionPicker = false },
        )
    }

    if (showLyricsProviderPicker) {
        LyricsProviderDialog(
            selectedProvider = settings.lyricsProvider,
            onSelect = { provider ->
                container.settingsStore.setLyricsProvider(provider)
                showLyricsProviderPicker = false
            },
            onDismiss = { showLyricsProviderPicker = false },
        )
    }

    if (showAudioQualityPicker) {
        AudioQualityDialog(
            selectedQuality = settings.audioQuality,
            onSelect = { quality ->
                container.settingsStore.setAudioQuality(quality)
                showAudioQualityPicker = false
            },
            onDismiss = { showAudioQualityPicker = false },
        )
    }

    confirm?.let { action ->
        ConfirmDialog(
            action = action,
            onConfirm = {
                when (action) {
                    ConfirmAction.ClearCache -> scope.launch {
                        container.playbackCache.clear()
                        // Re-read rather than assuming zero: eviction can fail for a
                        // span the player currently holds open.
                        cacheBytes = container.playbackCache.sizeBytes
                    }

                    ConfirmAction.ClearHistory -> container.localStore.clearHistory()
                    ConfirmAction.ClearLibrary -> scope.launch {
                        container.localStore.clearLibraryData()
                    }
                    ConfirmAction.ClearCompleted ->
                        container.downloadManager.clearCompletedDownloads()

                    ConfirmAction.ClearFailed ->
                        container.downloadManager.clearFailedDownloads()

                    ConfirmAction.ResetSettings -> container.settingsStore.reset()
                }
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }
}

// ------------------------------------------------------------------ scaffolding

/**
 * Emits a heading plus one rounded card of rows as a single list item.
 *
 * One item per group rather than one per row: the rows inside a group share a
 * background and dividers, and splitting them across list items would let the
 * card's rounded corners land in the middle of a scroll.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.settingsGroup(
    index: Int,
    heading: String,
    gutter: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    item(key = "group_$heading") {
        val colors = SpotKofiTheme.colors
        val dimens = SpotKofiTheme.dimens
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEntry(index)
                .padding(horizontal = gutter, vertical = dimens.spaceSm),
        ) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier.padding(
                    start = dimens.spaceXs,
                    bottom = dimens.spaceSm,
                ),
            )
            // Rows sit flat on the page background with no card surface and no
            // outline — the dividers inside the group are the only separation,
            // which is how native settings lists are drawn.
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun RowDivider() {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    Box(
        modifier = Modifier
            .padding(start = dimens.iconMd + dimens.spaceMd)
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.divider.copy(alpha = 0.6f)),
    )
}

@Composable
private fun RowIcon(icon: ImageVector, tinted: Boolean) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    // A bare glyph — no tile, no well, no border. The icon sizes itself to the
    // same 24dp the rest of the app's chrome uses, so rows read as a native
    // list rather than as decorated cards.
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (tinted) colors.textPrimary else colors.textTertiary,
        modifier = Modifier.size(dimens.iconMd),
    )
}

@Composable
private fun RowText(title: String, subtitle: String?, enabled: Boolean) {
    val colors = SpotKofiTheme.colors
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A row whose whole surface toggles the switch.
 *
 * The switch is not given its own click handler: two targets doing one thing means
 * a tap 4dp off the thumb silently does nothing, which reads as the app ignoring
 * the user.
 */
@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableScale(pressedScale = 0.99f) { onCheckedChange(!checked) }
            .padding(vertical = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, tinted = true)
        Spacer(Modifier.width(dimens.spaceMd))
        Box(modifier = Modifier.weight(1f)) {
            RowText(title, subtitle, enabled = true)
        }
        Spacer(Modifier.width(dimens.spaceSm))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = SettingsBrown,
                checkedBorderColor = SettingsBrown,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.chip,
                uncheckedBorderColor = colors.divider,
            ),
        )
    }
}

/** A row that performs something, with an optional short value on the right. */
@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailingText: String? = null,
    enabled: Boolean = true,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableScale(pressedScale = 0.99f, enabled = enabled, onClick = onClick)
            .padding(vertical = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, tinted = enabled)
        Spacer(Modifier.width(dimens.spaceMd))
        Box(modifier = Modifier.weight(1f)) {
            RowText(title, subtitle, enabled = enabled)
        }
        if (trailingText != null) {
            Spacer(Modifier.width(dimens.spaceSm))
            // The current value reads as one of the app's chips rather than as
            // floating accent text, so it anchors the row visually.
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(SpotKofiTheme.shapes.chip)
                    .background(colors.chip)
                    .padding(horizontal = dimens.spaceMd, vertical = 6.dp),
            )
        }
    }
}

/** A row that only states a fact, optionally opening a link. */
@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableScale(
                pressedScale = 0.99f,
                enabled = onClick != null,
                onClick = onClick ?: {},
            )
            .padding(vertical = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, tinted = true)
        Spacer(Modifier.width(dimens.spaceMd))
        Box(modifier = Modifier.weight(1f)) {
            RowText(title, subtitle, enabled = true)
        }
    }
}

@Composable
private fun AudioQualityDialog(
    selectedQuality: AudioQuality,
    onSelect: (AudioQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = SpotKofiTheme.shapes.sheet,
        containerColor = colors.highlight,
        titleContentColor = colors.textPrimary,
        title = { Text("Audio quality") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXxs)) {
                AudioQuality.entries.forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SpotKofiTheme.shapes.chip)
                            .clickableScale(pressedScale = 0.98f) { onSelect(quality) }
                            .padding(horizontal = dimens.spaceSm, vertical = dimens.spaceXs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = quality == selectedQuality,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = SettingsBrown),
                        )
                        Spacer(Modifier.width(dimens.spaceSm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = quality.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = quality.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = SettingsBrown)
            }
        },
    )
}

// ------------------------------------------------------------ lyrics picker

@Composable
internal fun LyricsProviderDialog(
    selectedProvider: LyricsProvider,
    onSelect: (LyricsProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = SpotKofiTheme.shapes.sheet,
        containerColor = colors.highlight,
        titleContentColor = colors.textPrimary,
        title = { Text("Lyrics provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXxs)) {
                LyricsProvider.entries.forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SpotKofiTheme.shapes.chip)
                            .clickableScale(pressedScale = 0.98f) { onSelect(provider) }
                            .padding(horizontal = dimens.spaceSm, vertical = dimens.spaceXs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = provider == selectedProvider,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = SettingsBrown),
                        )
                        Spacer(Modifier.width(dimens.spaceSm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = provider.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = provider.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = SettingsBrown)
            }
        },
    )
}

// ---------------------------------------------------------------- region picker

@Composable
private fun RegionPickerDialog(
    regions: List<ChartRegion>,
    selectedCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    // The selected region is pulled to the front so the current value is visible
    // without scrolling a list of seventy countries to find it.
    val ordered = remember(regions, selectedCode) {
        val selected = regions.filter { it.code == selectedCode }
        selected + regions.filterNot { it.code == selectedCode }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = SpotKofiTheme.shapes.sheet,
        containerColor = colors.highlight,
        titleContentColor = colors.textPrimary,
        title = { Text("Content region") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceXxs),
            ) {
                items(ordered, key = { it.code }) { region ->
                    val isSelected = region.code == selectedCode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SpotKofiTheme.shapes.chip)
                            .clickableScale(pressedScale = 0.99f) { onSelect(region.code) }
                            .padding(
                                horizontal = dimens.spaceMd,
                                vertical = dimens.spaceSm,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = region.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) SettingsBrown else colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = SettingsBrown,
                                modifier = Modifier.size(dimens.iconSm),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = SettingsBrown)
            }
        },
    )
}

// -------------------------------------------------------------------- confirming

/**
 * The destructive actions, each with the words shown before it happens.
 *
 * Modelled as data rather than five near-identical dialogs so the wording lives
 * next to the action it describes and cannot drift apart from it.
 */
private enum class ConfirmAction(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val icon: ImageVector,
) {
    ClearCache(
        title = "Clear streaming cache?",
        message = "Songs you have played will be fetched again the next time you " +
            "play them. Downloads are not affected.",
        confirmLabel = "Clear",
        icon = Icons.Filled.CleaningServices,
    ),
    ClearHistory(
        title = "Clear listening history?",
        message = "Recently played disappears and play counts start over. Saved " +
            "songs and playlists stay.",
        confirmLabel = "Clear",
        icon = Icons.Filled.History,
    ),
    ClearLibrary(
        title = "Clear library data?",
        message = "Saved songs, saved collections, visited collections, and playlists " +
            "created in this app will be removed. Downloads and listening history stay.",
        confirmLabel = "Clear",
        icon = Icons.Filled.DeleteSweep,
    ),
    ClearCompleted(
        title = "Clear finished downloads?",
        message = "The audio files are deleted from this device. The songs stay in " +
            "your library and can be downloaded again.",
        confirmLabel = "Delete",
        icon = Icons.Filled.DeleteSweep,
    ),
    ClearFailed(
        title = "Clear failed downloads?",
        message = "The failed entries are removed from the download list.",
        confirmLabel = "Clear",
        icon = Icons.Filled.ErrorOutline,
    ),
    ResetSettings(
        title = "Reset settings?",
        message = "Every option on this screen goes back to its default. Your " +
            "library, downloads and history are untouched.",
        confirmLabel = "Reset",
        icon = Icons.Filled.RestartAlt,
    ),
}

@Composable
private fun ConfirmDialog(
    action: ConfirmAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SpotKofiTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = SpotKofiTheme.shapes.sheet,
        containerColor = colors.highlight,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        icon = {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = colors.textPrimary,
            )
        },
        title = { Text(action.title) },
        text = { Text(action.message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(action.confirmLabel, color = SettingsBrown)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
    )
}

// ------------------------------------------------------------------- formatting

/** Sizes as the user thinks of them, not as bytes. */
private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "Empty"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    bytes < 1024L * 1024L * 1024L -> {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        if (mb < 10) String.format("%.1f MB", mb) else "${mb.toInt()} MB"
    }

    else -> String.format("%.2f GB", bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
}

/**
 * Opens an external link, ignoring the case where no browser exists.
 *
 * A device with nothing that handles http is unusual but real (kiosks, stripped
 * emulators); crashing the settings screen over an attribution link is worse than
 * the tap doing nothing.
 */
private fun android.content.Context.openLink(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure { error ->
        if (error !is ActivityNotFoundException) throw error
    }
}
