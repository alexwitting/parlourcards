package com.cardgames.core.data.upgrade

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardgames.core.data.db.CardGamesDatabase
import com.cardgames.core.data.repository.RoomSavedGameRepository
import com.cardgames.core.data.repository.RoomStatsRepository
import com.cardgames.core.data.settings.DataStoreSettingsRepository
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinMatchHighlights
import com.cardgames.core.domain.ginrummy.GinMatchRules
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinSnapshot
import com.cardgames.core.domain.ginrummy.RecentMove
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.AnimationSpeed
import com.cardgames.core.domain.repository.GameResult
import com.cardgames.core.domain.repository.GinMatchRecord
import com.cardgames.core.domain.repository.GinOptions
import com.cardgames.core.domain.repository.HintLimit
import com.cardgames.core.domain.solitaire.KlondikeEngine
import com.cardgames.core.domain.solitaire.KlondikeMove
import com.cardgames.core.domain.solitaire.KlondikeSnapshot
import com.cardgames.core.domain.solitaire.RestartMark
import com.cardgames.core.testing.FakeClockProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDateTime

/**
 * Writes one release's upgrade fixture: a saved Solitaire game, a saved Gin match, finished games
 * in the stats, and non-default settings — through the app's own repositories, so the files are
 * exactly what that release stores. Opt-in, once per release:
 *
 * ```
 * ./gradlew :core:data:testDebugUnitTest --tests "*UpgradeFixtureWriter" -PwriteUpgradeFixture=<version>
 * ```
 *
 * Then add that release's checks to [UpgradeFromReleaseTest]. The samples below may change for a
 * later release; fixtures already written never do.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UpgradeFixtureWriter {
    @Test
    fun write() {
        val out = System.getProperty("cardgames.writeUpgradeFixture")
        assumeTrue("opt-in: -PwriteUpgradeFixture=<version>", out != null)
        val dir = File(checkNotNull(out)).apply { mkdirs() }
        writeDatabase(File(dir, CardGamesDatabase.NAME))
        writeSettings(File(dir, SETTINGS_FILE))
    }

    private fun writeDatabase(target: File) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clock = FakeClockProvider().apply { setNow(LocalDateTime.of(2026, 11, 7, 12, 0)) }
        val db = CardGamesDatabase.open(context, WORKING_DB)
        runBlocking {
            val saves = RoomSavedGameRepository(db.savedGameDao(), clock)
            saves.save(GameId.KLONDIKE, solitaire().toJson())
            saves.save(GameId.GIN_RUMMY, gin().toJson())
            val stats = RoomStatsRepository(db.gameResultDao(), clock)
            results.forEach { stats.record(it) }
        }
        // Everything into the main file, so the copy is the whole database.
        db.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(TRUNCATE)")
            .close()
        db.close()
        context.getDatabasePath(WORKING_DB).copyTo(target, overwrite = true)
    }

    private fun writeSettings(target: File) {
        target.delete()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store = PreferenceDataStoreFactory.create(scope = scope) { target }
        runBlocking {
            DataStoreSettingsRepository(store).apply {
                setDrawCount(1)
                setThemeId("midnight")
                setSoundEnabled(false)
                setHapticsEnabled(true)
                setUseSolverForWinnableDeals(false)
                setAnimationSpeed(AnimationSpeed.SLOW)
                setReduceMotion(true)
                setSolitaireHints(HintLimit.OFF)
                setGinOptions(
                    GinOptions(
                        difficulty = GinDifficulty.EASY,
                        hints = HintLimit.UNLIMITED,
                        rules = GinMatchRules(target = 50, ginBonus = false, bigGin = true),
                        confirmKnock = true,
                        meldHints = false,
                    ),
                )
            }
            store.data.first()
        }
        scope.cancel()
    }

    /** Draw 3, two draws in, with undo history, hints taken and a restart still undoable. */
    private fun solitaire(): KlondikeSnapshot {
        val dealt = KlondikeEngine.deal(seed = 2026L, drawCount = 3)
        val once = KlondikeEngine.apply(dealt, KlondikeMove.DealFromStock)
        val twice = KlondikeEngine.apply(once, KlondikeMove.DealFromStock)
        return KlondikeSnapshot(
            game = twice,
            elapsedSeconds = 754,
            undoStack = listOf(dealt, once),
            hintsUsed = 2,
            restarts = listOf(RestartMark(before = dealt, elapsedSeconds = 300)),
        )
    }

    /** Hand 3 of a match to 250 with Big Gin on, against Vex, with highlights and the action message's moves. */
    private fun gin(): GinSnapshot {
        val start = GinEngine.newMatch(seed = 2026L, rules = GinMatchRules(target = 250, ginBonus = true, bigGin = true))
        val game = start.copy(handNumber = 3, matchScores = mapOf(GinPlayer.PLAYER to 47, GinPlayer.OPPONENT to 22))
        return GinSnapshot(
            game = game,
            undoStack = listOf(start),
            hintsUsed = 1,
            opponent = GinDifficulty.HARD,
            highlights = GinMatchHighlights(gins = 1, bestHand = 31, handsPlayed = 2, knocks = 2, knockDeadwood = 7),
            recentMoves = listOf(RecentMove(before = start, after = game)),
        )
    }

    private val results =
        listOf(
            GameResult(GameId.KLONDIKE, won = true, durationSeconds = 245, moves = 118, drawCount = 3),
            GameResult(GameId.KLONDIKE, won = false, durationSeconds = 600, moves = 40, drawCount = 1),
            GameResult(GameId.KLONDIKE, won = true, durationSeconds = 198, moves = 101, drawCount = 3),
            GameResult(
                GameId.GIN_RUMMY,
                won = true,
                durationSeconds = 1240,
                score = 212,
                gin =
                    GinMatchRecord(
                        GinDifficulty.HARD,
                        opponentScore = 64,
                        gins = 2,
                        undercuts = 1,
                        bestHand = 47,
                        knocks = 5,
                        knockDeadwood = 21,
                        hands = 8,
                    ),
            ),
        )

    private companion object {
        const val WORKING_DB = "fixture.db"
        const val SETTINGS_FILE = "settings.preferences_pb"
    }
}
