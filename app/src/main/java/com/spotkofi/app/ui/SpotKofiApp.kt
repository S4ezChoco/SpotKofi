package com.spotkofi.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
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
import com.spotkofi.app.ui.navigation.CollectionRoute
import com.spotkofi.app.ui.navigation.HomeRoute
import com.spotkofi.app.ui.navigation.LibraryRoute
import com.spotkofi.app.ui.navigation.NowPlayingRoute
import com.spotkofi.app.ui.navigation.SearchRoute
import com.spotkofi.app.ui.navigation.SettingsRoute
import com.spotkofi.app.ui.navigation.SpotKofiBottomBar
import com.spotkofi.app.ui.navigation.TopLevelDestination
import com.spotkofi.app.ui.theme.SpotKofiTheme
import kotlinx.coroutines.launch

/**
 * Root of the composable tree: provides the dependency container and theme, owns
 * the nav graph, the profile drawer, and the persistent bottom chrome.
 *
 * Inset handling: the Scaffold keeps its default system-bar insets, so
 * `innerPadding` already carries the status bar on top and the combined mini
 * player + tab bar height on the bottom. Screens pass it straight into their
 * `LazyColumn` as `contentPadding`, which is what lets content scroll under the
 * bars while still coming to rest somewhere reachable.
 */
@Composable
fun SpotKofiApp(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        SpotKofiTheme {
            val navController = rememberNavController()
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            val playbackState by container.playerController.state.collectAsStateWithLifecycle()

            var showCreateSheet by remember { mutableStateOf(false) }

            val backStackEntry by navController.currentBackStackEntryAsState()
            val destination = backStackEntry?.destination

            // Now Playing and Settings are takeovers: they hide the tab bar and
            // the mini player they were opened from.
            val isFullScreen = destination?.hasRoute(NowPlayingRoute::class) == true ||
                destination?.hasRoute(SettingsRoute::class) == true

            val selectedTab = when {
                destination?.hasRoute(SearchRoute::class) == true -> TopLevelDestination.Search
                destination?.hasRoute(LibraryRoute::class) == true -> TopLevelDestination.Library
                else -> TopLevelDestination.Home
            }

            val userName = remember { container.musicRepository.currentUserName() }
            val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
            val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

            ModalNavigationDrawer(
                drawerState = drawerState,
                // Edge swipe would fight the horizontal shelves, so the drawer is
                // avatar-only while a takeover is showing.
                gesturesEnabled = !isFullScreen,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = SpotKofiTheme.colors.elevated,
                        drawerShape = RectangleShape,
                        modifier = Modifier.width(330.dp),
                    ) {
                        ProfileDrawer(
                            userName = userName,
                            friends = container.musicRepository.friendActivity(),
                            conversations = container.musicRepository.conversations(),
                            onSettings = {
                                closeDrawer()
                                navController.navigate(SettingsRoute)
                            },
                            onDismiss = closeDrawer,
                        )
                    }
                },
            ) {
                Box(modifier = modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = SpotKofiTheme.colors.base,
                        bottomBar = {
                            AnimatedVisibility(
                                visible = !isFullScreen,
                                enter = slideInVertically { it },
                                exit = slideOutVertically { it },
                            ) {
                                // No inset here on purpose: SpotKofiBottomBar
                                // consumes the navigation-bar inset internally so
                                // its background can reach the screen edge.
                                Column {
                                    // Renders nothing until a track is loaded.
                                    MiniPlayer(
                                        state = playbackState,
                                        onClick = { navController.navigate(NowPlayingRoute) },
                                        onTogglePlayPause = container.playerController::togglePlayPause,
                                        onAddToLibrary = container.playerController::toggleSaved,
                                        onConnectDevice = { },
                                    )
                                    SpotKofiBottomBar(
                                        current = selectedTab,
                                        onSelect = { destinationTapped ->
                                            if (destinationTapped.isAction) {
                                                showCreateSheet = true
                                            } else {
                                                navController.switchTab(destinationTapped)
                                            }
                                        },
                                    )
                                }
                            }
                        },
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = HomeRoute,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            composable<HomeRoute> {
                                HomeScreen(
                                    onCollectionClick = {
                                        navController.navigate(CollectionRoute(it))
                                    },
                                    onOpenProfile = openDrawer,
                                    contentPadding = innerPadding,
                                )
                            }
                            composable<SearchRoute> {
                                SearchScreen(
                                    onCollectionClick = {
                                        navController.navigate(CollectionRoute(it))
                                    },
                                    onOpenProfile = openDrawer,
                                    contentPadding = innerPadding,
                                )
                            }
                            composable<LibraryRoute> {
                                LibraryScreen(
                                    onCollectionClick = {
                                        navController.navigate(CollectionRoute(it))
                                    },
                                    onOpenProfile = openDrawer,
                                    onCreate = { showCreateSheet = true },
                                    contentPadding = innerPadding,
                                )
                            }
                            composable<CollectionRoute> { entry ->
                                val route = entry.toRoute<CollectionRoute>()
                                CollectionScreen(
                                    collectionId = route.id,
                                    onBack = navController::popBackStack,
                                    contentPadding = innerPadding,
                                )
                            }
                            composable<SettingsRoute> {
                                SettingsScreen(
                                    onBack = navController::popBackStack,
                                    contentPadding = innerPadding,
                                )
                            }
                            composable<NowPlayingRoute>(
                                enterTransition = {
                                    slideInVertically(animationSpec = tween(300)) { it }
                                },
                                exitTransition = {
                                    slideOutVertically(animationSpec = tween(300)) { it }
                                },
                            ) {
                                NowPlayingScreen(onCollapse = { navController.popBackStack() })
                            }
                        }
                    }

                    // Above the Scaffold so the sheet's scrim also covers the
                    // bottom chrome, which is how the real sheet behaves.
                    // Always composed: it owns its own enter/exit animation, and a
                    // composable removed from the tree cannot animate out.
                    CreateSheet(
                        visible = showCreateSheet,
                        onDismiss = { showCreateSheet = false },
                        onOptionClick = { showCreateSheet = false },
                    )
                }
            }
        }
    }
}

/**
 * Standard bottom-nav behaviour: at most one entry per tab on the back stack,
 * with each tab remembering its own scroll position.
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
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
