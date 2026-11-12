package com.cardgames.feature.solitaire.board

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.solitaire.KlondikeMove
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.domain.solitaire.TableauPile
import com.cardgames.core.ui.cards.label
import com.cardgames.feature.solitaire.SolitaireState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SolitaireStatusTest {
    private val emptyPile = TableauPile(emptyList(), 0)
    private val nine = Card(Suit.CLUBS, Rank.NINE)
    private val tenDiamonds = Card(Suit.DIAMONDS, Rank.TEN)

    private val game =
        KlondikeState(
            seed = 0L,
            drawCount = 1,
            tableau =
                listOf(
                    TableauPile(listOf(Card(Suit.SPADES, Rank.TWO), nine), 1),
                    TableauPile(listOf(tenDiamonds), 0),
                ) + List(5) { emptyPile },
            foundations = Suit.entries.associateWith { 0 },
            stock = emptyList(),
            waste = emptyList(),
        )

    @Test
    fun `a hint is described, with the hints left`() {
        val line = hintLine(SolitaireState(game = game, hint = KlondikeMove.TableauToTableau(0, 1, 1), hintsLeft = 2))

        assertEquals("${nine.label()} fits on ${tenDiamonds.label()}.", line?.text)
        assertTrue(line?.isHint == true)
        assertEquals("2 hints left", line?.trailing)
    }

    @Test
    fun `without a hint the panel says nothing`() {
        assertNull(hintLine(SolitaireState(game = game, canAutoComplete = true)))
    }

    @Test
    fun `hint counts read naturally`() {
        assertNull(hintsLeftNote(null))
        assertEquals("No hints left", hintsLeftNote(0))
        assertEquals("1 hint left", hintsLeftNote(1))
    }

    @Test
    fun `every kind of hint is put in words`() {
        val eight = Card(Suit.HEARTS, Rank.EIGHT)
        val fromWaste = game.copy(waste = listOf(eight))
        assertEquals("${eight.label()} fits on ${nine.label()}.", fromWaste.describe(KlondikeMove.WasteToTableau(0)))
        assertEquals("${eight.label()} can go home to its foundation.", fromWaste.describe(KlondikeMove.WasteToFoundation))

        val nineHearts = Card(Suit.HEARTS, Rank.NINE)
        val withFoundation = game.copy(foundations = Suit.entries.associateWith { if (it == Suit.HEARTS) 9 else 0 })
        assertEquals(
            "${nineHearts.label()} can come down onto the empty column.",
            withFoundation.describe(KlondikeMove.FoundationToTableau(Suit.HEARTS, 3)),
        )

        assertEquals("${nine.label()} can go home to its foundation.", game.describe(KlondikeMove.TableauToFoundation(0)))
        assertEquals("Draw from the stock.", game.describe(KlondikeMove.DealFromStock))
        assertEquals("Turn the waste back over.", game.describe(KlondikeMove.RecycleWaste))
    }
}
