package com.spotkofi.app.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotkofi.app.R
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.data.model.Contributor
import com.spotkofi.app.data.model.EpisodeItem
import com.spotkofi.app.data.model.Lyrics
import com.spotkofi.app.data.model.PlaybackState
import com.spotkofi.app.data.model.RepeatMode
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.TrackDetails
import com.spotkofi.app.data.model.asTrackDuration
import com.spotkofi.app.data.repository.previewTrack
import com.spotkofi.app.data.repository.previewTrackDetails
import com.spotkofi.app.ui.components.Artwork
import com.spotkofi.app.ui.components.artworkSeedColor
import com.spotkofi.app.ui.theme.SpotKofiTheme

@Composable
fun NowPlayingScreen(
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: NowPlayingViewModel = viewModel {
        NowPlayingViewModel(container.musicRepository, container.playerController)
    }
    val state by viewModel.playbackState.collectAsStateWithLifecycle()
    val details by viewModel.details.collectAsStateWithLifecycle()

    NowPlayingContent(
        state = state,
        details = details,
        onCollapse = onCollapse,
        onTogglePlayPause = viewModel::onTogglePlayPause,
        onNext = viewModel::onNext,
        onPrevious = viewModel::onPrevious,
        onSeek = viewModel::onSeek,
        onToggleShuffle = viewModel::onToggleShuffle,
        onCycleRepeat = viewModel::onCycleRepeat,
        onToggleSaved = viewModel::onToggleSaved,
        modifier = modifier,
    )
}

@Composable
private fun NowPlayingContent(
    state: PlaybackState,
    details: TrackDetails?,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val track = state.track

    if (track == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.base),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Nothing playing",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
            )
        }
        return
    }

    val seed = remember(track.id) { artworkSeedColor(track.id) }
    val pageBrush = remember(seed) {
        Brush.verticalGradient(
            0f to lerp(seed, colors.base, 0.55f),
            0.35f to colors.base,
            1f to colors.base,
        )
    }

    val listState = rememberLazyListState()
    // The compact bar takes over once the hero has scrolled past.
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageBrush),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = dimens.spaceHuge),
        ) {
            // ---- Hero: artwork, overlaid lyric line, expanded top bar ----
            item(key = "hero") {
                Column {
                    HeroArtwork(
                        track = track,
                        contextLabel = details?.contextLabel.orEmpty(),
                        activeLyric = details?.lyrics?.activeLine(),
                        onCollapse = onCollapse,
                    )
                }
            }

            // ---- Transport block ----
            item(key = "transport") {
                Column(modifier = Modifier.padding(horizontal = dimens.spaceXl)) {
                    Spacer(Modifier.height(dimens.spaceLg))
                    TrackInfoRow(
                        track = track,
                        isSaved = state.isSaved,
                        onToggleSaved = onToggleSaved,
                    )
                    Spacer(Modifier.height(dimens.spaceSm))
                    Scrubber(state = state, track = track, onSeek = onSeek)
                    Spacer(Modifier.height(dimens.spaceSm))
                    TransportRow(
                        state = state,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                    )
                    Spacer(Modifier.height(dimens.spaceMd))
                    SecondaryRow(deviceName = state.deviceName)
                    Spacer(Modifier.height(dimens.spaceXl))
                }
            }

            if (details != null) {
                item(key = "lyrics") {
                    SectionSpacing { LyricsCard(lyrics = details.lyrics, seed = seed) }
                }
                item(key = "discover") {
                    SectionSpacing {
                        DiscoverCard(
                            artistName = track.artistName,
                            episodes = details.episodes,
                        )
                    }
                }
                item(key = "suggested") {
                    SectionSpacing { SuggestedVideoCard(trackId = track.id) }
                }
                item(key = "about") {
                    SectionSpacing {
                        AboutArtistCard(
                            artistName = track.artistName,
                            bio = details.artistBio,
                        )
                    }
                }
                item(key = "songdna") {
                    SectionSpacing {
                        SongDnaCard(
                            trackTitle = track.title,
                            contributors = details.contributors,
                        )
                    }
                }
                item(key = "explore") {
                    SectionSpacing(horizontal = false) {
                        ExploreRow(
                            artistName = track.artistName,
                            cards = details.exploreCards.map { it.title },
                        )
                    }
                }
                item(key = "credits") {
                    SectionSpacing { CreditsCard(details = details) }
                }
            }
        }

        // ---- Collapsed bar ----
        AnimatedVisibility(
            visible = collapsed,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(180)),
        ) {
            CollapsedBar(
                track = track,
                isPlaying = state.isPlaying,
                isSaved = state.isSaved,
                onTogglePlayPause = onTogglePlayPause,
                onToggleSaved = onToggleSaved,
            )
        }
    }
}

