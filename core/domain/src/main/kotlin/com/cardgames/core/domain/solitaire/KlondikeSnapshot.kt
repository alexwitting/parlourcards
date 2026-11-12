package com.cardgames.core.domain.solitaire

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A resumable solitaire position: [game] (which carries its seed, draw mode and move count), the
 * elapsed play time, and the undo/redo history (oldest first, capped at
 * [com.cardgames.core.domain.game.UndoRedoHistory.PERSISTED_LIMIT] when saved). Missing optional
 * fields load as their defaults; an incompatible or corrupt payload is dropped ([fromJson] returns
 * null).
 */
@Serializable
data class KlondikeSnapshot(
    val schemaVersion: Int = SCHEMA_VERSION,
    val game: KlondikeState,
    val elapsedSeconds: Int,
    val undoStack: List<KlondikeState> = emptyList(),
    val redoStack: List<KlondikeState> = emptyList(),
    /** Hints taken this deal (Parlour limits them per deal); undo doesn't give them back. */
    val hintsUsed: Int = 0,
    /** Restarts still on the undo stack, oldest first: the time each set aside, given back if undone. */
    val restarts: List<RestartMark> = emptyList(),
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val SCHEMA_VERSION = 1

        private val json =
            Json {
                ignoreUnknownKeys = true
                // Always write schemaVersion (a default value otherwise gets omitted), so a
                // future reader can tell an old save from a current one.
                encodeDefaults = true
            }

        /** Null on unparseable JSON, invariant-violating state, or a schema-version mismatch. */
        fun fromJson(raw: String): KlondikeSnapshot? =
            runCatching { json.decodeFromString(serializer(), raw) }
                .getOrNull()
                ?.takeIf { it.schemaVersion == SCHEMA_VERSION }
    }
}

/**
 * A restart of the deal: the position it left ([before], now on the undo stack) and the time on the
 * clock then. The replay's clock starts from 0; undoing back to [before] adds [elapsedSeconds] back.
 */
@Serializable
data class RestartMark(
    val before: KlondikeState,
    val elapsedSeconds: Int,
)
