package com.spotkofi.app.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.spotkofi.app.core.AppContainer
import com.spotkofi.app.data.model.PlaybackState
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.feature.browse.ExploreScreen
import com.spotkofi.app.feature.browse.MoodCategoryScreen
import com.spotkofi.app.feature.collection.CollectionScreen
import com.spotkofi.app.feature.home.HomeScreen
import com.spotkofi.app.feature.library.LibraryScreen
import com.spotkofi.app.feature.player.NowPlayingScreen
import com.spotkofi.app.feature.profile.ProfileDrawer
import com.spotkofi.app.feature.search.SearchScreen
import com.spotkofi.app.feature.settings.SettingsScreen
import com.spotkofi.app.ui.components.CreatePlaylistDialog
import com.spotkofi.app.ui.components.MiniPlayer
import com.spotkofi.app.ui.components.SpotKofiDrawer
import com.spotkofi.app.ui.components.SpotKofiLaunchOverlay
import com.spotkofi.app.ui.components.rememberSpotKofiDrawerState
import com.spotkofi.app.ui.navigation.CollectionRoute
import com.spotkofi.app.ui.navigation.ExploreRoute
import com.spotkofi.app.ui.navigation.MoodRoute
import com.spotkofi.app.ui.navigation.HomeGraph
import com.spotkofi.app.ui.navigation.HomeRoute
import com.spotkofi.app.ui.navigation.LibraryGraph
import com.spotkofi.app.ui.navigation.LibraryRoute
import com.spotkofi.app.ui.navigation.SearchGraph
import com.spotkofi.app.ui.navigation.SearchRoute
import com.spotkofi.app.ui.navigation.SettingsRoute
import com.spotkofi.app.ui.navigation.SpotKofiBottomBar
import com.spotkofi.app.ui.navigation.TopLevelDestination
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** How far the tab behind the player is blurred when the player is fully open. */
private val MaxBackdropBlur = 20.dp

/** Fraction of the screen a roughly half-height slow drag must cover before dismissing the player. */
private const val MINIMIZE_FRACTION = 0.48f

/** Downward release speed that dismisses the player before it reaches the distance threshold. */
private const val MINIMIZE_FLING_VELOCITY_PX_PER_SEC = 1_400f

/**
 * Root of the composable tree: provides the dependency container and theme, owns
 * the nav graph, the profile drawer, the floating bottom chrome, and the player.
 *
 * Two structural decisions worth knowing before editing this file.
 *
 * There is no `Scaffold`. The Create panel's scrim has to dim the content and the
 * mini player while leaving the floating nav bar lit and tappable, because that
 * bar holds the button the panel belongs to. A Scaffold nests its `bottomBar`
 * inside itself, which fixes it below any sibling overlay and makes that
 * z-ordering impossible.
 *
 * Now Playing is an overlay, not a navigation destination. As a destination the
 * screen behind it is not composed, so dragging the player down revealed flat
 * background instead of the tab it came from. As an overlay the whole backdrop
 * stays live underneath and can be blurred and scaled as the player moves.
 */