/* ------------------------------------------------------------------ hero --- */

@Composable
private fun HeroArtwork(
    track: Track,
    contextLabel: String,
    activeLyric: String?,
    onCollapse: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f),
    ) {
        Artwork(
            id = track.id,
            url = track.artworkUrl,
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.fillMaxSize(),
        )

        // Scrims: one under the top bar, one behind the lyric line, so both stay
        // legible whatever the artwork is.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.25f to Color.Transparent,
                        0.7f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f),
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = dimens.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCollapse) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Color.White,
                    modifier = Modifier.size(dimens.iconLg),
                )
            }
            Text(
                text = contextLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { /* Phase 5: queue and device options */ }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.cd_more_options),
                    tint = Color.White,
                )
            }
        }

        if (!activeLyric.isNullOrBlank()) {
            Text(
                text = activeLyric,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(dimens.spaceXl),
            )
        }
    }
}

/* ------------------------------------------------------------- transport --- */

@Composable
private fun TrackInfoRow(
    track: Track,
    isSaved: Boolean,
    onToggleSaved: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(verticalAlignment = Alignment.CenterVertically) {
        Artwork(id = track.id, size = 44.dp)
        Spacer(Modifier.width(dimens.spaceMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistName,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { /* Phase 5: dismiss from queue */ }) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove from queue",
                tint = colors.textPrimary,
                modifier = Modifier.size(dimens.iconMd),
            )
        }
        SavedCheck(isSaved = isSaved, onClick = onToggleSaved)
    }
}

/** Green filled circle when saved, hollow outline when not. */
@Composable
private fun SavedCheck(isSaved: Boolean, onClick: () -> Unit) {
    val colors = SpotKofiTheme.colors

    Box(
        modifier = Modifier
            .size(30.dp)
            .background(
                color = if (isSaved) colors.accent else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = stringResource(
                if (isSaved) R.string.cd_unfavourite else R.string.cd_favourite,
            ),
            tint = if (isSaved) colors.onAccent else colors.textSecondary,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun Scrubber(
    state: PlaybackState,
    track: Track,
    onSeek: (Float) -> Unit,
) {
    val colors = SpotKofiTheme.colors

    // Local scrub position so the thumb follows the finger instead of being
    // yanked back by the 500ms playhead tick mid-drag.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val fraction = if (scrubbing) scrubFraction else state.progress

    Column {
        Slider(
            value = fraction,
            onValueChange = {
                scrubbing = true
                scrubFraction = it
            },
            onValueChangeFinished = {
                onSeek(scrubFraction)
                scrubbing = false
            },
            colors = SliderDefaults.colors(
                thumbColor = colors.textPrimary,
                activeTrackColor = colors.textPrimary,
                inactiveTrackColor = colors.trackInactive,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = (track.durationMs * fraction).toLong().asTrackDuration(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = track.durationMs.asTrackDuration(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun TransportRow(
    state: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = stringResource(R.string.cd_shuffle),
                tint = if (state.isShuffled) colors.accent else colors.textSecondary,
                modifier = Modifier.size(dimens.iconMd),
            )
        }
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.cd_previous),
                tint = colors.textPrimary,
                modifier = Modifier.size(38.dp),
            )
        }

        // White, not green: on the tinted page a green button competes with the
        // accent used by shuffle, repeat and the saved check.
        WhitePlayButton(isPlaying = state.isPlaying, onClick = onTogglePlayPause)

        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.cd_next),
                tint = colors.textPrimary,
                modifier = Modifier.size(38.dp),
            )
        }
        IconButton(onClick = onCycleRepeat) {
            Icon(
                imageVector = if (state.repeatMode == RepeatMode.One) {
                    Icons.Filled.RepeatOne
                } else {
                    Icons.Filled.Repeat
                },
                contentDescription = stringResource(R.string.cd_repeat),
                tint = if (state.repeatMode == RepeatMode.Off) {
                    colors.textSecondary
                } else {
                    colors.accent
                },
                modifier = Modifier.size(dimens.iconMd),
            )
        }
    }
}

