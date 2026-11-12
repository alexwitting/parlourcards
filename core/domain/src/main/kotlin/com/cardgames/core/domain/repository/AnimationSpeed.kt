package com.cardgames.core.domain.repository

/**
 * How fast in-game motion plays — card glides, flips, the deal, result popups, and the AI's
 * pacing beats. [durationScale] multiplies every tuned duration: the timings were signed off at
 * [NORMAL], so the other speeds are proportional stretches of that, never separately tuned.
 */
enum class AnimationSpeed(
    val durationScale: Float,
) {
    SLOW(durationScale = 1.5f),
    NORMAL(durationScale = 1f),
    FAST(durationScale = 0.5f),
}
