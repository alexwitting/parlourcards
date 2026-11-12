package com.cardgames.core.domain.ginrummy.ai

import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.HandOutcome
import com.cardgames.core.domain.ginrummy.Melds

/**
 * Plays a sampled [GinState] (see [HiddenWorldSampler]) forward for both seats with a cheap shared
 * [policy], until the hand ends or [maxTicks] runs out. [maxTicks] is the rollout depth, and it
 * usually binds: a position cut off at the horizon is a normal result, scored by
 * [scoreForPlayer]'s fallback.
 */
object RolloutRunner {
    fun rollout(
        start: GinState,
        policy: GinOpponent,
        maxTicks: Int,
    ): GinState {
        var state = start
        var ticks = 0
        while (!GinEngine.isHandOver(state) && ticks < maxTicks) {
            state = GinOpponentDriver.driveOneDecision(state, GinPlayer.PLAYER, policy)
            state = GinOpponentDriver.driveOneDecision(state, GinPlayer.OPPONENT, policy)
            ticks++
        }
        return state
    }

    /**
     * How favorably [state] resolved for [GinPlayer.PLAYER] — the rollout-local "me" seat (see
     * [HiddenWorldSampler]'s doc comment). Positive favors PLAYER, negative favors OPPONENT.
     */
    fun scoreForPlayer(state: GinState): Int =
        when (val phase = state.phase) {
            is GinPhase.HandOver -> scoreHandOver(phase)
            is GinPhase.MatchOver -> if (phase.winner == GinPlayer.PLAYER) MATCH_WIN_SCORE else -MATCH_WIN_SCORE
            // Horizon cut before the hand resolved naturally (or mid-layoff, an odd but possible
            // cut point) — fall back to the current deadwood margin as a cheap "who's ahead"
            // proxy, via the same exact evaluator the rest of the engine already shares.
            else -> {
                val playerDeadwood = Melds.bestPartition(state.playerHand).deadwoodPips
                val opponentDeadwood = Melds.bestPartition(state.opponentHand).deadwoodPips
                opponentDeadwood - playerDeadwood
            }
        }

    private fun scoreHandOver(phase: GinPhase.HandOver): Int {
        val result = phase.result
        return when {
            result.outcome == HandOutcome.BLOCKED -> 0
            result.winner == GinPlayer.PLAYER -> result.pointsScored
            else -> -result.pointsScored
        }
    }

    /**
     * Deliberately larger than any plausible single-hand point total (deadwood is capped well
     * under 100 per hand; even a generous gin/undercut bonus stays far below this) so a rollout
     * that reaches an actual match win always outranks one that merely wins a hand.
     */
    private const val MATCH_WIN_SCORE = 1000
}
