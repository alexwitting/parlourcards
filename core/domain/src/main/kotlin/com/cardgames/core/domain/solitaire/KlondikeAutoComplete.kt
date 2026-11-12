package com.cardgames.core.domain.solitaire

/**
 * Auto-complete: once no tableau card is face down, nothing is hidden and the rest of the game is a
 * formality — stock and waste included. [playOut] finds the moves that finish it.
 *
 * The play-out sends home whatever can go, else deals from the stock, else turns the waste over.
 * With draw 1 that always wins: the lowest card left is either on top of its column (anything above
 * it would be lower still) or in the stock, where every pass reaches it, and its foundation is
 * waiting. With draw 3 a card can stay out of reach on every pass; when a whole pass sends nothing
 * home, the waste's top card goes onto the tableau if it can, which shifts the cards the next pass
 * turns up. Every such move takes a card out of the draw pile for good, so the play-out always ends.
 * If it still stalls (a finish that needs the tableau rearranged), [KlondikeSolver] searches with a
 * small fixed budget; failing that, there's no finish to offer.
 */
object KlondikeAutoComplete {
    /** True when [state] can be finished automatically: see [playOut]. */
    fun canComplete(state: KlondikeState): Boolean = playOut(state) != null

    /** The moves that finish [state] from here, or null while a card is hidden or no finish is found. */
    fun playOut(state: KlondikeState): List<KlondikeMove>? {
        if (KlondikeEngine.isWon(state) || state.tableau.any { it.faceDownCount > 0 }) return null
        return playedOutDirectly(state) ?: solved(state)
    }

    private fun playedOutDirectly(state: KlondikeState): List<KlondikeMove>? {
        var game = state
        val moves = mutableListOf<KlondikeMove>()
        // Waste turned over since a card last left the draw pile: a second one would only repeat the pass.
        var idleRecycles = 0
        while (!KlondikeEngine.isWon(game)) {
            val legal = KlondikeEngine.legalMoves(game)
            val home = legal.firstOrNull { it.sendsHome() }
            val move =
                when {
                    home != null -> home.also { idleRecycles = 0 }
                    game.stock.isNotEmpty() -> KlondikeMove.DealFromStock
                    game.waste.isNotEmpty() && idleRecycles == 0 -> KlondikeMove.RecycleWaste.also { idleRecycles++ }
                    else -> legal.firstOrNull { it is KlondikeMove.WasteToTableau }?.also { idleRecycles = 0 } ?: return null
                }
            moves += move
            game = KlondikeEngine.apply(game, move)
        }
        return moves
    }

    private fun solved(state: KlondikeState): List<KlondikeMove>? =
        (KlondikeSolver.solve(state, nodeBudget = SOLVER_BUDGET) as? KlondikeSolver.Result.Solved)?.moves

    private fun KlondikeMove.sendsHome(): Boolean = this is KlondikeMove.TableauToFoundation || this is KlondikeMove.WasteToFoundation

    /**
     * The search for a draw-3 finish the play-out misses (one needing the tableau rearranged). By
     * then the draw pile is small, so a finish turns up well within this; it's kept small because
     * the check runs as each move settles.
     */
    private const val SOLVER_BUDGET = 5_000
}
