package com.cardgames.core.domain.cards

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class DeckTest {
    @Test
    fun `standard deck has 52 unique cards`() {
        assertEquals(52, Deck.standard52.size)
        assertEquals(52, Deck.standard52.toSet().size)
    }

    @Test
    fun `standard deck has 13 cards of each suit`() {
        Suit.entries.forEach { suit ->
            assertEquals(13, Deck.standard52.count { it.suit == suit })
        }
    }

    @Test
    fun `shuffle is a permutation of the full deck`() {
        val shuffled = Deck.shuffled(Random(seed = 42))

        assertEquals(Deck.standard52.toSet(), shuffled.toSet())
    }

    @Test
    fun `same seed produces the same deal`() {
        assertEquals(Deck.shuffled(Random(seed = 7)), Deck.shuffled(Random(seed = 7)))
    }

    @Test
    fun `different seeds produce different deals`() {
        assertNotEquals(Deck.shuffled(Random(seed = 1)), Deck.shuffled(Random(seed = 2)))
    }

    @Test
    fun `shuffle does not mutate the canonical deck`() {
        val before = Deck.standard52.toList()
        Deck.shuffled(Random(seed = 3))

        assertEquals(before, Deck.standard52)
    }

    @Test
    fun `red suits are hearts and diamonds`() {
        assertTrue(Suit.HEARTS.isRed)
        assertTrue(Suit.DIAMONDS.isRed)
        assertFalse(Suit.CLUBS.isRed)
        assertFalse(Suit.SPADES.isRed)
        assertTrue(Suit.CLUBS.isBlack)
        assertTrue(Suit.SPADES.isBlack)
    }

    @Test
    fun `rank values are ace-low 1 to 13`() {
        assertEquals(1, Rank.ACE.value)
        assertEquals(13, Rank.KING.value)
        assertEquals(Rank.entries.map { it.value }, (1..13).toList())
    }

    @Test
    fun `pip values cap face cards at 10`() {
        assertEquals(1, Rank.ACE.pipValue)
        assertEquals(9, Rank.NINE.pipValue)
        assertEquals(10, Rank.TEN.pipValue)
        assertEquals(10, Rank.JACK.pipValue)
        assertEquals(10, Rank.QUEEN.pipValue)
        assertEquals(10, Rank.KING.pipValue)
    }

    @Test
    fun `isOneAbove holds only for adjacent ascending ranks`() {
        assertTrue(Rank.TWO.isOneAbove(Rank.ACE))
        assertTrue(Rank.KING.isOneAbove(Rank.QUEEN))
        assertFalse(Rank.ACE.isOneAbove(Rank.KING))
        assertFalse(Rank.THREE.isOneAbove(Rank.ACE))
        assertFalse(Rank.ACE.isOneAbove(Rank.ACE))
    }
}
