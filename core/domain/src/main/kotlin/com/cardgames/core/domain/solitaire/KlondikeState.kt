package com.cardgames.core.domain.solitaire

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import kotlinx.serialization.Serializable

/**
 * One tableau pile. [cards] are ordered bottom → top; the first [faceDownCount] are face
 * down. Engine invariant: the top card of a non-empty pile is always face up (the engine
 * auto-flips after removals).
 */
@Serializable
data class TableauPile(
    val cards: List<Card>,
    val faceDownCount: Int,
) {
    init {
        require(faceDownCount >= 0) { "faceDownCount must not be negative" }
        require(cards.isEmpty() || faceDownCount < cards.size) {
            "top card of a non-empty pile must be face up"
        }
        require(cards.isNotEmpty() || faceDownCount == 0) { "empty pile cannot have face-down cards" }
    }

    val topCard: Card?
        get() = cards.lastOrNull()

    val faceUpCards: List<Card>
        get() = cards.drop(faceDownCount)

    val isEmpty: Boolean
        get() = cards.isEmpty()
}

/**
 * A whole Klondike position. Carries the deal's [seed] (for restarting the same deal) and its
 * [drawCount], fixed when dealt. In [stock] and [waste] the last card is the top one. Foundations
 * hold a count per suit (the cards are A up to it).
 *
 * [dealBatchRemaining] is for display only: how many of the waste's top cards came from the latest
 * draw, so draw 3's fan shows that batch and never reaches into an older one. Set on
 * `DealFromStock`, reset on `RecycleWaste`, lowered as waste cards are played.
 */
@Serializable
data class KlondikeState(
    val seed: Long,
    val drawCount: Int,
    val tableau: List<TableauPile>,
    val foundations: Map<Suit, Int>,
    val stock: List<Card>,
    val waste: List<Card>,
    val moveCount: Int = 0,
    val dealBatchRemaining: Int = 0,
) {
    init {
        require(tableau.size == TABLEAU_PILE_COUNT) { "Klondike has $TABLEAU_PILE_COUNT tableau piles" }
        require(drawCount in ALLOWED_DRAW_COUNTS) { "drawCount must be 1 or 3" }
        require(foundations.keys == Suit.entries.toSet()) { "foundations must cover all four suits" }
        require(foundations.values.all { it in 0..FOUNDATION_MAX }) { "foundation counts must be 0..13" }
        require(dealBatchRemaining in 0..waste.size) { "dealBatchRemaining must not exceed the waste pile" }
    }

    fun foundationCount(suit: Suit): Int = foundations.getValue(suit)

    /** The visible top card of a suit's foundation, or null when it's empty (contents are implied). */
    fun foundationTop(suit: Suit): Card? {
        val count = foundationCount(suit)
        if (count == 0) return null
        return Card(suit, Rank.entries[count - 1])
    }

    companion object {
        const val TABLEAU_PILE_COUNT = 7
        const val FOUNDATION_MAX = 13
        val ALLOWED_DRAW_COUNTS = setOf(1, 3)
    }
}
