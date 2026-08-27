package com.spotkofi.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion tokens.
 *
 * Every animation in the app pulls its timing from here. That matters more than
 * it sounds: motion is what makes an interface feel like one product rather than
 * a pile of screens, and the fastest way to lose that is to let each call site
 * invent its own duration.
 *
 * Springs are preferred over fixed durations for anything the user drives
 * directly (presses, toggles, drags) because a spring keeps its velocity when
 * interrupted mid-flight, so rapid taps stay fluid instead of restarting.
 * Fixed-duration tweens are kept for things that are not interruptible, like a
 * one-shot entry fade.
 */
object Motion {

    // ---- Durations (ms) ----
    const val Instant = 90
    const val Fast = 150
    const val Medium = 260
    const val Slow = 420

    // ---- Easings ----
    /** Slow out, fast settle. Use for entering elements. */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Symmetric. Use for colour and simple property changes. */
    val Standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    // ---- Springs ----
    /** Tight and quick. Press feedback, small toggles. */
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.86f, stiffness = 900f)

    /** No overshoot. Layout and size changes, where a bounce would look broken. */
    fun <T> smooth(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 320f)

    /** Overshoots on purpose. Selection pops and icon morphs. */
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.5f, stiffness = 460f)

    /** Slow and heavy. Large surfaces such as sheets and drawers. */
    fun <T> gentle(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 170f)

    // ---- Tweens ----
    fun <T> fast(): FiniteAnimationSpec<T> = tween(Fast, easing = Standard)
    fun <T> medium(): FiniteAnimationSpec<T> = tween(Medium, easing = Emphasized)
    fun <T> slow(): FiniteAnimationSpec<T> = tween(Slow, easing = Emphasized)

    // ---- Stagger ----
    /** Delay added per item when a list animates in. */
    const val StaggerStepMs = 24

    /**
     * Cap on the stagger index.
     *
     * Without a cap, item 40 of a long list would wait a second before appearing,
     * which reads as jank rather than choreography.
     */
    const val StaggerMaxSteps = 10
}
