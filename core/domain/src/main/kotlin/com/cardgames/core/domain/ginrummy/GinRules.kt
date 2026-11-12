package com.cardgames.core.domain.ginrummy

import com.cardgames.core.domain.cards.Card

/** Legality only — no state mutation. Every check dispatches on the current [GinPhase] first. */
internal object GinRules {
    fun isLegal(
        state: GinState,
        move: GinMove,
    ): Boolean =
        when (val phase = state.phase) {
            is GinPhase.UpcardDecision -> move is GinMove.TakeUpcard || move is GinMove.PassUpcard
            is GinPhase.AwaitingDraw ->
                when (move) {
                    GinMove.DrawFromStock -> state.stock.size > GinState.STOCK_RESERVE
                    GinMove.DrawFromDiscard -> state.discardPile.isNotEmpty()
                    else -> false
                }
            is GinPhase.AwaitingDiscard -> isLegalDiscardPhaseMove(state, phase, move)
            is GinPhase.AwaitingLayoff -> isLegalLayoffPhaseMove(state, phase, move)
            is GinPhase.HandOver -> false
            is GinPhase.MatchOver -> false
        }

    private fun isLegalDiscardPhaseMove(
        state: GinState,
        phase: GinPhase.AwaitingDiscard,
        move: GinMove,
    ): Boolean {
        if (move == GinMove.BigGin) {
            return state.rules.bigGin && Melds.bestPartition(state.handOf(phase.toMove)).deadwoodPips == 0
        }
        val card =
            when (move) {
                is GinMove.Discard -> move.card
                is GinMove.Knock -> move.card
                else -> return false
            }
        if (card !in state.handOf(phase.toMove) || card == phase.mustNotDiscard) return false
        return move !is GinMove.Knock || knockDeadwood(state, phase.toMove, card) <= GinState.KNOCK_THRESHOLD
    }

    private fun knockDeadwood(
        state: GinState,
        player: GinPlayer,
        discarding: Card,
    ): Int = Melds.bestPartition(state.handOf(player) - discarding).deadwoodPips

    private fun isLegalLayoffPhaseMove(
        state: GinState,
        phase: GinPhase.AwaitingLayoff,
        move: GinMove,
    ): Boolean =
        when (move) {
            is GinMove.LayOff -> !phase.isGin && canLayOff(state, phase, move)
            GinMove.SettleHand -> true
            else -> false
        }

    private fun canLayOff(
        state: GinState,
        phase: GinPhase.AwaitingLayoff,
        move: GinMove.LayOff,
    ): Boolean {
        val defender = phase.knocker.other
        if (move.card !in state.handOf(defender)) return false
        val meld = phase.currentMelds.getOrNull(move.meldIndex) ?: return false
        return extendedMeldOrNull(meld, move.card) != null
    }

    /** The meld [card] would produce by joining [meld], or null if it can't legally join. */
    internal fun extendedMeldOrNull(
        meld: Meld,
        card: Card,
    ): Meld? =
        when (meld) {
            is Meld.Set ->
                if (meld.cards.size < SET_MAX && meld.cards.first().rank == card.rank && meld.cards.none { it.suit == card.suit }) {
                    Meld.Set(meld.cards + card)
                } else {
                    null
                }
            is Meld.Run ->
                when {
                    meld.cards.first().suit != card.suit -> null
                    card.rank.isOneAbove(meld.cards.last().rank) -> Meld.Run(meld.cards + card)
                    meld.cards
                        .first()
                        .rank
                        .isOneAbove(card.rank) -> Meld.Run(listOf(card) + meld.cards)
                    else -> null
                }
        }

    private const val SET_MAX = 4
}
