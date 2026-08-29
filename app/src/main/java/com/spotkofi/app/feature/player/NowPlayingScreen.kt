package com.spotkofi.app.feature.player

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotkofi.app.R
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.PlaybackState
import com.spotkofi.app.data.model.RepeatMode
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.TrackDetails
import com.spotkofi.app.data.model.asTrackDuration
import com.spotkofi.app.data.repository.previewTrack
import com.spotkofi.app.data.repository.previewTrackDetails
import com.spotkofi.app.ui.components.Artwork
import com.spotkofi.app.ui.components.MediaCard
import com.spotkofi.app.ui.components.AboutTrackCard
import com.spotkofi.app.ui.components.LyricsCard
import com.spotkofi.app.ui.components.QueueSheet
import com.spotkofi.app.ui.components.SavedToggle
import com.spotkofi.app.ui.components.TrackOptionsSheet
import com.spotkofi.app.ui.components.artworkSeedColor
import com.spotkofi.app.ui.motion.clickableScale
import com.spotkofi.app.ui.theme.ErrorRed
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme
import kotlinx.coroutines.launch

/** Downward fling speed, in px/s, that dismisses regardless of distance dragged. */
private const val DISMISS_VELOCITY = 1200f

@Composable
fun NowPlayingScreen(
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens an album from the related-content rows. */
    onCollectionClick: (String) -> Unit = {},
    /** Raw vertical drag delta, in px, from the hero artwork. */
    onDrag: (Float) -> Unit = {},
    /** Fling velocity in px/s when the finger lifts. */
    onDragStopped: (Float) -> Unit = {},
) {
    val container = LocalAppContainer.current
    val viewModel: NowPlayingViewModel = viewModel {
        NowPlayingViewModel(container.musicRepository, container.playerController)
    }
    val state by viewModel.playbackState.collectAsStateWithLifecycle()
    val details by viewModel.details.collectAsStateWithLifecycle()
    val queue by container.queueController.queue.collectAsStateWithLifecycle()
    val downloads by container.downloadManager.downloads.collectAsStateWithLifecycle()
    val download = state.track?.let { current ->
        downloads.firstOrNull { it.track.id == current.id }
    }

    NowPlayingContent(
        state = state,
        details = details,
        queue = queue,
        onQueueRemove = container.queueController::removeFromQueue,
        onQueueMove = container.queueController::moveInQueue,
        onQueueClear = container.queueController::clearQueue,
        onCollapse = onCollapse,
        onCollectionClick = onCollectionClick,
        onPlayTrack = viewModel::onPlayTrack,
        onDrag = onDrag,
        onDragStopped = onDragStopped,
        onTogglePlayPause = viewModel::onTogglePlayPause,
        onNext = viewModel::onNext,
        onPrevious = viewModel::onPrevious,
        onSeek = viewModel::onSeek,
        onToggleShuffle = viewModel::onToggleShuffle,
        onCycleRepeat = viewModel::onCycleRepeat,
        onToggleSaved = viewModel::onToggleSaved,
        onDownload = container.downloadManager::toggleDownload,
        downloadStatus = download?.status,
        downloadProgress = download?.progress ?: 0,
        modifier = modifier,
    )
}

