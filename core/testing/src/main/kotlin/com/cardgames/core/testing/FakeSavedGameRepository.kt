package com.cardgames.core.testing

import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.repository.SavedGame
import com.cardgames.core.domain.repository.SavedGameRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [SavedGameRepository]. Seed [saved] directly or via [save]; timestamps count up. */
class FakeSavedGameRepository : SavedGameRepository {
    private val savedFlow = MutableStateFlow<Map<GameId, SavedGame>>(emptyMap())
    private var nextTimestamp = 1L

    var saved: Map<GameId, SavedGame>
        get() = savedFlow.value
        set(value) {
            savedFlow.value = value
        }

    override fun observeInProgress(): Flow<Set<GameId>> = savedFlow.map { it.keys }

    override suspend fun load(gameId: GameId): SavedGame? = savedFlow.value[gameId]

    override suspend fun save(
        gameId: GameId,
        stateJson: String,
    ) {
        savedFlow.value = savedFlow.value +
            (gameId to SavedGame(gameId, stateJson, updatedAtEpochMillis = nextTimestamp++))
    }

    override suspend fun delete(gameId: GameId) {
        savedFlow.value = savedFlow.value - gameId
    }
}
