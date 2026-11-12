package com.cardgames.core.domain.ginrummy.ai

/**
 * The three difficulty tiers, all one algorithm: [MonteCarloGinOpponent] with a different
 * [MonteCarloConfig].
 *
 * - [MEDIUM] is the calibrated baseline: a 4-card shortlist, an 800ms budget, depth 3, and
 *   [HeuristicGinOpponent]'s knock patience (`earlyKnockThreshold = 1`).
 * - [EASY] has a narrower shortlist, a smaller budget and a much more eager knock (5).
 * - [HARD] has a wider shortlist and a bigger budget, and plays defensively
 *   ([MonteCarloConfig.defensiveWeight]): it avoids discarding cards that build on what the player
 *   has picked up from the pile. Its knock patience stays at MEDIUM's: waiting for gin
 *   (`earlyKnockThreshold = 0`) passes up good knocks, and loses to EASY's early ones.
 *
 * `GinTierBenchmark` (opt-in, in this module's tests) plays the tiers against each other; run it
 * before retuning any of these.
 */
enum class GinDifficulty {
    EASY,
    MEDIUM,
    HARD,
    ;

    fun toConfig(): MonteCarloConfig =
        when (this) {
            EASY ->
                MonteCarloConfig(
                    shortlistSize = 2,
                    roundCeiling = 150,
                    rolloutDepth = MonteCarloGinOpponent.CALIBRATED_ROLLOUT_DEPTH,
                    budgetMillis = 400L,
                    earlyKnockThreshold = 5,
                )
            MEDIUM ->
                MonteCarloConfig(
                    shortlistSize = 4,
                    roundCeiling = 500,
                    rolloutDepth = MonteCarloGinOpponent.CALIBRATED_ROLLOUT_DEPTH,
                    budgetMillis = 800L,
                    earlyKnockThreshold = 1,
                )
            HARD ->
                MonteCarloConfig(
                    shortlistSize = 7,
                    roundCeiling = 900,
                    rolloutDepth = MonteCarloGinOpponent.CALIBRATED_ROLLOUT_DEPTH,
                    budgetMillis = 1600L,
                    earlyKnockThreshold = 1,
                    defensiveWeight = HARD_DEFENSIVE_WEIGHT,
                )
        }
}

/**
 * Points of evaluation [GinDifficulty.HARD] gives up per unit of [discardDanger] — a starting
 * value, not a measured one: big enough that a card matching one the human just picked up loses
 * to a merely similar alternative, small enough that it never trades away real deadwood for it.
 * Retune against `GinTierBenchmark`.
 */
internal const val HARD_DEFENSIVE_WEIGHT = 2.0
