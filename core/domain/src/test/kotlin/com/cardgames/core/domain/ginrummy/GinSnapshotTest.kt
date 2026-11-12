package com.cardgames.core.domain.ginrummy

import com.cardgames.core.domain.cards.Card
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class GinSnapshotTest {
    @Test
    fun `a snapshot survives the JSON round trip exactly`() {
        val game = GinEngine.newMatch(seed = 1L)

        assertEquals(GinSnapshot(game = game), GinSnapshot.fromJson(GinSnapshot(game = game).toJson()))
    }

    @Test
    fun `undo and redo history survive the JSON round trip`() {
        val dealt = GinEngine.newMatch(seed = 1L)
        val snapshot = GinSnapshot(game = dealt, undoStack = listOf(dealt), redoStack = listOf(dealt))

        assertEquals(snapshot, GinSnapshot.fromJson(snapshot.toJson()))
    }

    @Test
    fun `a save with no history fields still parses, with empty history`() {
        // undoStack/redoStack are additive fields with defaults, so a payload missing them
        // entirely must still parse.
        val json =
            GinSnapshot(game = GinEngine.newMatch(seed = 1L))
                .toJson()
                .replace(",\"undoStack\":[],\"redoStack\":[]", "")

        val restored = GinSnapshot.fromJson(json)

        assertNotNull(restored)
        assertEquals(emptyList<GinState>(), restored?.undoStack)
        assertEquals(emptyList<GinState>(), restored?.redoStack)
    }

    @Test
    fun `a save from before known cards were tracked still parses, with nothing known`() {
        val json =
            GinSnapshot(game = GinEngine.newMatch(seed = 1L))
                .toJson()
                .replace(",\"knownHeld\":{}", "")
        check("knownHeld" !in json) { "test setup: the field should be stripped" }

        val restored = GinSnapshot.fromJson(json)

        assertNotNull(restored)
        assertEquals(emptyMap<GinPlayer, List<Card>>(), restored?.game?.knownHeld)
    }
}
