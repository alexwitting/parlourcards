package com.cardgames.core.domain.ginrummy.ai

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinMove
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.Melds

/**
 * Turns a [GinOpponent]'s decisions into engine moves. This is orchestration, not strategy —
 * `GinViewModel` drives the AI's turns with exactly this, and the AI-vs-AI simulation tests
 * reuse it verbatim for both seats.
 */
object GinOpponentDriver {
    /**
     * If the current phase is waiting on [seat], asks [opponent] and applies its decision;
     * otherwise returns [state] unchanged. Safe to call every seat every "tick" without first
     * checking whose turn it is.
     */
    fun driveOneDecision(
        state: GinState,
        seat: GinPlayer,
        opponent: GinOpponent,
    ): GinState =
        when (val phase = state.phase) {
            is GinPhase.UpcardDecision ->
                if (phase.deciding != seat) {
                    state
                } else {
                    val choice = opponent.chooseDraw(state.opponentView(seat))
                    val move = if (choice == DrawPreference.DISCARD) GinMove.TakeUpcard else GinMove.PassUpcard
                    GinEngine.apply(state, move)
                }
            is GinPhase.AwaitingDraw ->
                if (phase.toMove != seat) {
                    state
                } else {
                    val choice = opponent.chooseDraw(state.opponentView(seat))
                    val move = if (choice == DrawPreference.DISCARD) GinMove.DrawFromDiscard else GinMove.DrawFromStock
                    GinEngine.apply(state, move)
                }
            is GinPhase.AwaitingDiscard -> if (phase.toMove != seat) state else driveDiscardOrKnock(state, seat, phase, opponent)
            is GinPhase.AwaitingLayoff -> if (phase.knocker == seat) state else driveOneLayoff(state, seat, phase)
            is GinPhase.HandOver, is GinPhase.MatchOver -> state
        }

    private fun driveDiscardOrKnock(
        state: GinState,
        seat: GinPlayer,
        phase: GinPhase.AwaitingDiscard,
        opponent: GinOpponent,
    ): GinState {
        // Going out with every card melded is never wrong when the match allows it.
        if (GinEngine.isLegal(state, GinMove.BigGin)) return GinEngine.apply(state, GinMove.BigGin)
        val view = state.opponentView(seat, mustNotDiscard = phase.mustNotDiscard)
        val discard = opponent.chooseDiscard(view)
        val knockMove = GinMove.Knock(discard)
        val move = if (opponent.shouldKnock(view) && GinEngine.isLegal(state, knockMove)) knockMove else GinMove.Discard(discard)
        return GinEngine.apply(state, move)
    }

    /**
     * Not a [GinOpponent] decision: laying off every deadwood card that fits is always right. One
     * [GinMove.LayOff] per call (so each can be shown landing), then the settle once none remain.
     * Only deadwood is offered: a card from the defender's own meld costs nothing where it is, so
     * moving it can't help.
     */
    private fun driveOneLayoff(
        state: GinState,
        defender: GinPlayer,
        phase: GinPhase.AwaitingLayoff,
    ): GinState {
        val ownMeldCards =
            Melds
                .bestPartition(state.handOf(defender))
                .melds
                .flatMap { it.cards }
                .toSet()
        val move = findAnyLegalLayoff(state, phase, defender, ownMeldCards) ?: GinMove.SettleHand
        return GinEngine.apply(state, move)
    }

    private fun findAnyLegalLayoff(
        state: GinState,
        phase: GinPhase.AwaitingLayoff,
        defender: GinPlayer,
        ownMeldCards: Set<Card>,
    ): GinMove.LayOff? {
        for (card in state.handOf(defender)) {
            if (card in ownMeldCards) continue
            for (meldIndex in phase.currentMelds.indices) {
                val move = GinMove.LayOff(card, meldIndex)
                if (GinEngine.isLegal(state, move)) return move
            }
        }
        return null
    }
}
