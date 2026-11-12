package com.cardgames.feature.solitaire

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.solitaire.KlondikeEngine
import com.cardgames.core.domain.solitaire.KlondikeMove
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.domain.solitaire.TableauPile
import com.cardgames.core.testing.cards
import com.cardgames.core.testing.ui.TestDevices
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The table stays put while a card flies across it ("Keeping the table still" in
 * docs/DESIGN_SYSTEM.md): a card glides between piles of different sizes, and its in-flight size
 * must never resize the pile rows — or everything below them jumps.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = TestDevices.PHONE)
class SolitaireTableStillTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** The waste's ace (drawn 1.5× a column) heading for its foundation (1.25×), beside an empty column. */
    private val before =
        KlondikeState(
            seed = 1L,
            drawCount = 1,
            tableau = listOf(TableauPile(emptyList(), 0)) + cards("K♥ Q♣ J♦ 10♠ 9♥ 8♣").map { TableauPile(listOf(it), 0) },
            foundations = Suit.entries.associateWith { 0 },
            stock = cards("5♦ 6♦"),
            waste = cards("A♠"),
        )

    @Test
    fun `the empty column's outline holds still while a card flies to the foundations`() {
        var state by mutableStateOf(SolitaireState(game = before, loaded = true))
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            AppTheme {
                SolitaireContent(
                    state = state,
                    onIntent = {},
                    onNavigateBack = {},
                    onOpenSettings = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        val resting = emptyColumnTop()

        val after = KlondikeEngine.apply(before, KlondikeMove.WasteToFoundation)
        state = SolitaireState(game = after, loaded = true, pending = PendingMotion(id = 1L, kind = MotionKind.MOVE, from = before))
        val flight = CardGamesMotion.Flight.millis.toLong()
        val tops =
            List(STEPS) {
                composeTestRule.mainClock.advanceTimeBy(flight / STEPS)
                emptyColumnTop()
            }

        tops.forEach { top -> assertEquals(resting.value, top.value, 0.01f) }
    }

    private fun emptyColumnTop(): Dp = composeTestRule.onNodeWithText("K").getBoundsInRoot().top

    private companion object {
        const val SETTLE_MILLIS = 3_000L
        const val STEPS = 6
    }
}
