package com.cardgames.core.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * One JSON snapshot per game — adding a game needs no schema change. `gameId` is a
 * [com.cardgames.core.domain.game.GameId] name.
 */
@Entity(tableName = "saved_games")
data class SavedGameEntity(
    @PrimaryKey val gameId: String,
    val stateJson: String,
    val updatedAt: Long,
)

/** One finished game; aggregates are computed from these, never stored. */
@Entity(tableName = "game_results")
data class GameResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: String,
    val won: Boolean,
    val score: Int?,
    val durationSec: Int,
    val endedAt: Long,
    val moves: Int? = null,
    val drawCount: Int? = null,
    /** Gin's match details (see [com.cardgames.core.domain.repository.GinMatchRecord]); null for other games. */
    val ginOpponent: String? = null,
    val ginOpponentScore: Int? = null,
    val ginGins: Int? = null,
    val ginUndercuts: Int? = null,
    val ginBestHand: Int? = null,
    val ginKnocks: Int? = null,
    val ginKnockDeadwood: Int? = null,
    val ginHands: Int? = null,
)

@Dao
interface SavedGameDao {
    @Upsert
    suspend fun upsert(entity: SavedGameEntity)

    @Query("SELECT * FROM saved_games WHERE gameId = :gameId")
    suspend fun get(gameId: String): SavedGameEntity?

    @Query("DELETE FROM saved_games WHERE gameId = :gameId")
    suspend fun delete(gameId: String)

    @Query("SELECT gameId FROM saved_games")
    fun observeGameIds(): Flow<List<String>>
}

@Dao
interface GameResultDao {
    @Insert
    suspend fun insert(entity: GameResultEntity)

    @Query("SELECT * FROM game_results WHERE gameId = :gameId ORDER BY endedAt ASC, id ASC")
    fun observeResults(gameId: String): Flow<List<GameResultEntity>>
}

@Database(
    entities = [SavedGameEntity::class, GameResultEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class CardGamesDatabase : RoomDatabase() {
    abstract fun savedGameDao(): SavedGameDao

    abstract fun gameResultDao(): GameResultDao

    companion object {
        /**
         * Results gain moves and draw mode (the Parlour stats). Earlier results are cleared — the
         * redesign starts stats fresh — while saved games are kept.
         */
        val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE game_results ADD COLUMN moves INTEGER")
                    db.execSQL("ALTER TABLE game_results ADD COLUMN drawCount INTEGER")
                    db.execSQL("DELETE FROM game_results")
                }
            }

        /** Results gain Gin's match details: the opponent, their score, gins, undercuts, best hand, knocks. */
        val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    listOf(
                        "ginOpponent TEXT",
                        "ginOpponentScore INTEGER",
                        "ginGins INTEGER",
                        "ginUndercuts INTEGER",
                        "ginBestHand INTEGER",
                        "ginKnocks INTEGER",
                        "ginKnockDeadwood INTEGER",
                        "ginHands INTEGER",
                    ).forEach { column -> db.execSQL("ALTER TABLE game_results ADD COLUMN $column") }
                }
            }

        /** Every migration, in order: the chain from version 1 to the current one. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        /** The database's file name in the app's databases folder. */
        const val NAME = "cardgames.db"

        /**
         * The app's database, opened the one way the app and the upgrade tests both use. Only a
         * downgrade (an older build installed over a newer one) may start the data afresh: an
         * upgrade without its migration fails loudly — the upgrade tests catch it — instead of
         * silently wiping every save and stat.
         */
        fun open(
            context: Context,
            name: String = NAME,
        ): CardGamesDatabase =
            Room
                .databaseBuilder(context, CardGamesDatabase::class.java, name)
                .apply { MIGRATIONS.forEach { addMigrations(it) } }
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
