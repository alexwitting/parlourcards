package com.cardgames.feature.main

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.testing.ui.ScreenSize
import com.cardgames.core.testing.ui.snapshot
import com.cardgames.core.ui.cards.SampleCardTableThemes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines (Roborazzi): `verifyRoborazziDebug` checks them, `recordRoborazziDebug` re-records.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class MainScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun home_parlour() =
        composeTestRule.snapshot {
            HomeContent(state = SampleHomeState, onIntent = {}, onOpenSettings = {}, onOpenStats = {}, greeting = "Good evening")
        }

    @Test
    fun home_midnight() =
        composeTestRule.snapshot(SampleCardTableThemes.Midnight) {
            HomeContent(state = SampleHomeState, onIntent = {}, onOpenSettings = {}, onOpenStats = {}, greeting = "Good evening")
        }

    @Test
    fun settings_parlour() =
        composeTestRule.snapshot {
            SettingsContent(
                state = SettingsState(),
                onIntent = {},
                onNavigateBack = {},
                onOpenGame = {},
                snackbarHostState = SnackbarHostState(),
            )
        }

    @Test
    fun settings_midnight() =
        composeTestRule.snapshot(SampleCardTableThemes.Midnight) {
            SettingsContent(
                state = SettingsState(),
                onIntent = {},
                onNavigateBack = {},
                onOpenGame = {},
                snackbarHostState = SnackbarHostState(),
            )
        }

    @Test
    fun ginSettings() =
        composeTestRule.snapshot {
            GameSettingsContent(
                GameId.GIN_RUMMY,
                SettingsState(),
                onIntent = {},
                onNavigateBack = {},
                snackbarHostState = SnackbarHostState(),
            )
        }

    @Test
    fun solitaireSettings() =
        composeTestRule.snapshot {
            GameSettingsContent(
                GameId.KLONDIKE,
                SettingsState(),
                onIntent = {},
                onNavigateBack = {},
                snackbarHostState = SnackbarHostState(),
            )
        }

    @Test
    fun home_tablet() =
        composeTestRule.snapshot(size = ScreenSize.TABLET) {
            HomeContent(state = SampleHomeState, onIntent = {}, onOpenSettings = {}, onOpenStats = {}, greeting = "Good evening")
        }

    @Test
    fun homeNoMatch_tablet() =
        composeTestRule.snapshot(size = ScreenSize.TABLET) {
            HomeContent(state = HomeState(), onIntent = {}, onOpenSettings = {}, onOpenStats = {}, greeting = "Good evening")
        }

    @Test
    fun settings_tablet() =
        composeTestRule.snapshot(size = ScreenSize.TABLET) {
            SettingsContent(
                state = SettingsState(),
                onIntent = {},
                onNavigateBack = {},
                onOpenGame = {},
                snackbarHostState = SnackbarHostState(),
            )
        }

    @Test
    fun solitaireSettings_tablet() =
        composeTestRule.snapshot(size = ScreenSize.TABLET) {
            GameSettingsContent(
                GameId.KLONDIKE,
                SettingsState(),
                onIntent = {},
                onNavigateBack = {},
                snackbarHostState = SnackbarHostState(),
            )
        }

    // The tablet in each of the other theme packs.

    @Test
    fun home_tablet_violetPop() =
        composeTestRule.snapshot(SampleCardTableThemes.VioletPop, size = ScreenSize.TABLET) {
            HomeContent(state = SampleHomeState, onIntent = {}, onOpenSettings = {}, onOpenStats = {}, greeting = "Good evening")
        }

    @Test
    fun settings_tablet_violetPop() =
        composeTestRule.snapshot(SampleCardTableThemes.VioletPop, size = ScreenSize.TABLET) {
            SettingsContent(
                state = SettingsState(),
                onIntent = {},
                onNavigateBack = {},
                onOpenGame = {},
                snackbarHostState = SnackbarHostState(),
            )
        }

    @Test
    fun home_tablet_lagoon() =
        composeTestRule.snapshot(SampleCardTableThemes.Lagoon, size = ScreenSize.TABLET) {
            HomeContent(state = SampleHomeState, onIntent = {}, onOpenSettings = {}, onOpenStats = {}, greeting = "Good evening")
        }

    @Test
    fun settings_tablet_lagoon() =
        composeTestRule.snapshot(SampleCardTableThemes.Lagoon, size = ScreenSize.TABLET) {
            SettingsContent(
                state = SettingsState(),
                onIntent = {},
                onNavigateBack = {},
                onOpenGame = {},
                snackbarHostState = SnackbarHostState(),
            )
        }

    @Test
    fun home_tablet_midnight() =
        composeTestRule.snapshot(SampleCardTableThemes.Midnight, size = ScreenSize.TABLET) {
            HomeContent(state = SampleHomeState, onIntent = {}, onOpenSettings = {}, onOpenStats = {}, greeting = "Good evening")
        }

    @Test
    fun settings_tablet_midnight() =
        composeTestRule.snapshot(SampleCardTableThemes.Midnight, size = ScreenSize.TABLET) {
            SettingsContent(
                state = SettingsState(),
                onIntent = {},
                onNavigateBack = {},
                onOpenGame = {},
                snackbarHostState = SnackbarHostState(),
            )
        }
}