@Composable
private fun NowPlayingContent(
    state: PlaybackState,
    details: TrackDetails?,
    queue: List<Track>,
    onQueueRemove: (String) -> Unit,
    onQueueMove: (Int, Int) -> Unit,
    onQueueClear: () -> Unit,
    onCollapse: () -> Unit,
    onCollectionClick: (String) -> Unit,
    onPlayTrack: (Track) -> Unit,
    onDrag: (Float) -> Unit,
    onDragStopped: (Float) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleSaved: () -> Unit,
    onDownload: (Track) -> Unit,
    downloadStatus: com.spotkofi.app.data.service.DownloadManagerStatus?,
    downloadProgress: Int,
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

    var showOptions by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Hand-off intents are built here rather than in the sheet so the sheet stays
    // a dumb list of rows and can be previewed without a real Context.
    val shareTrack: (Track) -> Unit = remember(context) {
        { shared ->
            shared.externalUrl?.takeIf { it.isNotBlank() }?.let { url ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "${shared.title} - ${shared.artistName}")
                    putExtra(Intent.EXTRA_TEXT, "${shared.title} - ${shared.artistName}\n$url")
                }
                runCatching {
                    context.startActivity(Intent.createChooser(intent, "Share track"))
                }
            }
        }
    }

    // This screen deliberately owns NO drag position.
    //
    // It used to keep its own `dragPx`, which meant two sources of truth: this
    // one and the host's blur progress. If the composable was reused before its
    // exit finished, the stale offset left the page parked off-screen while the
    // host still believed it was open, so the blur stuck on with nothing visible.
    // The host now owns the single position value and this screen only forwards
    // gestures.
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
            // ---- Hero: video/artwork, overlay label, expanded top bar ----
            item(key = "hero") {
                Column {
                    HeroArtwork(
                        track = track,
                        contextLabel = listOfNotNull(
                            details?.contextLabel?.takeIf { it.isNotBlank() },
                            details?.artistGenre?.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        onCollapse = onCollapse,
                        onDrag = onDrag,
                        onDragStopped = onDragStopped,
                        onMoreOptions = { showOptions = true },
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
                    Scrubber(state = state, onSeek = onSeek)
                    state.error?.let { error ->
                        Spacer(Modifier.height(dimens.spaceXs))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.labelSmall,
                            color = ErrorRed,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                    SecondaryRow(
                        deviceName = state.deviceName,
                        canShare = track.isExternallyOpenable,
                        onShare = { shareTrack(track) },
                        onShowQueue = { showQueue = true },
                        onMoreOptions = { showOptions = true },
                    )
                    Spacer(Modifier.height(dimens.spaceXl))
                }
            }

            // Only sections that can be filled with real data.
            //
            // Lyrics come from a lyrics provider at runtime and appear only when
            // that provider actually has the track; the "about" card lists only
            // fields the catalog returned. An artist biography and contributor
            // credits are still absent because nothing supplies them, and inventing
            // them would put fiction under a real song's title.
            if (details != null) {
                details.lyrics?.takeIf { it.hasText || it.instrumental }?.let { lyrics ->
                    item(key = "lyrics") {
                        SectionSpacing {
                            LyricsCard(lyrics = lyrics, positionMs = state.positionMs)
                        }
                    }
                }

                item(key = "about") {
                    SectionSpacing {
                        AboutTrackCard(track = track, genre = details.artistGenre)
                    }
                }

                if (details.albumTracks.isNotEmpty()) {
                    item(key = "album_tracks") {
                        SectionSpacing {
                            TrackListCard(
                                title = "More from ${track.albumTitle}",
                                tracks = details.albumTracks,
                                onTrackClick = onPlayTrack,
                            )
                        }
                    }
                }

                if (details.moreByArtist.isNotEmpty()) {
                    item(key = "more_by_artist") {
                        SectionSpacing {
                            TrackListCard(
                                title = "More by ${track.artistName}",
                                tracks = details.moreByArtist.take(8),
                                onTrackClick = onPlayTrack,
                            )
                        }
                    }
                }

                if (details.recommendations.isNotEmpty()) {
                    item(key = "spotify_recommendations") {
                        SectionSpacing {
                            TrackListCard(
                                title = "Recommended by Spotify",
                                tracks = details.recommendations.take(8),
                                onTrackClick = onPlayTrack,
                            )
                        }
                    }
                }

                if (details.artistAlbums.isNotEmpty()) {
                    item(key = "artist_albums") {
                        SectionSpacing(horizontal = false) {
                            AlbumRow(
                                title = "Albums by ${track.artistName}",
                                albums = details.artistAlbums,
                                onAlbumClick = onCollectionClick,
                            )
                        }
                    }
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

        // Placed last inside the page Box so it covers the list and the collapsed
        // bar, and so its scrim dims the artwork behind it.
        TrackOptionsSheet(
            visible = showOptions,
            track = track,
            isSaved = state.isSaved,
            isShuffled = state.isShuffled,
            repeatMode = state.repeatMode,
            remainingMs = state.remainingMs,
            onDismiss = { showOptions = false },
            onToggleSaved = onToggleSaved,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
            onOpenAlbum = onCollectionClick,
            onOpenArtist = onCollectionClick,
            onShare = shareTrack,
            onDownload = onDownload,
            downloadStatus = downloadStatus,
            downloadProgress = downloadProgress,
        )
        QueueSheet(
            visible = showQueue,
            queue = queue,
            currentTrackId = state.track?.id,
            onDismiss = { showQueue = false },
            onRemove = onQueueRemove,
            onMove = onQueueMove,
            onClear = onQueueClear,
        )
    }
}

/* ------------------------------------------------------------------ hero --- */

@Composable
private fun HeroArtwork(
    track: Track,
    contextLabel: String,
    onCollapse: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragStopped: (Float) -> Unit,
    onMoreOptions: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    val dragState = rememberDraggableState { delta -> onDrag(delta) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Artwork is shown while the official YouTube app or browser owns playback.
            .aspectRatio(0.82f)
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> onDragStopped(velocity) },
            ),
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
            IconButton(onClick = onMoreOptions) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.cd_more_options),
                    tint = Color.White,
                )
            }
        }

        // Artwork remains visible while the official YouTube app or browser owns
        // playback. No lyrics or protected media are scraped by this app.
        if (track.albumTitle.isNotBlank()) {
            Text(
                text = track.albumTitle,
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
        Artwork(id = track.id, size = 44.dp, url = track.artworkUrl)
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
        // The former "remove from queue" button lived here and did nothing. A
        // control that looks live but is inert is worse than no control, so it is
        // gone until queue editing exists.
        SavedCheck(isSaved = isSaved, onClick = onToggleSaved)
    }
}