@Composable
fun SpotKofiApp(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        SpotKofiTheme {
            val dimens = SpotKofiTheme.dimens

            val navController = rememberNavController()
            val drawerState = rememberSpotKofiDrawerState()

            val playbackStateFlow = container.playerController.state
            val playbackHasTrack by remember(playbackStateFlow) {
                playbackStateFlow.map { it.hasTrack }.distinctUntilChanged()
            }.collectAsStateWithLifecycle(initialValue = false)
            val playbackTrackId by remember(playbackStateFlow) {
                playbackStateFlow.map { it.track?.id }.distinctUntilChanged()
            }.collectAsStateWithLifecycle(initialValue = null)
            val playbackRequestId by remember(playbackStateFlow) {
                playbackStateFlow.map { it.playRequestId }.distinctUntilChanged()
            }.collectAsStateWithLifecycle(initialValue = 0L)
            val settings by container.settingsStore.settings.collectAsStateWithLifecycle()

            var showPlaylistDialog by remember { mutableStateOf(false) }
            var showLaunchOverlay by remember { mutableStateOf(true) }

            // ---------------- Player position: one value, one owner ----------------
            // 0 = fully open, 1 = fully off the bottom. This single number drives the
            // player's translation, the backdrop blur and the dismiss decision.
            //
            // Previously the screen kept its own drag offset while the host kept a
            // separate blur progress. Those could desync: if the player composable
            // was reused before its exit animation finished, the stale offset parked
            // it off-screen while the host still thought it was open, leaving the
            // blur stuck on with nothing visible to dismiss.
            //
            // Kept as a State object and NEVER read during composition. Reading it
            // with `by` here would recompose this whole function on every drag
            // frame; it is only read inside graphicsLayer blocks, in the draw phase.
            val playerPos = remember { mutableFloatStateOf(1f) }
            val playerHeightPx = remember { mutableFloatStateOf(1f) }

            // Separate boolean so the player can be removed from composition once it
            // is off-screen. Changes rarely, so reading it in composition is cheap.
            var playerMounted by remember { mutableStateOf(false) }

            var miniPlayerDismissed by remember { mutableStateOf(false) }

            // Track identity and explicit play requests are narrow flows, so root
            // navigation does not recompose for the player's 100ms progress ticks.
            LaunchedEffect(playbackTrackId, playbackRequestId, playbackHasTrack) {
                if (playbackHasTrack) {
                    miniPlayerDismissed = false
                }
            }

            val scope = rememberCoroutineScope()
            val settleJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

            fun settlePlayer(open: Boolean, velocityPxPerSec: Float = 0f) {
                settleJob.value?.cancel()
                settleJob.value = scope.launch {
                    animate(
                        initialValue = playerPos.floatValue,
                        targetValue = if (open) 0f else 1f,
                        // Velocity arrives in px/s but the value is a 0..1 fraction.
                        // The player spring is shared by opening, closing and an
                        // interrupted drag, so reversing direction never snaps.
                        initialVelocity = velocityPxPerSec / playerHeightPx.floatValue,
                        animationSpec = Motion.player(),
                    ) { value, _ -> playerPos.floatValue = value }
                    if (!open) playerMounted = false
                }
            }

            fun openPlayer() {
                // Opening the full player makes the collapsed dock available again
                // when the user collapses it.
                miniPlayerDismissed = false
                // Mount at the dismissed position, then animate into place. The
                // previous implementation assigned 0f immediately, which made a
                // tap or upward mini-player swipe feel like a hard teleport.
                playerMounted = true
                settlePlayer(open = true)
            }

            // Keyed on the play-request counter, NOT on the track id.
            //
            // Keying on the track id meant every change of current track reopened
            // this window, so a song finishing and rolling into the next one threw
            // the full player over whatever the user was doing. The counter only
            // moves when someone actually taps a track, so an automatic queue
            // advance (and next/previous) now leaves the player exactly as the
            // user left it.
            //
            // Opening at all is a preference: with it off, tapping a song starts the
            // audio and leaves the user on the list they were browsing.
            LaunchedEffect(playbackRequestId) {
                if (!settings.openPlayerOnPlay) return@LaunchedEffect
                if (playbackRequestId > 0L && playbackHasTrack) openPlayer()
            }

            val backStackEntry by navController.currentBackStackEntryAsState()
            val destination = backStackEntry?.destination

            val isSettings = destination?.hasRoute(SettingsRoute::class) == true

            // Derived from the destination's graph, not tracked in local state.
            //
            // Tracking it meant the bar could claim a tab was active while
            // navigation had actually left the user somewhere else. Reading the
            // hierarchy makes that impossible, and a collection opened from Search
            // still keeps Search highlighted because it lives in the Search graph.
            val activeTab = remember(destination) {
                when {
                    destination == null -> TopLevelDestination.Home
                    destination.hierarchy.any { it.hasRoute(SearchGraph::class) } ->
                        TopLevelDestination.Search

                    destination.hierarchy.any { it.hasRoute(LibraryGraph::class) } ->
                        TopLevelDestination.Library

                    else -> TopLevelDestination.Home
                }
            }

            /** True when the current destination IS the tab screen, not something above it. */
            fun isOnTabRoot(tab: TopLevelDestination): Boolean = when (tab) {
                TopLevelDestination.Home -> destination?.hasRoute(HomeRoute::class) == true
                TopLevelDestination.Search -> destination?.hasRoute(SearchRoute::class) == true
                TopLevelDestination.Library -> destination?.hasRoute(LibraryRoute::class) == true
            }

            val userName = remember { container.musicRepository.currentUserName() }

            // Insets are resolved here because the floating chrome sits outside the
            // normal layout flow, so nothing else can compute this for the screens.
            val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

            val barBlock = dimens.floatingBarHeight
            val targetMiniBlock = if (playbackHasTrack && !miniPlayerDismissed) {
                dimens.miniPlayerHeight + dimens.spaceMd
            } else {
                0.dp
            }
            val miniBlock by animateDpAsState(
                targetValue = targetMiniBlock,
                animationSpec = Motion.smooth(),
                label = "miniPlayerContentPadding",
            )

            val screenPadding = PaddingValues(
                top = statusInset,
                bottom = if (isSettings) navInset else navInset + barBlock + miniBlock,
            )

            val maxBlurPx = with(LocalDensity.current) { MaxBackdropBlur.toPx() }
            // RenderEffect landed in API 31; below that the scale alone carries the
            // depth cue.
            val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            BackHandler(enabled = playerMounted) { settlePlayer(open = false) }

            Box(modifier = modifier.fillMaxSize()) {
                SpotKofiDrawer(
                state = drawerState,
                gesturesEnabled = !playerMounted && !isSettings,
                drawerContent = {
                    ProfileDrawer(
                        userName = userName,
                        friends = container.musicRepository.friendActivity(),
                        conversations = container.musicRepository.conversations(),
                        onSettings = {
                            drawerState.close()
                            navController.navigate(SettingsRoute)
                        },
                        onDismiss = drawerState::close,
                    )
                },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SpotKofiTheme.colors.base),
                ) {
                    // ---------- Backdrop: everything the player covers ----------
                    // Grouped into one layer so a single blur and scale applies to
                    // the content, the mini player and the nav bar together.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // The blur is set as a renderEffect inside graphicsLayer
                            // rather than with Modifier.blur. Modifier.blur takes a
                            // Dp evaluated at composition, so animating it would
                            // recompose this subtree every frame. Assigning the
                            // effect here keeps the whole thing in the draw phase.
                            .graphicsLayer {
                                // Derived straight from the player position, so the
                                // blur can never disagree with where the player is.
                                val b = (1f - playerPos.floatValue).coerceIn(0f, 1f)
                                val s = 1f - b * 0.05f
                                scaleX = s
                                scaleY = s
                                renderEffect = if (blurSupported && b > 0.01f) {
                                    val r = maxBlurPx * b
                                    BlurEffect(r, r)
                                } else {
                                    null
                                }
                            },
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = HomeGraph,
                            modifier = Modifier.fillMaxSize(),
                            // Tab switches cross-fade with a short rise.
                            //
                            // Still no scale: a screen that grows or shrinks reads as
                            // the window moving towards or away from the viewer, which
                            // is the "receding then closing" effect that looked wrong
                            // on back. The rise is a fraction of the screen, so it
                            // gives the arriving tab a direction without turning a tab
                            // switch into a push.
                            //
                            // The incoming fade is longer than the outgoing one and
                            // eased, so the two overlap instead of handing off at a
                            // hard edge. Timings come from Motion rather than being
                            // invented here.
                            enterTransition = {
                                if (isTabSwitch()) {
                                    // Tab switches move horizontally as one surface:
                                    // the incoming root slides in while the old root
                                    // fades and moves out, so changing tabs feels like
                                    // a deliberate navigation rather than a flash.
                                    fadeIn(tween(Motion.Medium, easing = Motion.Standard)) +
                                        slideInHorizontally(
                                            tween(Motion.Medium, easing = Motion.Emphasized),
                                        ) { width ->
                                            width * tabSwitchDirection() / 6
                                        }
                                } else {
                                    fadeIn(tween(Motion.Medium, easing = Motion.Emphasized)) +
                                        slideInVertically(
                                            tween(Motion.Medium, easing = Motion.Emphasized),
                                        ) { height -> height / 22 }
                                }
                            },
                            exitTransition = {
                                if (isTabSwitch()) {
                                    fadeOut(tween(Motion.Medium, easing = Motion.Standard)) +
                                        slideOutHorizontally(
                                            tween(Motion.Medium, easing = Motion.Emphasized),
                                        ) { width ->
                                            -width * tabSwitchDirection() / 6
                                        }
                                } else {
                                    fadeOut(tween(Motion.Fast, easing = Motion.Standard)) +
                                        slideOutVertically(
                                            tween(Motion.Fast, easing = Motion.Standard),
                                        ) { height -> -height / 40 }
                                }
                            },
                            // Nothing at all on the way back. On a pop the screen
                            // underneath was never gone, so animating it in makes it
                            // look like a fresh screen arriving. It should simply be
                            // uncovered as the one above slides away.
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = {
                                fadeOut(tween(Motion.Fast, easing = Motion.Standard))
                            },
                        ) {
                            // One nested graph per tab, each carrying its own copy of
                            // the collection destination. That is what gives every tab
                            // an independent detail stack and makes save/restore key
                            // per tab instead of per route.
                            navigation<HomeGraph>(startDestination = HomeRoute) {
                                composable<HomeRoute> {
                                    HomeScreen(
                                        onCollectionClick = {
                                            navController.navigate(CollectionRoute(it))
                                        },
                                        onOpenProfile = drawerState::open,
                                        contentPadding = screenPadding,
                                    )
                                }
                                collectionDestination(navController, screenPadding)
                            }

                            navigation<SearchGraph>(startDestination = SearchRoute) {
                                composable<SearchRoute> {
                                    SearchScreen(
                                        onCollectionClick = {
                                            navController.navigate(CollectionRoute(it))
                                        },
                                        onTrackClick = { track, queue ->
                                            container.playerController.play(track, queue)
                                        },
                                        onOpenProfile = drawerState::open,
                                        contentPadding = screenPadding,
                                    )
                                }
                                // Explore lives in the Search graph, so the Search
                                // tab stays highlighted while browsing it and its
                                // stack is kept when the user switches tabs away.
                                exploreDestinations(navController, screenPadding)
                                collectionDestination(navController, screenPadding)
                            }

                            navigation<LibraryGraph>(startDestination = LibraryRoute) {
                                composable<LibraryRoute> {
                                    LibraryScreen(
                                        onCollectionClick = {
                                            navController.navigate(CollectionRoute(it))
                                        },
                                        onTrackClick = { track, queue ->
                                            container.playerController.play(track, queue)
                                        },
                                        onOpenProfile = drawerState::open,
                                        onCreate = { showPlaylistDialog = true },
                                        contentPadding = screenPadding,
                                    )
                                }
                                collectionDestination(navController, screenPadding)
                            }

                            composable<SettingsRoute>(
                                enterTransition = {
                                    slideInHorizontally(
                                        tween(Motion.Medium, easing = Motion.Emphasized),
                                    ) { it }
                                },
                                exitTransition = {
                                    slideOutHorizontally(
                                        tween(Motion.Medium, easing = Motion.Emphasized),
                                    ) { -it / 6 }
                                },
                                popEnterTransition = {
                                    slideInHorizontally(
                                        tween(Motion.Medium, easing = Motion.Emphasized),
                                    ) { -it / 6 }
                                },
                                popExitTransition = {
                                    slideOutHorizontally(
                                        tween(Motion.Medium, easing = Motion.Accelerate),
                                    ) { it }
                                },
                            ) {
                                SettingsScreen(
                                    onBack = navController::popBackStack,
                                    contentPadding = screenPadding,
                                )
                            }
                        }

                        MiniPlayerHost(
                            stateFlow = playbackStateFlow,
                            visible = !isSettings && playbackHasTrack && !miniPlayerDismissed,
                            barBlock = barBlock,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            onClick = ::openPlayer,
                            onTogglePlayPause = container.playerController::togglePlayPause,
                            onToggleSaved = container.playerController::toggleSaved,
                            onNext = container.playerController::next,
                            onPrevious = container.playerController::previous,
                            onDismiss = {
                                miniPlayerDismissed = true
                                if (settings.stopOnPlayerDismiss) {
                                    container.playerController.stop()
                                }
                            },
                        )
                        CreatePlaylistDialog(
                            visible = showPlaylistDialog,
                            onDismiss = { showPlaylistDialog = false },
                            onCreate = { name, description ->
                                scope.launch {
                                    container.localStore.createPlaylist(name, description)
                                    showPlaylistDialog = false
                                }
                            },
                        )

                        // ---- Bottom navigation, docked to the system inset ----
                        // The bar stays visible above the system navigation inset.
                        AnimatedVisibility(
                            visible = !isSettings,
                            enter = slideInVertically(Motion.gentle()) { it * 2 } +
                                fadeIn(Motion.fast()),
                            exit = slideOutVertically(Motion.snappy()) { it * 2 } +
                                fadeOut(Motion.fast()),
                            modifier = Modifier.align(Alignment.BottomCenter),
                        ) {
                            SpotKofiBottomBar(
                                current = activeTab,
                                onSelect = { tapped ->
                                    when {
                                        // Already standing on that tab's own
                                        // screen: do nothing. Navigating would
                                        // pop and re-push it, destroying and
                                        // rebuilding the screen, which is the
                                        // reload being seen.
                                        isOnTabRoot(tapped) -> Unit

                                        // Deep inside the tab that is already
                                        // active: the tap means "take me back to
                                        // the top of this tab". Handled as an
                                        // explicit pop so it cannot depend on
                                        // saved-state restoration.
                                        tapped == activeTab ->
                                            navController.popToTabRoot(tapped)

                                        else -> navController.switchTab(tapped)
                                    }
                                },
                            )
                        }
                    }

                    // ---------- Player, over the blurred backdrop ----------
                    // No AnimatedVisibility. Its enter/exit would be a second
                    // animation fighting `playerPos` for control of the same
                    // translation, which is exactly how the two got out of step
                    // before. Mount/unmount is a plain boolean and all motion comes
                    // from the one position value.
                    // The full player is mounted only while it is visible or settling.
                    // Once collapsed, the mini-player owns progress rendering; keeping
                    // this hidden subtree alive would recompose its scrubber, lyrics,
                    // and transport on every playback tick for no visible benefit.
                    if (playerMounted) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged {
                                    playerHeightPx.floatValue =
                                        it.height.toFloat().coerceAtLeast(1f)
                                }
                                .graphicsLayer {
                                    val p = playerPos.floatValue
                                    translationY = p * size.height
                                    // Shrinks as it falls so it reads as receding
                                    // into the mini player rather than sliding off a
                                    // shelf. No alpha fade: the page must stay opaque
                                    // or the blurred backdrop shows through the
                                    // artwork, which looks like a rendering fault.
                                    val s = 1f - p * 0.08f
                                    scaleX = s
                                    scaleY = s
                                },
                        ) {
                            NowPlayingScreen(
                                onCollapse = { settlePlayer(open = false) },
                                onCollectionClick = { id ->
                                    // Collapse first, otherwise the album opens
                                    // behind the player and the tap looks ignored.
                                    settlePlayer(open = false)
                                    navController.navigate(CollectionRoute(id))
                                },
                                onDrag = { delta ->
                                    // Written synchronously: no coroutine per touch
                                    // event, so the page tracks the finger exactly.
                                    playerPos.floatValue =
                                        (playerPos.floatValue + delta / playerHeightPx.floatValue)
                                            .coerceIn(0f, 1f)
                                },
                                onDragStopped = { velocity ->
                                    // A slow partial gesture is reversible until it
                                    // reaches the half-height distance threshold. A
                                    // decisive downward fling may dismiss sooner, while
                                    // a decisive upward release always returns home.
                                    val downwardFling =
                                        velocity >= MINIMIZE_FLING_VELOCITY_PX_PER_SEC
                                    val upwardFling =
                                        velocity <= -MINIMIZE_FLING_VELOCITY_PX_PER_SEC
                                    val dismiss = !upwardFling && (
                                        playerPos.floatValue >= MINIMIZE_FRACTION ||
                                            downwardFling
                                        )
                                    settlePlayer(open = !dismiss, velocityPxPerSec = velocity)
                                },
                                onDragCancelled = {
                                    // Pointer cancellation is not a release decision.
                                    // Always return to the full player instead of
                                    // accidentally dismissing after a half drag.
                                    settlePlayer(open = true)
                                },
                            )
                        }
                    }

                    if (showLaunchOverlay) {
                        SpotKofiLaunchOverlay(
                            onFinished = { showLaunchOverlay = false },
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun MiniPlayerHost(
    stateFlow: StateFlow<PlaybackState>,
    visible: Boolean,
    barBlock: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleSaved: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit,
) {
    val liveState by stateFlow.collectAsStateWithLifecycle()
    var lastTrackState by remember { mutableStateOf<PlaybackState?>(null) }

    // Progress updates stay inside this small subtree. The app root only observes
    // track identity and play requests, which prevents navigation and backdrop
    // composition from running ten times per second.
    LaunchedEffect(Unit) {
        stateFlow.collect { state ->
            if (state.hasTrack) lastTrackState = state
        }
    }

    val renderedState = if (liveState.hasTrack) liveState else lastTrackState
    if (renderedState != null) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(Motion.player()) { it } + fadeIn(Motion.fast()),
            exit = slideOutVertically(Motion.player()) { it } + fadeOut(Motion.fast()),
            modifier = modifier,
        ) {
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = barBlock),
            ) {
                MiniPlayer(
                    state = renderedState,
                    onClick = onClick,
                    onTogglePlayPause = onTogglePlayPause,
                    onToggleSaved = onToggleSaved,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

/**
 * Tab switching that preserves each tab's own state.
 *
 * `saveState`/`restoreState` were previously left off so that a tab press was
 * always a reset. That is a defensible rule, but it also destroyed the tab's
 * composition and its ViewModel on every switch, so returning to a tab re-ran its
 * network loads and threw away scroll position. That rebuild is the stutter felt
 * when moving between tabs, and no amount of transition tuning hides it.
 *
 * State is now saved and restored, so switching tabs is a swap of already-built
 * screens rather than a reload.
 */
private fun NavHostController.switchTab(destination: TopLevelDestination) {
    val graphRoute: Any = when (destination) {
        TopLevelDestination.Home -> HomeGraph
        TopLevelDestination.Search -> SearchGraph
        TopLevelDestination.Library -> LibraryGraph
    }
    navigate(graphRoute) {
        popUpTo(graph.findStartDestination().id) {
            inclusive = false
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Returns the already-active tab to its own root screen.
 *
 * Kept separate from [switchTab] because the two answer different questions:
 * switching tabs should restore where the user left off, while re-tapping the
 * current tab should unwind to the top of it.
 */
private fun NavHostController.popToTabRoot(destination: TopLevelDestination) {
    when (destination) {
        TopLevelDestination.Home -> popBackStack(HomeRoute, inclusive = false)
        TopLevelDestination.Search -> popBackStack(SearchRoute, inclusive = false)
        TopLevelDestination.Library -> popBackStack(LibraryRoute, inclusive = false)
    }
}

/**
 * A tab switch is not a detail push. Navigation Compose otherwise lets the
 * outgoing collection transition win, so an album can slide away while the new
 * tab fades in over a transparent-looking frame. The graph hierarchy gives us a
 * stable way to distinguish that case without inspecting generated route names.
 */
private fun NavDestination.topLevelTab(): TopLevelDestination? = when {
    hierarchy.any { it.hasRoute(HomeGraph::class) } -> TopLevelDestination.Home
    hierarchy.any { it.hasRoute(SearchGraph::class) } -> TopLevelDestination.Search
    hierarchy.any { it.hasRoute(LibraryGraph::class) } -> TopLevelDestination.Library
    else -> null
}

private fun androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>
    .isTabSwitch(): Boolean {
    val from = initialState.destination.topLevelTab()
    val to = targetState.destination.topLevelTab()
    return from != null && to != null && from != to
}

private fun androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>
    .tabSwitchDirection(): Int {
    val from = initialState.destination.topLevelTab()
    val to = targetState.destination.topLevelTab()
    if (from == null || to == null) return 1
    return if (tabOrder(to) >= tabOrder(from)) 1 else -1
}

private fun tabOrder(destination: TopLevelDestination): Int = when (destination) {
    TopLevelDestination.Home -> 0
    TopLevelDestination.Search -> 1
    TopLevelDestination.Library -> 2
}

/**
 * The collection detail destination, registered once per tab graph.
 *
 * Declared as an extension rather than copied three times so the push and pop
 * transitions cannot drift apart between tabs.
 */
private fun NavGraphBuilder.collectionDestination(
    navController: NavHostController,
    screenPadding: PaddingValues,
) {
    // Detail is a push, so it slides in from the trailing edge and slides back out
    // the same way. The direction is what tells the user which way back is.
    composable<CollectionRoute>(
        enterTransition = {
            if (isTabSwitch()) {
                EnterTransition.None
            } else {
                slideInHorizontally(tween(Motion.Medium, easing = Motion.Emphasized)) { it }
            }
        },
        // A tab change is a replacement, not a detail push. Let the destination
        // root own the frame so the collection cannot expose a transparent seam.
        exitTransition = {
            if (isTabSwitch()) {
                ExitTransition.None
            } else {
                slideOutHorizontally(tween(Motion.Medium, easing = Motion.Emphasized)) { -it / 6 }
            }
        },
        popEnterTransition = {
            slideInHorizontally(tween(Motion.Medium, easing = Motion.Emphasized)) { -it / 6 }
        },
        // Slide only, no fade. Fading while sliding makes the screen go translucent
        // mid-motion, so for a moment both screens are visible through each other,
        // and that smear is the ugly part of the close.
        popExitTransition = {
            slideOutHorizontally(tween(Motion.Medium, easing = Motion.Accelerate)) { it }
        },
    ) { entry ->
        val route = entry.toRoute<CollectionRoute>()
        CollectionScreen(
            collectionId = route.id,
            onBack = navController::popBackStack,
            contentPadding = screenPadding,
        )
    }
}

/**
 * Explore and the mood/genre page it opens.
 *
 * Registered together and with the same transitions as [collectionDestination],
 * because to the user these are the same kind of move: a push onto the tab they
 * are already in.
 */
private fun NavGraphBuilder.exploreDestinations(
    navController: NavHostController,
    screenPadding: PaddingValues,
) {
    composable<ExploreRoute>(
        enterTransition = {
            slideInHorizontally(tween(Motion.Medium, easing = Motion.Emphasized)) { it }
        },
        exitTransition = {
            slideOutHorizontally(tween(Motion.Medium, easing = Motion.Emphasized)) { -it / 6 }
        },
        popEnterTransition = {
            slideInHorizontally(tween(Motion.Medium, easing = Motion.Emphasized)) { -it / 6 }
        },
        popExitTransition = {
            slideOutHorizontally(tween(Motion.Medium, easing = Motion.Accelerate)) { it }
        },
    ) {
        ExploreScreen(
            onBack = navController::popBackStack,
            onCollectionClick = { navController.navigate(CollectionRoute(it)) },
            onCategoryClick = { category ->
                navController.navigate(
                    MoodRoute(title = category.title, params = category.params),
                )
            },
            contentPadding = screenPadding,
        )
    }

    composable<MoodRoute>(
        enterTransition = {
            slideInHorizontally(tween(Motion.Medium, easing = Motion.Emphasized)) { it }
        },
        exitTransition = {
            slideOutHorizontally(tween(Motion.Medium, easing = Motion.Emphasized)) { -it / 6 }
        },
        popEnterTransition = {
            slideInHorizontally(tween(Motion.Medium, easing = Motion.Emphasized)) { -it / 6 }
        },
        popExitTransition = {
            slideOutHorizontally(tween(Motion.Medium, easing = Motion.Accelerate)) { it }
        },
    ) { entry ->
        val route = entry.toRoute<MoodRoute>()
        MoodCategoryScreen(
            title = route.title,
            params = route.params,
            onBack = navController::popBackStack,
            onCollectionClick = { navController.navigate(CollectionRoute(it)) },
            contentPadding = screenPadding,
        )
    }
}
