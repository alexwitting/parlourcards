package com.cardgames.core.domain.ginrummy

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.testing.card
import com.cardgames.core.testing.cards
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MeldsTest {
    @Test
    fun `an empty hand has no melds and no deadwood`() {
        val partition = Melds.bestPartition(emptyList())

        assertTrue(partition.melds.isEmpty())
        assertTrue(partition.deadwood.isEmpty())
        assertEquals(0, partition.deadwoodPips)
    }

    @Test
    fun `three consecutive same-suit cards form a run`() {
        val hand = cards("4♥ 5♥ 6♥")

        val partition = Melds.bestPartition(hand)

        assertEquals(listOf(Meld.Run(hand)), partition.melds)
        assertEquals(0, partition.deadwoodPips)
    }

    @Test
    fun `three same-rank cards form a set`() {
        val hand = cards("9♣ 9♦ 9♥")

        val partition = Melds.bestPartition(hand)

        assertEquals(listOf(Meld.Set(hand)), partition.melds)
        assertEquals(0, partition.deadwoodPips)
    }

    @Test
    fun `all four cards of a rank are used as one set when nothing competes for them`() {
        val hand = Suit.entries.map { Card(it, Rank.KING) }

        val partition = Melds.bestPartition(hand)

        assertEquals(listOf(Meld.Set(hand)), partition.melds)
        assertEquals(0, partition.deadwoodPips)
    }

    @Test
    fun `cards with no meld are entirely deadwood at their pip value`() {
        val ace = card("A♠")
        val king = card("K♥")
        val five = card("5♣")

        val partition = Melds.bestPartition(listOf(ace, king, five))

        assertTrue(partition.melds.isEmpty())
        assertEquals(setOf(ace, king, five), partition.deadwood.toSet())
        assertEquals(1 + 10 + 5, partition.deadwoodPips)
    }

    @Test
    fun `a shared card resolves to whichever meld leaves less deadwood - run wins`() {
        // 6s: spades could join the 7-8 run, or the two other 6s as a set.
        // Run {6s,7s,8s} + deadwood {6h,6d} = 12 pips beats Set {6s,6h,6d} + deadwood {7s,8s} = 15.
        val sixSpades = card("6♠")
        val hand =
            listOf(
                sixSpades,
                card("7♠"),
                card("8♠"),
                card("6♥"),
                card("6♦"),
            )

        val partition = Melds.bestPartition(hand)

        assertEquals(1, partition.melds.size)
        val meld = partition.melds.single()
        assertTrue(meld is Meld.Run && sixSpades in meld.cards)
        assertEquals(12, partition.deadwoodPips)
    }

    @Test
    fun `a shared card resolves to whichever meld leaves less deadwood - set wins`() {
        // Symmetric to the run-wins case: here the run side has nowhere to extend, so folding
        // the shared card into the set (clearing all three 6s) beats leaving it in a 2-card
        // non-meld with a lone 5.
        val sixSpades = card("6♠")
        val hand =
            listOf(
                card("5♠"),
                sixSpades,
                card("6♥"),
                card("6♦"),
            )

        val partition = Melds.bestPartition(hand)

        assertEquals(1, partition.melds.size)
        val meld = partition.melds.single()
        assertTrue(meld is Meld.Set && sixSpades in meld.cards)
        assertEquals(5, partition.deadwoodPips)
    }

    @Test
    fun `giving up a four-card set for a run plus a three-card set beats keeping the four-card set`() {
        // 5s-6s-7s-8s as a run (using only 3 of the 4 sevens elsewhere) frees 7h-7d-7c to be
        // its own set: total deadwood 0. Keeping 7s-7h-7d-7c as a 4-set strands 5s,6s,8s (not
        // consecutive without the 7) plus junk as deadwood. Classic overlapping-meld case —
        // this is the one that breaks a greedy solver.
        val hand =
            cards("5♠ 6♠ 7♠ 8♠ 7♥ 7♦ 7♣")

        val partition = Melds.bestPartition(hand)

        assertEquals(0, partition.deadwoodPips)
        assertEquals(2, partition.melds.size)
        assertTrue(partition.melds.any { it is Meld.Run && it.cards.size == 4 })
        assertTrue(partition.melds.any { it is Meld.Set && it.cards.size == 3 })
    }

    @Test
    fun `two separate runs are both found`() {
        val lowRun = cards("A♣ 2♣ 3♣")
        val highRun = cards("J♥ Q♥ K♥")

        val partition = Melds.bestPartition(lowRun + highRun)

        assertEquals(2, partition.melds.size)
        assertEquals(0, partition.deadwoodPips)
    }

    @Test
    fun `a full gin hand has zero deadwood`() {
        val quad = Suit.entries.map { Card(it, Rank.TEN) }
        val run = cards("2♠ 3♠ 4♠")
        val triple = cards("7♥ 7♦ 7♣")

        val partition = Melds.bestPartition(quad + run + triple)

        assertEquals(0, partition.deadwoodPips)
        assertEquals(3, partition.melds.size)
    }

    @Test
    fun `a run cannot wrap from king to ace`() {
        assertThrows(IllegalArgumentException::class.java) {
            Meld.Run(cards("K♠ A♠ 2♠"))
        }
    }

    @Test
    fun `Set rejects the wrong size, mixed ranks, and duplicate suits`() {
        assertThrows(IllegalArgumentException::class.java) {
            Meld.Set(cards("5♣ 5♦"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Meld.Set(cards("5♣ 6♦ 5♥"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Meld.Set(cards("5♣ 5♣ 5♥"))
        }
    }

    @Test
    fun `Run rejects the wrong size, mixed suits, and non-consecutive ranks`() {
        assertThrows(IllegalArgumentException::class.java) {
            Meld.Run(cards("5♣ 6♣"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Meld.Run(cards("5♣ 6♦ 7♣"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Meld.Run(cards("5♣ 7♣ 8♣"))
        }
    }
}
