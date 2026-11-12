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
import com.cardgames.core.domain.ginrummy.GinMatchHighlights
import com.cardgames.core.domain.ginrummy.GinMatchRules
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinSnapshot
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.AnimationSpeed
import com.cardgames.core.domain.repository.GinMatchRecord
import com.cardgames.core.domain.repository.GinOptions
import com.cardgames.core.domain.repository.HintLimit
import com.cardgames.core.domain.repository.Settings
import com.cardgames.core.domain.solitaire.KlondikeSnapshot
import com.cardgames.core.testing.FakeClockProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * A player updating from an earlier release keeps everything: each release's real stored data (its
 * database and settings file, written by [UpgradeFixtureWriter] and kept under
 * `src/test/resources/upgrade/<version>/`) is opened by today's code, through the same builder and
 * repositories the app uses, and every saved value checked. A change that would lose or misread a
 * player's saves, stats or settings fails here, not on their phone.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UpgradeFromReleaseTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var database: CardGamesDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        scope.cancel()
    }

    /** [version]'s database, put where the app keeps it and opened the way the app opens it. */
    private fun databaseFrom(version: String): CardGamesDatabase {
        val target = context.getDatabasePath(CardGamesDatabase.NAME).apply { parentFile?.mkdirs() }
        resource("$version/${CardGamesDatabase.NAME}").copyTo(target, overwrite = true)
        return CardGamesDatabase.open(context).also { database = it }
    }

    /** [version]'s settings, read by today's settings repository. */
    private fun settingsFrom(version: String): Settings {
        val file = resource("$version/settings.preferences_pb").copyTo(File(temp.root, "settings.preferences_pb"))
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        return runBlocking { DataStoreSettingsRepository(store).observeSettings().first() }
    }

    private fun resource(path: String): File = File(checkNotNull(javaClass.getResource("/upgrade/$path")) { "no fixture $path" }.toURI())

    // --- 1.0.0 ---

    @Test
    fun `release 1_0_0 - the Solitaire game in progress resumes as it was`() {
        val db = databaseFrom("1.0.0")
        val saved = runBlocking { RoomSavedGameRepository(db.savedGameDao(), FakeClockProvider()).load(GameId.KLONDIKE) }
        val snapshot = checkNotNull(KlondikeSnapshot.fromJson(checkNotNull(saved).stateJson)) { "the Solitaire save no longer reads" }

        assertEquals(2026L, snapshot.game.seed)
        assertEquals(3, snapshot.game.drawCount)
        assertEquals(6, snapshot.game.waste.size)
        assertEquals(18, snapshot.game.stock.size)
        assertEquals(754, snapshot.elapsedSeconds)
        assertEquals(2, snapshot.undoStack.size)
        assertEquals(2, snapshot.hintsUsed)
        assertEquals(listOf(300), snapshot.restarts.map { it.elapsedSeconds })
        assertEquals(snapshot.undoStack.first(), snapshot.restarts.single().before)
    }

    @Test
    fun `release 1_0_0 - the Gin match in progress resumes as it was`() {
        val db = databaseFrom("1.0.0")
        val saved = runBlocking { RoomSavedGameRepository(db.savedGameDao(), FakeClockProvider()).load(GameId.GIN_RUMMY) }
        val snapshot = checkNotNull(GinSnapshot.fromJson(checkNotNull(saved).stateJson)) { "the Gin save no longer reads" }

        assertEquals(3, snapshot.game.handNumber)
        assertEquals(mapOf(GinPlayer.PLAYER to 47, GinPlayer.OPPONENT to 22), snapshot.game.matchScores)
        assertEquals(GinMatchRules(target = 250, ginBonus = true, bigGin = true), snapshot.game.rules)
        assertEquals(GinDifficulty.HARD, snapshot.opponent)
        assertEquals(GinMatchHighlights(gins = 1, bestHand = 31, handsPlayed = 2, knocks = 2, knockDeadwood = 7), snapshot.highlights)
        assertEquals(1, snapshot.undoStack.size)
        assertEquals(1, snapshot.recentMoves.size)
        assertEquals(1, snapshot.hintsUsed)
    }

    @Test
    fun `release 1_0_0 - stats are kept`() {
        val db = databaseFrom("1.0.0")
        val stats = RoomStatsRepository(db.gameResultDao(), FakeClockProvider())
        val solitaire = runBlocking { stats.observeStats(GameId.KLONDIKE).first() }
        val solitaireResults = runBlocking { stats.observeResults(GameId.KLONDIKE).first() }
        val gin = runBlocking { stats.observeStats(GameId.GIN_RUMMY).first() }
        val ginResults = runBlocking { stats.observeResults(GameId.GIN_RUMMY).first() }

        assertEquals(3, solitaire.gamesPlayed)
        assertEquals(2, solitaire.gamesWon)
        assertEquals(198, solitaire.bestDurationSeconds)
        assertEquals(101, solitaire.fewestMoves)
        assertEquals(listOf(3, 1, 3), solitaireResults.map { it.result.drawCount })
        assertEquals(1, gin.gamesWon)
        assertEquals(212, gin.bestScore)
        assertEquals(
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
            ginResults.single().result.gin,
        )
    }

    @Test
    fun `release 1_0_0 - settings are kept`() {
        assertEquals(
            Settings(
                drawCount = 1,
                themeId = "midnight",
                soundEnabled = false,
                hapticsEnabled = true,
                useSolverForWinnableDeals = false,
                gin =
                    GinOptions(
                        difficulty = GinDifficulty.EASY,
                        hints = HintLimit.UNLIMITED,
                        rules = GinMatchRules(target = 50, ginBonus = false, bigGin = true),
                        confirmKnock = true,
                        meldHints = false,
                    ),
                animationSpeed = AnimationSpeed.SLOW,
                reduceMotion = true,
                solitaireHints = HintLimit.OFF,
            ),
            settingsFrom("1.0.0"),
        )
    }
}