@Composable
private fun WhitePlayButton(isPlaying: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.96f,
        animationSpec = tween(160),
        label = "playScale",
    )

    Box(
        modifier = Modifier
            .size(66.dp * scale)
            .background(Color.White, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = stringResource(
                if (isPlaying) R.string.cd_pause else R.string.cd_play,
            ),
            tint = Color.Black,
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun SecondaryRow(deviceName: String?) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Computer,
            contentDescription = stringResource(R.string.cd_connect_device),
            tint = if (deviceName != null) colors.accent else colors.textSecondary,
            modifier = Modifier
                .size(dimens.iconMd)
                .clickable { },
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = "Share",
            tint = colors.textSecondary,
            modifier = Modifier
                .size(dimens.iconMd)
                .clickable { },
        )
        Spacer(Modifier.width(dimens.spaceXl))
        Icon(
            imageVector = Icons.Filled.QueueMusic,
            contentDescription = "Queue",
            tint = colors.textSecondary,
            modifier = Modifier
                .size(dimens.iconMd)
                .clickable { },
        )
    }
}

/* ------------------------------------------------------------ page cards --- */

/** Consistent gutter and gap for every card below the controls. */
@Composable
private fun SectionSpacing(
    horizontal: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dimens = SpotKofiTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (horizontal) dimens.screenGutter else 0.dp,
                vertical = dimens.spaceSm,
            ),
    ) {
        content()
    }
}

@Composable
private fun LyricsCard(lyrics: Lyrics, seed: Color) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    // Saturated panel derived from the artwork, the way the real card samples the
    // cover rather than using a fixed brand colour.
    val panel = remember(seed) { lerp(seed, Color(0xFF1B3FA0), 0.45f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SpotKofiTheme.shapes.card)
            .background(panel)
            .padding(dimens.spaceLg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Lyrics",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            CircleIconButton(Icons.Filled.Share, "Share lyrics")
            Spacer(Modifier.width(dimens.spaceSm))
            CircleIconButton(Icons.Filled.OpenInFull, "Expand lyrics")
        }

        Spacer(Modifier.height(dimens.spaceLg))

        lyrics.lines.forEachIndexed { index, line ->
            val isActive = index == lyrics.activeIndex
            if (line.isInstrumental) {
                Text(
                    text = "\u266A",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.padding(vertical = dimens.spaceSm),
                )
            } else {
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.headlineMedium,
                    // Sung line is opaque white; the rest recede.
                    color = if (isActive) {
                        Color.White
                    } else {
                        Color.White.copy(alpha = 0.55f)
                    },
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = dimens.spaceXs),
                )
            }
        }

        Spacer(Modifier.height(dimens.spaceSm))
        Text(
            text = "Placeholder lyrics",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(Color.Black.copy(alpha = 0.28f), CircleShape)
            .clickable { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun DiscoverCard(artistName: String, episodes: List<EpisodeItem>) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SpotKofiTheme.shapes.card)
            .background(colors.card)
            .padding(dimens.spaceLg),
    ) {
        Text(
            text = "Discover more about $artistName",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(dimens.spaceMd))
        episodes.forEach { episode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(id = episode.id, size = 52.dp)
                Spacer(Modifier.width(dimens.spaceMd))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = episode.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(dimens.spaceSm))
                Icon(
                    imageVector = Icons.Filled.AddCircleOutline,
                    contentDescription = stringResource(R.string.cd_add_to_library),
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable { },
                )
            }
        }
    }
}

