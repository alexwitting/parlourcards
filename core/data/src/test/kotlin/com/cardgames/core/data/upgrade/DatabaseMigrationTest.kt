package com.cardgames.core.data.upgrade

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cardgames.core.data.db.CardGamesDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The migrations on a real SQLite database, from each exported schema (`core/data/schemas`), with
 * Room checking every result against the current schema. Complements the mock-based
 * `CardGamesDatabaseMigrationTest`, which pins the SQL itself.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CardGamesDatabase::class.java)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** The versions with an exported schema, oldest first. */
    private val exportedVersions: List<Int> =
        checkNotNull(context.assets.list(CardGamesDatabase::class.java.name))
            .map { it.removeSuffix(".json").toInt() }
            .sorted()

    @Test
    fun `a version 1 database keeps its saves and starts the stats fresh`() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO saved_games (gameId, stateJson, updatedAt) VALUES ('KLONDIKE', '{}', 1)")
            execSQL("INSERT INTO game_results (gameId, won, score, durationSec, endedAt) VALUES ('KLONDIKE', 1, NULL, 200, 1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, CURRENT, true, *CardGamesDatabase.MIGRATIONS)

        db.query("SELECT gameId FROM saved_games").use { assertEquals(1, it.count) }
        db.query("SELECT id FROM game_results").use { assertEquals(0, it.count) }
    }

    @Test
    fun `a version 2 database keeps its saves and stats`() {
        helper.createDatabase(DB, 2).apply {
            execSQL("INSERT INTO saved_games (gameId, stateJson, updatedAt) VALUES ('GIN_RUMMY', '{}', 1)")
            execSQL(
                "INSERT INTO game_results (gameId, won, score, durationSec, endedAt, moves, drawCount) " +
                    "VALUES ('KLONDIKE', 1, NULL, 198, 1, 101, 3)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, CURRENT, true, *CardGamesDatabase.MIGRATIONS)

        db.query("SELECT gameId FROM saved_games").use { assertEquals(1, it.count) }
        db.query("SELECT moves, drawCount, ginOpponent FROM game_results").use {
            assertTrue(it.moveToFirst())
            assertEquals(101, it.getInt(0))
            assertEquals(3, it.getInt(1))
            assertTrue(it.isNull(2))
        }
    }

    @Test
    fun `every exported version opens through the app's builder`() {
        // A schema change without its migration fails here rather than wiping a player's data.
        for (version in exportedVersions) {
            val name = "v$version.db"
            helper.createDatabase(name, version).close()
            val db = CardGamesDatabase.open(context, name)
            db.openHelper.writableDatabase
                .query("SELECT COUNT(*) FROM saved_games")
                .close()
            db.close()
        }
    }

    @Test
    fun `the current version's schema is exported`() {
        assertEquals(CURRENT, exportedVersions.last())
        assertEquals((1..CURRENT).toList(), exportedVersions)
    }

    private companion object {
        const val DB = "migration-test.db"

        /** Keep in step with `@Database(version = …)`: a new version needs its migration and its schema. */
        const val CURRENT = 3
    }
}
