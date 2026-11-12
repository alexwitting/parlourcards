package com.cardgames.core.domain.ginrummy.ai

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState

/** Which pile a [GinOpponent] wants to draw from — or, during the upcard phase, take/pass it. */
enum class DrawPreference {
    STOCK,
    DISCARD,
}

/**
 * What a [GinOpponent] may know when it decides: what a player at the table could see — its own
 * hand, the whole discard pile, the counts, and the cards the other seat is known to hold
 * ([opponentKnownCards], seen going from the pile into that hand). Never the other hand or the
 * stock. [mustNotDiscard], when set, is the card it just took from the pile and can't throw back.
 */
data class OpponentView(
    val ownHand: List<Card>,
    val discardPile: List<Card>,
    val stockRemaining: Int,
    val opponentHandSize: Int,
    val mustNotDiscard: Card? = null,
    val opponentKnownCards: List<Card> = emptyList(),
)

/** The information-restricted view of [this] state from [seat]'s side of the table. */
fun GinState.opponentView(
    seat: GinPlayer,
    mustNotDiscard: Card? = null,
): OpponentView =
    OpponentView(
        ownHand = handOf(seat),
        discardPile = discardPile,
        stockRemaining = stock.size,
        opponentHandSize = handOf(seat.other).size,
        mustNotDiscard = mustNotDiscard,
        opponentKnownCards = knownHeldBy(seat.other),
    )
