package com.cardgames.core.domain.solitaire

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KlondikeHintsTest {
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
    ) = KlondikeState(
        seed = 0L,
        drawCount = 1,
        tableau = tableau,
        foundations = foundations,
        stock = stock,
        waste = waste,
    )

    private fun pile(vararg cards: Card) = TableauPile(cards.toList(), 0)

    @Test
    fun `a foundation move beats everything else`() {
        // Ace on pile 0 could also sit on the red 2 of pile 1, and the stock is dealable.
        val start =
            state(
                tableau =
                    listOf(
                        pile(card(Rank.ACE, Suit.SPADES)),
                        pile(card(Rank.TWO, Suit.HEARTS)),
                    ) + List(5) { emptyPile },
                stock = listOf(card(Rank.NINE, Suit.CLUBS)),
            )

        assertEquals(KlondikeMove.TableauToFoundation(fromPile = 0), KlondikeHints.suggest(start))
    }

    @Test
    fun `a flip-enabling tableau move beats a pointless one`() {
        // Pile 0 hides a card under its movable 9♥; pile 2's fully-face-up 8♠ run could also
        // move (onto the 9♥) but frees nothing.
        val start =
            state(
                tableau =
                    listOf(
                        TableauPile(listOf(card(Rank.FIVE, Suit.CLUBS), card(Rank.NINE, Suit.HEARTS)), 1),
                        pile(card(Rank.TEN, Suit.SPADES)),
                        pile(card(Rank.EIGHT, Suit.SPADES)),
                    ) + List(4) { emptyPile },
            )

        assertEquals(
            KlondikeMove.TableauToTableau(fromPile = 0, cardIndex = 1, toPile = 1),
            KlondikeHints.suggest(start),
        )
    }

    @Test
    fun `a placeable waste card beats dealing from the stock`() {
        val start =
            state(
                tableau = listOf(pile(card(Rank.TEN, Suit.SPADES))) + List(6) { emptyPile },
                waste = listOf(card(Rank.NINE, Suit.HEARTS)),
                stock = listOf(card(Rank.FOUR, Suit.DIAMONDS)),
            )

        assertEquals(KlondikeMove.WasteToTableau(toPile = 0), KlondikeHints.suggest(start))
    }

    @Test
    fun `falls back to the stock when nothing is productive`() {
        val start =
            state(
                tableau = listOf(pile(card(Rank.FIVE, Suit.HEARTS))) + List(6) { emptyPile },
                stock = listOf(card(Rank.FOUR, Suit.DIAMONDS)),
            )

        assertEquals(KlondikeMove.DealFromStock, KlondikeHints.suggest(start))
    }

    @Test
    fun `suggests recycling an exhausted stock`() {
        val start =
            state(
                tableau = listOf(pile(card(Rank.FIVE, Suit.HEARTS))) + List(6) { emptyPile },
                waste = listOf(card(Rank.FOUR, Suit.DIAMONDS)),
            )

        assertEquals(KlondikeMove.RecycleWaste, KlondikeHints.suggest(start))
    }

    @Test
    fun `M7_1 - suggests the solver's own first move when the position is provably winnable`() {
        // Same near-win shape as KlondikeSolverTest: only the last two spades remain.
        val start =
            state(
                tableau =
                    listOf(
                        pile(card(Rank.QUEEN, Suit.SPADES)),
                        pile(card(Rank.KING, Suit.SPADES)),
                    ) + List(5) { emptyPile },
                foundations = mapOf(Suit.CLUBS to 13, Suit.DIAMONDS to 13, Suit.HEARTS to 13, Suit.SPADES to 11),
            )
        val solved = KlondikeSolver.solve(start, nodeBudget = 100) as KlondikeSolver.Result.Solved

        assertEquals(solved.moves.first(), KlondikeHints.suggest(start))

        // Following KlondikeHints.suggest turn by turn (not the solver's own precomputed line —
        // a fresh suggestion each time, as the real hint button would) also reaches a win.
        var replay = start
        repeat(solved.moves.size) {
            val nextHint = requireNotNull(KlondikeHints.suggest(replay)) { "expected a hint at every step of a winnable line" }
            replay = KlondikeEngine.apply(replay, nextHint)
        }
        assertTrue(KlondikeEngine.isWon(replay))
    }

    @Test
    fun `returns null when only unproductive moves remain`() {
        // A lone fully-face-up king, no stock, no waste: the only legal moves are shuffling the
        // king to an empty pile or pulling a foundation card down — neither is a hint.
        val start =
            state(
                tableau = listOf(pile(card(Rank.KING, Suit.HEARTS))) + List(6) { emptyPile },
                foundations = Suit.entries.associateWith { if (it == Suit.SPADES) 1 else 0 },
            )

        assertNull(KlondikeHints.suggest(start))
    }
}
