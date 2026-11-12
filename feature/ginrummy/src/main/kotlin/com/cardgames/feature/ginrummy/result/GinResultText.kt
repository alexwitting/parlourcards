package com.cardgames.feature.ginrummy.result

import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinScoring
import com.cardgames.core.domain.ginrummy.HandOutcome
import com.cardgames.core.domain.ginrummy.HandResult
import com.cardgames.feature.ginrummy.opponentName
import kotlin.math.abs

/**
 * Pure text for the hand and match results (`GinResultOverlays.kt`) — plain branching with no
 * `@Composable` involved.
 *
 * [HandResult] doesn't store the knocker directly: knock/gin's winner is the knocker, but an
 * undercut's winner is the defender.
 */
internal fun knockerOf(result: HandResult): GinPlayer? =
    when (result.outcome) {
        HandOutcome.BLOCKED -> null
        HandOutcome.KNOCK, HandOutcome.GIN -> result.winner
        HandOutcome.UNDERCUT -> result.winner?.other
    }

/** The line over a hand's result: "YOU KNOCKED", "ROSA KNOCKED", "ZERO DEADWOOD" for a gin. */
internal fun handOverline(
    result: HandResult,
    opponentName: String,
): String {
    val knocker = if (knockerOf(result) == GinPlayer.PLAYER) "You" else opponentName
    return when (result.outcome) {
        HandOutcome.GIN -> "Zero deadwood"
        HandOutcome.BLOCKED -> "Blocked"
        HandOutcome.KNOCK, HandOutcome.UNDERCUT -> "$knocker knocked"
    }
}

/** A hand result's headline: "You win the hand!", "Undercut! You win the hand", "Rosa went gin!", "Rosa wins the hand". */
internal fun handTitle(
    result: HandResult,
    opponentName: String,
): String {
    val yours = result.winner == GinPlayer.PLAYER
    return when {
        result.outcome == HandOutcome.BLOCKED -> "Hand blocked"
        result.outcome == HandOutcome.UNDERCUT && yours -> "Undercut! You win the hand"
        result.outcome == HandOutcome.UNDERCUT -> "$opponentName undercut you"
        result.outcome == HandOutcome.GIN -> "${if (yours) "You" else opponentName} went ${ginName(result)}!"
        yours -> "You win the hand!"
        else -> "$opponentName wins the hand"
    }
}

/** "gin", or "Big Gin" when the hand went out with all eleven cards. */
internal fun ginName(result: HandResult): String = if (handBonus(result) == GinScoring.BIG_GIN_BONUS) "Big Gin" else "gin"

/**
 * The line under a match result's title: how a gin took it, else how close it was —
 * "Rosa went gin to take it.", "First to 100. Well played.", "So close. You were 12 points from winning."
 */
internal fun matchLine(
    won: Boolean,
    target: Int,
    short: Int,
    lastHand: HandResult?,
    opponentName: String,
): String =
    when {
        lastHand?.outcome == HandOutcome.GIN -> "${if (won) "You" else opponentName} went ${ginName(lastHand)} to take it."
        won -> "First to $target. Well played."
        short > 0 -> "So close. You were $short points from winning."
        else -> "A close one."
    }

/** What the hand added on top of the deadwood difference: a gin, Big Gin or undercut bonus (0 for a plain knock). */
internal fun handBonus(result: HandResult): Int = result.pointsScored - abs(result.defenderDeadwood - result.knockerDeadwood)

/**
 * The hand's points as a sum — "19 − 1 = 18", "6 − 0 + 25 undercut bonus = 31",
 * "22 + 25 gin bonus = 47" — so the score doesn't have to be taken on faith. Null for a blocked hand.
 */
internal fun pointsSum(result: HandResult): String? {
    val bonus = handBonus(result)
    val points = result.pointsScored
    return when (result.outcome) {
        HandOutcome.BLOCKED -> null
        HandOutcome.KNOCK -> "${result.defenderDeadwood} − ${result.knockerDeadwood} = $points"
        HandOutcome.UNDERCUT -> "${result.knockerDeadwood} − ${result.defenderDeadwood} + $bonus undercut bonus = $points"
        HandOutcome.GIN ->
            if (bonus > 0) "${result.defenderDeadwood} + $bonus gin bonus = $points" else "${result.defenderDeadwood} − 0 = $points"
    }
}

/** [pointsSum] as separate terms for the chips — `["19", "−", "1", "=", "18"]`. Empty for a blocked hand. */
internal fun sumTerms(result: HandResult): List<String> {
    val bonus = handBonus(result)
    val points = "${result.pointsScored}"
    val defender = "${result.defenderDeadwood}"
    val knocker = "${result.knockerDeadwood}"
    return when (result.outcome) {
        HandOutcome.BLOCKED -> emptyList()
        HandOutcome.KNOCK -> listOf(defender, "−", knocker, "=", points)
        HandOutcome.UNDERCUT -> listOf(knocker, "−", defender, "+", "$bonus", "=", points)
        HandOutcome.GIN -> if (bonus > 0) listOf(defender, "+", "$bonus", "=", points) else listOf(defender, "−", "0", "=", points)
    }
}
