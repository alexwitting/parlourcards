package com.cardgames.core.domain.solitaire

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Hand-built fixtures rather than full random deals, for determinism — a real shuffled deal is
 * only used for the "budget/deadline exhausted" cases, where the exact outcome doesn't matter,
 * only that the search stops.
 */
class KlondikeSolverTest {
    private fun card(
        rank: Rank,
        suit: Suit,
    ) = Card(suit, rank)

    private val emptyPile = TableauPile(emptyList(), 0)

    private fun state(
        tableau: List<TableauPile> = List(7) { emptyPile },
        foundations: Map<Suit, Int> = Suit.entries.associateWith { 0 },
        stock: List<Card> = emptyList(),
        waste: List<Card> = emptyList(),
    ) = KlondikeState(seed = 0L, drawCount = 1, tableau = tableau, foundations = foundations, stock = stock, waste = waste)

    @Test
    fun `finds a two-move win when only the last two cards of one suit remain`() {
        val position =
            state(
                tableau =
                    listOf(
                        TableauPile(listOf(card(Rank.QUEEN, Suit.SPADES)), faceDownCount = 0),
                        TableauPile(listOf(card(Rank.KING, Suit.SPADES)), faceDownCount = 0),
                        emptyPile,
                        emptyPile,
                        emptyPile,
                        emptyPile,
                        emptyPile,
                    ),
                foundations = mapOf(Suit.CLUBS to 13, Suit.DIAMONDS to 13, Suit.HEARTS to 13, Suit.SPADES to 11),
            )

        val result = KlondikeSolver.solve(position, nodeBudget = 100)

        assertTrue(result is KlondikeSolver.Result.Solved)
        val moves = (result as KlondikeSolver.Result.Solved).moves
        var replay = position
        moves.forEach { replay = KlondikeEngine.apply(replay, it) }
        assertTrue(KlondikeEngine.isWon(replay))
    }

    @Test
    fun `NotFound once the node budget is exhausted`() {
        val freshDeal = KlondikeEngine.deal(seed = 999L, drawCount = 1)

        val result = KlondikeSolver.solve(freshDeal, nodeBudget = 1)

        assertEquals(KlondikeSolver.Result.NotFound, result)
    }

    @Test
    fun `NotFound immediately once the deadline has already passed, regardless of node budget`() {
        val freshDeal = KlondikeEngine.deal(seed = 999L, drawCount = 1)

        val result = KlondikeSolver.solve(freshDeal, nodeBudget = 1_000_000, deadlineNanos = System.nanoTime() - 1)

        assertEquals(KlondikeSolver.Result.NotFound, result)
    }
}
