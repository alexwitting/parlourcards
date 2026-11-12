package com.cardgames.feature.ginrummy

import com.cardgames.feature.ginrummy.motion.GinMotionTokens
import kotlin.random.Random

/**
 * The opponent's turn beats, in ms at normal speed (the ViewModel scales them by the player's Card
 * speed): a think of [thinkMinMillis]–[thinkMaxMillis] before a draw or upcard decision, [stepMillis]
 * before a discard, [layoffPauseMillis] before layoffs resolve. Injected, so tests pass [Instant]
 * (real delays never resolve under a test dispatcher nothing advances).
 */
data class GinPacing(
    val thinkMinMillis: Long,
    val thinkMaxMillis: Long,
    val stepMillis: Long,
    val layoffPauseMillis: Long,
) {
    /** A think's length, anywhere in the range. */
    fun thinkMillis(random: Random = Random): Long = random.nextLong(thinkMinMillis, thinkMaxMillis + 1)

    companion object {
        /** The shipped pacing. */
        val Normal =
            GinPacing(
                thinkMinMillis = GinMotionTokens.THINK_MIN,
                thinkMaxMillis = GinMotionTokens.THINK_MAX,
                stepMillis = GinMotionTokens.STEP,
                layoffPauseMillis = GinMotionTokens.LAYOFF_PAUSE,
            )

        /** No beats at all — for tests. */
        val Instant = GinPacing(thinkMinMillis = 0, thinkMaxMillis = 0, stepMillis = 0, layoffPauseMillis = 0)
    }
}
