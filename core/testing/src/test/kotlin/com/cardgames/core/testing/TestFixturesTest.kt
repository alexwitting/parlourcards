package com.cardgames.core.testing

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** The fixtures other modules' tests are built on: wrong here would be wrong everywhere. */
class TestFixturesTest {
    @Test
    fun `cards read as written, with symbols or letters`() {
        assertEquals(
            listOf(Card(Suit.CLUBS, Rank.ACE), Card(Suit.HEARTS, Rank.TEN), Card(Suit.SPADES, Rank.KING), Card(Suit.DIAMONDS, Rank.QUEEN)),
            cards("A♣ 10♥︎  KS qd"),
        )
    }

    @Test
    fun `text that isn't a card is refused`() {
        assertThrows<IllegalArgumentException> { card("11♣") }
        assertThrows<IllegalArgumentException> { card("KX") }
        assertThrows<IllegalArgumentException> { card("K") }
    }

    @Test
    fun `a position deals every card exactly once`() {
        val hand = cards("A♣ 2♣ 3♣ 5♦ 5♥ 5♠ 9♥ 10♥ J♥ 2♦ K♠")
        val position = ginPosition(hand, upcard = card("9♠"))

        val all = position.playerHand + position.opponentHand + position.discardPile + position.stock
        assertEquals(52, all.toSet().size)
        assertEquals(52, all.size)
        assertEquals(hand, position.playerHand)
        assertEquals(GinState.HAND_SIZE, position.opponentHand.size)
        assertEquals(listOf(card("9♠")), position.discardPile)
        assertEquals(GinPhase.AwaitingDiscard(GinPlayer.PLAYER), position.phase)
        assertEquals(GinPlayer.OPPONENT, position.dealer)
    }

    @Test
    fun `the hand can be the opponent's, and ten cards wait to draw`() {
        val hand = cards("A♣ 2♣ 3♣ 5♦ 5♥ 5♠ 9♥ 10♥ J♥ 2♦")

        val position = ginPosition(hand, seat = GinPlayer.OPPONENT)

        assertEquals(hand, position.opponentHand)
        assertEquals(GinPhase.AwaitingDraw(GinPlayer.OPPONENT), position.phase)
        assertEquals(GinPlayer.PLAYER, position.dealer)
    }

    @Test
    fun `a card placed twice is refused`() {
        assertThrows<IllegalArgumentException> { ginPosition(cards("A♣ 2♣"), upcard = card("A♣")) }
    }
}
