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

/*
 * There is no NowPlaying route on purpose.
 *
 * The player is a full-screen overlay owned by SpotKofiApp, not a destination. As
 * a destination the screen behind it is not composed, so dragging the player down
 * revealed empty background instead of the tab it was opened from.
 */

@Serializable
data object SettingsRoute

/**
 * The four items in the bottom bar.
 *
 * [Create] is deliberately in this list even though it is not a route: in the
 * real app it sits in the bar as a peer of the three tabs but opens a modal
 * sheet. Modelling it as a nav destination would put a bogus entry on the back
 * stack, so the bar reports the tap and the caller decides what to do.
 */
enum class TopLevelDestination(val labelRes: Int) {
    Home(R.string.nav_home),
    Search(R.string.nav_search),
    Library(R.string.nav_library),
    Create(R.string.nav_create),
    ;

    val isAction: Boolean get() = this == Create
}
