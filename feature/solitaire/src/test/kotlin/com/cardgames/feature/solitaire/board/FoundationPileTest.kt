package com.cardgames.feature.solitaire.board

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FoundationPileTest {
    private fun shown(
        count: Int,
        lowestMoving: Int?,
    ) = (0 until count).filter { foundationCardShown(it, count, lowestMoving) }

    @Test
    fun `at rest only the top two cards are drawn`() {
        assertEquals(listOf(11, 12), shown(count = 13, lowestMoving = null))
        assertEquals(listOf(0), shown(count = 1, lowestMoving = null))
    }

    @Test
    fun `while cards fly up, they and the card they land on are drawn`() {
        // Complete from 9s home: the 10 (index 9) through the king are on their way.
        assertEquals((8..12).toList(), shown(count = 13, lowestMoving = 9))
    }

    @Test
    fun `a single card arriving changes nothing`() {
        assertEquals(listOf(4, 5), shown(count = 6, lowestMoving = 5))
    }
}
