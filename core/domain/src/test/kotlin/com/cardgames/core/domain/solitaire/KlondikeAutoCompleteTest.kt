package com.cardgames.core.domain.solitaire

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Deck
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class KlondikeAutoCompleteTest {
    private val emptyPile = TableauPile(emptyList(), 0)

    private fun state(
        tableau: List<TableauPile> = List(KlondikeState.TABLEAU_PILE_COUNT) { emptyPile },
        foundations: Map<Suit, Int> = Suit.entries.associateWith { 0 },
        stock: List<Card> = emptyList(),
        waste: List<Card> = emptyList(),
        drawCount: Int = 1,
    ) = KlondikeState(seed = 0L, drawCount = drawCount, tableau = tableau, foundations = foundations, stock = stock, waste = waste)

    /** Plays [moves] from [start], checking each is legal, and returns where they end. */
    private fun play(
        start: KlondikeState,
        moves: List<KlondikeMove>,
    ): KlondikeState =
        moves.fold(start) { game, move ->
            assertTrue(KlondikeEngine.isLegal(game, move)) { "illegal $move" }
            KlondikeEngine.apply(game, move)
        }

    /** The kings left on the tableau, everything else home: the classic finish. */
    private val kingsLeft =
        state(
            foundations = Suit.entries.associateWith { 12 },
            tableau = Suit.entries.map { TableauPile(listOf(Card(it, Rank.KING)), 0) } + List(3) { emptyPile },
        )

    @Test
    fun `a finished tableau plays out to a win`() {
        val moves = checkNotNull(KlondikeAutoComplete.playOut(kingsLeft))

        assertTrue(KlondikeEngine.isWon(play(kingsLeft, moves)))
        assertEquals(4, moves.size)
    }

    @Test
    fun `nothing is offered while a tableau card is face down, or once the game is won`() {
        val hidden =
            kingsLeft.copy(
                tableau =
                    listOf(TableauPile(listOf(Card(Suit.CLUBS, Rank.QUEEN), Card(Suit.CLUBS, Rank.KING)), 1)) + kingsLeft.tableau.drop(1),
                foundations = kingsLeft.foundations + (Suit.CLUBS to 11),
            )

        assertNull(KlondikeAutoComplete.playOut(hidden))
        assertNull(KlondikeAutoComplete.playOut(state(foundations = Suit.entries.associateWith { 13 })))
    }

    @Test
    fun `cards left in the stock and waste are played out too`() {
        val clubs = Rank.entries.map { Card(Suit.CLUBS, it) }
        val start =
            state(
                foundations = Suit.entries.associateWith { 13 } + (Suit.CLUBS to 7),
                stock = clubs.subList(9, 13).reversed(),
                waste = clubs.subList(7, 9),
            )

        val moves = checkNotNull(KlondikeAutoComplete.playOut(start))

        assertTrue(KlondikeEngine.isWon(play(start, moves)))
    }

    @Test
    fun `with draw 1 every endgame with nothing hidden finishes`() {
        repeat(ENDGAMES) { i -> assertFinishes(randomEndgame(Random(i), drawCount = 1)) }
    }

    @Test
    fun `with draw 3 a short draw pile finishes, and every finish offered is a legal win`() {
        var offered = 0
        repeat(DRAW_THREE_ENDGAMES) { i ->
            val start = randomEndgame(Random(i), drawCount = 3, fewestHome = 10)
            val moves = KlondikeAutoComplete.playOut(start) ?: return@repeat
            offered++
            assertTrue(KlondikeEngine.isWon(play(start, moves)))
        }
        assertTrue(offered >= DRAW_THREE_ENDGAMES * 9 / 10) { "only $offered of $DRAW_THREE_ENDGAMES finished" }
    }

    private fun assertFinishes(start: KlondikeState) {
        val moves = checkNotNull(KlondikeAutoComplete.playOut(start)) { "no finish for $start" }
        assertTrue(KlondikeEngine.isWon(play(start, moves)))
    }

    /**
     * Some cards of each suit home (at least [fewestHome]), the rest shuffled between the stock and
     * the waste, the tableau empty — nothing hidden, everything still to play from the draw pile.
     */
    private fun randomEndgame(
        random: Random,
        drawCount: Int,
        fewestHome: Int = 0,
    ): KlondikeState {
        val home = Suit.entries.associateWith { random.nextInt(fewestHome, 13) }
        val left = Deck.standard52.filter { it.rank.ordinal >= home.getValue(it.suit) }.shuffled(random)
        val split = random.nextInt(0, left.size + 1)
        return state(foundations = home, stock = left.take(split), waste = left.drop(split), drawCount = drawCount)
    }

    private companion object {
        const val ENDGAMES = 200

        /** Fewer: a stalled draw-3 play-out falls back to a search. */
        const val DRAW_THREE_ENDGAMES = 60
    }
}
