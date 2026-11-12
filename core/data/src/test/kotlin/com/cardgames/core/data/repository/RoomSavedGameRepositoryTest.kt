package com.cardgames.core.data.repository

import com.cardgames.core.data.db.SavedGameDao
import com.cardgames.core.data.db.SavedGameEntity
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.testing.FakeClockProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RoomSavedGameRepositoryTest {
    private val dao = mockk<SavedGameDao>()
    private val repository = RoomSavedGameRepository(dao, FakeClockProvider())

    @Test
    fun `unknown game ids in the table are ignored`() =
        runTest {
            every { dao.observeGameIds() } returns flowOf(listOf("KLONDIKE", "CANASTA_FROM_THE_FUTURE"))

            assertEquals(setOf(GameId.KLONDIKE), repository.observeInProgress().first())
        }

    @Test
    fun `load maps the row back to the domain model`() =
        runTest {
            coEvery { dao.get("GIN_RUMMY") } returns
                SavedGameEntity(gameId = "GIN_RUMMY", stateJson = "{}", updatedAt = 123L)

            val saved = repository.load(GameId.GIN_RUMMY)

            assertEquals(GameId.GIN_RUMMY, saved!!.gameId)
            assertEquals("{}", saved.stateJson)
            assertEquals(123L, saved.updatedAtEpochMillis)
        }
}
