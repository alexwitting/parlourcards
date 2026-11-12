package com.cardgames.core.testing

import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.repository.GameResult
import com.cardgames.core.domain.repository.GameStats
import com.cardgames.core.domain.repository.RecordedResult
import com.cardgames.core.domain.repository.StatsRepository
import com.cardgames.core.domain.repository.toGameStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [StatsRepository]: keeps raw [recorded] results, each stamped with [now] (advance it to
 * spread results over days).
 */
class FakeStatsRepository : StatsRepository {
    private val recordedFlow = MutableStateFlow<List<RecordedResult>>(emptyList())

    /** The time the next result is stamped with. */
    var now: Long = 0L

    val recorded: List<GameResult>
        get() = recordedFlow.value.map { it.result }

    override suspend fun record(result: GameResult) {
        recordedFlow.value = recordedFlow.value + RecordedResult(result, now)
    }

    override fun observeStats(gameId: GameId): Flow<GameStats> =
        observeResults(gameId).map { results -> results.map { it.result }.toGameStats() }

    override fun observeResults(gameId: GameId): Flow<List<RecordedResult>> =
        recordedFlow.map { results -> results.filter { it.result.gameId == gameId } }
}