/**
 * The saved control, delegating to the shared component.
 *
 * It used to be a private copy that lived only in this file, which is how the
 * mini player ended up drawing a permanent plus while this screen showed a green
 * check for the same track.
 */
@Composable
private fun SavedCheck(isSaved: Boolean, onClick: () -> Unit) {
    SavedToggle(isSaved = isSaved, onToggle = onClick)
}

@Composable
private fun Scrubber(
    state: PlaybackState,
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
        val durationMs = state.effectiveDurationMs
        val elapsedMs = (durationMs * fraction).toLong()
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = elapsedMs.asTrackDuration(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            // Time remaining rather than total length. During a scrub this counts
            // down from the dragged position, so it answers "when does this end"
            // for the place the finger is, which is the question the seek bar is
            // actually being used to ask.
            Text(
                text = "-" + (durationMs - elapsedMs).coerceAtLeast(0L).asTrackDuration(),
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
private fun SecondaryRow(
    deviceName: String?,
    canShare: Boolean,
    onShare: () -> Unit,
    onShowQueue: () -> Unit,
    onMoreOptions: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status, not a control. There is no cast support to hand a tap to, and
        // the previous version was a button with an empty body.
        Icon(
            imageVector = Icons.Filled.Computer,
            contentDescription = if (deviceName != null) {
                stringResource(R.string.cd_connect_device)
            } else {
                null
            },
            tint = if (deviceName != null) colors.accent else colors.textSecondary,
            modifier = Modifier.size(dimens.iconMd),
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onShowQueue) {
            Icon(
                imageVector = Icons.Filled.QueueMusic,
                contentDescription = "Open queue",
                tint = colors.textSecondary,
                modifier = Modifier.size(dimens.iconMd),
            )
        }
        if (canShare) {
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(dimens.iconMd),
                )
            }
            Spacer(Modifier.width(dimens.spaceSm))
        }
        IconButton(onClick = onMoreOptions) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = stringResource(R.string.cd_more_options),
                tint = colors.textSecondary,
                modifier = Modifier.size(dimens.iconMd),
            )
        }
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

/**
 * A card listing tracks, used for the rest of the album and for other work by the
 * same artist.
 *
 * Unplayable rows are dimmed rather than hidden: a catalog can lack a stream
 * for a track, and silently dropping it would make an album look incomplete.
 */
@Composable
private fun TrackListCard(
    title: String,
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SpotKofiTheme.shapes.card)
            .background(colors.card)
            .padding(vertical = dimens.spaceMd),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = dimens.spaceLg),
        )

        Spacer(Modifier.height(dimens.spaceSm))

        tracks.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableScale(pressedScale = 0.98f, enabled = item.isExternallyOpenable) {
                        onTrackClick(item)
                    }
                    .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(id = item.id, size = 44.dp, url = item.artworkUrl)
                Spacer(Modifier.width(dimens.spaceMd))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (item.isExternallyOpenable) {
                            colors.textPrimary
                        } else {
                            colors.textTertiary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (item.isExternallyOpenable) {
                            item.artistName
                        } else {
                            "Official link unavailable"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = item.durationMs.asTrackDuration(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

/** Horizontally scrolling albums by the current artist. */
@Composable
private fun AlbumRow(
    title: String,
    albums: List<Album>,
    onAlbumClick: (String) -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = dimens.screenGutter),
        )
        Spacer(Modifier.height(dimens.spaceMd))
        LazyRow(
            contentPadding = PaddingValues(horizontal = dimens.screenGutter),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
        ) {
            items(items = albums, key = { it.id }) { album ->
                MediaCard(item = album, onClick = { onAlbumClick(album.id) })
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
            queue = listOf(previewTrack()),
            onQueueRemove = {},
            onQueueMove = { _, _ -> },
            onQueueClear = {},
            onCollapse = {},
            onCollectionClick = {},
            onPlayTrack = {},

            onDrag = {},
            onDragStopped = {},
            onTogglePlayPause = {},
            onNext = {},
            onPrevious = {},
            onSeek = {},
            onToggleShuffle = {},
            onCycleRepeat = {},
            onToggleSaved = {},
            onDownload = {},
            downloadStatus = null,
            downloadProgress = 0,
        )
    }
}
