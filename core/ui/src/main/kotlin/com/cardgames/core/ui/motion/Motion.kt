package com.cardgames.core.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * One animation's timing: [millis] at normal animation speed, along [easing]. Use [tween] or
 * [scaledMillis] to apply it — both honor the player's animation-speed setting, so a caller never
 * scales a duration by hand.
 */
@Immutable
data class MotionSpec(
    val millis: Int,
    val easing: Easing = FastOutSlowInEasing,
) {
    /** The same timing starting [delay] later — for choreography built from other specs. */
    operator fun plus(delay: Int): MotionSpec = copy(millis = millis + delay)
}

/** [MotionSpec.millis] at the player's animation speed. */
@Composable
@ReadOnlyComposable
fun MotionSpec.scaledMillis(): Int = scaledMillis(millis)

/** A tween for this spec at the player's animation speed, optionally after [delay] (also a spec, so it scales too). */
@Composable
@ReadOnlyComposable
fun <T> MotionSpec.tween(delay: MotionSpec? = null): TweenSpec<T> =
    tween(durationMillis = scaledMillis(), delayMillis = delay?.scaledMillis() ?: 0, easing = easing)

/**
 * A spring by role, at normal animation speed. Apply it with [spring], which honors the
 * animation-speed setting (via [scaledStiffness]) and Reduce motion (no overshoot).
 */
@Immutable
data class SpringMotion(
    val dampingRatio: Float,
    val stiffness: Float,
)

/** A spring for this role at the player's animation speed; critically damped under Reduce motion. */
@Composable
@ReadOnlyComposable
fun <T> SpringMotion.spring(visibilityThreshold: T? = null): SpringSpec<T> =
    spring(
        dampingRatio = if (LocalReduceMotion.current) Spring.DampingRatioNoBouncy else dampingRatio,
        stiffness = scaledStiffness(stiffness),
        visibilityThreshold = visibilityThreshold,
    )

/** Parlour's standard curve: leaves at once, lands softly. */
val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** For things arriving: fastest at the start. */
val DecelerateEasing: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

/**
 * The app's shared motion vocabulary — every duration, curve and spring both games use, by role.
 * Each game's own numbers are in its motion-tokens file (`GinMotionTokens`, `SolitaireMotionTokens`)
 * and when they apply within a move in its choreography file.
 */
object CardGamesMotion {
    // Parlour durations by role.

    /** Taps answered on the spot: a ring appearing, a button press. */
    val Instant = MotionSpec(90, StandardEasing)

    /** Small arrivals: a status line, a tag, a card rising when selected. */
    val Quick = MotionSpec(160, DecelerateEasing)

    /** Panels and sheets changing. */
    val Base = MotionSpec(240, StandardEasing)

    /** A card flying between places. */
    val Flight = MotionSpec(320, StandardEasing)

    /** One screen replacing another (the navigation fade and slide). */
    val Screen = MotionSpec(260, StandardEasing)

    /** A win or a GIN! moment. */
    val CelebrateLong = MotionSpec(600, StandardEasing)

    /** What a flight becomes under Reduce motion: a cross-fade in place. */
    val ReducedFade = MotionSpec(120, StandardEasing)

    /** A card turning over. */
    val Flip = MotionSpec(280)

    /** A card arriving on the table for the first time. */
    val CardAppear = MotionSpec(200)

    /** A quiet status line fading in or out; panel modes cross-fading. */
    val StatusFade = MotionSpec(220)

    /** A finished hand fading off the table at a new deal; a popup leaving. */
    val PopupExit = MotionSpec(180)

    /** Picking a card up. */
    val Lift = SpringMotion(dampingRatio = 0.56f, stiffness = 320f)

    /** A card arriving in a slot, with a 2–4dp overshoot. */
    val Land = SpringMotion(dampingRatio = 0.52f, stiffness = 240f)

    /** The hand re-flowing, a fan spreading. */
    val Settle = SpringMotion(dampingRatio = 0.52f, stiffness = 180f)

    /** A title or button popping in (GIN!, Complete coming on). */
    val Pop = SpringMotion(dampingRatio = 0.35f, stiffness = 200f)

    /** Gaps between things in a sequence shared by both games, in ms at normal speed. */
    object Stagger {
        /** Cards turning over one after another (a knock's reveal, the Solved kings). */
        const val REVEAL = 90L
    }

    /** Hold this long to select a card (shorter than the system's 400ms long press). Not scaled. */
    const val LONG_PRESS_MILLIS = 220L

    /** An illegal move shakes the card [TableMotion.shakeDistance], this many times, over [Shake]. */
    const val SHAKE_CYCLES = 2
    val Shake = MotionSpec(240, StandardEasing)
}
