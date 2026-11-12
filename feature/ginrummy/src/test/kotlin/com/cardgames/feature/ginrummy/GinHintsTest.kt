package com.cardgames.feature.ginrummy

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.testing.card
import com.cardgames.core.testing.cards
import com.cardgames.core.testing.ginPosition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GinHintsTest {
    /** Three melds, the lone K♠ and Q♥ — throwing the king leaves the least deadwood. */
    private val hand =
        cards("A♣ 2♣ 3♣ 5♦ 5♥ 5♠ 9♥ 10♥ J♥ 2♦ K♠")

    private fun discarding(mustNotDiscard: Card? = null): GinState =
        ginPosition(hand, phase = GinPhase.AwaitingDiscard(GinPlayer.PLAYER, mustNotDiscard = mustNotDiscard))

    @Test
    fun `the discard hint throws the card that leaves the least deadwood`() {
        assertEquals(GinHint.Discard(card("K♠")), discarding().suggestion())
    }

    @Test
    fun `the discard hint never suggests the card just taken from the pile`() {
        val king = card("K♠")

        val hint = discarding(mustNotDiscard = king).suggestion()

        assertEquals(GinHint.Discard(card("2♦")), hint)
    }

    @Test
    fun `there's no hint once the hand is being settled`() {
        val state =
            discarding().copy(
                phase =
                    GinPhase.AwaitingLayoff(
                        knocker = GinPlayer.OPPONENT,
                        isGin = false,
                        knockerDiscard = null,
                        knockerMelds = emptyList(),
                    ),
            )

        assertNull(state.suggestion())
    }
}
