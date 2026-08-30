package com.spotkofi.app.ui.navigation

import com.spotkofi.app.R
import kotlinx.serialization.Serializable

/*
 * Type-safe navigation routes.
 *
 * Each destination is a @Serializable object or data class rather than a string
 * template, so arguments are checked at compile time and a typo in a route is a
 * build error instead of a crash at runtime.
 */

/*
 * Each tab is a nested graph, not a bare destination.
 *
 * A single flat graph made tab switching unreliable: `popUpTo(startDestination) {
 * saveState = true }` keys the popped stack by the destination that was popped,
 * which is the same route the very next `navigate(... restoreState = true)` asks
 * for. Tapping a tab while inside an album therefore restored the album and the
 * tap looked ignored. With one graph per tab, save/restore is keyed per tab, so
 * each tab keeps its own detail stack and switching always lands on the tab.
 */
@Serializable
data object HomeGraph

@Serializable
data object SearchGraph

@Serializable
data object LibraryGraph

@Serializable
data object HomeRoute

@Serializable
data object SearchRoute

@Serializable
data object LibraryRoute

/** Album, playlist or artist detail, resolved by [id]. */
@Serializable
data class CollectionRoute(val id: String)

/** Charts, moods and genres, new releases and trending playlists. */
@Serializable
data object ExploreRoute

/**
 * One mood, moment or genre page.
 *
 * [params] is the provider's own opaque token for the category and is the only
 * thing that can address the page, so it travels as a route argument. [title] rides
 * along purely so the app bar has something to show before the page responds.
 */
@Serializable
data class MoodRoute(val title: String, val params: String)

/*
 * There is no NowPlaying route on purpose.
 *
 * The player is a full-screen overlay owned by SpotKofiApp, not a destination. As
 * a destination the screen behind it is not composed, so dragging the player down
 * revealed empty background instead of the tab it was opened from.
 */

@Serializable
data object SettingsRoute

/** The three persistent items in the bottom bar. */
enum class TopLevelDestination(val labelRes: Int) {
    Home(R.string.nav_home),
    Search(R.string.nav_search),
    Library(R.string.nav_library),
}
