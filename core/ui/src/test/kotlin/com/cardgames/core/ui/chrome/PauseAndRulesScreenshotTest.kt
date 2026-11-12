package com.cardgames.core.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.ui.cards.CardTableTheme
import com.cardgames.core.ui.cards.SampleCardTableThemes
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.rules.HowToPlayContent
import com.cardgames.core.ui.theme.AppTheme
import com.cardgames.core.ui.theme.CardGamesTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot baselines of the pause menu over the felt, and How to play's content on its cream sheet. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class PauseAndRulesScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun pause(
        theme: CardTableTheme,
        qualifiers: String? = null,
    ) {
        qualifiers?.let { RuntimeEnvironment.setQualifiers(it) }
        composeTestRule.setContent {
            AppTheme(cardTableTheme = theme) {
                Box(modifier = Modifier.fillMaxSize()) {
                    TableBackground(modifier = Modifier.matchParentSize())
                    PauseMenu(
                        overline = "Hand 5 · vs Rosa",
                        actions =
                            listOf(
                                PauseAction("Restart this hand", ParlourIcons.Restart) {},
                                PauseAction("Redo", ParlourIcons.Redo, enabled = false) {},
                                PauseAction("How to play", ParlourIcons.Book) {},
                                PauseAction("Settings", ParlourIcons.Sliders) {},
                            ),
                        leaveLabel = "Leave match",
                        onResume = {},
                        onLeave = {},
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun rules(game: GameId) {
        composeTestRule.setContent {
            AppTheme(cardTableTheme = SampleCardTableThemes.Parlour) {
                Box(modifier = Modifier.fillMaxSize().background(CardGamesTheme.colors.panel)) {
                    HowToPlayContent(initial = game, onClose = {})
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun pauseMenu_parlour() = pause(SampleCardTableThemes.Parlour)

    @Test
    fun pauseMenu_midnight() = pause(SampleCardTableThemes.Midnight)

    @Test
    fun pauseMenu_tablet() = pause(SampleCardTableThemes.Parlour, TABLET)

    @Test
    fun howToPlay_gin() = rules(GameId.GIN_RUMMY)

    @Test
    fun howToPlay_solitaire() = rules(GameId.KLONDIKE)

    private companion object {
        /** The tablet's size (`TestDevices.TABLET`, which this module can't depend on). */
        const val TABLET = "w800dp-h1280dp-xhdpi"
    }
}
