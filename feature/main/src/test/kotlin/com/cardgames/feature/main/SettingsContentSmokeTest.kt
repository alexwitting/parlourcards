package com.cardgames.feature.main

import androidx.compose.material3.SnackbarHostState
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
 * Renders the screen from a representative state and checks its content shows. The settings column scrolls, so
 * lower sections are scrolled into view before asserting they're displayed.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsContentSmokeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersEverySection() {
        composeTestRule.setContent {
            AppTheme {
                SettingsContent(
                    state = SettingsState(),
                    onIntent = {},
                    onNavigateBack = {},
                    onOpenGame = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gin Rummy").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Rosa · to 100 · meld hints on").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Draw 3 · winnable deals").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Reduce motion").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Version").performScrollTo().assertIsDisplayed()
    }
}
