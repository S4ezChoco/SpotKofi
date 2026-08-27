package com.spotkofi.app.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animate
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.spotkofi.app.core.AppContainer
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.feature.collection.CollectionScreen
import com.spotkofi.app.feature.home.HomeScreen
import com.spotkofi.app.feature.library.LibraryScreen
import com.spotkofi.app.feature.player.NowPlayingScreen
import com.spotkofi.app.feature.profile.ProfileDrawer
import com.spotkofi.app.feature.search.SearchScreen
import com.spotkofi.app.feature.settings.SettingsScreen
import com.spotkofi.app.ui.components.CreateSheet
import com.spotkofi.app.ui.components.MiniPlayer
import com.spotkofi.app.ui.components.SpotKofiDrawer
import com.spotkofi.app.ui.components.rememberSpotKofiDrawerState
import com.spotkofi.app.ui.navigation.CollectionRoute
import com.spotkofi.app.ui.navigation.HomeRoute
import com.spotkofi.app.ui.navigation.LibraryRoute
import com.spotkofi.app.ui.navigation.SearchRoute
import com.spotkofi.app.ui.navigation.SettingsRoute
import com.spotkofi.app.ui.navigation.SpotKofiBottomBar
import com.spotkofi.app.ui.navigation.TopLevelDestination
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme
import kotlinx.coroutines.launch

/** How far the tab behind the player is blurred when the player is fully open. */
private val MaxBackdropBlur = 20.dp

/** Fraction of the screen the player must be dragged past to dismiss on release. */
private const val DISMISS_FRACTION = 0.2f

