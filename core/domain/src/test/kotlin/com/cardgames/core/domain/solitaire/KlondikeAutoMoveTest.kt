package com.cardgames.core.domain.solitaire

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KlondikeAutoMoveTest {
    private fun card(
        rank: Rank,
        suit: Suit,
    ) = Card(suit, rank)

    private val emptyPile = TableauPile(emptyList(), 0)

    private fun state(
        tableau: List<TableauPile> = List(7) { emptyPile },
        foundations: Map<Suit, Int> = Suit.entries.associateWith { 0 },
        waste: List<Card> = emptyList(),
    ) = KlondikeState(
        seed = 0L,
        drawCount = 1,
        tableau = tableau,
        foundations = foundations,
        stock = emptyList(),
        waste = waste,
    )

    private fun pile(vararg cards: Card) = TableauPile(cards.toList(), 0)

    @Test
    fun `waste ace prefers the foundation over a legal tableau spot`() {
        val start =
            state(
                waste = listOf(card(Rank.ACE, Suit.HEARTS)),
                tableau = listOf(pile(card(Rank.TWO, Suit.SPADES))) + List(6) { emptyPile },
            )

        assertEquals(
            KlondikeMove.WasteToFoundation,
            KlondikeAutoMove.bestMoveFor(start, KlondikeAutoMove.Source.Waste),
        )
    }

    @Test
    fun `waste card falls back to a tableau target`() {
        val start =
            state(
                waste = listOf(card(Rank.NINE, Suit.HEARTS)),
                tableau = listOf(pile(card(Rank.TEN, Suit.SPADES))) + List(6) { emptyPile },
            )

        assertEquals(
            KlondikeMove.WasteToTableau(toPile = 0),
            KlondikeAutoMove.bestMoveFor(start, KlondikeAutoMove.Source.Waste),
        )
    }

    @Test
    fun `tapped top card goes to the foundation when it fits`() {
        val start =
            state(
                foundations = Suit.entries.associateWith { 0 } + (Suit.CLUBS to 1),
                tableau = listOf(pile(card(Rank.TWO, Suit.CLUBS))) + List(6) { emptyPile },
            )

        assertEquals(
            KlondikeMove.TableauToFoundation(fromPile = 0),
            KlondikeAutoMove.bestMoveFor(start, KlondikeAutoMove.Source.Tableau(pile = 0, cardIndex = 0)),
        )
    }

    @Test
    fun `a mid-run card never goes to the foundation, its run moves instead`() {
        // Pile 0: 2♣ (foundation-ready) buried under A♦; tapping the 2♣ moves the run 2♣+A♦? No —
        // A♦ on 2♣ is a legal tableau stack (red on black descending); tapping the buried 2♣ can
        // only move the run to another tableau spot (onto a red 3), never to the foundation.
        val start =
            state(
                foundations = Suit.entries.associateWith { 0 } + (Suit.CLUBS to 1),
                tableau =
                    listOf(
                        pile(card(Rank.TWO, Suit.CLUBS), card(Rank.ACE, Suit.DIAMONDS)),
                        pile(card(Rank.THREE, Suit.HEARTS)),
                    ) + List(5) { emptyPile },
            )

        assertEquals(
            KlondikeMove.TableauToTableau(fromPile = 0, cardIndex = 0, toPile = 1),
            KlondikeAutoMove.bestMoveFor(start, KlondikeAutoMove.Source.Tableau(pile = 0, cardIndex = 0)),
        )
    }

    @Test
    fun `occupied tableau targets beat empty piles`() {
        val king = card(Rank.KING, Suit.SPADES)
        // King can go to empty pile 2 or onto nothing else; with no occupied target it takes the gap.
        val onlyEmpty = state(tableau = listOf(pile(card(Rank.FIVE, Suit.HEARTS), king)) + List(6) { emptyPile })
        assertEquals(
            KlondikeMove.TableauToTableau(fromPile = 0, cardIndex = 1, toPile = 1),
            KlondikeAutoMove.bestMoveFor(onlyEmpty, KlondikeAutoMove.Source.Tableau(pile = 0, cardIndex = 1)),
        )

        // A red 9 with both an empty pile and a black 10 available picks the 10.
        val both =
            state(
                tableau =
                    listOf(
                        pile(card(Rank.NINE, Suit.HEARTS)),
                        emptyPile,
                        pile(card(Rank.TEN, Suit.SPADES)),
                    ) + List(4) { emptyPile },
            )
        assertEquals(
            KlondikeMove.TableauToTableau(fromPile = 0, cardIndex = 0, toPile = 2),
            KlondikeAutoMove.bestMoveFor(both, KlondikeAutoMove.Source.Tableau(pile = 0, cardIndex = 0)),
        )
    }

    @Test
    fun `returns null when the tapped card has no legal move`() {
        val start = state(tableau = listOf(pile(card(Rank.FIVE, Suit.HEARTS))) + List(6) { emptyPile })

        assertNull(KlondikeAutoMove.bestMoveFor(start, KlondikeAutoMove.Source.Tableau(pile = 0, cardIndex = 0)))
        assertNull(KlondikeAutoMove.bestMoveFor(start, KlondikeAutoMove.Source.Waste))
    }
}
