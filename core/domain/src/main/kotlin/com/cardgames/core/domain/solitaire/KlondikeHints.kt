package com.cardgames.core.domain.solitaire

/**
 * [suggest] tries [KlondikeSolver] first — a move on an actual winning line, not just any
 * productive one — with a small budget/deadline, since a hint tap should feel instant, not pause
 * for the ~1-2s [KlondikeDealer] budgets tolerate. On [KlondikeSolver.Result.NotFound] (a messy
 * mid-game position the bounded search can't crack that fast), falls back to [heuristicSuggest]:
 * a productive legal move, no lookahead. Priority: foundation moves, then tableau moves that free
 * a face-down flip, then getting the waste card down, then cycling the stock. Never suggests
 * foundation→tableau (always legal, never productive on its own).
 */
object KlondikeHints {
    fun suggest(state: KlondikeState): KlondikeMove? {
        val deadline = System.nanoTime() + HINT_DEADLINE_MILLIS * NANOS_PER_MILLI
        val solved = KlondikeSolver.solve(state, nodeBudget = HINT_NODE_BUDGET, deadlineNanos = deadline)
        if (solved is KlondikeSolver.Result.Solved) return solved.moves.first()
        return heuristicSuggest(state)
    }

    private fun heuristicSuggest(state: KlondikeState): KlondikeMove? {
        val legal = KlondikeEngine.legalMoves(state)
        return legal.firstOrNull { it is KlondikeMove.TableauToFoundation || it is KlondikeMove.WasteToFoundation }
            ?: legal.filterIsInstance<KlondikeMove.TableauToTableau>().firstOrNull { state.freesAFlip(it) }
            ?: legal.firstOrNull { it is KlondikeMove.WasteToTableau }
            ?: legal.firstOrNull { it is KlondikeMove.DealFromStock || it is KlondikeMove.RecycleWaste }
    }

    private const val NANOS_PER_MILLI = 1_000_000L
    private const val HINT_DEADLINE_MILLIS = 150L
    private const val HINT_NODE_BUDGET = 20_000
}

/**
 * Moving the whole face-up run off a pile that still hides cards exposes a flip. Top-level (not a
 * member of [KlondikeHints]) and `internal` since [KlondikeSolver]'s move ordering uses the same
 * signal.
 */
internal fun KlondikeState.freesAFlip(move: KlondikeMove.TableauToTableau): Boolean {
    val from = tableau[move.fromPile]
    return from.faceDownCount > 0 && move.cardIndex == from.faceDownCount
}
