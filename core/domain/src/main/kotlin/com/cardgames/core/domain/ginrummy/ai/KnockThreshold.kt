package com.cardgames.core.domain.ginrummy.ai

import com.cardgames.core.domain.ginrummy.GinState
import kotlin.math.roundToInt

/**
 * The knock threshold every AI opponent uses, relaxing as the stock runs down: near
 * [earlyThreshold] through about the first half of the stock, then toward the full legal
 * [GinState.KNOCK_THRESHOLD]. The ramp is squared rather than linear, so the player gets most of a
 * hand to build before the AI turns eager.
 *
 * Only [earlyThreshold] varies: [HeuristicGinOpponent]'s is fixed, and [MonteCarloGinOpponent]'s is
 * set per [GinDifficulty] (lower is pickier, higher knocks on weaker hands).
 */
internal fun ageRampedKnockThreshold(
    stockRemaining: Int,
    earlyThreshold: Int,
): Int {
    val drawable = (stockRemaining - GinState.STOCK_RESERVE).coerceAtLeast(0)
    val linearAgeFraction = 1.0 - (drawable.toDouble() / STARTING_STOCK_SIZE).coerceIn(0.0, 1.0)
    val ageFraction = linearAgeFraction * linearAgeFraction
    val threshold = earlyThreshold + (GinState.KNOCK_THRESHOLD - earlyThreshold) * ageFraction
    return threshold.roundToInt().coerceIn(0, GinState.KNOCK_THRESHOLD)
}

/** [GinEngine.dealHand]'s stock size: 52 - 10 - 10 - 1 upcard. */
private const val STARTING_STOCK_SIZE = 31
