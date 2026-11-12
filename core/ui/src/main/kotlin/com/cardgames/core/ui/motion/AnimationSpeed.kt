package com.cardgames.core.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import com.cardgames.core.domain.repository.AnimationSpeed
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The player's chosen [AnimationSpeed], provided once by the app shell from live settings.
 * Defaults to [AnimationSpeed.NORMAL] so previews and smoke tests run at the tuned timings.
 */
val LocalAnimationSpeed = compositionLocalOf { AnimationSpeed.NORMAL }

/**
 * The player's Reduce motion setting, provided by the app shell. When on, card flights become
 * [CardGamesMotion.ReducedFade] cross-fades in place, with no flips, and springs don't overshoot.
 */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * [millis] stretched or shrunk by the current [LocalAnimationSpeed]. Wrap every tuned in-game
 * duration (tweens, entry delays, pacing `delay`s) in this at the point it's used, so the
 * constants themselves stay the NORMAL-speed values they were tuned at.
 */
@Composable
@ReadOnlyComposable
fun scaledMillis(millis: Int): Int = (millis * LocalAnimationSpeed.current.durationScale).roundToInt()

/** [scaledMillis] for `delay`-style `Long` durations. */
@Composable
@ReadOnlyComposable
fun scaledMillis(millis: Long): Long = (millis * LocalAnimationSpeed.current.durationScale).roundToLong()

/**
 * The spring equivalent of [scaledMillis]: a spring's settle time goes as 1/√stiffness, so
 * dividing [stiffness] by the scale squared stretches it by exactly the duration scale.
 */
@Composable
@ReadOnlyComposable
fun scaledStiffness(stiffness: Float): Float {
    val scale = LocalAnimationSpeed.current.durationScale
    return stiffness / (scale * scale)
}
