package com.cardgames.feature.main

import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.GameStats
import com.cardgames.core.domain.stats.Record

data class HomeState(
    val games: List<GameId> = listOf(GameId.GIN_RUMMY, GameId.KLONDIKE),
    /** The most recently played unfinished game, for the Continue card; null when there's none. */
    val continueGame: ContinueSummary? = null,
    /** Per-game aggregates; absent until the first recorded result. */
    val stats: Map<GameId, GameStats> = emptyMap(),
    /** The record against the most recent Gin opponent, for Gin's row; null before the first match. */
    val ginRecord: Pair<GinDifficulty, Record>? = null,
)

/** What the Continue card says about the game waiting to be resumed. */
sealed interface ContinueSummary {
    val gameId: GameId

    data class Gin(
        val handNumber: Int,
        val opponent: GinDifficulty,
        val yourScore: Int,
        val theirScore: Int,
        val target: Int,
        val yourTurn: Boolean,
    ) : ContinueSummary {
        override val gameId = GameId.GIN_RUMMY
    }

    data class Solitaire(
        val drawCount: Int,
        val moves: Int,
        val elapsedSeconds: Int,
        /** Cards already on the foundations, of 52. */
        val cardsHome: Int,
    ) : ContinueSummary {
        override val gameId = GameId.KLONDIKE
    }
}
