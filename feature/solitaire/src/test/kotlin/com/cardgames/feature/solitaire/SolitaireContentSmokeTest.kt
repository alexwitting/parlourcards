package com.cardgames.feature.solitaire

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.solitaire.KlondikeEngine
import com.cardgames.core.domain.solitaire.KlondikeMove
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.domain.solitaire.TableauPile
import com.cardgames.core.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Renders the screen from a representative state and checks its content shows. Two states are covered since the won
 * overlay is a materially different render branch, not just a data change.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SolitaireContentSmokeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersMidGameBoard() {
        val dealt = KlondikeEngine.deal(seed = 1L, drawCount = 1)
        val game = KlondikeEngine.apply(dealt, KlondikeMove.DealFromStock)

        composeTestRule.setContent {
            AppTheme {
                SolitaireContent(
                    state = SolitaireState(game = game, canUndo = true, elapsedSeconds = 205),
                    onIntent = {},
                    onNavigateBack = {},
                    onOpenSettings = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeTestRule.onNodeWithText("MOVES").assertIsDisplayed()
    }

    @Test
    fun rendersWonOverlay() {
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            AppTheme {
                SolitaireContent(
                    state =
                        SolitaireState(
                            game =
                                KlondikeState(
                                    seed = 1L,
                                    drawCount = 1,
                                    tableau = List(7) { TableauPile(emptyList(), 0) },
                                    foundations = Suit.entries.associateWith { KlondikeState.FOUNDATION_MAX },
                                    stock = emptyList(),
                                    waste = emptyList(),
                                    moveCount = 118,
                                ),
                            isWon = true,
                            elapsedSeconds = 245,
                            solved =
                                SolvedSummary(
                                    seconds = 245,
                                    moves = 118,
                                    drawCount = 1,
                                    streak = 4,
                                    bestSeconds = 198,
                                    isNewBest = false,
                                    gamesWon = 41,
                                    gamesPlayed = 63,
                                ),
                        ),
                    onIntent = {},
                    onNavigateBack = {},
                    onOpenSettings = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onNodeWithText("Solved").assertIsDisplayed()
    }
}
