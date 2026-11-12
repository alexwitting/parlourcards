package com.cardgames.feature.main

import app.cash.turbine.test
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinSnapshot
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.GameResult
import com.cardgames.core.domain.solitaire.KlondikeEngine
import com.cardgames.core.domain.solitaire.KlondikeSnapshot
import com.cardgames.core.testing.FakeSavedGameRepository
import com.cardgames.core.testing.FakeStatsRepository
import com.cardgames.core.testing.MainDispatcherExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class HomeViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val savedGames = FakeSavedGameRepository()
    private val stats = FakeStatsRepository()

    private fun viewModel() = HomeViewModel(savedGames, stats)

    @Test
    fun `the most recent save is the one to continue, live`() =
        runTest {
            savedGames.save(
                GameId.KLONDIKE,
                KlondikeSnapshot(game = KlondikeEngine.deal(seed = 1L, drawCount = 3), elapsedSeconds = 75).toJson(),
            )
            val vm = viewModel()

            val solitaire = vm.state.value.continueGame as ContinueSummary.Solitaire
            assertEquals(3, solitaire.drawCount)
            assertEquals(75, solitaire.elapsedSeconds)

            // A Gin match saved since takes the Continue card.
            savedGames.save(GameId.GIN_RUMMY, GinSnapshot(game = GinEngine.newMatch(seed = 1L), opponent = GinDifficulty.HARD).toJson())
            val gin = vm.state.value.continueGame as ContinueSummary.Gin
            assertEquals(GinDifficulty.HARD, gin.opponent)
            assertEquals(1, gin.handNumber)

            // Both finished: nothing to continue, without a reload.
            savedGames.delete(GameId.GIN_RUMMY)
            savedGames.delete(GameId.KLONDIKE)
            assertNull(vm.state.value.continueGame)
        }

    @Test
    fun `gin's row opens match setup, solitaire's its table`() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.onIntent(HomeIntent.PlayGame(GameId.GIN_RUMMY))
                assertEquals(HomeEffect.NavigateToGinSetup, awaitItem())
                vm.onIntent(HomeIntent.PlayGame(GameId.KLONDIKE))
                assertEquals(HomeEffect.NavigateToGame(GameId.KLONDIKE), awaitItem())
                vm.onIntent(HomeIntent.Resume(GameId.GIN_RUMMY))
                assertEquals(HomeEffect.NavigateToGame(GameId.GIN_RUMMY), awaitItem())
            }
        }

    @Test
    fun `stats reach the state per game`() =
        runTest {
            stats.record(GameResult(GameId.KLONDIKE, won = true, durationSeconds = 90))
            stats.record(GameResult(GameId.KLONDIKE, won = false, durationSeconds = 200))
            val vm = viewModel()

            val klondike =
                vm.state.value.stats
                    .getValue(GameId.KLONDIKE)
            assertEquals(2, klondike.gamesPlayed)
            assertEquals(1, klondike.gamesWon)
            assertEquals(90, klondike.bestDurationSeconds)
            assertEquals(
                0,
                vm.state.value.stats
                    .getValue(GameId.GIN_RUMMY)
                    .gamesPlayed,
            )
        }
}
