package com.cardgames.core.domain.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UndoRedoHistoryTest {
    @Test
    fun `undo returns the recorded position and makes the current one redoable`() {
        val history = UndoRedoHistory<String>()
        history.record("a")

        assertEquals("a", history.undo(current = "b"))
        assertFalse(history.canUndo)
        assertEquals("b", history.redo(current = "a"))
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun `recording a new change discards the redo history`() {
        val history = UndoRedoHistory<String>()
        history.record("a")
        history.undo(current = "b")

        history.record("a")

        assertFalse(history.canRedo)
    }

    @Test
    fun `stepping past either end returns null and changes nothing`() {
        val history = UndoRedoHistory<String>()

        assertNull(history.undo(current = "a"))
        assertNull(history.redo(current = "a"))
        assertFalse(history.canUndo || history.canRedo)
    }

    @Test
    fun `the persisted history is the most recent steps, capped, oldest first`() {
        val history = UndoRedoHistory<Int>()
        (1..5).forEach(history::record)

        assertEquals(listOf(3, 4, 5), history.persistedUndo(limit = 3))
    }

    @Test
    fun `restore replaces the history with a saved one`() {
        val history = UndoRedoHistory<Int>()
        history.record(9)

        history.restore(undo = listOf(1, 2), redo = listOf(3))

        assertEquals(2, history.undo(current = 0))
        assertEquals(listOf(3, 0), history.persistedRedo())
    }
}
