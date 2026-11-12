package com.cardgames.core.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The statements each migration runs. Room checks the migrated schema against [GameResultEntity]
 * at runtime (an instrumented `MigrationTestHelper` test would run them on a real SQLite); this
 * pins the SQL, and that every column the entity gained is added with a matching type.
 */
class CardGamesDatabaseMigrationTest {
    @Test
    fun `1 to 2 adds moves and draw mode and starts the stats fresh, keeping saves`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        CardGamesDatabase.MIGRATION_1_2.migrate(db)

        verifyOrder {
            db.execSQL("ALTER TABLE game_results ADD COLUMN moves INTEGER")
            db.execSQL("ALTER TABLE game_results ADD COLUMN drawCount INTEGER")
            db.execSQL("DELETE FROM game_results")
        }
    }

    @Test
    fun `2 to 3 adds every Gin match column the entity declares, nullable`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val added = mutableListOf<String>()
        every { db.execSQL(capture(added)) } returns Unit

        CardGamesDatabase.MIGRATION_2_3.migrate(db)

        val columns = added.map { it.removePrefix("ALTER TABLE game_results ADD COLUMN ") }
        val ginFields =
            GameResultEntity::class.java.declaredFields
                .map { it.name }
                .filter { it.startsWith("gin") }
        assertEquals(ginFields.toSet(), columns.map { it.substringBefore(' ') }.toSet())
        assertEquals("ginOpponent TEXT", columns.first { it.startsWith("ginOpponent ") })
        columns.filterNot { it.startsWith("ginOpponent ") }.forEach { assertEquals("INTEGER", it.substringAfter(' ')) }
    }

    @Test
    fun `the migrations run in sequence from 1 to the current version`() {
        assertEquals(1, CardGamesDatabase.MIGRATION_1_2.startVersion)
        assertEquals(CardGamesDatabase.MIGRATION_1_2.endVersion, CardGamesDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, CardGamesDatabase.MIGRATION_2_3.endVersion)
    }
}
