package com.cardgames.core.domain.ginrummy.ai

import com.cardgames.core.domain.cards.Deck
import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinMove
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.testing.cards
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class HiddenWorldSamplerTest {
    private val ownHand =
        cards("A♠ 2♠ 3♠ 7♥ 7♦ 7♣ 4♥ K♥ 9♣ Q♦")
    private val discardPile = cards("9♠")
    private val view = OpponentView(ownHand = ownHand, discardPile = discardPile, stockRemaining = 31, opponentHandSize = 10)
    private val phase = GinPhase.AwaitingDiscard(GinPlayer.PLAYER)

    @Test
    fun `sampled opponent hand and stock sizes match the view's declared counts`() {
        val sampled = HiddenWorldSampler.sample(view, phase, Random(1))

        assertEquals(view.opponentHandSize, sampled.opponentHand.size)
        assertEquals(view.stockRemaining, sampled.stock.size)
    }

    @Test
    fun `never deals a card that's already in ownHand or the discard pile`() {
        val sampled = HiddenWorldSampler.sample(view, phase, Random(2))
        val known = (ownHand + discardPile).toSet()

        assertTrue(sampled.opponentHand.none { it in known })
        assertTrue(sampled.stock.none { it in known })
    }

    @Test
    fun `ownHand, discard pile, sampled opponent hand and stock together are exactly one standard deck`() {
        val sampled = HiddenWorldSampler.sample(view, phase, Random(3))

        val everyCard = ownHand + discardPile + sampled.opponentHand + sampled.stock
        assertEquals(Deck.standard52.toSet(), everyCard.toSet())
        assertEquals(Deck.standard52.size, everyCard.size, "no card should be dealt twice")
    }

    @Test
    fun `the sampled state is legal to continue from the supplied phase`() {
        val sampled = HiddenWorldSampler.sample(view, phase, Random(4))

        assertTrue(GinEngine.isLegal(sampled, GinMove.Discard(ownHand.first())))
    }

    @Test
    fun `the same Random seed produces the same split`() {
        val first = HiddenWorldSampler.sample(view, phase, Random(42))
        val second = HiddenWorldSampler.sample(view, phase, Random(42))

        assertEquals(first.opponentHand, second.opponentHand)
        assertEquals(first.stock, second.stock)
    }

    @Test
    fun `cards the hidden seat is known to hold are always in its sampled hand`() {
        val pickedUp = cards("8♥ 8♣")
        val knowing = view.copy(opponentKnownCards = pickedUp)

        for (seed in 1L..20L) {
            val sampled = HiddenWorldSampler.sample(knowing, phase, Random(seed))
            assertTrue(sampled.opponentHand.containsAll(pickedUp), "seed $seed")
            assertEquals(knowing.opponentHandSize, sampled.opponentHand.size)
            assertEquals(Deck.standard52.toSet(), (ownHand + discardPile + sampled.opponentHand + sampled.stock).toSet())
        }
    }

    @Test
    fun `a known card that isn't actually unseen is ignored rather than dealt twice`() {
        // Inconsistent on purpose: the "known" card is in the deciding seat's own hand.
        val stale = view.copy(opponentKnownCards = listOf(ownHand.first()))

        val sampled = HiddenWorldSampler.sample(stale, phase, Random(1L))

        assertTrue(ownHand.first() !in sampled.opponentHand)
        assertEquals(stale.opponentHandSize, sampled.opponentHand.size)
    }
}
