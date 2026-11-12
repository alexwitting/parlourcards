package com.cardgames.feature.stats

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
class StatsScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun stats_parlour() = composeTestRule.snapshot { StatsContent(state = SampleStatsState, onNavigateBack = {}, onOpenGame = {}) }

    @Test
    fun stats_midnight() =
        composeTestRule.snapshot(
            SampleCardTableThemes.Midnight,
        ) { StatsContent(state = SampleStatsState, onNavigateBack = {}, onOpenGame = {}) }

    @Test
    fun ginStats() = composeTestRule.snapshot { GameStatsContent(GameId.GIN_RUMMY, SampleStatsState, onNavigateBack = {}) }

    @Test
    fun solitaireStats() = composeTestRule.snapshot { GameStatsContent(GameId.KLONDIKE, SampleStatsState, onNavigateBack = {}) }

    @Test
    fun stats_tablet() =
        composeTestRule.snapshot(size = ScreenSize.TABLET) { StatsContent(state = SampleStatsState, onNavigateBack = {}, onOpenGame = {}) }

    @Test
    fun solitaireStats_tablet() =
        composeTestRule.snapshot(size = ScreenSize.TABLET) { GameStatsContent(GameId.KLONDIKE, SampleStatsState, onNavigateBack = {}) }

    // The tablet in each of the other theme packs.

    @Test
    fun stats_tablet_violetPop() =
        composeTestRule.snapshot(SampleCardTableThemes.VioletPop, size = ScreenSize.TABLET) {
            StatsContent(state = SampleStatsState, onNavigateBack = {}, onOpenGame = {})
        }

    @Test
    fun stats_tablet_lagoon() =
        composeTestRule.snapshot(SampleCardTableThemes.Lagoon, size = ScreenSize.TABLET) {
            StatsContent(state = SampleStatsState, onNavigateBack = {}, onOpenGame = {})
        }

    @Test
    fun stats_tablet_midnight() =
        composeTestRule.snapshot(SampleCardTableThemes.Midnight, size = ScreenSize.TABLET) {
            StatsContent(state = SampleStatsState, onNavigateBack = {}, onOpenGame = {})
        }
}
