package com.spotkofi.app.feature.player

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.spotkofi.app.feature.settings.LyricsProviderDialog
import com.spotkofi.app.ui.components.AboutTrackCard
import com.spotkofi.app.ui.components.LyricsCard
import com.spotkofi.app.ui.components.LyricsSheet
import com.spotkofi.app.ui.components.LyricsSkeleton
import com.spotkofi.app.ui.components.LoadingDots
import com.spotkofi.app.ui.components.QueueSheet
import com.spotkofi.app.ui.components.SavedToggle
import com.spotkofi.app.ui.components.TrackCreditsSheet
import com.spotkofi.app.ui.components.TrackOptionsSheet
import com.spotkofi.app.ui.components.artworkSeedColor
import com.spotkofi.app.ui.motion.clickableScale
import com.spotkofi.app.ui.theme.ErrorRed
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val HORIZONTAL_SWIPE_DISTANCE_PX = 96f

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
    /** Follows a cancelled gesture back to the open player instead of settling by threshold. */
    onDragCancelled: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val viewModel: NowPlayingViewModel = viewModel {
        NowPlayingViewModel(
            container.musicRepository,
            container.playerController,
            container.settingsStore,
        )
    }
    val state by viewModel.playbackState.collectAsStateWithLifecycle()
    val details by viewModel.details.collectAsStateWithLifecycle()
    val detailsLoading by viewModel.isDetailsLoading.collectAsStateWithLifecycle()
    val queue by container.queueController.queue.collectAsStateWithLifecycle()
    val downloads by container.downloadManager.downloads.collectAsStateWithLifecycle()
    val download = state.track?.let { current ->
        downloads.firstOrNull { it.track.id == current.id }
    }

    NowPlayingContent(
        state = state,
        details = details,
        detailsLoading = detailsLoading,
        queue = queue,
        onQueueRemove = container.queueController::removeFromQueueAt,
        onQueueMove = container.queueController::moveInQueue,
        onQueueClear = container.queueController::clearQueue,
        onCollapse = onCollapse,
        onCollectionClick = onCollectionClick,
        onPlayTrack = viewModel::onPlayTrack,
        onDrag = onDrag,
        onDragStopped = onDragStopped,
        onDragCancelled = onDragCancelled,
        onTogglePlayPause = viewModel::onTogglePlayPause,
        onNext = viewModel::onNext,
        onPrevious = viewModel::onPrevious,
        onSeek = viewModel::onSeek,
        onSeekTo = viewModel::onSeekTo,
        onToggleShuffle = viewModel::onToggleShuffle,
        onCycleRepeat = viewModel::onCycleRepeat,
        onToggleSaved = viewModel::onToggleSaved,
        onStop = {
            container.playerController.stop()
            onCollapse()
        },
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
    detailsLoading: Boolean,
    queue: List<Track>,
    onQueueRemove: (Int) -> Unit,
    onQueueMove: (Int, Int) -> Unit,
    onQueueClear: () -> Unit,
    onCollapse: () -> Unit,
    onCollectionClick: (String) -> Unit,
    onPlayTrack: (Track) -> Unit,
    onDrag: (Float) -> Unit,
    onDragStopped: (Float) -> Unit,
    onDragCancelled: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleSaved: () -> Unit,
    onStop: () -> Unit,
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
    var showLyrics by remember { mutableStateOf(false) }
    var showCredits by remember { mutableStateOf(false) }
    var showLyricsProviderPicker by remember { mutableStateOf(false) }

    val container = LocalAppContainer.current
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle()

    // Direction is updated before the queue changes, so AnimatedContent can move
    // the outgoing artwork left for Next and right for Previous.
    var artworkSwipeDirection by remember { mutableStateOf(1) }
    val playNextWithAnimation = {
        artworkSwipeDirection = 1
        onNext()
    }
    val playPreviousWithAnimation = {
        artworkSwipeDirection = -1
        onPrevious()
    }
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
                        contextLabel = "Now Playing",
                        swipeDirection = artworkSwipeDirection,
                        onCollapse = onCollapse,
                        onDrag = onDrag,
                        onDragStopped = onDragStopped,
                        onDragCancelled = onDragCancelled,
                        onSwipeHorizontal = { delta ->
                            if (delta < 0f) playNextWithAnimation() else playPreviousWithAnimation()
                        },
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
                        onNext = playNextWithAnimation,
                        onPrevious = playPreviousWithAnimation,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                    )
                    Spacer(Modifier.height(dimens.spaceMd))
                    SecondaryRow(
                        deviceName = state.deviceName,
                        canShare = track.isExternallyOpenable,
                        onShare = { shareTrack(track) },
                        onShowQueue = { showQueue = true },
                        onShowOptions = { showOptions = true },
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
            if (detailsLoading) {
                item(key = "lyrics_loading") {
                    SectionSpacing {
                        LyricsSkeleton()
                    }
                }
            }

            if (!detailsLoading && details != null) {
                details.lyrics?.takeIf { it.hasText || it.instrumental }?.let { lyrics ->
                    item(key = "lyrics") {
                        SectionSpacing {
                            LyricsCard(
                                lyrics = lyrics,
                                positionMs = state.positionMs,
                                tint = seed,
                                onExpand = { showLyrics = true },
                                providerName = settings.lyricsProvider.displayName,
                                onChangeProvider = { showLyricsProviderPicker = true },
                            )
                        }
                    }
                }

                item(key = "about") {
                    SectionSpacing {
                        AboutTrackCard(
                            track = track,
                            genre = details.artistGenre,
                            credits = details.credits,
                            onExpand = { showCredits = true },
                        )
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
                isLoading = state.isLoading,
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
            onOpenLyrics = if (detailsLoading || details?.lyrics?.let { it.hasText || it.instrumental } == true) {
                { showLyrics = true }
            } else {
                null
            },
            onOpenDetails = { showCredits = true },
            onStop = onStop,
            onDownload = onDownload,
            downloadStatus = downloadStatus,
            downloadProgress = downloadProgress,
        )
        TrackCreditsSheet(
            visible = showCredits,
            track = track,
            credits = details?.credits,
            genre = details?.artistGenre,
            onDismiss = { showCredits = false },
        )
        // Above every sheet: the reader is a whole screen, not a panel over the
        // player, so nothing else should paint on top of it.
        LyricsSheet(
            visible = showLyrics,
            track = track,
            lyrics = details?.lyrics,
            loading = detailsLoading,
            positionMs = state.positionMs,
            onDismiss = { showLyrics = false },
            onSeekTo = onSeekTo,
            providerName = settings.lyricsProvider.displayName,
            onChangeProvider = { showLyricsProviderPicker = true },
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
    }
}

/* ------------------------------------------------------------------ hero --- */

@Composable
private fun HeroArtwork(
    track: Track,
    contextLabel: String,
    swipeDirection: Int,
    onCollapse: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragStopped: (Float) -> Unit,
    onDragCancelled: () -> Unit,
    onSwipeHorizontal: (Float) -> Unit,
) {
    val dimens = SpotKofiTheme.dimens

    // Keep the header in its own vertical band. The artwork starts below the
    // back-button safe area instead of painting underneath that control.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                var horizontal = 0f
                var vertical = 0f
                val velocityTracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = {
                        horizontal = 0f
                        vertical = 0f
                        velocityTracker.resetTracking()
                    },
                    onDrag = { change, amount ->
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        horizontal += amount.x
                        vertical += amount.y
                        if (abs(vertical) >= abs(horizontal)) onDrag(amount.y)
                    },
                    onDragEnd = {
                        if (abs(horizontal) >= abs(vertical) &&
                            abs(horizontal) >= HORIZONTAL_SWIPE_DISTANCE_PX
                        ) {
                            onSwipeHorizontal(horizontal)
                        } else {
                            onDragStopped(-velocityTracker.calculateVelocity().y)
                        }
                        velocityTracker.resetTracking()
                    },
                    onDragCancel = {
                        velocityTracker.resetTracking()
                        onDragCancelled()
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = dimens.spaceSm, start = dimens.spaceXs, end = dimens.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCollapse) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = SpotKofiTheme.colors.textPrimary,
                    modifier = Modifier.size(dimens.iconLg),
                )
            }
            Text(
                text = contextLabel,
                style = MaterialTheme.typography.labelMedium,
                color = SpotKofiTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Balance the back-button touch target without adding a second
            // overflow action at the top of the player.
            Spacer(Modifier.size(48.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spaceXl, vertical = dimens.spaceMd)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp)),
        ) {
            AnimatedContent(
                targetState = track,
                transitionSpec = {
                    val direction = if (swipeDirection >= 0) 1 else -1
                    (
                        slideInHorizontally(
                            animationSpec = tween(360, easing = Motion.Emphasized),
                            initialOffsetX = { width -> direction * width },
                        ) + fadeIn(tween(260, easing = Motion.Emphasized))
                    ) togetherWith (
                        slideOutHorizontally(
                            animationSpec = tween(360, easing = Motion.Emphasized),
                            targetOffsetX = { width -> -direction * width },
                        ) + fadeOut(tween(220, easing = Motion.Standard))
                    )
                },
                label = "artworkSwipe",
                modifier = Modifier.fillMaxSize(),
            ) { animatedTrack ->
                Artwork(
                    id = animatedTrack.id,
                    url = animatedTrack.artworkUrl,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // The image keeps only readability scrims; the header is no longer
            // painted over the artwork, so the top gradient does not hide the
            // first row of pixels or make the back button look embedded in it.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.18f),
                            0.68f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f),
                        ),
                    ),
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

    Row(verticalAlignment = Alignment.CenterVertically) {
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

    // Keep the touch target generous while drawing only the slim 3 dp track used
    // by the reference. The transparent Slider remains responsible for accurate
    // tap/drag seeking, so this visual change does not remove interaction.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val targetFraction = if (scrubbing) scrubFraction else state.progress
    val fraction by animateFloatAsState(
        targetValue = targetFraction.coerceIn(0f, 1f),
        animationSpec = tween(120),
        label = "scrubberProgress",
    )

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(SpotKofiTheme.shapes.chip)
                    .background(colors.trackInactive),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(colors.textPrimary),
                )
            }
            Slider(
                value = targetFraction.coerceIn(0f, 1f),
                onValueChange = {
                    scrubbing = true
                    scrubFraction = it
                },
                onValueChangeFinished = {
                    onSeek(scrubFraction)
                    scrubbing = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().height(24.dp),
            )
        }
        val durationMs = state.effectiveDurationMs
        val elapsedMs = (durationMs * targetFraction.coerceIn(0f, 1f)).toLong()
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = elapsedMs.asTrackDuration(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.weight(1f))
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
        WhitePlayButton(
            isPlaying = state.isPlaying,
            isLoading = state.isLoading,
            onClick = onTogglePlayPause,
        )

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
private fun WhitePlayButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
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
        if (isLoading) {
            LoadingDots(
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 3.dp),
            )
        } else {
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
}

@Composable
private fun SecondaryRow(
    deviceName: String?,
    canShare: Boolean,
    onShare: () -> Unit,
    onShowQueue: () -> Unit,
    onShowOptions: () -> Unit,
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
        // Keep the overflow in the lower action row. The top header is reserved
        // for navigation and no longer carries a second visual menu affordance.
        IconButton(onClick = onShowOptions) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
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
    isLoading: Boolean,
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
            if (isLoading) {
                LoadingDots(
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
            } else {
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
            detailsLoading = false,
            queue = listOf(previewTrack()),
            onQueueRemove = {},
            onQueueMove = { _, _ -> },
            onQueueClear = {},
            onCollapse = {},
            onCollectionClick = {},
            onPlayTrack = {},

            onDrag = {},
            onDragStopped = {},
            onDragCancelled = {},
            onTogglePlayPause = {},
            onNext = {},
            onPrevious = {},
            onSeek = {},
            onSeekTo = {},
            onToggleShuffle = {},
            onCycleRepeat = {},
            onToggleSaved = {},
            onStop = {},
            onDownload = {},
            downloadStatus = null,
            downloadProgress = 0,
        )
    }
}
