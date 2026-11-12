package com.cardgames.core.domain.game

import com.cardgames.core.domain.time.ClockProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class PlayTimerTest {
    private class SteppedClock : ClockProvider {
        var millis = 0L

        override fun today(): LocalDate = now().toLocalDate()

        override fun now(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)

        override fun epochMillis(): Long = millis
    }

    private val clock = SteppedClock()
    private val timer = PlayTimer(clock)

    @Test
    fun `time only accrues while running`() {
        timer.start()
        clock.millis += 5_000
        timer.stop()
        clock.millis += 60_000

        assertEquals(5, timer.elapsedSeconds)
    }

    @Test
    fun `stopped segments add up across restarts`() {
        timer.start()
        clock.millis += 3_000
        timer.stop()
        timer.start()
        clock.millis += 4_000

        assertEquals(7, timer.elapsedSeconds)
    }

    @Test
    fun `reset rewinds to a given value and stops`() {
        timer.start()
        clock.millis += 9_000

        timer.reset(elapsedSeconds = 2)
        clock.millis += 10_000

        assertEquals(2, timer.elapsedSeconds)
    }

    @Test
    fun `starting an already running timer keeps its original start`() {
        timer.start()
        clock.millis += 2_000
        timer.start()
        clock.millis += 2_000

        assertEquals(4, timer.elapsedSeconds)
    }

    @Test
    fun `the on-screen ticker refreshes once a second until stopped`() =
        runTest {
            var ticks = 0
            timer.start(backgroundScope) { ticks++ }

            advanceTimeBy(3_500)
            assertEquals(3, ticks)

            timer.stop()
            advanceTimeBy(5_000)
            assertEquals(3, ticks)
        }

    @Test
    fun `starting the ticker while already running doesn't restart the clock or add a second ticker`() =
        runTest {
            var ticks = 0
            timer.start(backgroundScope) { ticks++ }
            clock.millis += 2_000

            timer.start(backgroundScope) { ticks++ }
            advanceTimeBy(1_500)

            assertEquals(1, ticks)
            assertEquals(2, timer.elapsedSeconds)
        }
}
