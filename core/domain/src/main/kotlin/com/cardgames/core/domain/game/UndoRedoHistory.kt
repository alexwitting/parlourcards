package com.cardgames.core.domain.game

/**
 * A game's undo/redo history of whole positions [S]. The ViewModel calls [record] with the
 * position before each undoable change, and [undo]/[redo] with the position on screen now;
 * both return the position to show instead, or null when there's nothing to step to.
 *
 * Unbounded in memory; [persistedUndo]/[persistedRedo] give the capped tail that goes into a save
 * (see [PERSISTED_LIMIT]), and [restore] puts a saved history back.
 */
class UndoRedoHistory<S> {
    private val undoStack = ArrayDeque<S>()
    private val redoStack = ArrayDeque<S>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** [before] is the position an undoable change is about to replace. Any redo history is discarded. */
    fun record(before: S) {
        redoStack.clear()
        undoStack.addLast(before)
    }

    /** The position [undo] would step back to, without stepping. */
    fun peekUndo(): S? = undoStack.lastOrNull()

    /** Steps back from [current]: returns the previous position (or null), and makes [current] redoable. */
    fun undo(current: S): S? = step(from = undoStack, to = redoStack, current = current)

    /** Steps forward from [current]: returns the next position (or null), and makes [current] undoable. */
    fun redo(current: S): S? = step(from = redoStack, to = undoStack, current = current)

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    /** Replaces the history with a saved one, oldest first. */
    fun restore(
        undo: List<S>,
        redo: List<S>,
    ) {
        clear()
        undoStack.addAll(undo)
        redoStack.addAll(redo)
    }

    /** The most recent undo steps, oldest first, capped at [limit] — for a save. */
    fun persistedUndo(limit: Int = PERSISTED_LIMIT): List<S> = undoStack.takeLast(limit)

    /** The most recent redo steps, oldest first, capped at [limit] — for a save. */
    fun persistedRedo(limit: Int = PERSISTED_LIMIT): List<S> = redoStack.takeLast(limit)

    private fun step(
        from: ArrayDeque<S>,
        to: ArrayDeque<S>,
        current: S,
    ): S? {
        val target = from.removeLastOrNull() ?: return null
        to.addLast(current)
        return target
    }

    companion object {
        /**
         * How many steps each direction a save keeps. The live session stays unbounded; only the
         * copy written to disk to survive process death is capped, so a long session can't grow
         * the save without limit.
         */
        const val PERSISTED_LIMIT = 100
    }
}
