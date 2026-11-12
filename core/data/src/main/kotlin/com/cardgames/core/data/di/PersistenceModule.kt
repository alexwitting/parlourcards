package com.cardgames.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.cardgames.core.data.db.CardGamesDatabase
import com.cardgames.core.data.db.GameResultDao
import com.cardgames.core.data.db.SavedGameDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Storage infrastructure: the Room database, its DAOs, and DataStore. */
@Module
@InstallIn(SingletonComponent::class)
internal object PersistenceModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): CardGamesDatabase = CardGamesDatabase.open(context)

    @Provides
    fun provideSavedGameDao(db: CardGamesDatabase): SavedGameDao = db.savedGameDao()

    @Provides
    fun provideGameResultDao(db: CardGamesDatabase): GameResultDao = db.gameResultDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.settingsDataStore
}
