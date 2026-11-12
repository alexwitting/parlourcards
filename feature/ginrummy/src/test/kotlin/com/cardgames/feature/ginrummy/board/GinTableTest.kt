package com.cardgames.feature.ginrummy.board

import com.cardgames.core.domain.cards.Deck
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.testing.card
import com.cardgames.core.testing.cards
import com.cardgames.core.testing.ginPosition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GinTableTest {
    @Test
    fun `the hand splits into sorted melds and sorted loose cards`() {
        val layout = playerHandLayout(ginPosition(cards("5♠ 5♥ 5♦ 3♣ 2♣ A♣ K♠ 9♥ 2♦ J♥")), drawn = null)

        assertEquals(listOf(cards("A♣ 2♣ 3♣"), cards("5♦ 5♥ 5♠")), layout.melds)
        assertEquals(cards("2♦ 9♥ J♥ K♠"), layout.loose)
        assertEquals(2 + 9 + 10 + 10, layout.deadwood)
    }

    @Test
    fun `the card just drawn waits at the end of the loose cards`() {
        val drawn = card("2♦")
        val layout = playerHandLayout(ginPosition(cards("A♣ 2♣ 3♣ 5♦ 5♥ 5♠ 9♥ J♥ K♠ 2♦ 4♥")), drawn = drawn)

        assertEquals(cards("4♥ 9♥ J♥ K♠ 2♦"), layout.loose)
    }

    @Test
    fun `the opponent thinks on its turn and nudges the player on theirs`() {
        val draw = GinPhase.AwaitingDraw(GinPlayer.PLAYER)

        assertEquals(Bubble.Thinking, bubbleFor(draw, thinking = true, playerToMove = false))
        assertEquals(Bubble.Says("Your draw!"), bubbleFor(draw, thinking = false, playerToMove = true))
        assertEquals(
            Bubble.Says("Your move!"),
            bubbleFor(GinPhase.AwaitingDiscard(GinPlayer.PLAYER), thinking = false, playerToMove = true),
        )
        assertNull(bubbleFor(draw, thinking = false, playerToMove = false))
    }

    @Test
    fun `each discard has its own fixed tilt within six degrees`() {
        // Fixed values, the same on every launch (and every screenshot run).
        assertEquals(-6f, discardTilt(card("A♣")))
        assertEquals(-1f, discardTilt(card("2♣")))
        val tilts = Deck.standard52.map(::discardTilt)
        assertTrue(tilts.all { it in -6f..6f })
        assertTrue(Deck.standard52.zipWithNext().all { (a, b) -> discardTilt(a) != discardTilt(b) })
    }
}
