package com.spotkofi.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.data.model.HomeSection
import com.spotkofi.app.data.model.HomeTab
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.repository.previewHomeSections
import com.spotkofi.app.data.repository.previewQuickPicks
import com.spotkofi.app.data.repository.previewReleaseSections
import com.spotkofi.app.ui.components.MediaCard
import com.spotkofi.app.ui.components.ProfileAvatar
import com.spotkofi.app.ui.components.QuickPickCard
import com.spotkofi.app.ui.components.ReleaseCard
import com.spotkofi.app.ui.components.SectionHeader
import com.spotkofi.app.ui.components.SegmentedChipPair
import com.spotkofi.app.ui.components.SpotKofiChip
import com.spotkofi.app.ui.components.SpotlightCard
import com.spotkofi.app.ui.components.StationCard
import com.spotkofi.app.ui.theme.SpotKofiTheme

@Composable
fun HomeScreen(
    onCollectionClick: (String) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel {
        HomeViewModel(container.musicRepository, container.playerController)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        onChipClick = viewModel::onChipClick,
        onCollectionClick = onCollectionClick,
        onOpenProfile = onOpenProfile,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

@Composable
private fun HomeContent(
    state: HomeViewModel.UiState,
    onChipClick: (HomeTab) -> Unit,
    onCollectionClick: (String) -> Unit,
    onOpenProfile: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            // Avatar and chips share one row. There is no greeting: the header IS
            // the filter bar.
            item(key = "header") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HomeHeader(
                        state = state,
                        onChipClick = onChipClick,
                        onOpenProfile = onOpenProfile,
                    )
                    Spacer(Modifier.height(dimens.spaceMd))
                }
            }

            if (state.isLoading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                }
                return@LazyColumn
            }

            if (state.showQuickPicks) {
                // Two-column grid built from paired rows. A LazyVerticalGrid cannot
                // nest inside a LazyColumn, and chunking keeps the screen as one
                // scroll container instead of two.
                items(
                    items = state.quickPicks.chunked(2),
                    key = { pair -> "qp_" + pair.first().id },
                ) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = dimens.screenGutter,
                                vertical = dimens.spaceXs,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
                    ) {
                        pair.forEach { item ->
                            QuickPickCard(
                                item = item,
                                onClick = { onCollectionClick(item.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Keeps a lone trailing card at half width.
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                item(key = "qp_spacer") {
                    Spacer(Modifier.height(dimens.shelfSpacing))
                }
            }

            state.sections.forEach { section ->
                item(key = section.id) {
                    // A LazyColumn item is a single slot, so the block and its
                    // trailing gap need a layout around them.
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HomeSectionBlock(
                            section = section,
                            onCollectionClick = onCollectionClick,
                        )
                        Spacer(Modifier.height(dimens.shelfSpacing))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    state: HomeViewModel.UiState,
    onChipClick: (HomeTab) -> Unit,
    onOpenProfile: () -> Unit,
) {
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimens.spaceMd, bottom = dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(dimens.screenGutter))
        ProfileAvatar(name = state.userName, onClick = onOpenProfile)
        Spacer(Modifier.width(dimens.spaceMd))

        // Built explicitly rather than from a list, because Music and Following
        // are one segmented control, not two independent chips.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
            contentPadding = PaddingValues(end = dimens.screenGutter),
        ) {
            item(key = "all") {
                SpotKofiChip(
                    label = HomeTab.All.label,
                    selected = state.selectedChip == HomeTab.All,
                    onClick = { onChipClick(HomeTab.All) },
                )
            }
            item(key = "music_following") {
                SegmentedChipPair(
                    leadingLabel = HomeTab.Music.label,
                    trailingLabel = HomeTab.Following.label,
                    leadingSelected = state.selectedChip == HomeTab.Music,
                    trailingSelected = state.followingActive,
                    trailingVisible = state.followingVisible,
                    onLeadingClick = { onChipClick(HomeTab.Music) },
                    onTrailingClick = { onChipClick(HomeTab.Following) },
                )
            }
            item(key = "podcasts") {
                SpotKofiChip(
                    label = HomeTab.Podcasts.label,
                    selected = state.selectedChip == HomeTab.Podcasts,
                    onClick = { onChipClick(HomeTab.Podcasts) },
                )
            }
        }
    }
}

/**
 * Dispatches on section type. A new block shape forces a new branch here.
 *
 * Emits several siblings, so every call site must place it inside a layout
 * (a Column), not directly in a LazyColumn `item` slot.
 */
@Composable
private fun HomeSectionBlock(
    section: HomeSection,
    onCollectionClick: (String) -> Unit,
) {
    val dimens = SpotKofiTheme.dimens

    when (section) {
        is HomeSection.Cards -> {
            SectionHeader(title = section.title)
            LazyRow(
                contentPadding = PaddingValues(horizontal = dimens.screenGutter),
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
            ) {
                items(items = section.items, key = { it.id }) { item ->
                    MediaCard(item = item, onClick = { onCollectionClick(item.id) })
                }
            }
        }

        is HomeSection.Stations -> {
            SectionHeader(title = section.title)
            LazyRow(
                contentPadding = PaddingValues(horizontal = dimens.screenGutter),
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
            ) {
                items(items = section.items, key = { it.id }) { station ->
                    StationCard(station = station, onClick = { })
                }
            }
        }

        is HomeSection.Spotlight -> {
            SectionHeader(title = section.title)
            Box(modifier = Modifier.padding(horizontal = dimens.screenGutter)) {
                SpotlightCard(
                    item = section.item,
                    onClick = { onCollectionClick(section.item.id) },
                )
            }
        }

        is HomeSection.Releases -> {
            // The feed's own heading is a screen title, not a shelf label.
            Text(
                text = section.title,
                style = MaterialTheme.typography.displaySmall,
                color = SpotKofiTheme.colors.textPrimary,
                modifier = Modifier.padding(
                    horizontal = dimens.screenGutter,
                    vertical = dimens.spaceSm,
                ),
            )
            section.items.forEach { release ->
                Box(
                    modifier = Modifier.padding(
                        horizontal = dimens.screenGutter,
                        vertical = dimens.spaceSm,
                    ),
                ) {
                    ReleaseCard(
                        release = release,
                        onClick = { },
                        onPlay = { },
                        onAdd = { },
                        onMore = { },
                    )
                }
            }
        }
    }
}

@Preview(name = "Home / All", backgroundColor = 0xFF121212, showBackground = true, heightDp = 1000)
@Composable
private fun HomePreview() {
    SpotKofiTheme {
        HomeContent(
            state = HomeViewModel.UiState(
                userName = "CHOCO",
                quickPicks = previewQuickPicks(),
                sections = previewHomeSections(),
                isLoading = false,
            ),
            onChipClick = {},
            onCollectionClick = {},
            onOpenProfile = {},
            contentPadding = PaddingValues(),
        )
    }
}

@Preview(name = "Home / Following", backgroundColor = 0xFF121212, showBackground = true, heightDp = 1000)
@Composable
private fun HomeFollowingPreview() {
    SpotKofiTheme {
        HomeContent(
            state = HomeViewModel.UiState(
                userName = "CHOCO",
                selectedChip = HomeTab.Music,
                followingActive = true,
                sections = previewReleaseSections(),
                isLoading = false,
            ),
            onChipClick = {},
            onCollectionClick = {},
            onOpenProfile = {},
            contentPadding = PaddingValues(),
        )
    }
}
