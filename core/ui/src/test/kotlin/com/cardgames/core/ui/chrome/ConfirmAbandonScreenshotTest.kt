package com.cardgames.core.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardgames.core.ui.cards.CardTableTheme
import com.cardgames.core.ui.cards.SampleCardTableThemes
import com.cardgames.core.ui.theme.AppTheme
import com.cardgames.core.ui.theme.CardGamesTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines of the new-game confirmation. It's a separate dialog window, which a
 * capture of the compose root can't see — so this captures the whole screen instead.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class ConfirmAbandonScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(theme: CardTableTheme) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            AppTheme(cardTableTheme = theme) {
                Box(modifier = Modifier.fillMaxSize().background(CardGamesTheme.colors.table)) {
                    val requestNewGame =
                        rememberConfirmAbandon(needsConfirmation = true, title = "Start a new game?", confirmLabel = "New game") {}
                    LaunchedEffect(Unit) { requestNewGame() }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage()
    }

    @Test
    fun confirmNewGame_parlour() = capture(SampleCardTableThemes.Parlour)

    @Test
    fun confirmNewGame_midnight() = capture(SampleCardTableThemes.Midnight)
}
