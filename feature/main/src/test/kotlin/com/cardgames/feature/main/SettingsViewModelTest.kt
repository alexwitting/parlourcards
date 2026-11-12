package com.cardgames.feature.main

import app.cash.turbine.test
import com.cardgames.core.domain.ginrummy.GinMatchRules
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.AnimationSpeed
import com.cardgames.core.domain.repository.HintLimit
import com.cardgames.core.domain.repository.Settings
import com.cardgames.core.domain.repository.SettingsRepository
import com.cardgames.core.testing.FakeSettingsRepository
import com.cardgames.core.testing.FakeThemeRepository
import com.cardgames.core.testing.MainDispatcherExtension
import com.cardgames.core.testing.testThemePack
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SettingsViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val settings = FakeSettingsRepository()
    private val themes = FakeThemeRepository()

    private fun viewModel() = SettingsViewModel(settings, themes)

    @Test
    fun `state mirrors stored settings and installed packs`() =
        runTest {
            themes.packs = listOf(testThemePack("parlour"))
            settings.setDrawCount(3)

            val vm = viewModel()

            assertEquals(3, vm.state.value.settings.drawCount)
            assertEquals(
                listOf("parlour"),
                vm.state.value.themePacks
                    .map { it.id },
            )
        }

    @Test
    fun `the default pack shows as selected before any explicit choice`() =
        runTest {
            themes.packs = listOf(testThemePack("another_pack"), testThemePack("parlour"))

            val vm = viewModel()

            // themeId is still null — the picker must mark the effective pack (the default).
            assertEquals(null, vm.state.value.settings.themeId)
            assertEquals("parlour", vm.state.value.selectedThemeId)

            vm.onIntent(SettingsIntent.SelectTheme("another_pack"))
            assertEquals("another_pack", vm.state.value.selectedThemeId)
        }

    @Test
    fun `intents persist and flow back into the state`() =
        runTest {
            val vm = viewModel()

            vm.onIntent(SettingsIntent.SetDrawCount(3))
            vm.onIntent(SettingsIntent.SelectTheme("royal_navy"))
            vm.onIntent(SettingsIntent.SetSoundEnabled(false))
            vm.onIntent(SettingsIntent.SetHapticsEnabled(false))
            vm.onIntent(SettingsIntent.SetUseSolverForWinnableDeals(false))
            vm.onIntent(SettingsIntent.SetGinDifficulty(GinDifficulty.HARD))
            vm.onIntent(SettingsIntent.SetAnimationSpeed(AnimationSpeed.FAST))

            assertEquals(3, vm.state.value.settings.drawCount)
            assertEquals("royal_navy", vm.state.value.settings.themeId)
            assertFalse(vm.state.value.settings.soundEnabled)
            assertFalse(vm.state.value.settings.hapticsEnabled)
            assertFalse(vm.state.value.settings.useSolverForWinnableDeals)
            assertEquals(GinDifficulty.HARD, vm.state.value.settings.gin.difficulty)
            assertEquals(AnimationSpeed.FAST, vm.state.value.settings.animationSpeed)
        }

    @Test
    fun `defaults to medium difficulty before any explicit choice`() =
        runTest {
            val vm = viewModel()

            assertEquals(GinDifficulty.MEDIUM, vm.state.value.settings.gin.difficulty)
        }

    @Test
    fun `defaults to normal animation speed before any explicit choice`() =
        runTest {
            val vm = viewModel()

            assertEquals(AnimationSpeed.NORMAL, vm.state.value.settings.animationSpeed)
        }

    @Test
    fun `motion, hint and Gin match settings persist and flow back`() =
        runTest {
            val vm = viewModel()

            vm.onIntent(SettingsIntent.SetReduceMotion(true))
            vm.onIntent(SettingsIntent.SetSolitaireHints(HintLimit.UNLIMITED))
            vm.onIntent(SettingsIntent.SetGinHints(HintLimit.OFF))
            vm.onIntent(SettingsIntent.SetGinRules(GinMatchRules(target = 250, bigGin = true)))
            vm.onIntent(SettingsIntent.SetGinConfirmKnock(true))
            vm.onIntent(SettingsIntent.SetGinMeldHints(false))

            val saved = vm.state.value.settings
            assertTrue(saved.reduceMotion)
            assertEquals(HintLimit.UNLIMITED, saved.solitaireHints)
            assertEquals(HintLimit.OFF, saved.gin.hints)
            assertEquals(250, saved.gin.rules.target)
            assertTrue(saved.gin.rules.bigGin)
            assertTrue(saved.gin.confirmKnock)
            assertFalse(saved.gin.meldHints)
        }

    @Test
    fun `Gin settings change one option at a time, keeping the others`() =
        runTest {
            val vm = viewModel()
            vm.onIntent(SettingsIntent.SetGinDifficulty(GinDifficulty.EASY))

            vm.onIntent(SettingsIntent.SetGinMeldHints(false))

            assertEquals(GinDifficulty.EASY, vm.state.value.settings.gin.difficulty)
        }

    @Test
    fun `a setting that fails to save says so`() =
        runTest {
            val failing =
                mockk<SettingsRepository> {
                    every { observeSettings() } returns flowOf(Settings())
                    coEvery { setSoundEnabled(any()) } throws IllegalStateException("disk full")
                }
            val vm = SettingsViewModel(failing, themes)

            vm.effects.test {
                vm.onIntent(SettingsIntent.SetSoundEnabled(false))
                assertTrue(awaitItem() is SettingsEffect.ShowSnackbar)
            }
        }
}
