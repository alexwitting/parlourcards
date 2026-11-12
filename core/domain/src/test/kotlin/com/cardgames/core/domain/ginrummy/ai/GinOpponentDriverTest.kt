package com.cardgames.core.domain.ginrummy.ai

import com.cardgames.core.domain.cards.Deck
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.Meld
import com.cardgames.core.testing.card
import com.cardgames.core.testing.cards
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GinOpponentDriverTest {
    /**
     * The defender's 5-6-7 spade run is a complete, deadwood-free meld that happens to include
     * the one card (7S) that would extend the knocker's 7-set. Auto-layoff must skip it — laying
     * it off would strip the run down to an incomplete 5-6 (10 deadwood pips), a pure loss for
     * the defender since that card wasn't costing them anything to begin with.
     */
    @Test
    fun `auto layoff never breaks the defenders own complete meld to feed the knockers meld`() {
        val knockerMeld =
            Meld.Set(cards("7♥ 7♦ 7♣"))
        val defenderRun = cards("5♠ 6♠ 7♠")
        val defenderDeadwood = cards("K♥ Q♣")
        val defenderHand = defenderRun + defenderDeadwood
        val knockerHand = knockerMeld.cards + card("K♦")
        val knockerDiscard = card("K♠")

        val used = (knockerHand + defenderHand + knockerDiscard).toSet()
        val remaining = Deck.standard52.filterNot { it in used }
        val state =
            GinState(
                seed = 0L,
                handNumber = 1,
                dealer = GinPlayer.OPPONENT,
                playerHand = knockerHand,
                opponentHand = defenderHand,
                stock = remaining.drop(1),
                discardPile = listOf(remaining.first()),
                phase =
                    GinPhase.AwaitingLayoff(
                        knocker = GinPlayer.PLAYER,
                        isGin = false,
                        knockerDiscard = knockerDiscard,
                        knockerMelds = listOf(knockerMeld),
                    ),
                matchScores = GinPlayer.entries.associateWith { 0 },
                boxes = GinPlayer.entries.associateWith { 0 },
            )

        val result = GinOpponentDriver.driveOneDecision(state, GinPlayer.OPPONENT, HeuristicGinOpponent())

        assertTrue(card("7♠") in result.opponentHand)
        val handOver = result.phase as GinPhase.HandOver
        assertEquals(20, handOver.result.defenderDeadwood)
    }
}
