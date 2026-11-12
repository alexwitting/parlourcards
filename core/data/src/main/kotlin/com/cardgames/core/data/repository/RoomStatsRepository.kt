package com.cardgames.core.data.repository

import com.cardgames.core.data.db.GameResultDao
import com.cardgames.core.data.db.GameResultEntity
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.GameResult
import com.cardgames.core.domain.repository.GameStats
import com.cardgames.core.domain.repository.GinMatchRecord
import com.cardgames.core.domain.repository.RecordedResult
import com.cardgames.core.domain.repository.StatsRepository
import com.cardgames.core.domain.repository.toGameStats
import com.cardgames.core.domain.time.ClockProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RoomStatsRepository
    @Inject
    constructor(
        private val dao: GameResultDao,
        private val clockProvider: ClockProvider,
    ) : StatsRepository {
        override suspend fun record(result: GameResult) {
            val gin = result.gin
            dao.insert(
                GameResultEntity(
                    gameId = result.gameId.name,
                    won = result.won,
                    score = result.score,
                    durationSec = result.durationSeconds,
                    endedAt = clockProvider.epochMillis(),
                    moves = result.moves,
                    drawCount = result.drawCount,
                    ginOpponent = gin?.opponent?.name,
                    ginOpponentScore = gin?.opponentScore,
                    ginGins = gin?.gins,
                    ginUndercuts = gin?.undercuts,
                    ginBestHand = gin?.bestHand,
                    ginKnocks = gin?.knocks,
                    ginKnockDeadwood = gin?.knockDeadwood,
                    ginHands = gin?.hands,
                ),
            )
        }

        override fun observeStats(gameId: GameId): Flow<GameStats> =
            observeResults(gameId).map { results -> results.map { it.result }.toGameStats() }

        override fun observeResults(gameId: GameId): Flow<List<RecordedResult>> =
            dao.observeResults(gameId.name).map { rows -> rows.mapNotNull { it.toRecorded() } }
    }

/** A stored row back as a result; null for a row naming a game this build doesn't know. */
private fun GameResultEntity.toRecorded(): RecordedResult? {
    val game = runCatching { GameId.valueOf(gameId) }.getOrNull() ?: return null
    val opponent = ginOpponent?.let { name -> runCatching { GinDifficulty.valueOf(name) }.getOrNull() }
    val gin =
        opponent?.let {
            GinMatchRecord(
                opponent = it,
                opponentScore = ginOpponentScore ?: 0,
                gins = ginGins ?: 0,
                undercuts = ginUndercuts ?: 0,
                bestHand = ginBestHand ?: 0,
                knocks = ginKnocks ?: 0,
                knockDeadwood = ginKnockDeadwood ?: 0,
                hands = ginHands ?: 0,
            )
        }
    return RecordedResult(
        GameResult(game, won, durationSec, score = score, moves = moves, drawCount = drawCount, gin = gin),
        endedAtEpochMillis = endedAt,
    )
}
