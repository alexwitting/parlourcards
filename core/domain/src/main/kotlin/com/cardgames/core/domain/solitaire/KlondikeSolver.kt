package com.cardgames.core.domain.solitaire

/**
 * Best-effort Klondike solver — pure, synchronous, no coroutines (same layering rule as
 * [KlondikeEngine]). Backs three consumers: [KlondikeDealer]'s winnable-deal guarantee,
 * [KlondikeHints]'s solver-first hint, and `SolitaireViewModel`'s stuck detection.
 *
 * Klondike's real-world solvability rate for random deals is well below 100% (~70-80% in the
 * literature), and even solvable deals can need a line this search doesn't find within budget —
 * [Result.NotFound] means "no proof found within budget," not "unsolvable." Callers that need a
 * guarantee (like [KlondikeDealer]) handle that by retrying a fresh deal, not by chasing a bigger
 * budget here.
 */
object KlondikeSolver {
    sealed interface Result {
        data class Solved(
            val moves: List<KlondikeMove>,
        ) : Result

        data object NotFound : Result
    }

    /**
     * Iterative (explicit-stack) depth-first search — Klondike solutions can run 100+ moves deep,
     * so this avoids any JVM recursion-depth risk. Two independent stopping conditions: [nodeBudget]
     * (a hard ceiling, what tests use for determinism) and [deadlineNanos] (a wall-clock cutoff,
     * what production callers use to honor a real time budget regardless of device speed —
     * defaults to effectively unbounded so tests are governed by [nodeBudget] alone). Move
     * ordering biases toward productive moves first (see [priority]) so a winning line, if one
     * exists, tends to surface well before the budget runs out.
     */
    fun solve(
        state: KlondikeState,
        nodeBudget: Int,
        deadlineNanos: Long = Long.MAX_VALUE,
    ): Result {
        val visited = HashSet<Int>()
        val stack = ArrayDeque<Frame>()
        stack.addLast(Frame(state, emptyList()))
        visited += positionHash(state)

        var nodes = 0
        while (stack.isNotEmpty()) {
            if (nodes >= nodeBudget || System.nanoTime() >= deadlineNanos) return Result.NotFound
            nodes++
            val (current, path) = stack.removeLast()
            if (KlondikeEngine.isWon(current)) return Result.Solved(path)
            for (move in orderedMoves(current).asReversed()) {
                val next = KlondikeEngine.apply(current, move)
                if (visited.add(positionHash(next))) {
                    stack.addLast(Frame(next, path + move))
                }
            }
        }
        return Result.NotFound
    }

    private data class Frame(
        val state: KlondikeState,
        val path: List<KlondikeMove>,
    )

    /** Best-priority-first (lowest [priority] value), so pushing in reverse leaves it on top of the stack. */
    private fun orderedMoves(state: KlondikeState): List<KlondikeMove> = KlondikeEngine.legalMoves(state).sortedBy { priority(state, it) }

    /**
     * Mirrors [KlondikeHints]'s own reasoning: foundation moves first, then tableau moves that
     * free a flip, then getting the waste card down, then cycling the stock. Unlike the hint
     * (which never suggests it), [KlondikeMove.FoundationToTableau] stays available here, just
     * last — some real Klondike wins require pulling a card back down.
     */
    private fun priority(
        state: KlondikeState,
        move: KlondikeMove,
    ): Int =
        when {
            move is KlondikeMove.TableauToFoundation || move is KlondikeMove.WasteToFoundation -> 0
            move is KlondikeMove.TableauToTableau && state.freesAFlip(move) -> 1
            move is KlondikeMove.WasteToTableau -> 2
            move is KlondikeMove.TableauToTableau -> 3
            move is KlondikeMove.DealFromStock || move is KlondikeMove.RecycleWaste -> 4
            else -> 5
        }

    /** Position identity for visited-state pruning — deliberately excludes `seed`/`moveCount`, which don't affect reachability. */
    private fun positionHash(state: KlondikeState): Int = java.util.Objects.hash(state.tableau, state.foundations, state.stock, state.waste)
}
