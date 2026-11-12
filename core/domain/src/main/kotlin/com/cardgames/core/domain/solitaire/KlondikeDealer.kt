package com.cardgames.core.domain.solitaire

import com.cardgames.core.domain.game.SeedProvider

/**
 * Winnable deals, for draw 1 and draw 3: tries seeds until [KlondikeSolver] proves one winnable,
 * within a time budget. The one part of `:core:domain` that loops on a budget, so it's kept apart
 * from [KlondikeEngine]'s rules. Synchronous; `SolitaireViewModel` calls it off the main thread.
 *
 * Several short attempts beat one long one: most solvable deals solve in well under a second, so
 * each seed gets [ATTEMPT_DEADLINE_MILLIS] within [OVERALL_BUDGET_MILLIS]. Draw 3 is harder to
 * prove in that time, so when the budget runs out it falls back to an ordinary deal.
 */
object KlondikeDealer {
    /**
     * [attemptDeadlineMillis]/[overallBudgetMillis] default to the production-calibrated values
     * below; tests pass a much more generous pair for a search expected to solve in milliseconds,
     * so the assertion isn't sensitive to how fast the machine running it happens to be — the
     * search itself is fully deterministic (same seed, same fixed move ordering), so the only
     * thing a tight deadline adds under real machine-speed variance is flakiness, not signal.
     */
    fun dealWinnable(
        seedProvider: SeedProvider,
        drawCount: Int,
        attemptDeadlineMillis: Long = ATTEMPT_DEADLINE_MILLIS,
        overallBudgetMillis: Long = OVERALL_BUDGET_MILLIS,
    ): KlondikeState {
        val overallDeadline = System.nanoTime() + overallBudgetMillis * NANOS_PER_MILLI
        var lastDeal = KlondikeEngine.deal(seedProvider.nextSeed(), drawCount)
        while (System.nanoTime() < overallDeadline) {
            val attemptDeadline = minOf(System.nanoTime() + attemptDeadlineMillis * NANOS_PER_MILLI, overallDeadline)
            val result = KlondikeSolver.solve(lastDeal, nodeBudget = NODE_BUDGET_CEILING, deadlineNanos = attemptDeadline)
            if (result is KlondikeSolver.Result.Solved) return lastDeal
            lastDeal = KlondikeEngine.deal(seedProvider.nextSeed(), drawCount)
        }
        // Budget exhausted without a proof — deal this last (unchecked) seed rather than block
        // indefinitely.
        return lastDeal
    }

    private const val NANOS_PER_MILLI = 1_000_000L
    private const val ATTEMPT_DEADLINE_MILLIS = 400L
    private const val OVERALL_BUDGET_MILLIS = 1500L

    /** A safety ceiling, not the binding constraint in practice — solved games in the benchmark needed low thousands of nodes at most. */
    private const val NODE_BUDGET_CEILING = 500_000
}
