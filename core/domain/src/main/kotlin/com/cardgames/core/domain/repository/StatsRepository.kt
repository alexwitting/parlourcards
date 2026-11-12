package com.cardgames.core.domain.repository

import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import kotlinx.coroutines.flow.Flow

/** The outcome of one finished game, recorded at game end. */
data class GameResult(
    val gameId: GameId,
    val won: Boolean,
    val durationSeconds: Int,
    /** Game-specific points (gin rummy); null where the game has no score (solitaire). */
    val score: Int? = null,
    /** Moves played (solitaire); null where not tracked. */
    val moves: Int? = null,
    /** Solitaire's draw mode (1 or 3); null for other games. */
    val drawCount: Int? = null,
    /** A Gin match's details; null for other games. */
    val gin: GinMatchRecord? = null,
)

/** What a finished (or abandoned) Gin match adds to the stats beyond won/lost and the score. */
data class GinMatchRecord(
    val opponent: GinDifficulty,
    val opponentScore: Int,
    val gins: Int,
    val undercuts: Int,
    /** The player's biggest single hand. */
    val bestHand: Int,
    /** Hands the player went out on (knocks and gins), and the deadwood they held across them. */
    val knocks: Int,
    val knockDeadwood: Int,
    val hands: Int,
)

/** A [GameResult] with when it ended. */
data class RecordedResult(
    val result: GameResult,
    val endedAtEpochMillis: Long,
)

/** Aggregates over every recorded [GameResult] of one game, ordered by when they ended. */
data class GameStats(
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0,
    /** Fastest win, seconds; null until the first win. */
    val bestDurationSeconds: Int? = null,
    /** Highest winning score; null until the first win or where the game has no score. */
    val bestScore: Int? = null,
    /** Fewest moves in a win; null until the first win or where moves aren't tracked. */
    val fewestMoves: Int? = null,
    /** Consecutive wins ending at the most recent game (0 if it was a loss). */
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
) {
    val winPercent: Int
        get() = if (gamesPlayed == 0) 0 else gamesWon * PERCENT / gamesPlayed

    private companion object {
        const val PERCENT = 100
    }
}

/** Game results + aggregates. Implemented over Room in `:core:data`. */
interface StatsRepository {
    /** Persists [result], stamping it with the current time. */
    suspend fun record(result: GameResult)

    fun observeStats(gameId: GameId): Flow<GameStats>

    /** Every recorded result of [gameId], oldest first — for the stats pages' lists, weeks and records. */
    fun observeResults(gameId: GameId): Flow<List<RecordedResult>>
}

/** The aggregates over [this] — one game's results, oldest first (the streak walk relies on it). */
fun List<GameResult>.toGameStats(): GameStats {
    var currentStreak = 0
    var bestStreak = 0
    forEach { result ->
        currentStreak = if (result.won) currentStreak + 1 else 0
        if (currentStreak > bestStreak) bestStreak = currentStreak
    }
    val wins = filter { it.won }
    return GameStats(
        gamesPlayed = size,
        gamesWon = wins.size,
        bestDurationSeconds = wins.minOfOrNull { it.durationSeconds },
        bestScore = wins.mapNotNull { it.score }.maxOrNull(),
        fewestMoves = wins.mapNotNull { it.moves }.minOrNull(),
        currentStreak = currentStreak,
        bestStreak = bestStreak,
    )
}
