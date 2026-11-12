package com.cardgames.core.data.repository

import com.cardgames.core.data.db.SavedGameDao
import com.cardgames.core.data.db.SavedGameEntity
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.repository.SavedGame
import com.cardgames.core.domain.repository.SavedGameRepository
import com.cardgames.core.domain.time.ClockProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RoomSavedGameRepository
    @Inject
    constructor(
        private val dao: SavedGameDao,
        private val clockProvider: ClockProvider,
    ) : SavedGameRepository {
        override fun observeInProgress(): Flow<Set<GameId>> =
            dao.observeGameIds().map { ids ->
                ids.mapNotNull { id -> GameId.entries.firstOrNull { it.name == id } }.toSet()
            }

        override suspend fun load(gameId: GameId): SavedGame? =
            dao.get(gameId.name)?.let {
                SavedGame(
                    gameId = gameId,
                    stateJson = it.stateJson,
                    updatedAtEpochMillis = it.updatedAt,
                )
            }

        override suspend fun save(
            gameId: GameId,
            stateJson: String,
        ) {
            dao.upsert(
                SavedGameEntity(
                    gameId = gameId.name,
                    stateJson = stateJson,
                    updatedAt = clockProvider.epochMillis(),
                ),
            )
        }

        override suspend fun delete(gameId: GameId) {
            dao.delete(gameId.name)
        }
    }
