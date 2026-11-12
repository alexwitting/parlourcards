package com.cardgames.feature.solitaire.motion

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Deck
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.solitaire.KlondikeEngine
import com.cardgames.core.domain.solitaire.KlondikeMove
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.domain.solitaire.TableauPile
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.feature.solitaire.MotionKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SolitaireChoreographyTest {
    private val flight = CardGamesMotion.Flight.millis
    private val flip = CardGamesMotion.Flip.millis
    private val emptyPile = TableauPile(emptyList(), 0)

    private fun table(
        tableau: List<TableauPile> = List(7) { emptyPile },
        stock: List<Card> = emptyList(),
        waste: List<Card> = emptyList(),
        drawCount: Int = 1,
    ) = KlondikeState(0L, drawCount, tableau, Suit.entries.associateWith { 0 }, stock, waste)

    @Test
    fun `the deal goes out row by row, 50ms apart, last card of each column turning over in flight`() {
        val dealt = KlondikeEngine.deal(seed = 7L)
        val result = choreograph(MotionKind.DEAL, dealt.gathered(), dealt)

        val order = dealt.dealOrder()
        assertEquals(28, order.size)
        assertEquals(dealt.tableau[0].cards[0], order[0])
        assertEquals(dealt.tableau[1].cards[0], order[1])
        assertEquals(dealt.tableau[1].cards[1], order[7])
        order.forEachIndexed { i, card -> assertEquals(i * 50, result.timings.getValue(card).delayMillis) }
        assertEquals(27 * 50 + 160 + flip, result.totalMillis)
    }

    @Test
    fun `gathering a deal puts every card in the stock, the first to deal on top`() {
        val dealt = KlondikeEngine.deal(seed = 7L)
        val gathered = dealt.gathered()

        assertEquals(52, gathered.stock.size)
        assertEquals(dealt.dealOrder().first(), gathered.stock.last())
    }

    @Test
    fun `draw 3 staggers the cards 60ms apart, each turning over 160ms into its flight`() {
        val stock = listOf(Card(Suit.CLUBS, Rank.TWO), Card(Suit.CLUBS, Rank.THREE), Card(Suit.CLUBS, Rank.FOUR))
        val from = table(stock = stock, drawCount = 3)
        val to = KlondikeEngine.apply(from, KlondikeMove.DealFromStock)

        val result = choreograph(MotionKind.DRAW, from, to)

        to.waste.forEachIndexed { i, card ->
            assertEquals(CardTiming(delayMillis = i * 60, flipDelayMillis = i * 60 + 160), result.timings[card])
        }
        assertEquals(2 * 60 + 160 + flip, result.totalMillis)
    }

    @Test
    fun `a run trails 30ms per card and the card it uncovers turns over after it lands`() {
        val hidden = Card(Suit.DIAMONDS, Rank.TWO)
        val nine = Card(Suit.HEARTS, Rank.NINE)
        val eight = Card(Suit.SPADES, Rank.EIGHT)
        val from =
            table(
                tableau =
                    listOf(
                        TableauPile(listOf(hidden, nine, eight), 1),
                        TableauPile(listOf(Card(Suit.SPADES, Rank.TEN)), 0),
                    ) + List(5) { emptyPile },
            )
        val to = KlondikeEngine.apply(from, KlondikeMove.TableauToTableau(0, 1, 1))

        val result = choreograph(MotionKind.MOVE, from, to)

        assertEquals(0, result.timings.getValue(nine).delayMillis)
        assertEquals(30, result.timings.getValue(eight).delayMillis)
        val reveal = result.timings.getValue(hidden).flipDelayMillis
        // The uncovered card turns almost at once: 60ms after the run's last card has left.
        assertEquals(30 + 60, reveal)
        // The move lasts until both the run has landed and the uncovered card has turned.
        assertEquals(maxOf(30 + flight, reveal + flip), result.totalMillis)
    }

    @Test
    fun `a dropped card is already in place`() {
        val ace = Card(Suit.HEARTS, Rank.ACE)
        val from = table(waste = listOf(ace))
        val to = KlondikeEngine.apply(from, KlondikeMove.WasteToFoundation)

        val result = choreograph(MotionKind.DROP, from, to)

        assertEquals(0, result.timings.getValue(ace).glideMillis)
        assertEquals(0, result.totalMillis)
    }

    @Test
    fun `undo turns cards back over first, then returns them a little quicker`() {
        val stock = listOf(Card(Suit.CLUBS, Rank.TWO))
        val drawn = KlondikeEngine.apply(table(stock = stock), KlondikeMove.DealFromStock)

        val result = choreograph(MotionKind.UNDO, drawn, table(stock = stock))

        val timing = result.timings.getValue(stock.single())
        assertEquals(210, timing.delayMillis)
        assertEquals(240, timing.glideMillis)
        assertTrue(result.totalMillis >= 450)
    }

    @Test
    fun `auto-complete sends the lowest ranks home first, 120ms apart`() {
        val queens = Suit.entries.map { Card(it, Rank.QUEEN) }
        val kings = Suit.entries.map { Card(it, Rank.KING) }
        val from =
            KlondikeState(
                0L,
                1,
                Suit.entries.map { TableauPile(listOf(Card(it, Rank.KING), Card(it, Rank.QUEEN)), 0) } + List(3) { emptyPile },
                Suit.entries.associateWith { 11 },
                emptyList(),
                emptyList(),
            )
        val to = from.copy(tableau = List(7) { emptyPile }, foundations = Suit.entries.associateWith { 13 })

        val result = choreograph(MotionKind.AUTO_COMPLETE, from, to)

        val delays = (queens + kings).map { result.timings.getValue(it).delayMillis }
        assertEquals((0 until 8).map { it * 120 }, delays)
    }

    @Test
    fun `turning the waste over flips the top few in place, then slides the stack home`() {
        val waste = Deck.standard52.take(5)
        val from = table(waste = waste)
        val to = KlondikeEngine.apply(from, KlondikeMove.RecycleWaste)

        val result = choreograph(MotionKind.RECYCLE, from, to)

        // Top card first, 40ms apart, only the top three staggered.
        assertEquals(0, result.timings.getValue(waste[4]).flipDelayMillis)
        assertEquals(40, result.timings.getValue(waste[3]).flipDelayMillis)
        assertEquals(80, result.timings.getValue(waste[2]).flipDelayMillis)
        assertEquals(80, result.timings.getValue(waste[0]).flipDelayMillis)
        // Everything slides once the visible ones have turned.
        val slide = flip + 2 * 40
        waste.forEach { assertEquals(slide, result.timings.getValue(it).delayMillis) }
    }

    @Test
    fun `a restart gathers the foundations first, then the waste, then each column from the top`() {
        val ace = Card(Suit.HEARTS, Rank.ACE)
        val wasteCard = Card(Suit.CLUBS, Rank.NINE)
        val bottom = Card(Suit.SPADES, Rank.KING)
        val top = Card(Suit.DIAMONDS, Rank.QUEEN)
        val from =
            table(
                tableau = listOf(TableauPile(listOf(bottom, top), 1)) + List(6) { emptyPile },
                waste = listOf(wasteCard),
            ).copy(foundations = Suit.entries.associateWith { if (it == Suit.HEARTS) 1 else 0 })
        val to = from.allInStock()

        val result = choreograph(MotionKind.GATHER, from, to)

        val delays = listOf(ace, wasteCard, top, bottom).map { result.timings.getValue(it).delayMillis }
        assertEquals(listOf(0, 12, 24, 36), delays)
        // Each turns face down as it leaves.
        listOf(ace, wasteCard, top, bottom).forEach {
            assertEquals(result.timings.getValue(it).delayMillis, result.timings.getValue(it).flipDelayMillis)
        }
    }

    @Test
    fun `gathered for a new deal, every card is in the stock face down`() {
        val from = KlondikeEngine.deal(seed = 3L)

        val gathered = from.allInStock()

        assertEquals(52, gathered.stock.size)
        assertEquals(52, gathered.stock.toSet().size)
        assertTrue(gathered.tableau.all { it.isEmpty })
        assertTrue(gathered.waste.isEmpty())
    }
}
