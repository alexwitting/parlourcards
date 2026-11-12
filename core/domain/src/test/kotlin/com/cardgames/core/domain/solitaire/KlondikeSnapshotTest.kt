package com.cardgames.core.domain.solitaire

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KlondikeSnapshotTest {
    @Test
    fun `a snapshot survives the JSON round trip exactly`() {
        var game = KlondikeEngine.deal(seed = 42L, drawCount = 3)
        game = KlondikeEngine.apply(game, KlondikeMove.DealFromStock)
        val snapshot = KlondikeSnapshot(game = game, elapsedSeconds = 125)

        assertEquals(snapshot, KlondikeSnapshot.fromJson(snapshot.toJson()))
    }

    @Test
    fun `undo and redo history survive the JSON round trip`() {
        val dealt = KlondikeEngine.deal(seed = 42L, drawCount = 1)
        val afterMove = KlondikeEngine.apply(dealt, KlondikeMove.DealFromStock)
        val snapshot =
            KlondikeSnapshot(
                game = afterMove,
                elapsedSeconds = 10,
                undoStack = listOf(dealt),
                redoStack = listOf(afterMove),
            )

        assertEquals(snapshot, KlondikeSnapshot.fromJson(snapshot.toJson()))
    }

    @Test
    fun `a save with no history fields still parses, with empty history`() {
        // undoStack/redoStack are additive fields with defaults, so a payload missing them
        // entirely must still parse.
        val json =
            KlondikeSnapshot(game = KlondikeEngine.deal(seed = 1L), elapsedSeconds = 0)
                .toJson()
                .replace(",\"undoStack\":[],\"redoStack\":[]", "")

        val restored = KlondikeSnapshot.fromJson(json)

        assertNotNull(restored)
        assertEquals(emptyList<KlondikeState>(), restored?.undoStack)
        assertEquals(emptyList<KlondikeState>(), restored?.redoStack)
    }

    @Test
    fun `the schema version is written explicitly`() {
        val snapshot = KlondikeSnapshot(game = KlondikeEngine.deal(seed = 1L), elapsedSeconds = 0)

        assertTrue("\"schemaVersion\":" in snapshot.toJson())
    }

    @Test
    fun `garbage is discarded, not thrown`() {
        assertNull(KlondikeSnapshot.fromJson("not json at all"))
        assertNull(KlondikeSnapshot.fromJson("{}"))
    }

    @Test
    fun `a foreign schema version is discarded`() {
        val json =
            KlondikeSnapshot(game = KlondikeEngine.deal(seed = 1L), elapsedSeconds = 0)
                .toJson()
                .replace("\"schemaVersion\":1", "\"schemaVersion\":999")

        assertNull(KlondikeSnapshot.fromJson(json))
    }

    @Test
    fun `a state violating engine invariants is discarded`() {
        val json =
            KlondikeSnapshot(game = KlondikeEngine.deal(seed = 1L), elapsedSeconds = 0)
                .toJson()
                .replace("\"drawCount\":1", "\"drawCount\":5")

        assertNull(KlondikeSnapshot.fromJson(json))
    }
}
