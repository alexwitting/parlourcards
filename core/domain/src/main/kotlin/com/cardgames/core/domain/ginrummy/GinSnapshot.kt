package com.cardgames.core.domain.ginrummy

import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A resumable gin position: [game] and its undo/redo history (oldest first, capped at
 * [com.cardgames.core.domain.game.UndoRedoHistory.PERSISTED_LIMIT] when saved), plus the match's
 * opponent and highlights. Missing optional fields load as their defaults; an incompatible or
 * corrupt payload is dropped ([fromJson] returns null).
 */
@Serializable
data class GinSnapshot(
    val schemaVersion: Int = SCHEMA_VERSION,
    val game: GinState,
    val undoStack: List<GinState> = emptyList(),
    val redoStack: List<GinState> = emptyList(),
    /** Hints taken this hand; undo doesn't give them back. */
    val hintsUsed: Int = 0,
    /** The match's opponent; when null, the current setting is used. */
    val opponent: GinDifficulty? = null,
    /** The player's highlights so far this match, for the match result. */
    val highlights: GinMatchHighlights = GinMatchHighlights(),
    /** The last few moves the table described, so the screen can tell them again on resume. */
    val recentMoves: List<RecentMove> = emptyList(),
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val SCHEMA_VERSION = 1

        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        /** Null on unparseable JSON, invariant-violating state, or a schema-version mismatch. */
        fun fromJson(raw: String): GinSnapshot? =
            runCatching { json.decodeFromString(serializer(), raw) }
                .getOrNull()
                ?.takeIf { it.schemaVersion == SCHEMA_VERSION }
    }
}

/** One move as the positions either side of it. */
@Serializable
data class RecentMove(
    val before: GinState,
    val after: GinState,
)

/** The player's best moments in a match: gins, undercuts and their best single hand. */
@Serializable
data class GinMatchHighlights(
    val gins: Int = 0,
    val undercuts: Int = 0,
    val bestHand: Int = 0,
    val handsPlayed: Int = 0,
    /** Hands the player went out on (knocks and gins) and the deadwood held across them. */
    val knocks: Int = 0,
    val knockDeadwood: Int = 0,
) {
    /** These highlights with one more finished hand, [result], counted. */
    fun plus(result: HandResult): GinMatchHighlights {
        val mine = result.winner == GinPlayer.PLAYER
        val knocker = if (result.outcome == HandOutcome.UNDERCUT) result.winner?.other else result.winner
        val myKnock = knocker == GinPlayer.PLAYER && result.outcome != HandOutcome.BLOCKED
        return copy(
            gins = gins + if (mine && result.outcome == HandOutcome.GIN) 1 else 0,
            undercuts = undercuts + if (mine && result.outcome == HandOutcome.UNDERCUT) 1 else 0,
            bestHand = if (mine) maxOf(bestHand, result.pointsScored) else bestHand,
            handsPlayed = handsPlayed + 1,
            knocks = knocks + if (myKnock) 1 else 0,
            knockDeadwood = knockDeadwood + if (myKnock) result.knockerDeadwood else 0,
        )
    }
}
