package com.cardgames.feature.ginrummy.motion

import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinMove
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.feature.ginrummy.GinUiState
import com.cardgames.feature.ginrummy.isPlayerTurn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The pure half of the on-screen deal (`GinDeal.kt`) — which cards the table shows mid-deal. */
class GinDealTest {
    private val fresh = GinEngine.newMatch(seed = 7L)

    @Test
    fun `a newly dealt hand is a fresh deal, one with a move made is not`() {
        assertTrue(fresh.isFreshDeal())
        assertFalse(GinEngine.apply(fresh, GinMove.PassUpcard).isFreshDeal())
    }

    @Test
    fun `deal order alternates seats starting with the non-dealer and ends with the upcard`() {
        val order = fresh.dealOrder()
        val first = fresh.handOf(fresh.dealer.other)
        val second = fresh.handOf(fresh.dealer)

        assertEquals(DEAL_CARD_COUNT, order.size)
        assertEquals(first.first(), order[0])
        assertEquals(second.first(), order[1])
        assertEquals(fresh.discardPile.single(), order.last())
        assertEquals((first + second + fresh.discardPile).toSet(), order.toSet())
    }

    @Test
    fun `before any card is dealt everything sits on the stock with the first card on top`() {
        val table = fresh.midDeal(0)

        assertTrue(table.playerHand.isEmpty())
        assertTrue(table.opponentHand.isEmpty())
        assertTrue(table.discardPile.isEmpty())
        assertEquals(fresh.stock.size + DEAL_CARD_COUNT, table.stock.size)
        assertEquals(fresh.dealOrder().first(), table.stock.last())
    }

    @Test
    fun `part way through, each seat holds only what has been dealt to it`() {
        val table = fresh.midDeal(5)

        assertEquals(3, table.handOf(fresh.dealer.other).size)
        assertEquals(2, table.handOf(fresh.dealer).size)
        assertEquals(fresh.dealOrder()[5], table.stock.last())
    }

    @Test
    fun `nothing is the player's to play while the deal is in progress`() {
        assertFalse(GinUiState(game = fresh.midDeal(10)).isPlayerTurn)
    }

    @Test
    fun `once every card is dealt the table is exactly the real position`() {
        assertSame(fresh, fresh.midDeal(DEAL_CARD_COUNT))
        assertEquals(GinState.HAND_SIZE * 2 + 1, DEAL_CARD_COUNT)
    }
}
