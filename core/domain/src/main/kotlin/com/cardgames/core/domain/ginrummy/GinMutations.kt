package com.cardgames.core.domain.ginrummy

import com.cardgames.core.domain.cards.Card

/** State transitions only — callers gate on [GinRules.isLegal] first. */
internal object GinMutations {
    fun apply(
        state: GinState,
        move: GinMove,
    ): GinState =
        when (move) {
            GinMove.TakeUpcard -> takeUpcard(state)
            GinMove.PassUpcard -> passUpcard(state)
            GinMove.DrawFromStock -> draw(state, fromDiscard = false)
            GinMove.DrawFromDiscard -> draw(state, fromDiscard = true)
            is GinMove.Discard -> discard(state, move.card)
            is GinMove.Knock -> knock(state, move.card)
            GinMove.BigGin -> bigGin(state)
            is GinMove.LayOff -> layOff(state, move)
            GinMove.SettleHand -> settleHand(state)
        }

    private fun takeUpcard(state: GinState): GinState {
        val phase = state.phase as GinPhase.UpcardDecision
        val upcard = state.discardPile.last()
        return state
            .withHand(phase.deciding, state.handOf(phase.deciding) + upcard)
            .withKnownHeld(phase.deciding, state.knownHeldBy(phase.deciding) + upcard)
            .copy(
                discardPile = state.discardPile.dropLast(1),
                phase = GinPhase.AwaitingDiscard(phase.deciding, mustNotDiscard = upcard),
            )
    }

    private fun passUpcard(state: GinState): GinState {
        val phase = state.phase as GinPhase.UpcardDecision
        val nonDealer = state.dealer.other
        val nextPhase =
            if (phase.deciding == nonDealer) {
                GinPhase.UpcardDecision(state.dealer)
            } else {
                // Both passed: non-dealer begins normal rotation by drawing from stock.
                GinPhase.AwaitingDraw(nonDealer)
            }
        return state.copy(phase = nextPhase)
    }

    private fun draw(
        state: GinState,
        fromDiscard: Boolean,
    ): GinState {
        val phase = state.phase as GinPhase.AwaitingDraw
        val card = if (fromDiscard) state.discardPile.last() else state.stock.last()
        val updated = state.withHand(phase.toMove, state.handOf(phase.toMove) + card)
        return if (fromDiscard) {
            updated
                .withKnownHeld(phase.toMove, state.knownHeldBy(phase.toMove) + card)
                .copy(
                    discardPile = state.discardPile.dropLast(1),
                    phase = GinPhase.AwaitingDiscard(phase.toMove, mustNotDiscard = card),
                )
        } else {
            updated.copy(stock = state.stock.dropLast(1), phase = GinPhase.AwaitingDiscard(phase.toMove))
        }
    }

    private fun discard(
        state: GinState,
        card: Card,
    ): GinState {
        val phase = state.phase as GinPhase.AwaitingDiscard
        val afterDiscard =
            state
                .withHand(phase.toMove, state.handOf(phase.toMove) - card)
                .withKnownHeld(phase.toMove, state.knownHeldBy(phase.toMove) - card)
                .copy(discardPile = state.discardPile + card)
        // The 2-card stock reserve is never dealt — running down to it blocks the hand
        // instead of offering the other player a draw.
        val nextPhase =
            if (afterDiscard.stock.size <= GinState.STOCK_RESERVE) {
                GinPhase.HandOver(BLOCKED_RESULT)
            } else {
                GinPhase.AwaitingDraw(phase.toMove.other)
            }
        return afterDiscard.copy(phase = nextPhase)
    }

    private fun knock(
        state: GinState,
        card: Card,
    ): GinState {
        val phase = state.phase as GinPhase.AwaitingDiscard
        val remaining = state.handOf(phase.toMove) - card
        val partition = Melds.bestPartition(remaining)
        return state
            .withHand(phase.toMove, remaining)
            .withKnownHeld(phase.toMove, state.knownHeldBy(phase.toMove) - card)
            .copy(
                discardPile = state.discardPile + card,
                phase =
                    GinPhase.AwaitingLayoff(
                        knocker = phase.toMove,
                        isGin = partition.deadwoodPips == 0,
                        knockerDiscard = card,
                        knockerMelds = partition.melds,
                    ),
            )
    }

    private fun bigGin(state: GinState): GinState {
        val phase = state.phase as GinPhase.AwaitingDiscard
        return state.copy(
            phase =
                GinPhase.AwaitingLayoff(
                    knocker = phase.toMove,
                    isGin = true,
                    knockerDiscard = null,
                    knockerMelds = Melds.bestPartition(state.handOf(phase.toMove)).melds,
                    isBigGin = true,
                ),
        )
    }

    private fun layOff(
        state: GinState,
        move: GinMove.LayOff,
    ): GinState {
        val phase = state.phase as GinPhase.AwaitingLayoff
        val defender = phase.knocker.other
        val extended = requireNotNull(GinRules.extendedMeldOrNull(phase.currentMelds[move.meldIndex], move.card))
        val newMelds = phase.currentMelds.toMutableList().apply { set(move.meldIndex, extended) }
        return state
            .withHand(defender, state.handOf(defender) - move.card)
            .copy(
                phase = phase.copy(currentMelds = newMelds),
                laidOff = state.laidOff + (phase.knocker to (state.laidOffOnto(phase.knocker) + move.card)),
            )
    }

    private fun settleHand(state: GinState): GinState {
        val phase = state.phase as GinPhase.AwaitingLayoff
        val defender = phase.knocker.other

        val knockerMeldCards = phase.knockerMelds.flatMap { it.cards }.toSet()
        val knockerDeadwood = state.handOf(phase.knocker).filterNot { it in knockerMeldCards }.sumOf { it.rank.pipValue }
        val defenderDeadwood = Melds.bestPartition(state.handOf(defender)).deadwoodPips

        val ginBonus =
            when {
                phase.isBigGin -> GinScoring.BIG_GIN_BONUS
                phase.isGin && state.rules.ginBonus -> GinScoring.GIN_BONUS
                else -> 0
            }
        val score = GinScoring.scoreKnock(knockerDeadwood, defenderDeadwood, phase.isGin, ginBonus)
        val winner = if (score.knockerWins) phase.knocker else defender
        val newMatchScores = state.matchScores + (winner to (state.matchScores.getValue(winner) + score.points))
        val newBoxes = state.boxes + (winner to (state.boxes.getValue(winner) + 1))
        val result = HandResult(score.outcome, winner, score.points, knockerDeadwood, defenderDeadwood)

        return if (newMatchScores.getValue(winner) >= state.rules.target) {
            val loser = winner.other
            val finalScore =
                GinScoring.settleMatch(
                    winnerRawScore = newMatchScores.getValue(winner),
                    winnerBoxes = newBoxes.getValue(winner),
                    loserRawScore = newMatchScores.getValue(loser),
                )
            state.copy(
                matchScores = newMatchScores + (winner to finalScore),
                boxes = newBoxes,
                phase = GinPhase.MatchOver(winner, lastHand = result, raceScores = newMatchScores),
            )
        } else {
            state.copy(matchScores = newMatchScores, boxes = newBoxes, phase = GinPhase.HandOver(result))
        }
    }

    private val BLOCKED_RESULT =
        HandResult(HandOutcome.BLOCKED, winner = null, pointsScored = 0, knockerDeadwood = 0, defenderDeadwood = 0)
}
