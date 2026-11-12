package com.cardgames.feature.solitaire

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.solitaire.KlondikeMove
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.domain.solitaire.TableauPile
import com.cardgames.feature.solitaire.board.HintSpot
import com.cardgames.feature.solitaire.board.hintSpots
import com.cardgames.feature.solitaire.motion.spots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SolitaireHintTest {
    private val emptyPile = TableauPile(emptyList(), 0)

    private fun state(
        tableau: List<TableauPile> = List(7) { emptyPile },
        waste: List<Card> = emptyList(),
    ) = KlondikeState(
        seed = 0L,
        drawCount = 1,
        tableau = tableau,
        foundations = Suit.entries.associateWith { 0 },
        stock = emptyList(),
        waste = waste,
    )

    @Test
    fun `no suggestion means no spots`() {
        assertEquals(emptySet<HintSpot>(), state().hintSpots(null))
    }

    @Test
    fun `stock moves highlight only the stock`() {
        assertEquals(setOf(HintSpot.Stock), state().hintSpots(KlondikeMove.DealFromStock))
        assertEquals(setOf(HintSpot.Stock), state().hintSpots(KlondikeMove.RecycleWaste))
    }

    @Test
    fun `a run move highlights the grabbed card and the destination pile`() {
        val spots = state().hintSpots(KlondikeMove.TableauToTableau(fromPile = 2, cardIndex = 3, toPile = 5))

        assertEquals(
            setOf(HintSpot.TableauCard(pile = 2, cardIndex = 3), HintSpot.TableauDestination(pile = 5)),
            spots,
        )
    }

    @Test
    fun `a tableau-to-foundation move highlights the top card and its suit pile`() {
        val game =
            state(
                tableau =
                    listOf(
                        TableauPile(listOf(Card(Suit.CLUBS, Rank.FIVE), Card(Suit.HEARTS, Rank.ACE)), 1),
                    ) + List(6) { emptyPile },
            )

        val spots = game.hintSpots(KlondikeMove.TableauToFoundation(fromPile = 0))

        assertEquals(
            setOf(HintSpot.TableauCard(pile = 0, cardIndex = 1), HintSpot.Foundation(Suit.HEARTS)),
            spots,
        )
    }

    @Test
    fun `a waste-to-foundation move highlights the waste and its suit pile`() {
        val game = state(waste = listOf(Card(Suit.SPADES, Rank.ACE)))

        assertEquals(
            setOf(HintSpot.Waste, HintSpot.Foundation(Suit.SPADES)),
            game.hintSpots(KlondikeMove.WasteToFoundation),
        )
    }
}
