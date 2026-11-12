package com.cardgames.feature.stats

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Renders the screen from a representative state and checks its content shows.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StatsContentSmokeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersEmptyStateForEveryGame() {
        composeTestRule.setContent {
            AppTheme { StatsContent(state = StatsState(), onNavigateBack = {}, onOpenGame = {}) }
        }

        composeTestRule.onNodeWithText("Stats").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gin Rummy").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Solitaire").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun rendersEachGamesFigures() {
        composeTestRule.setContent {
            AppTheme { GameStatsContent(GameId.GIN_RUMMY, SampleStatsState, onNavigateBack = {}) }
        }

        composeTestRule.onNodeWithText("50%").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 of 4 · best streak 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("+47").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Milo").performScrollTo().assertIsDisplayed()
    }
}