@Composable
private fun SuggestedVideoCard(trackId: String) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SpotKofiTheme.shapes.card)
            .background(colors.card)
            .padding(dimens.spaceLg),
    ) {
        Text(
            text = "Suggested Video",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(dimens.spaceMd))
        Box {
            Artwork(
                id = trackId + "_vid",
                shape = SpotKofiTheme.shapes.tile,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.7f),
            )
            Text(
                text = "05:35",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(dimens.spaceSm)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun AboutArtistCard(artistName: String, bio: String) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SpotKofiTheme.shapes.card)
            .background(colors.card)
            .clickable { expanded = !expanded }
            .padding(dimens.spaceLg),
    ) {
        Text(
            text = "About $artistName",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(dimens.spaceSm))
        Text(
            text = bio,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(dimens.spaceXs))
        Text(
            text = if (expanded) "see less" else "see more",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun SongDnaCard(trackTitle: String, contributors: List<Contributor>) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SpotKofiTheme.shapes.card)
            .background(colors.card)
            .padding(dimens.spaceLg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "SongDNA",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Spacer(Modifier.width(dimens.spaceSm))
            Text(
                text = "Beta",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.onAccent,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.accent)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }

        Spacer(Modifier.height(dimens.spaceLg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceLg),
        ) {
            contributors.forEach { contributor ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Artwork(id = contributor.id, size = 104.dp, shape = CircleShape)
                    Spacer(Modifier.height(dimens.spaceSm))
                    Text(
                        text = contributor.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = contributor.role,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(dimens.spaceLg))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trackTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${contributors.size + 1} contributors",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            Text(
                text = "Explore",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
                modifier = Modifier
                    .clip(SpotKofiTheme.shapes.chip)
                    .clickable { }
                    .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
            )
        }

        Spacer(Modifier.height(dimens.spaceSm))
        Text(
            text = "Discover the people behind the song.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun ExploreRow(artistName: String, cards: List<String>) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Column {
        Text(
            text = "Explore $artistName",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = dimens.screenGutter),
        )
        Spacer(Modifier.height(dimens.spaceMd))
        LazyRow(
            contentPadding = PaddingValues(horizontal = dimens.screenGutter),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
        ) {
            items(items = cards, key = { it }) { title ->
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .aspectRatio(0.85f)
                        .clip(SpotKofiTheme.shapes.tile)
                        .clickable { },
                ) {
                    Artwork(id = title, modifier = Modifier.fillMaxSize())
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                ),
                            ),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(dimens.spaceMd),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditsCard(details: TrackDetails) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SpotKofiTheme.shapes.card)
            .background(colors.card)
            .padding(dimens.spaceLg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Credits",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Show all",
                style = MaterialTheme.typography.labelMedium,
                color = colors.accent,
                modifier = Modifier.clickable { },
            )
        }
        Spacer(Modifier.height(dimens.spaceMd))
        details.credits.forEach { credit ->
            Column(modifier = Modifier.padding(vertical = dimens.spaceSm)) {
                Text(
                    text = credit.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = credit.role,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/* ----------------------------------------------------------- collapsed --- */

@Composable
private fun CollapsedBar(
    track: Track,
    isPlaying: Boolean,
    isSaved: Boolean,
    onTogglePlayPause: () -> Unit,
    onToggleSaved: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.highlight)
            .statusBarsPadding()
            .padding(
                start = dimens.screenGutter,
                end = dimens.spaceSm,
                top = dimens.spaceSm,
                bottom = dimens.spaceSm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistName,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SavedCheck(isSaved = isSaved, onClick = onToggleSaved)
        Spacer(Modifier.width(dimens.spaceSm))
        IconButton(onClick = onTogglePlayPause) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (isPlaying) R.string.cd_pause else R.string.cd_play,
                ),
                tint = colors.textPrimary,
                modifier = Modifier.size(dimens.iconLg),
            )
        }
    }
}

/** The line currently being sung, or null when there is nothing to show. */
private fun Lyrics.activeLine(): String? =
    lines.getOrNull(activeIndex)?.takeIf { !it.isInstrumental }?.text

@Preview(name = "Now Playing", backgroundColor = 0xFF121212, showBackground = true, heightDp = 1400)
@Composable
private fun NowPlayingPreview() {
    SpotKofiTheme {
        NowPlayingContent(
            state = PlaybackState(
                track = previewTrack(),
                isPlaying = true,
                positionMs = 48_000,
                isShuffled = true,
                isSaved = true,
                deviceName = "SpotKofi Web Player",
            ),
            details = previewTrackDetails(),
            onCollapse = {},
            onTogglePlayPause = {},
            onNext = {},
            onPrevious = {},
            onSeek = {},
            onToggleShuffle = {},
            onCycleRepeat = {},
            onToggleSaved = {},
        )
    }
}
