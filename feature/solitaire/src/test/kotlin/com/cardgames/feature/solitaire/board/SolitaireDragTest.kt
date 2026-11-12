package com.cardgames.feature.solitaire.board

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.solitaire.KlondikeMove
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.domain.solitaire.TableauPile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SolitaireDragTest {
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

    @Test
    fun `run onto a tableau pile maps to TableauToTableau`() {
        val game = state()

        val move =
            game.moveFor(
                SolitaireDragSource.TableauRun(pile = 2, cardIndex = 1),
                SolitaireDropTarget.Tableau(pile = 5),
            )

        assertEquals(KlondikeMove.TableauToTableau(fromPile = 2, cardIndex = 1, toPile = 5), move)
    }

    @Test
    fun `waste onto a tableau pile maps to WasteToTableau`() {
        val move = state().moveFor(SolitaireDragSource.Waste, SolitaireDropTarget.Tableau(pile = 3))

        assertEquals(KlondikeMove.WasteToTableau(toPile = 3), move)
    }

    @Test
    fun `foundation card onto a tableau pile maps to FoundationToTableau`() {
        val move =
            state().moveFor(
                SolitaireDragSource.Foundation(Suit.HEARTS),
                SolitaireDropTarget.Tableau(pile = 0),
            )

        assertEquals(KlondikeMove.FoundationToTableau(Suit.HEARTS, toPile = 0), move)
    }

    @Test
    fun `tableau top card onto its own suit foundation maps to TableauToFoundation`() {
        val game =
            state(
                tableau =
                    listOf(TableauPile(listOf(Card(Suit.HEARTS, Rank.ACE)), 0)) + List(6) { emptyPile },
            )

        val move =
            game.moveFor(
                SolitaireDragSource.TableauRun(pile = 0, cardIndex = 0),
                SolitaireDropTarget.Foundation(Suit.HEARTS),
            )

        assertEquals(KlondikeMove.TableauToFoundation(fromPile = 0), move)
    }

    @Test
    fun `tableau card onto a wrong-suit foundation maps to nothing`() {
        val game =
            state(
                tableau =
                    listOf(TableauPile(listOf(Card(Suit.HEARTS, Rank.ACE)), 0)) + List(6) { emptyPile },
            )

        val move =
            game.moveFor(
                SolitaireDragSource.TableauRun(pile = 0, cardIndex = 0),
                SolitaireDropTarget.Foundation(Suit.SPADES),
            )

        assertNull(move)
    }

    @Test
    fun `a mid-run card onto a foundation maps to nothing`() {
        val game =
            state(
                tableau =
                    listOf(
                        TableauPile(
                            listOf(Card(Suit.HEARTS, Rank.TWO), Card(Suit.SPADES, Rank.ACE)),
                            0,
                        ),
                    ) + List(6) { emptyPile },
            )

        val move =
            game.moveFor(
                SolitaireDragSource.TableauRun(pile = 0, cardIndex = 0),
                SolitaireDropTarget.Foundation(Suit.HEARTS),
            )

        assertNull(move)
    }

    @Test
    fun `waste onto a matching foundation maps to WasteToFoundation`() {
        val game = state(waste = listOf(Card(Suit.CLUBS, Rank.ACE)))

        val move = game.moveFor(SolitaireDragSource.Waste, SolitaireDropTarget.Foundation(Suit.CLUBS))

        assertEquals(KlondikeMove.WasteToFoundation, move)
    }

    @Test
    fun `waste onto a wrong-suit foundation maps to nothing`() {
        val game = state(waste = listOf(Card(Suit.CLUBS, Rank.ACE)))

        assertNull(game.moveFor(SolitaireDragSource.Waste, SolitaireDropTarget.Foundation(Suit.DIAMONDS)))
    }

    @Test
    fun `foundation onto a foundation maps to nothing`() {
        val move =
            state().moveFor(
                SolitaireDragSource.Foundation(Suit.HEARTS),
                SolitaireDropTarget.Foundation(Suit.HEARTS),
            )

        assertNull(move)
    }

    @Test
    fun `dragged cards for a run are the pile tail from the grabbed card`() {
        val run = listOf(Card(Suit.HEARTS, Rank.NINE), Card(Suit.SPADES, Rank.EIGHT))
        val game =
            state(
                tableau =
                    listOf(TableauPile(listOf(Card(Suit.CLUBS, Rank.KING)) + run, 0)) + List(6) { emptyPile },
            )

        assertEquals(run, game.draggedCards(SolitaireDragSource.TableauRun(pile = 0, cardIndex = 1)))
    }

    @Test
    fun `dragged cards for the waste and a foundation are their top cards`() {
        val game =
            state(
                waste = listOf(Card(Suit.CLUBS, Rank.FIVE), Card(Suit.HEARTS, Rank.TEN)),
                foundations = Suit.entries.associateWith { if (it == Suit.SPADES) 3 else 0 },
            )

        assertEquals(listOf(Card(Suit.HEARTS, Rank.TEN)), game.draggedCards(SolitaireDragSource.Waste))
        assertEquals(
            listOf(Card(Suit.SPADES, Rank.THREE)),
            game.draggedCards(SolitaireDragSource.Foundation(Suit.SPADES)),
        )
    }
}
