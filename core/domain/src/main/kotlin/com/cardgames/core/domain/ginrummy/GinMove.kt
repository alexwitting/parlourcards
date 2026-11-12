package com.cardgames.core.domain.ginrummy

import com.cardgames.core.domain.cards.Card

/** Every move a player can make in gin rummy. See [GinPhase] for which moves are legal when. */
sealed interface GinMove {
    /** Take the discard pile's starter card (valid only during [GinPhase.UpcardDecision]). */
    data object TakeUpcard : GinMove

    /** Decline the starter card; passes to the dealer, or begins normal play if both pass. */
    data object PassUpcard : GinMove

    data object DrawFromStock : GinMove

    data object DrawFromDiscard : GinMove

    /** End the turn normally, discarding [card] from the 11-card hand. */
    data class Discard(
        val card: Card,
    ) : GinMove

    /**
     * Discard [card] and knock: the engine partitions the remaining 10 cards itself via
     * [Melds.bestPartition] (there is no player-chosen meld arrangement — gin's melds are
     * always the deadwood-minimizing ones). Legal only when that partition's deadwood is at
     * or under [GinState.KNOCK_THRESHOLD].
     */
    data class Knock(
        val card: Card,
    ) : GinMove

    /**
     * The defender moves [card] from their hand onto the meld at [meldIndex] in the current
     * [GinPhase.AwaitingLayoff.currentMelds] — extending a run at either end, or completing a
     * set to four of a kind. Illegal (and never offered) during a gin knock.
     */
    data class LayOff(
        val card: Card,
        val meldIndex: Int,
    ) : GinMove

    /**
     * Go out with all 11 cards melded, without a discard — legal only when the match's
     * [GinMatchRules.bigGin] allows it and the 11-card hand has no deadwood at all.
     */
    data object BigGin : GinMove

    /** The defender is done laying off (or never could) — resolves scoring for the hand. */
    data object SettleHand : GinMove
}
