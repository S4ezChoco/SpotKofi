package com.spotkofi.app.feature.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotkofi.app.R
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.data.model.MoodCategory
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.ui.components.AppFooter
import com.spotkofi.app.ui.components.ErrorState
import com.spotkofi.app.ui.components.ExploreSkeleton
import com.spotkofi.app.ui.components.MediaCard
import com.spotkofi.app.ui.components.SectionHeader
import com.spotkofi.app.ui.components.TrackActionsSheetHost
import com.spotkofi.app.ui.components.TrackRow
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * One mood, moment or genre from Explore.
 *
 * Opened with the provider's own opaque `params` token rather than a title,
 * because the token is the only thing that can address the page; a title would
 * have to be searched for and would land somewhere else.
 */
@Composable
fun MoodCategoryScreen(
    title: String,
    params: String,
    onBack: () -> Unit,
    onCollectionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val container = LocalAppContainer.current
    val viewModel: MoodCategoryViewModel = viewModel(key = "mood_$params") {
        MoodCategoryViewModel(
            category = MoodCategory(title = title, params = params),
            repository = container.musicRepository,
            player = container.playerController,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val downloads by container.downloadManager.downloads.collectAsStateWithLifecycle()

    var selectedTrack by remember { mutableStateOf<Track?>(null) }

    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val listState = rememberLazyListState()

    val downloadsByTrack = remember(downloads) { downloads.associateBy { it.track.id } }

    val lifted by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 4
        }
    }
    val barColor by animateColorAsState(
        targetValue = if (lifted) colors.highlight else colors.base,
        animationSpec = Motion.fast(),
        label = "moodBar",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
                    // The provider's heading wins once it arrives; the tile's label
                    // is what is shown until then, so the bar is never blank.
                    text = state.title.ifBlank { title },
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            when {
                state.error != null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorState(
                        message = state.error.orEmpty(),
                        onRetry = viewModel::retry,
                    )
                }

                state.isLoading -> ExploreSkeleton(modifier = Modifier.fillMaxSize())

                state.isEmpty -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "This category has nothing in it right now.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(dimens.spaceXl),
                    )
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = dimens.spaceSm,
                        bottom = contentPadding.calculateBottomPadding() + dimens.spaceLg,
                    ),
                ) {
                    if (state.playlists.isNotEmpty()) {
                        item(key = "playlists") {
                            Column {
                                SectionHeader(title = "Playlists")
                                LazyRow(
                                    contentPadding = PaddingValues(
                                        horizontal = dimens.screenGutter,
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(
                                        dimens.spaceMd,
                                    ),
                                ) {
                                    items(state.playlists, key = { it.id }) { item ->
                                        MediaCard(
                                            item = item,
                                            onClick = { onCollectionClick(item.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (state.songs.isNotEmpty()) {
                        item(key = "songs_header") { SectionHeader(title = "Songs") }
                        items(state.songs, key = { "mood_song_${it.id}" }) { track ->
                            val download = downloadsByTrack[track.id]
                            TrackRow(
                                track = track,
                                isPlaying = track.id == playback.track?.id,
                                downloadStatus = download?.status,
                                downloadProgress = download?.progress ?: 0,
                                onClick = { viewModel.onPlayTrack(track, state.songs) },
                                onMoreClick = { selectedTrack = track },
                            )
                        }
                    }

                    item(key = "footer") { AppFooter() }
                }
            }
        }

        TrackActionsSheetHost(
            track = selectedTrack,
            onDismiss = { selectedTrack = null },
        )
    }
}
