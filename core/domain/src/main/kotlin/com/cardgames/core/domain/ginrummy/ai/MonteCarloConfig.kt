package com.cardgames.core.domain.ginrummy.ai

/**
 * The settings [MonteCarloGinOpponent] is tuned by, one set per [GinDifficulty].
 *
 * @param shortlistSize how many of the rule-ranked discards are simulated at all; the rest never are.
 * @param roundCeiling the most rollouts any one candidate gets — a deterministic cap (what tests
 *   drive); in play, [budgetMillis] stops the search first.
 * @param rolloutDepth how many ticks (one [GinOpponentDriver.driveOneDecision] per seat) a rollout
 *   plays before its position is scored. Kept at [MonteCarloGinOpponent.CALIBRATED_ROLLOUT_DEPTH]
 *   for every tier: deeper rollouts measure worse, as the rollout policy repairs a weak start
 *   toward parity and washes out the difference being measured.
 * @param budgetMillis the wall-clock budget for one whole decision, shared by every candidate.
 * @param earlyKnockThreshold how patient [MonteCarloGinOpponent.shouldKnock] is early in a hand (see
 *   [ageRampedKnockThreshold]): lower holds out for a stronger hand, higher knocks on weaker ones.
 * @param defensiveWeight points taken off a discard's value per unit of [discardDanger] against the
 *   cards the other seat is known to hold; 0 turns defence off.
 */
data class MonteCarloConfig(
    val shortlistSize: Int,
    val roundCeiling: Int,
    val rolloutDepth: Int,
    val budgetMillis: Long,
    val earlyKnockThreshold: Int,
    val defensiveWeight: Double = 0.0,
)
