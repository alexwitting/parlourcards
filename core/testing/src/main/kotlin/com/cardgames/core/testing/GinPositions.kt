package com.cardgames.core.testing

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Deck
import com.cardgames.core.domain.ginrummy.GinMatchRules
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState

/**
 * A legal Gin position built around one seat's [hand], without a real deal. [seat] holds [hand];
 * the other seat holds [otherHand] (by default the first ten cards left in canonical deck order),
 * the pile shows [upcard] (by default the next card left) and the rest is the stock — so the same
 * arguments always build the same position. The other seat dealt, so [seat] moves first. [phase]
 * defaults to [seat] discarding an 11-card hand, or drawing with 10. Seed 0, nil–nil, no moves yet —
 * `.copy(...)` anything else.
 */
fun ginPosition(
    hand: List<Card>,
    seat: GinPlayer = GinPlayer.PLAYER,
    phase: GinPhase = if (hand.size > GinState.HAND_SIZE) GinPhase.AwaitingDiscard(seat) else GinPhase.AwaitingDraw(seat),
    otherHand: List<Card>? = null,
    upcard: Card? = null,
    rules: GinMatchRules = GinMatchRules(),
): GinState {
    val placed = hand + otherHand.orEmpty() + listOfNotNull(upcard)
    require(placed.size == placed.toSet().size) { "A card is placed twice: $placed" }
    var left = Deck.standard52 - placed.toSet()
    val other = otherHand ?: left.take(GinState.HAND_SIZE).also { left = left.drop(GinState.HAND_SIZE) }
    val pile = upcard ?: left.first().also { left = left.drop(1) }
    return GinState(
        seed = 0L,
        handNumber = 1,
        dealer = seat.other,
        playerHand = if (seat == GinPlayer.PLAYER) hand else other,
        opponentHand = if (seat == GinPlayer.PLAYER) other else hand,
        stock = left,
        discardPile = listOf(pile),
        phase = phase,
        matchScores = GinPlayer.entries.associateWith { 0 },
        boxes = GinPlayer.entries.associateWith { 0 },
        rules = rules,
    )
}
