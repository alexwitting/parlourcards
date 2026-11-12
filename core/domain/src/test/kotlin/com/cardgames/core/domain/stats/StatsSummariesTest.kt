package com.cardgames.core.domain.stats

import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.GameResult
import com.cardgames.core.domain.repository.GinMatchRecord
import com.cardgames.core.domain.repository.RecordedResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsSummariesTest {
    private fun gin(
        won: Boolean,
        rival: GinDifficulty = GinDifficulty.MEDIUM,
        gins: Int = 0,
        undercuts: Int = 0,
        bestHand: Int = 0,
        // Times the player went out, and the deadwood they held across those knocks.
        knocks: Pair<Int, Int> = 0 to 0,
    ) = GameResult(
        GameId.GIN_RUMMY,
        won = won,
        durationSeconds = 600,
        score = if (won) 100 else 50,
        gin =
            GinMatchRecord(
                opponent = rival,
                opponentScore = if (won) 50 else 100,
                gins = gins,
                undercuts = undercuts,
                bestHand = bestHand,
                knocks = knocks.first,
                knockDeadwood = knocks.second,
                hands = 5,
            ),
    )

    private fun deal(
        seconds: Int?,
        drawCount: Int = 3,
        moves: Int = 100,
    ) = GameResult(GameId.KLONDIKE, won = seconds != null, durationSeconds = seconds ?: 900, moves = moves, drawCount = drawCount)

    @Test
    fun `Gin's summary adds up the matches, its tiles and the best streak`() {
        val results =
            listOf(
                gin(won = true, gins = 1, bestHand = 30, knocks = 2 to 9),
                gin(won = true, undercuts = 1, bestHand = 47, knocks = 1 to 3),
                gin(won = false, rival = GinDifficulty.HARD),
                gin(won = true, rival = GinDifficulty.EASY),
            )

        val summary = results.ginSummary()

        assertEquals(4, summary.matches)
        assertEquals(3, summary.wins)
        assertEquals(75, summary.winPercent)
        assertEquals(2, summary.bestStreak)
        assertEquals(1, summary.gins)
        assertEquals(1, summary.undercuts)
        assertEquals(47, summary.bestHand)
        assertEquals(4.0, summary.averageKnockDeadwood)
        assertEquals(Record(wins = 2, losses = 0), summary.byOpponent[GinDifficulty.MEDIUM])
        assertEquals(Record(wins = 0, losses = 1), summary.byOpponent[GinDifficulty.HARD])
        assertEquals(3, summary.byOpponent.getValue(GinDifficulty.MEDIUM).played + summary.byOpponent.getValue(GinDifficulty.HARD).played)
    }

    @Test
    fun `before any match, Gin's figures are empty rather than dividing by zero`() {
        val summary = emptyList<GameResult>().ginSummary()

        assertEquals(0, summary.winPercent)
        assertNull(summary.averageKnockDeadwood)
        assertEquals(0, summary.bestHand)
    }

    @Test
    fun `Solitaire's summary times only solved deals and splits by draw mode`() {
        val results =
            listOf(
                deal(seconds = 300, drawCount = 1, moves = 120),
                deal(seconds = null, drawCount = 3),
                deal(seconds = 200, drawCount = 3, moves = 90),
                deal(seconds = 400, drawCount = 3, moves = 110),
            )

        val summary = results.solitaireSummary()

        assertEquals(4, summary.deals)
        assertEquals(3, summary.solved)
        assertEquals(75, summary.solvedPercent)
        assertEquals(2, summary.currentStreak)
        assertEquals(2, summary.bestStreak)
        assertEquals(200, summary.bestSeconds)
        assertEquals(300, summary.averageSeconds)
        assertEquals(90, summary.fewestMoves)
        assertEquals(DrawModeSummary(solved = 1, played = 1, bestSeconds = 300, averageSeconds = 300), summary.byDrawMode[1])
        assertEquals(DrawModeSummary(solved = 2, played = 3, bestSeconds = 200, averageSeconds = 300), summary.byDrawMode[3])
    }

    @Test
    fun `a draw mode never played shows no times`() {
        val summary = listOf(deal(seconds = 100, drawCount = 3)).solitaireSummary()

        assertEquals(DrawModeSummary(solved = 0, played = 0, bestSeconds = null, averageSeconds = null), summary.byDrawMode[1])
    }

    @Test
    fun `this week counts games per day, Monday first, ignoring other weeks`() {
        // Thursday 2026-10-29.
        val today = LocalDate.of(2026, 10, 29)

        fun at(day: LocalDate) = RecordedResult(deal(seconds = 100), day.atTime(20, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
        val results =
            listOf(
                at(LocalDate.of(2026, 10, 26)),
                at(LocalDate.of(2026, 10, 29)),
                at(LocalDate.of(2026, 10, 29)),
                at(LocalDate.of(2026, 11, 1)),
                at(LocalDate.of(2026, 10, 25)),
            )

        assertEquals(listOf(1, 0, 0, 2, 0, 0, 1), results.perDayThisWeek(today, ZoneOffset.UTC))
    }
}
