package com.cardgames.feature.stats

import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.GameResult
import com.cardgames.core.domain.repository.GinMatchRecord
import com.cardgames.core.domain.stats.Record
import com.cardgames.core.testing.FakeClockProvider
import com.cardgames.core.testing.FakeStatsRepository
import com.cardgames.core.testing.MainDispatcherExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class StatsViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val stats = FakeStatsRepository()
    private val clock = FakeClockProvider().apply { setNow(LocalDateTime.of(2026, 10, 29, 12, 0)) }

    private fun viewModel() = StatsViewModel(stats, clock)

    private fun at(day: LocalDate): Long =
        day
            .atTime(9, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun gin(
        won: Boolean,
        rival: GinDifficulty,
        knockDeadwood: Int = 0,
    ) = GameResult(
        GameId.GIN_RUMMY,
        won = won,
        durationSeconds = 600,
        score = 100,
        gin = GinMatchRecord(rival, 60, gins = 1, undercuts = 0, bestHand = 30, knocks = 2, knockDeadwood = knockDeadwood, hands = 5),
    )

    @Test
    fun `nothing played shows an empty week and dashes`() =
        runTest {
            val vm = viewModel()

            assertEquals(List(7) { 0 }, vm.state.value.week)
            assertEquals(0, vm.state.value.gin.matches)
            assertNull(vm.state.value.solitaire.bestSeconds)
        }

    @Test
    fun `this week counts both games by day, Monday first`() =
        runTest {
            // Thursday the 29th: Monday is the 26th.
            stats.now = at(LocalDate.of(2026, 10, 26))
            stats.record(GameResult(GameId.KLONDIKE, won = true, durationSeconds = 200, drawCount = 1))
            stats.now = at(LocalDate.of(2026, 10, 29))
            stats.record(gin(won = true, rival = GinDifficulty.MEDIUM))
            stats.record(GameResult(GameId.KLONDIKE, won = false, durationSeconds = 90, drawCount = 3))
            // Last week doesn't count.
            stats.now = at(LocalDate.of(2026, 10, 25))
            stats.record(GameResult(GameId.KLONDIKE, won = true, durationSeconds = 150, drawCount = 1))

            assertEquals(listOf(1, 0, 0, 2, 0, 0, 0), viewModel().state.value.week)
        }

    @Test
    fun `gin's page sums the matches and keeps a record per rival`() =
        runTest {
            stats.record(gin(won = true, rival = GinDifficulty.MEDIUM, knockDeadwood = 6))
            stats.record(gin(won = false, rival = GinDifficulty.MEDIUM, knockDeadwood = 3))
            stats.record(gin(won = true, rival = GinDifficulty.EASY, knockDeadwood = 0))

            val gin = viewModel().state.value.gin
            assertEquals(3, gin.matches)
            assertEquals(66, gin.winPercent)
            assertEquals(3, gin.gins)
            assertEquals(1.5, gin.averageKnockDeadwood)
            assertEquals(Record(wins = 1, losses = 1), gin.byOpponent.getValue(GinDifficulty.MEDIUM))
            assertEquals(Record(wins = 0, losses = 0), gin.byOpponent.getValue(GinDifficulty.HARD))
        }

    @Test
    fun `solitaire's page splits by draw mode and lists recent wins, best marked`() =
        runTest {
            stats.record(GameResult(GameId.KLONDIKE, won = true, durationSeconds = 300, moves = 120, drawCount = 1))
            stats.record(GameResult(GameId.KLONDIKE, won = true, durationSeconds = 250, moves = 140, drawCount = 3))
            stats.record(GameResult(GameId.KLONDIKE, won = false, durationSeconds = 90, drawCount = 3))

            val state = viewModel().state.value
            assertEquals(2, state.solitaire.solved)
            assertEquals(120, state.solitaire.fewestMoves)
            assertEquals(
                1,
                state.solitaire.byDrawMode
                    .getValue(3)
                    .solved,
            )
            assertEquals(
                2,
                state.solitaire.byDrawMode
                    .getValue(3)
                    .played,
            )
            assertEquals(listOf(250, 300), state.recentWins.map { it.seconds })
            assertTrue(state.recentWins.first().best)
        }
}
