package com.cardgames.core.domain.ginrummy.ai

import com.cardgames.core.domain.cards.Card

/**
 * The AI's decisions. The engine and ViewModel see only this interface, over the restricted
 * [OpponentView], so difficulties or other algorithms plug in without engine changes.
 * Implementations hold no state: everything a player could know is in [OpponentView] on each call.
 *
 * Laying off isn't here: laying off every card that fits is always right, so [GinOpponentDriver]
 * does it.
 */
interface GinOpponent {
    /** Stock or the discard pile's top card — also doubles as take/pass during the upcard phase. */
    fun chooseDraw(view: OpponentView): DrawPreference

    /** Which card to discard from the current (11-card) hand. Must respect [OpponentView.mustNotDiscard]. */
    fun chooseDiscard(view: OpponentView): Card

    /** Whether to knock this turn — using whatever [chooseDiscard] would return as the knock's discard. */
    fun shouldKnock(view: OpponentView): Boolean
}
