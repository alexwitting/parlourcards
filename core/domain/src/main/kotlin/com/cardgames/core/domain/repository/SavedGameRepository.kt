package com.cardgames.core.domain.repository

import com.cardgames.core.domain.game.GameId
import kotlinx.coroutines.flow.Flow

/**
 * One saved-game row: the game's own serialized snapshot, opaque to this layer. What goes inside
 * the JSON is each game's business (e.g. [com.cardgames.core.domain.solitaire.KlondikeSnapshot]).
 */
data class SavedGame(
    val gameId: GameId,
    val stateJson: String,
    val updatedAtEpochMillis: Long,
)

/** In-progress game snapshots, one per game at most. Implemented over Room in `:core:data`. */
interface SavedGameRepository {
    /** Which games currently have a resumable save — drives the home screen's Continue affordance. */
    fun observeInProgress(): Flow<Set<GameId>>

    suspend fun load(gameId: GameId): SavedGame?

    suspend fun save(
        gameId: GameId,
        stateJson: String,
    )

    suspend fun delete(gameId: GameId)
}
