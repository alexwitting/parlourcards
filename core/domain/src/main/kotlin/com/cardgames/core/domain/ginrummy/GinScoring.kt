package com.cardgames.core.domain.ginrummy

/**
 * Pure scoring math, kept separate from [GinEngine] and behind named constants so match rules
 * are easy to retune. Two distinct "+25"s exist and are **not**
 * stacked on the same hand: [GIN_BONUS]/[UNDERCUT_BONUS] apply to that hand's raw score (and
 * feed the race to [GinState.MATCH_TARGET]); [BOX_BONUS] is a separate per-hand-won tally
 * ([GinState.boxes]) settled only once the match itself ends — standard gin rummy scoring
 * keeps "boxes" off the race to 100 and adds them only at final settlement.
 */
object GinScoring {
    data class HandScore(
        val outcome: HandOutcome,
        val knockerWins: Boolean,
        val points: Int,
    )

    /**
     * Resolves one knock. [ginBonus] is what a gin adds — [GIN_BONUS], [BIG_GIN_BONUS], or 0 when the
     * match plays without the bonus.
     * [defenderDeadwoodAfterLayoff] must already reflect any successful
     * layoffs (0 layoffs happened if [isGin], per the rules). An [isGin] knock can't be
     * undercut in practice — the defender would need 0 deadwood of their own, which they'd
     * ordinarily have knocked with themselves — but the formula isn't special-cased against it.
     */
    fun scoreKnock(
        knockerDeadwood: Int,
        defenderDeadwoodAfterLayoff: Int,
        isGin: Boolean,
        ginBonus: Int = GIN_BONUS,
    ): HandScore =
        if (defenderDeadwoodAfterLayoff > knockerDeadwood) {
            val diff = defenderDeadwoodAfterLayoff - knockerDeadwood
            HandScore(
                outcome = if (isGin) HandOutcome.GIN else HandOutcome.KNOCK,
                knockerWins = true,
                points = if (isGin) diff + ginBonus else diff,
            )
        } else {
            val diff = knockerDeadwood - defenderDeadwoodAfterLayoff
            HandScore(outcome = HandOutcome.UNDERCUT, knockerWins = false, points = diff + UNDERCUT_BONUS)
        }

    /**
     * Final match total for [winner] once their raw [GinState.matchScores] reaches the target:
     * their accumulated raw score, plus 25 per hand they won ([GinState.boxes], tallied only
     * now — not during the race to 100), plus the flat game bonus, doubled if [loserRawScore]
     * is a shutout zero.
     */
    fun settleMatch(
        winnerRawScore: Int,
        winnerBoxes: Int,
        loserRawScore: Int,
    ): Int {
        val total = winnerRawScore + winnerBoxes * BOX_BONUS + GAME_BONUS
        return if (loserRawScore == 0) total * 2 else total
    }

    const val UNDERCUT_BONUS = 25
    const val GIN_BONUS = 25

    /** Going out with all 11 cards melded (a house rule, off by default). */
    const val BIG_GIN_BONUS = 31
    const val BOX_BONUS = 25
    const val GAME_BONUS = 100
}
