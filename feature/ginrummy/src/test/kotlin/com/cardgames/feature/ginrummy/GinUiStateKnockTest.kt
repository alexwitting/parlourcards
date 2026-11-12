package com.cardgames.feature.ginrummy

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.testing.card
import com.cardgames.core.testing.cards
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [GinUiState.knockCard] and [canKnock] offer no knock (or gin) while `game.phase` is the
 * opponent's `AwaitingDiscard`: Knock would play the opponent's cards.
 */
class GinUiStateKnockTest {
    /** Run (0) + set (0) + set (0), discarding the king leaves zero deadwood — a gin. */
    private fun ginEligibleHand(): List<Card> = cards("A♠ 2♠ 3♠ 4♠ 7♥ 7♦ 7♣ 9♥ 9♦ 9♣ K♥")

    private fun stateWith(
        phase: GinPhase,
        playerHand: List<Card> = emptyList(),
        opponentHand: List<Card> = emptyList(),
    ) = GinUiState(
        game =
            GinState(
                seed = 0L,
                handNumber = 1,
                dealer = GinPlayer.OPPONENT,
                playerHand = playerHand,
                opponentHand = opponentHand,
                stock = emptyList(),
                discardPile = emptyList(),
                phase = phase,
                matchScores = GinPlayer.entries.associateWith { 0 },
                boxes = GinPlayer.entries.associateWith { 0 },
            ),
    )

    @Test
    fun `knockCard finds the player's own gin during the player's turn`() {
        val state = stateWith(GinPhase.AwaitingDiscard(GinPlayer.PLAYER), playerHand = ginEligibleHand())

        assertEquals(card("K♥"), state.knockCard)
        assertTrue(state.canKnock)
        assertTrue(state.knockIsGin)
    }

    @Test
    fun `knockCard is null during the opponent's own AwaitingDiscard, even with a gin-eligible opponent hand`() {
        val state = stateWith(GinPhase.AwaitingDiscard(GinPlayer.OPPONENT), opponentHand = ginEligibleHand())

        assertNull(state.knockCard)
        assertFalse(state.canKnock)
        assertFalse(state.knockIsGin)
    }

    @Test
    fun `knockCard is null outside AwaitingDiscard even for the player`() {
        val state = stateWith(GinPhase.AwaitingDraw(GinPlayer.PLAYER), playerHand = ginEligibleHand())

        assertNull(state.knockCard)
    }
}
