package com.cardgames.core.domain.game

import com.cardgames.core.domain.time.ClockProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Wall-clock play timer over [ClockProvider], shared by every game. Finished run segments fold
 * into an accumulator, so elapsed time only accrues between a `start` and [stop]. The optional
 * ticker exists purely to refresh a visible timer once a second — elapsed time is always derived
 * from the clock, never counted. Solitaire shows it and pauses it while the screen is hidden;
 * gin times the match silently with the ticker-free [start].
 */
class PlayTimer(
    private val clockProvider: ClockProvider,
) {
    private var accumulatedMillis = 0L
    private var runningSince: Long? = null
    private var tickerJob: Job? = null

    val elapsedSeconds: Int
        get() = (elapsedMillis() / MILLIS_PER_SECOND).toInt()

    /** Starts (or keeps) running, without a ticker — for a timer nobody watches tick. */
    fun start() {
        if (runningSince == null) runningSince = clockProvider.epochMillis()
    }

    /** Starts running and calls [onTick] once a second (in [scope]) until [stop] — for an on-screen timer. */
    fun start(
        scope: CoroutineScope,
        onTick: () -> Unit,
    ) {
        if (runningSince != null) return
        runningSince = clockProvider.epochMillis()
        tickerJob =
            scope.launch {
                while (true) {
                    delay(TICK_MILLIS)
                    onTick()
                }
            }
    }

    fun stop() {
        accumulatedMillis = elapsedMillis()
        runningSince = null
        tickerJob?.cancel()
        tickerJob = null
    }

    /** Stops and rewinds to [elapsedSeconds] (zero for a fresh game; a saved value on restore). */
    fun reset(elapsedSeconds: Int = 0) {
        stop()
        accumulatedMillis = elapsedSeconds * MILLIS_PER_SECOND
    }

    private fun elapsedMillis(): Long = accumulatedMillis + (runningSince?.let { clockProvider.epochMillis() - it } ?: 0L)

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
