package com.cardgames.feature.ginrummy

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.testing.card
import com.cardgames.core.testing.cards
import com.cardgames.core.testing.ginPosition
import com.cardgames.core.testing.ui.TestDevices
import com.cardgames.core.ui.theme.AppTheme
import com.cardgames.feature.ginrummy.board.GinDragSource
import com.cardgames.feature.ginrummy.board.GinDropTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The table's taps, end to end through the real composables: what each tap sends. The ViewModel
 * tests cover what the intents then do.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = TestDevices.PHONE)
class GinTableInteractionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val sent = mutableListOf<GinIntent>()

    private val king = card("K♠")
    private val upcard = card("9♥")

    private fun table(state: GinUiState) {
        composeTestRule.setContent {
            AppTheme {
                GinContent(
                    state = state,
                    onIntent = { sent += it },
                    onNavigateBack = {},
                    onOpenSettings = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    /** The player to act in [phase], holding [king]: an 11-card hand when discarding, 10 otherwise; [upcard] on the pile. */
    private fun position(phase: GinPhase): GinState {
        val hand = cards("A♣ 2♣ 3♣ 4♣ 5♣ 6♣ 7♣ 8♣ 9♣") + king
        val discarding = phase is GinPhase.AwaitingDiscard
        return ginPosition(if (discarding) hand + card("10♣") else hand, phase = phase, upcard = upcard).copy(moveCount = 5)
    }

    @Test
    fun tappingTheUpcardTakesIt() {
        table(GinUiState(game = position(GinPhase.UpcardDecision(deciding = GinPlayer.PLAYER))))

        composeTestRule.onNodeWithContentDescription(upcard.toString()).performClick()

        assertTrue(GinIntent.TakeUpcard in sent)
    }

    @Test
    fun tappingThePileOnTheDrawTakesItsTopCard() {
        table(GinUiState(game = position(GinPhase.AwaitingDraw(toMove = GinPlayer.PLAYER))))

        composeTestRule.onNodeWithContentDescription(upcard.toString()).performClick()

        assertTrue(GinIntent.DrawFromDiscard in sent)
    }

    @Test
    fun aFirstTapLiftsAHandCardAndASecondThrowsIt() {
        table(GinUiState(game = position(GinPhase.AwaitingDiscard(toMove = GinPlayer.PLAYER))))

        composeTestRule.onNodeWithContentDescription(king.toString()).performClick()
        assertEquals(GinIntent.SelectHandCard(king), sent.last())
    }

    @Test
    fun tappingTheLiftedCardOrThePileThrowsIt() {
        table(GinUiState(game = position(GinPhase.AwaitingDiscard(toMove = GinPlayer.PLAYER)), selectedCard = king))

        composeTestRule.onNodeWithContentDescription(king.toString()).performClick()
        assertEquals(GinIntent.DropCard(GinDragSource.HandCard(king), GinDropTarget.Discard, carried = false), sent.last())

        composeTestRule.onNodeWithContentDescription(upcard.toString()).performClick()
        assertEquals(GinIntent.DropCard(GinDragSource.HandCard(king), GinDropTarget.Discard, carried = false), sent.last())
    }

    @Test
    fun theActionMessageTellsTheLastTwoMovesByOnePlayerAsOneLine() {
        val feed =
            listOf(
                GinFeedLine(GinPlayer.OPPONENT, "drew from the stock"),
                GinFeedLine(GinPlayer.OPPONENT, "discarded", upcard),
            )
        table(GinUiState(game = position(GinPhase.AwaitingDraw(toMove = GinPlayer.PLAYER)), feed = feed))

        composeTestRule
            .onNodeWithText("Rosa drew from the stock · discarded 9♥︎", substring = false)
            .assertIsDisplayed()
    }

    /** The opponent's top line in each bubble's position: the bubble, and the count chip's bounds (null when it stepped aside). */
    private val shown = mutableStateOf<GinUiState?>(null)

    private fun seatAt(state: GinUiState): Pair<Rect, Rect?> {
        if (shown.value == null) {
            shown.value = state
            composeTestRule.setContent {
                AppTheme {
                    GinContent(
                        state = shown.value ?: state,
                        onIntent = {},
                        onNavigateBack = {},
                        onOpenSettings = {},
                        snackbarHostState = SnackbarHostState(),
                    )
                }
            }
        }
        shown.value = state
        composeTestRule.waitForIdle()
        val bubble = composeTestRule.onNode(hasText("Your", substring = true).or(hasText("Thinking"))).fetchSemanticsNode().boundsInRoot
        val count =
            composeTestRule
                .onAllNodesWithText("10 cards")
                .fetchSemanticsNodes()
                .singleOrNull()
                ?.boundsInRoot
        return bubble to count
    }

    private val bubbles =
        mapOf(
            "draw" to GinUiState(game = position(GinPhase.AwaitingDraw(toMove = GinPlayer.PLAYER))),
            "discard" to GinUiState(game = position(GinPhase.AwaitingDiscard(toMove = GinPlayer.PLAYER))),
            "upcard" to GinUiState(game = position(GinPhase.UpcardDecision(deciding = GinPlayer.PLAYER))),
            "thinking" to GinUiState(game = position(GinPhase.AwaitingDraw(toMove = GinPlayer.OPPONENT)), isOpponentThinking = true),
        )

    // Native graphics for these: the default measures text far narrower than a phone, so anything fits.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun atNormalSizeTheCardCountShowsBesideEveryBubble() {
        bubbles.forEach { (name, state) ->
            val (bubble, count) = seatAt(state)
            assertTrue("$name: the count chip is shown", count != null)
            assertTrue("$name: the chip clears the bubble", count!!.right <= bubble.left)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun atLargeTextTheCardCountNeverRunsUnderABubble() {
        RuntimeEnvironment.setFontScale(2f)
        bubbles.forEach { (name, state) ->
            val (bubble, count) = seatAt(state)
            assertTrue("$name: the chip, when shown, clears the bubble", count == null || count.right <= bubble.left)
        }
    }
}
