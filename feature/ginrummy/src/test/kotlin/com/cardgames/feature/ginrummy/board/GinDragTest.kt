package com.cardgames.feature.ginrummy.board

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.ginrummy.GinMove
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GinDragTest {
    private fun state(phase: GinPhase) =
        GinState(
            seed = 0L,
            handNumber = 1,
            dealer = GinPlayer.OPPONENT,
            playerHand = emptyList(),
            opponentHand = emptyList(),
            stock = emptyList(),
            discardPile = emptyList(),
            phase = phase,
            matchScores = GinPlayer.entries.associateWith { 0 },
            boxes = GinPlayer.entries.associateWith { 0 },
        )

    private val jack = Card(Suit.CLUBS, Rank.JACK)
    private val ten = Card(Suit.HEARTS, Rank.TEN)

    @Test
    fun `cardFor resolves a hand card drag to that same card`() {
        val game = state(GinPhase.AwaitingDiscard(GinPlayer.PLAYER))

        assertEquals(jack, game.cardFor(GinDragSource.HandCard(jack)))
    }

    @Test
    fun `cardFor resolves the stock and discard drags to their current top cards`() {
        val game = state(GinPhase.AwaitingDraw(GinPlayer.PLAYER)).copy(stock = listOf(ten, jack), discardPile = listOf(jack, ten))

        assertEquals(jack, game.cardFor(GinDragSource.StockTop))
        assertEquals(ten, game.cardFor(GinDragSource.DiscardTop))
    }

    @Test
    fun `cardFor is null for an empty stock or discard pile`() {
        val game = state(GinPhase.AwaitingDraw(GinPlayer.PLAYER))

        assertNull(game.cardFor(GinDragSource.StockTop))
        assertNull(game.cardFor(GinDragSource.DiscardTop))
    }

    @Test
    fun `stock dropped on the hand maps to DrawFromStock`() {
        val game = state(GinPhase.AwaitingDraw(GinPlayer.PLAYER))

        val move = game.moveFor(GinDragSource.StockTop, GinDropTarget.Hand)

        assertEquals(GinMove.DrawFromStock, move)
    }

    @Test
    fun `discard dropped on the hand maps to DrawFromDiscard outside the upcard decision`() {
        val game = state(GinPhase.AwaitingDraw(GinPlayer.PLAYER))

        val move = game.moveFor(GinDragSource.DiscardTop, GinDropTarget.Hand)

        assertEquals(GinMove.DrawFromDiscard, move)
    }

    @Test
    fun `discard dropped on the hand maps to TakeUpcard during the upcard decision`() {
        val game = state(GinPhase.UpcardDecision(GinPlayer.PLAYER))

        val move = game.moveFor(GinDragSource.DiscardTop, GinDropTarget.Hand)

        assertEquals(GinMove.TakeUpcard, move)
    }

    @Test
    fun `a hand card dropped back on the hand maps to nothing`() {
        val game = state(GinPhase.AwaitingDiscard(GinPlayer.PLAYER))

        assertNull(game.moveFor(GinDragSource.HandCard(jack), GinDropTarget.Hand))
    }

    @Test
    fun `a hand card dropped on the discard pile maps to Discard`() {
        val game = state(GinPhase.AwaitingDiscard(GinPlayer.PLAYER))

        val move = game.moveFor(GinDragSource.HandCard(jack), GinDropTarget.Discard)

        assertEquals(GinMove.Discard(jack), move)
    }

    @Test
    fun `the stock or discard dropped on the discard pile maps to nothing`() {
        val game = state(GinPhase.AwaitingDraw(GinPlayer.PLAYER))

        assertNull(game.moveFor(GinDragSource.StockTop, GinDropTarget.Discard))
        assertNull(game.moveFor(GinDragSource.DiscardTop, GinDropTarget.Discard))
    }
}