/** Downward fling speed, px/s, that dismisses regardless of distance dragged. */
private const val DISMISS_VELOCITY_PX = 1100f

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

            val playbackState by container.playerController.state.collectAsStateWithLifecycle()

            var showCreateSheet by remember { mutableStateOf(false) }

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

            val scope = rememberCoroutineScope()

            fun settlePlayer(open: Boolean, velocityPxPerSec: Float = 0f) {
                scope.launch {
                    animate(
                        initialValue = playerPos.floatValue,
                        targetValue = if (open) 0f else 1f,
                        // Velocity arrives in px/s but the value is a 0..1 fraction.
                        initialVelocity = velocityPxPerSec / playerHeightPx.floatValue,
                        animationSpec = if (open) Motion.gentle() else Motion.snappy(),
                    ) { value, _ -> playerPos.floatValue = value }
                    if (!open) playerMounted = false
                }
            }

            fun openPlayer() {
                playerMounted = true
                settlePlayer(open = true)
            }

            val backStackEntry by navController.currentBackStackEntryAsState()
            val destination = backStackEntry?.destination

            val isSettings = destination?.hasRoute(SettingsRoute::class) == true

            // Tracked rather than derived from the destination, because a collection
            // opened from Search should keep Search highlighted. Deriving it would
            // fall through to Home for every non-tab route.
            var activeTab by remember { mutableStateOf(TopLevelDestination.Home) }

            /** True when the current destination IS the tab screen, not something above it. */
            fun isOnTabRoot(tab: TopLevelDestination): Boolean = when (tab) {
                TopLevelDestination.Home -> destination?.hasRoute(HomeRoute::class) == true
                TopLevelDestination.Search -> destination?.hasRoute(SearchRoute::class) == true
                TopLevelDestination.Library -> destination?.hasRoute(LibraryRoute::class) == true
                TopLevelDestination.Create -> false
            }

            val userName = remember { container.musicRepository.currentUserName() }

            // Insets are resolved here because the floating chrome sits outside the
            // normal layout flow, so nothing else can compute this for the screens.
            val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

            val barBlock = dimens.floatingBarHeight + dimens.floatingBarGap
            val miniBlock = if (playbackState.hasTrack) {
                dimens.miniPlayerHeight + dimens.spaceMd
            } else {
                0.dp
            }

            val screenPadding = PaddingValues(
                top = statusInset,
                bottom = if (isSettings) navInset else navInset + barBlock + miniBlock,
            )

            val maxBlurPx = with(LocalDensity.current) { MaxBackdropBlur.toPx() }
            // RenderEffect landed in API 31; below that the scale alone carries the
            // depth cue.
            val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            BackHandler(enabled = playerMounted) { settlePlayer(open = false) }

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
                    modifier = modifier
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
                            startDestination = HomeRoute,
                            modifier = Modifier.fillMaxSize(),
                            // Tab switches cross-fade. No scale: a screen that grows
                            // or shrinks reads as the window moving towards or away
                            // from the viewer, which is the "receding then closing"
                            // effect that looked wrong on back.
                            enterTransition = { fadeIn(tween(200)) },
                            exitTransition = { fadeOut(tween(140)) },
                            // Nothing at all on the way back. On a pop the screen
                            // underneath was never gone, so animating it in makes it
                            // look like a fresh screen arriving. It should simply be
                            // uncovered as the one above slides away.
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { fadeOut(tween(140)) },
                        ) {
                            composable<HomeRoute> {
                                HomeScreen(
                                    onCollectionClick = {
                                        navController.navigate(CollectionRoute(it))
                                    },
                                    onOpenProfile = drawerState::open,
                                    contentPadding = screenPadding,
                                )
                            }
                            composable<SearchRoute> {
                                SearchScreen(
                                    onCollectionClick = {
                                        navController.navigate(CollectionRoute(it))
                                    },
                                    onOpenProfile = drawerState::open,
                                    contentPadding = screenPadding,
                                )
                            }
                            composable<LibraryRoute> {
                                LibraryScreen(
                                    onCollectionClick = {
                                        navController.navigate(CollectionRoute(it))
                                    },
                                    onOpenProfile = drawerState::open,
                                    onCreate = { showCreateSheet = true },
                                    contentPadding = screenPadding,
                                )
                            }
                            // Detail is a push, so it slides in from the trailing
                            // edge and slides back out the same way. The direction
                            // is what tells the user which way back is.
                            composable<CollectionRoute>(
                                enterTransition = {
                                    slideInHorizontally(tween(280, easing = Motion.Emphasized)) { it }
                                },
                                exitTransition = { fadeOut(tween(140)) },
                                // Slide only, no fade. Fading while sliding makes the
                                // screen go translucent mid-motion, so for a moment
                                // both screens are visible through each other, and
                                // that smear is the ugly part of the close.
                                popExitTransition = {
                                    slideOutHorizontally(tween(260, easing = Motion.Accelerate)) { it }
                                },
                            ) { entry ->
                                val route = entry.toRoute<CollectionRoute>()
                                CollectionScreen(
                                    collectionId = route.id,
                                    onBack = navController::popBackStack,
                                    contentPadding = screenPadding,
                                )
                            }
                            composable<SettingsRoute>(
                                enterTransition = {
                                    slideInHorizontally(tween(280, easing = Motion.Emphasized)) { it }
                                },
                                popExitTransition = {
                                    slideOutHorizontally(tween(260, easing = Motion.Accelerate)) { it }
                                },
                            ) {
                                SettingsScreen(
                                    onBack = navController::popBackStack,
                                    contentPadding = screenPadding,
                                )
                            }
                        }

                        // ---- Mini player: above content, below the Create scrim ----
                        AnimatedVisibility(
                            visible = !isSettings,
                            enter = slideInVertically(Motion.gentle()) { it } +
                                fadeIn(Motion.fast()),
                            exit = slideOutVertically(Motion.snappy()) { it } +
                                fadeOut(Motion.fast()),
                            modifier = Modifier.align(Alignment.BottomCenter),
                        ) {
                            Box(
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .padding(bottom = barBlock),
                            ) {
                                // Renders nothing until a track is loaded.
                                MiniPlayer(
                                    state = playbackState,
                                    onClick = ::openPlayer,
                                    onTogglePlayPause = container.playerController::togglePlayPause,
                                    onAddToLibrary = container.playerController::toggleSaved,
                                    onConnectDevice = { },
                                )
                            }
                        }

                        // ---- Create panel and its scrim ----
                        CreateSheet(
                            visible = showCreateSheet,
                            onDismiss = { showCreateSheet = false },
                            onOptionClick = { showCreateSheet = false },
                        )

                        // ---- Floating nav bar, on top of the Create scrim ----
                        // Above the scrim so the Create button stays lit and
                        // tappable while its panel is open. That is what makes the
                        // panel read as belonging to the button.
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
                                createExpanded = showCreateSheet,
                                onSelect = { tapped ->
                                    if (tapped.isAction) {
                                        // Same button opens and closes, matching the
                                        // plus-to-cross rotation.
                                        showCreateSheet = !showCreateSheet
                                    } else {
                                        showCreateSheet = false
                                        // Already standing on that tab's own screen:
                                        // do nothing. Navigating would pop and
                                        // re-push it, destroying and rebuilding the
                                        // screen, which is the reload being seen.
                                        if (!isOnTabRoot(tapped)) {
                                            activeTab = tapped
                                            navController.switchTab(tapped)
                                        }
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
                                onDrag = { delta ->
                                    // Written synchronously: no coroutine per touch
                                    // event, so the page tracks the finger exactly.
                                    playerPos.floatValue =
                                        (playerPos.floatValue + delta / playerHeightPx.floatValue)
                                            .coerceIn(0f, 1f)
                                },
                                onDragStopped = { velocity ->
                                    val dismiss = playerPos.floatValue > DISMISS_FRACTION ||
                                        velocity > DISMISS_VELOCITY_PX
                                    settlePlayer(open = !dismiss, velocityPxPerSec = velocity)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab switching that always lands on the tab itself.
 *
 * Deliberately no `saveState` / `restoreState`. Those restore each tab's nested
 * stack, so opening a playlist under Home and later pressing Home would return to
 * the playlist rather than to Home. Popping to the start destination instead means
 * a tab press is always a reset, and Home means Home.
 */
private fun NavHostController.switchTab(destination: TopLevelDestination) {
    val route: Any = when (destination) {
        TopLevelDestination.Home -> HomeRoute
        TopLevelDestination.Search -> SearchRoute
        TopLevelDestination.Library -> LibraryRoute
        // Create never routes; the caller intercepts it before reaching here.
        TopLevelDestination.Create -> return
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { inclusive = false }
        launchSingleTop = true
    }
}
