package com.cardgames.feature.ginrummy

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Deck
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.HandOutcome
import com.cardgames.core.domain.ginrummy.HandResult
import com.cardgames.core.ui.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Renders the screen from a representative state and checks its content shows. Gin's UI is the most complex of the
 * four screens (drag-and-drop, movable-content glide cache), so this is deliberately a static
 * render check, not an interaction test.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GinContentSmokeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersFreshlyDealtHand() {
        composeTestRule.setContent {
            AppTheme {
                GinContent(
                    state = GinUiState(game = GinEngine.newMatch(seed = 1L)),
                    onIntent = {},
                    onNavigateBack = {},
                    onOpenSettings = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeTestRule.onNodeWithText("You").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hand 1 · First to 100").assertIsDisplayed()
    }

    @Test
    fun showsCardsLaidOffOntoTheKnockersMeldsAtHandEnd() {
        // The opponent knocked; the player laid the seven of spades off onto its melds. It's in
        // neither hand any more — only in laidOff — and must still be on the table.
        val laidOff = Card(Suit.SPADES, Rank.SEVEN)
        val rest = Deck.standard52 - laidOff
        val game =
            GinEngine.newMatch(seed = 1L).copy(
                playerHand = rest.take(9),
                opponentHand = rest.drop(9).take(10),
                discardPile = rest.drop(19).take(1),
                stock = rest.drop(20),
                phase =
                    GinPhase.HandOver(
                        HandResult(HandOutcome.KNOCK, GinPlayer.OPPONENT, pointsScored = 12, knockerDeadwood = 4, defenderDeadwood = 16),
                    ),
                moveCount = 20,
                laidOff = mapOf(GinPlayer.OPPONENT to listOf(laidOff)),
            )
        composeTestRule.setContent {
            AppTheme {
                GinContent(
                    state = GinUiState(game = game),
                    onIntent = {},
                    onNavigateBack = {},
                    onOpenSettings = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        assertTrue(composeTestRule.onAllNodesWithContentDescription(laidOff.toString()).fetchSemanticsNodes().isNotEmpty())
    }
}
