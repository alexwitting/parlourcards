package com.cardgames.feature.ginrummy

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.Melds

/**
 * The cards in the player's hand that the discard pile's top card would make a meld with — the
 * sage rings while the player chooses where to draw from. Empty when it makes no meld.
 */
internal fun GinState.discardMeldPartners(): Set<Card> {
    val top = discardPile.lastOrNull() ?: return emptySet()
    val meld = Melds.bestPartition(playerHand + top).melds.firstOrNull { top in it.cards } ?: return emptySet()
    return (meld.cards - top).toSet()
}

/** The Hint button's suggestion for the player's decision in [this] position, or null when there's none to make. */
internal fun GinState.suggestion(): GinHint? =
    when (val phase = phase) {
        is GinPhase.UpcardDecision, is GinPhase.AwaitingDraw ->
            if (discardMeldPartners().isNotEmpty()) GinHint.TakeDiscard else GinHint.DrawStock
        is GinPhase.AwaitingDiscard ->
            playerHand
                .filter { it != phase.mustNotDiscard }
                .minByOrNull { Melds.bestPartition(playerHand - it).deadwoodPips }
                ?.let(GinHint::Discard)
        else -> null
    }

/** The deadwood left after throwing [card] from an 11-card hand. */
internal fun GinState.deadwoodAfterDiscarding(card: Card): Int = Melds.bestPartition(playerHand - card).deadwoodPips
