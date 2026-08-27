package com.spotkofi.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Open/closed position of [SpotKofiDrawer], as a continuous 0..1 value.
 *
 * Continuous rather than a boolean because the drawer is draggable: the scrim and
 * the content parallax both read this mid-gesture, so two discrete states would
 * not be enough to render a half-open drawer.
 */
@Stable
class SpotKofiDrawerState internal constructor(
    internal val progress: Animatable<Float, AnimationVector1D>,
    private val scope: CoroutineScope,
) {
    /** True whenever any part of the drawer is on screen. */
    val isVisible: Boolean get() = progress.value > 0.001f

    fun open() {
        scope.launch { progress.animateTo(1f, Motion.gentle()) }
    }

    /** Snappier than opening: dismissal should get out of the way. */
    fun close() {
        scope.launch { progress.animateTo(0f, Motion.snappy()) }
    }

    /** Called from the drag callback, which cannot suspend. */
    internal fun drag(deltaFraction: Float) {
        scope.launch {
            progress.snapTo((progress.value + deltaFraction).coerceIn(0f, 1f))
        }
    }

    /** Decides where to land when the finger lifts. */
    internal fun settle(velocity: Float) {
        scope.launch {
            // Velocity wins over position, so a fast flick closes the drawer even
            // when it is still most of the way open.
            val opening = when {
                velocity < -FLING_THRESHOLD -> false
                velocity > FLING_THRESHOLD -> true
                else -> progress.value > 0.5f
            }
            progress.animateTo(
                targetValue = if (opening) 1f else 0f,
                animationSpec = if (opening) Motion.gentle() else Motion.snappy(),
            )
        }
    }

    private companion object {
        const val FLING_THRESHOLD = 400f
    }
}

@Composable
fun rememberSpotKofiDrawerState(): SpotKofiDrawerState {
    val scope = rememberCoroutineScope()
    return remember(scope) { SpotKofiDrawerState(Animatable(0f), scope) }
}

/**
 * Navigation drawer with hand-rolled motion.
 *
 * Replaces Material 3's `ModalNavigationDrawer`, which animates on a fixed tween
 * and closes abruptly on a back press. Owning the animation buys three things
 * that fix the feel:
 *
 *  - back press animates out on the app's own spring instead of snapping shut,
 *  - the panel is draggable and settles on velocity, so a flick closes it,
 *  - the content behind recedes slightly, giving the drawer real depth.
 */
@Composable
fun SpotKofiDrawer(
    state: SpotKofiDrawerState,
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    drawerWidth: Dp = 330.dp,
    gesturesEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val widthPx = with(LocalDensity.current) { drawerWidth.toPx() }

    // Animating out on back rather than snapping is the whole point of this being
    // hand-rolled.
    BackHandler(enabled = state.isVisible) { state.close() }

    // Hoisted out of the conditional below so it is created unconditionally.
    val dragState = rememberDraggableState { delta -> state.drag(delta / widthPx) }

    Box(modifier = modifier.fillMaxSize()) {
        // ---- Main content, pushed and shrunk as the drawer comes in ----
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = state.progress.value
                    translationX = p * widthPx * 0.12f
                    val s = 1f - p * 0.06f
                    scaleX = s
                    scaleY = s
                    // Scaling from the trailing edge keeps the content visually
                    // attached to the drawer instead of shrinking towards centre.
                    transformOrigin = TransformOrigin(1f, 0.5f)
                },
        ) {
            content()
        }

        if (state.isVisible) {
            // ---- Scrim ----
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = state.progress.value }
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = state::close,
                    ),
            )

            // ---- Panel ----
            Box(
                modifier = Modifier
                    .width(drawerWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = -(1f - state.progress.value) * widthPx
                    }
                    .background(SpotKofiTheme.colors.elevated)
                    .then(
                        if (gesturesEnabled) {
                            Modifier.draggable(
                                state = dragState,
                                orientation = Orientation.Horizontal,
                                onDragStopped = { velocity -> state.settle(velocity) },
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                drawerContent()
            }
        }
    }
}
