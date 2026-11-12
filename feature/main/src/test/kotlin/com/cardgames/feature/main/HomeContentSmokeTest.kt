package com.cardgames.feature.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class HomeContentSmokeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersContinueCardAndGames() {
        composeTestRule.setContent {
            AppTheme {
                HomeContent(state = SampleHomeState, onIntent = {}, onOpenSettings = {}, onOpenStats = {}, greeting = "Good evening")
            }
        }

        composeTestRule.onNodeWithText("Parlour Cards").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gin Rummy vs Rosa").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue match").assertIsDisplayed()
        composeTestRule.onNodeWithText("Solitaire").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("You lead Rosa 12 – 10").performScrollTo().assertIsDisplayed()
    }
}
