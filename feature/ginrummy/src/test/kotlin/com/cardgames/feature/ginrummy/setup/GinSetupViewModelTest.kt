package com.cardgames.feature.ginrummy.setup

import app.cash.turbine.test
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.GinMatchRules
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.GameResult
import com.cardgames.core.domain.repository.GinMatchRecord
import com.cardgames.core.domain.repository.GinOptions
import com.cardgames.core.domain.repository.HintLimit
import com.cardgames.core.domain.repository.Settings
import com.cardgames.core.domain.repository.SettingsRepository
import com.cardgames.core.testing.FakeSavedGameRepository
import com.cardgames.core.testing.FakeSettingsRepository
import com.cardgames.core.testing.FakeStatsRepository
import com.cardgames.core.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class GinSetupViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val settings = FakeSettingsRepository()
    private val savedGames = FakeSavedGameRepository()
    private val stats = FakeStatsRepository()

    private fun viewModel(settingsRepository: SettingsRepository = settings) = GinSetupViewModel(settingsRepository, savedGames, stats)

    @Test
    fun `setup starts from the saved Gin options`() {
        val saved = GinOptions(difficulty = GinDifficulty.HARD, rules = GinMatchRules(target = 250), hints = HintLimit.OFF)
        settings.settings = settings.settings.copy(gin = saved)

        assertEquals(saved, viewModel().state.value.options)
    }

    @Test
    fun `each choice changes the options but nothing is saved until Deal`() {
        val vm = viewModel()
        val before = settings.settings.gin

        vm.onIntent(GinSetupIntent.PickOpponent(GinDifficulty.EASY))
        vm.onIntent(GinSetupIntent.PlayTo(50))
        vm.onIntent(GinSetupIntent.GinBonus(false))
        vm.onIntent(GinSetupIntent.BigGin(true))
        vm.onIntent(GinSetupIntent.Hints(false))

        val options = vm.state.value.options
        assertEquals(GinDifficulty.EASY, options.difficulty)
        assertEquals(50, options.rules.target)
        assertFalse(options.rules.ginBonus)
        assertTrue(options.rules.bigGin)
        assertEquals(HintLimit.OFF, options.hints)
        assertEquals(before, settings.settings.gin)

        vm.onIntent(GinSetupIntent.Hints(true))
        assertEquals(HintLimit.THREE, vm.state.value.options.hints)
    }

    @Test
    fun `Deal saves the choices, then starts the match`() =
        runTest {
            val vm = viewModel()
            vm.onIntent(GinSetupIntent.PickOpponent(GinDifficulty.HARD))

            vm.effects.test {
                vm.onIntent(GinSetupIntent.Deal)
                assertEquals(GinSetupEffect.StartMatch, awaitItem())
            }
            assertEquals(GinDifficulty.HARD, settings.settings.gin.difficulty)
        }

    @Test
    fun `a save that fails says so instead of starting the match`() =
        runTest {
            val failing =
                mockk<SettingsRepository> {
                    every { observeSettings() } returns flowOf(Settings())
                    coEvery { setGinOptions(any()) } throws IllegalStateException("disk full")
                }
            val vm = viewModel(failing)

            vm.effects.test {
                vm.onIntent(GinSetupIntent.Deal)
                assertTrue(awaitItem() is GinSetupEffect.ShowSnackbar)
            }
        }

    @Test
    fun `a saved Gin match is flagged so dealing asks first`() {
        val vm = viewModel()
        assertFalse(vm.state.value.matchInProgress)

        runBlocking { savedGames.save(GameId.GIN_RUMMY, "{}") }

        assertTrue(vm.state.value.matchInProgress)
    }

    @Test
    fun `each rival shows the player's record against them`() {
        runBlocking {
            stats.record(ginResult(GinDifficulty.MEDIUM, won = true))
            stats.record(ginResult(GinDifficulty.MEDIUM, won = false))
            stats.record(ginResult(GinDifficulty.HARD, won = false))
        }

        val records = viewModel().state.value.records

        assertEquals(1, records.getValue(GinDifficulty.MEDIUM).wins)
        assertEquals(1, records.getValue(GinDifficulty.MEDIUM).losses)
        assertEquals(1, records.getValue(GinDifficulty.HARD).losses)
        assertEquals(0, records.getValue(GinDifficulty.EASY).wins + records.getValue(GinDifficulty.EASY).losses)
    }

    private fun ginResult(
        rival: GinDifficulty,
        won: Boolean,
    ) = GameResult(
        GameId.GIN_RUMMY,
        won = won,
        durationSeconds = 600,
        score = if (won) 100 else 40,
        gin =
            GinMatchRecord(
                opponent = rival,
                opponentScore = if (won) 40 else 100,
                gins = 0,
                undercuts = 0,
                bestHand = 0,
                knocks = 0,
                knockDeadwood = 0,
                hands = 3,
            ),
    )
}
