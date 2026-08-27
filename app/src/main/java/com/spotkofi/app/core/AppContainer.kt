package com.spotkofi.app.core

import androidx.compose.runtime.staticCompositionLocalOf
import com.spotkofi.app.data.repository.FakeMusicRepository
import com.spotkofi.app.data.repository.MusicRepository
import com.spotkofi.app.player.FakePlayerController
import com.spotkofi.app.player.PlayerController
import kotlinx.coroutines.CoroutineScope

/**
 * Manual dependency container.
 *
 * Phase 1 has exactly two dependencies, so a hand-written container is smaller
 * and clearer than Hilt, and it avoids pinning a KSP version. Hilt arrives in
 * Phase 3 alongside Supabase, when there is real graph complexity to justify it.
 *
 * This class is the *only* place that decides which implementation the app runs
 * against. Swapping [FakeMusicRepository] for a real catalog client is a one
 * line change here.
 */
class AppContainer(applicationScope: CoroutineScope) {

    val musicRepository: MusicRepository = FakeMusicRepository()

    val playerController: PlayerController = FakePlayerController(applicationScope)
}

/**
 * Fails loudly rather than silently handing back a default, so a missing
 * provider shows up immediately instead of as confusing empty screens.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided. Wrap the tree in SpotKofiApp.")
}
