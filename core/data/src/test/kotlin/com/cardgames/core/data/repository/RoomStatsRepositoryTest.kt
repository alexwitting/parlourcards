package com.cardgames.core.data.repository

import com.cardgames.core.data.db.GameResultDao
import com.cardgames.core.data.db.GameResultEntity
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.GameResult
import com.cardgames.core.domain.repository.GinMatchRecord
import com.cardgames.core.testing.FakeClockProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RoomStatsRepositoryTest {
    private val dao = mockk<GameResultDao>()
    private val clock = FakeClockProvider()
    private val repository = RoomStatsRepository(dao, clock)

    private fun result(
        won: Boolean,
        durationSec: Int = 100,
        score: Int? = null,
        endedAt: Long = 0,
        moves: Int? = null,
    ) = GameResultEntity(
        gameId = GameId.KLONDIKE.name,
        won = won,
        score = score,
        durationSec = durationSec,
        endedAt = endedAt,
        moves = moves,
    )

    @Test
    fun `record stamps the current time onto the row`() =
        runTest {
            val now = LocalDateTime.of(2026, 7, 9, 15, 0, 0)
            clock.setNow(now)
            val inserted = slot<GameResultEntity>()
            coEvery { dao.insert(capture(inserted)) } returns Unit

            repository.record(GameResult(GameId.KLONDIKE, won = true, durationSeconds = 90, moves = 112, drawCount = 3))

            coVerify(exactly = 1) { dao.insert(any()) }
            assertEquals(now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), inserted.captured.endedAt)
            assertEquals("KLONDIKE", inserted.captured.gameId)
            assertEquals(90, inserted.captured.durationSec)
            assertEquals(112, inserted.captured.moves)
            assertEquals(3, inserted.captured.drawCount)
        }

    @Test
    fun `aggregates count wins, streaks and bests from ordered results`() =
        runTest {
            // Chronological: W(120s) W(80s) L W(200s, score 30)
            every { dao.observeResults("KLONDIKE") } returns
                flowOf(
                    listOf(
                        result(won = true, durationSec = 120, moves = 140),
                        result(won = true, durationSec = 80, moves = 95),
                        result(won = false, durationSec = 300, moves = 40),
                        result(won = true, durationSec = 200, score = 30),
                    ),
                )

            val stats = repository.observeStats(GameId.KLONDIKE).first()

            assertEquals(4, stats.gamesPlayed)
            assertEquals(3, stats.gamesWon)
            assertEquals(75, stats.winPercent)
            assertEquals(80, stats.bestDurationSeconds)
            assertEquals(30, stats.bestScore)
            assertEquals(95, stats.fewestMoves)
            assertEquals(1, stats.currentStreak)
            assertEquals(2, stats.bestStreak)
        }

    @Test
    fun `no results means zeroed stats`() =
        runTest {
            every { dao.observeResults("KLONDIKE") } returns flowOf(emptyList())

            val stats = repository.observeStats(GameId.KLONDIKE).first()

            assertEquals(0, stats.gamesPlayed)
            assertEquals(0, stats.winPercent)
            assertFalse(stats.bestDurationSeconds != null)
        }

    @Test
    fun `a gin match's details go into the row and come back out`() =
        runTest {
            val inserted = slot<GameResultEntity>()
            coEvery { dao.insert(capture(inserted)) } returns Unit
            val record =
                GinMatchRecord(
                    GinDifficulty.HARD,
                    opponentScore = 101,
                    gins = 2,
                    undercuts = 1,
                    bestHand = 47,
                    knocks = 4,
                    knockDeadwood = 9,
                    hands = 8,
                )

            repository.record(GameResult(GameId.GIN_RUMMY, won = false, durationSeconds = 900, score = 87, gin = record))
            every { dao.observeResults(GameId.GIN_RUMMY.name) } returns flowOf(listOf(inserted.captured))

            val back =
                repository
                    .observeResults(GameId.GIN_RUMMY)
                    .first()
                    .single()
                    .result
            assertEquals(record, back.gin)
            assertEquals(87, back.score)
        }
}
