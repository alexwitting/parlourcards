package com.cardgames.core.domain.ginrummy.ai

import com.cardgames.core.testing.card
import com.cardgames.core.testing.cards
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiscardDangerTest {
    private val pickedUp = cards("7♥")

    @Test
    fun `a card of the same rank is a strong link`() {
        assertEquals(2, discardDanger(card("7♠"), pickedUp))
    }

    @Test
    fun `the next card in the same suit is a strong link`() {
        assertEquals(2, discardDanger(card("8♥"), pickedUp))
        assertEquals(2, discardDanger(card("6♥"), pickedUp))
    }

    @Test
    fun `two ranks away in the same suit is a weak link`() {
        assertEquals(1, discardDanger(card("9♥"), pickedUp))
    }

    @Test
    fun `unrelated cards and an empty known list are safe`() {
        assertEquals(0, discardDanger(card("8♣"), pickedUp))
        assertEquals(0, discardDanger(card("10♥"), pickedUp))
        assertEquals(0, discardDanger(card("8♥"), emptyList()))
    }

    @Test
    fun `links to several known cards add up`() {
        val both = pickedUp + card("9♥")

        assertEquals(4, discardDanger(card("8♥"), both))
    }
}
