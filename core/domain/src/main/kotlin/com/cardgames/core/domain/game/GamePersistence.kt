package com.cardgames.core.domain.game

import com.cardgames.core.domain.repository.GameResult
import com.cardgames.core.domain.repository.GinMatchRecord
import com.cardgames.core.domain.repository.SavedGameRepository
import com.cardgames.core.domain.repository.StatsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One game's view of the persistence layer: its save slot (keyed by [gameId], stored as JSON via
 * [encode]/[decode]) and its stats. Every game uses this one class with its own snapshot codec,
 * rather than each writing the same save/load/record plumbing.
 *
 * The JSON work runs on [codec] — a background dispatcher in the app, since a game's snapshot
 * (position plus undo history) is big enough that encoding it on the main thread drops frames.
 * Saves go one at a time, in the order they were asked for, so a later position is never
 * overwritten by an earlier one that happened to finish encoding second.
 */
class GamePersistence<Snapshot : Any>(
    private val gameId: GameId,
    private val savedGames: SavedGameRepository,
    private val stats: StatsRepository,
    private val encode: (Snapshot) -> String,
    private val decode: (String) -> Snapshot?,
    private val codec: CoroutineDispatcher,
) {
    private val saving = Mutex()

    /** The resumable snapshot, or null when there's no save or it's stale/corrupt ([decode] returned null). */
    suspend fun load(): Snapshot? = savedGames.load(gameId)?.stateJson?.let { withContext(codec) { decode(it) } }

    suspend fun save(snapshot: Snapshot) {
        saving.withLock { savedGames.save(gameId, withContext(codec) { encode(snapshot) }) }
    }

    suspend fun clear() {
        savedGames.delete(gameId)
    }

    /**
     * [score] only for games that score (gin rummy: the player's own final match total); [moves]
     * and [drawCount] only for solitaire.
     */
    suspend fun recordResult(
        won: Boolean,
        durationSeconds: Int,
        score: Int? = null,
        moves: Int? = null,
        drawCount: Int? = null,
        gin: GinMatchRecord? = null,
    ) {
        stats.record(
            GameResult(
                gameId = gameId,
                won = won,
                durationSeconds = durationSeconds,
                score = score,
                moves = moves,
                drawCount = drawCount,
                gin = gin,
            ),
        )
    }
}
