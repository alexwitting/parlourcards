package com.cardgames.core.domain.game

import com.cardgames.core.domain.repository.GameResult
import com.cardgames.core.domain.repository.SavedGameRepository
import com.cardgames.core.testing.FakeSavedGameRepository
import com.cardgames.core.testing.FakeStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GamePersistenceTest {
    private val savedGames = FakeSavedGameRepository()
    private val stats = FakeStatsRepository()

    private fun persistence(decode: (String) -> String? = { it.removePrefix("saved:") }) =
        GamePersistence<String>(
            GameId.KLONDIKE,
            savedGames,
            stats,
            encode = { "saved:$it" },
            decode = decode,
            codec = Dispatchers.Unconfined,
        )

    @Test
    fun `a saved snapshot loads back through the codec`() =
        runTest {
            val p = persistence()

            p.save("position")

            assertEquals("saved:position", savedGames.load(GameId.KLONDIKE)?.stateJson)
            assertEquals("position", p.load())
        }

    @Test
    fun `no save, or one the codec rejects, loads as nothing`() =
        runTest {
            assertNull(persistence().load())

            savedGames.save(GameId.KLONDIKE, "corrupt")
            assertNull(persistence(decode = { null }).load())
        }

    @Test
    fun `clearing removes the save`() =
        runTest {
            val p = persistence()
            p.save("position")

            p.clear()

            assertNull(savedGames.load(GameId.KLONDIKE))
        }

    @Test
    fun `saves land in the order they were asked for, even when an earlier one writes slower`() =
        runTest {
            // The older position's write takes longer: without saves going one at a time, the newer
            // one would land first and then be overwritten by the older.
            val slowFirst =
                object : SavedGameRepository by savedGames {
                    override suspend fun save(
                        gameId: GameId,
                        stateJson: String,
                    ) {
                        if (stateJson == "older") delay(1_000)
                        savedGames.save(gameId, stateJson)
                    }
                }
            val p =
                GamePersistence<String>(GameId.KLONDIKE, slowFirst, stats, encode = { it }, decode = { it }, codec = Dispatchers.Unconfined)

            val first = async { p.save("older") }
            val second = async { p.save("newer") }
            first.await()
            second.await()

            assertEquals("newer", savedGames.load(GameId.KLONDIKE)?.stateJson)
        }

    @Test
    fun `a result is recorded against the game with its details`() =
        runTest {
            persistence().recordResult(won = true, durationSeconds = 321, moves = 98, drawCount = 1)

            assertEquals(
                GameResult(GameId.KLONDIKE, won = true, durationSeconds = 321, moves = 98, drawCount = 1),
                stats
                    .observeResults(GameId.KLONDIKE)
                    .first()
                    .single()
                    .result,
            )
        }
}
