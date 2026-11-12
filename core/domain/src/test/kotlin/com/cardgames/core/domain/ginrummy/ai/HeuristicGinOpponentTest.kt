package com.cardgames.core.domain.ginrummy.ai

import com.cardgames.core.testing.card
import com.cardgames.core.testing.cards
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeuristicGinOpponentTest {
    private val opponent = HeuristicGinOpponent()

    @Test
    fun `discards the card that minimizes resulting deadwood`() {
        // Keeping the run + set intact and discarding the lone king is optimal.
        val hand =
            cards("A♠ 2♠ 3♠ 7♥ 7♦ 7♣ 4♥ K♥")
        val view = OpponentView(ownHand = hand, discardPile = emptyList(), stockRemaining = 31, opponentHandSize = 10)

        assertEquals(card("K♥"), opponent.chooseDiscard(view))
    }

    @Test
    fun `never discards the card it must not discard, even if it's the best choice`() {
        val hand =
            cards("A♠ 2♠ 3♠ 7♥ 7♦ 7♣ 4♥ K♥")
        val forbidden = card("K♥") // the obviously-correct discard
        val view =
            OpponentView(
                ownHand = hand,
                discardPile = emptyList(),
                stockRemaining = 31,
                opponentHandSize = 10,
                mustNotDiscard = forbidden,
            )

        assertFalse(opponent.chooseDiscard(view) == forbidden)
    }

    @Test
    fun `takes the discard pile when it strictly improves the hand`() {
        // Holding 4s5s6s, an offered 7s completes a longer run — an obvious improvement.
        val hand =
            cards("4♠ 5♠ 6♠ K♥ Q♣ J♦ 2♥ 3♣ 4♦ 9♠")
        val view =
            OpponentView(
                ownHand = hand,
                discardPile = cards("7♠"),
                stockRemaining = 31,
                opponentHandSize = 10,
            )

        assertEquals(DrawPreference.DISCARD, opponent.chooseDraw(view))
    }

    @Test
    fun `passes on a discard pile card that would not help`() {
        val hand =
            cards("A♠ 2♠ 3♠ 7♥ 7♦ 7♣ 4♥ 9♣ Q♦ 9♠")
        val view =
            OpponentView(
                ownHand = hand,
                discardPile = cards("K♥"), // no interaction with this hand
                stockRemaining = 31,
                opponentHandSize = 10,
            )

        assertEquals(DrawPreference.STOCK, opponent.chooseDraw(view))
    }

    @Test
    fun `knocks early only with a very strong hand, but relaxes as the stock depletes`() {
        // Run (0) + set (0) + one isolated 6-pip deadwood card, no competing meld options.
        val handWithDeadwoodSix =
            listOf(
                card("2♠"),
                card("3♠"),
                card("4♠"),
                card("9♥"),
                card("9♦"),
                card("9♣"),
                card("6♦"),
                card("K♣"), // discard candidate, irrelevant value
            )
        val earlyView =
            OpponentView(ownHand = handWithDeadwoodSix, discardPile = emptyList(), stockRemaining = 31, opponentHandSize = 10)
        val lateView = earlyView.copy(stockRemaining = 3)

        assertFalse(opponent.shouldKnock(earlyView))
        assertTrue(opponent.shouldKnock(lateView))
    }

    @Test
    fun `stays patient through roughly the first half of the stock, not just the very start`() {
        // The ramp from EARLY_THRESHOLD to the full legal threshold is squared, not linear, so a
        // mid-strength hand (deadwood 5) shouldn't yet qualify at the stock's halfway point even
        // though it would comfortably clear a linear ramp there.
        val handWithDeadwoodFive =
            listOf(
                card("2♠"),
                card("3♠"),
                card("4♠"),
                card("9♥"),
                card("9♦"),
                card("9♣"),
                card("5♦"),
                card("K♣"), // discard candidate, irrelevant value
            )
        val midStockView =
            OpponentView(ownHand = handWithDeadwoodFive, discardPile = emptyList(), stockRemaining = 16, opponentHandSize = 10)

        assertFalse(opponent.shouldKnock(midStockView))
    }

    @Test
    fun `always knocks a gin hand, at any point in the stock`() {
        val ginHand =
            listOf(
                card("A♠"),
                card("2♠"),
                card("3♠"),
                card("4♠"),
                card("7♥"),
                card("7♦"),
                card("7♣"),
                card("9♥"),
                card("9♦"),
                card("9♣"),
                card("K♥"), // discard candidate
            )
        val view = OpponentView(ownHand = ginHand, discardPile = emptyList(), stockRemaining = 31, opponentHandSize = 10)

        assertTrue(opponent.shouldKnock(view))
    }
}
