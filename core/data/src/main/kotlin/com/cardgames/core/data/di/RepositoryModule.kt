package com.cardgames.core.data.di

import com.cardgames.core.data.game.RandomSeedProvider
import com.cardgames.core.data.repository.RoomSavedGameRepository
import com.cardgames.core.data.repository.RoomStatsRepository
import com.cardgames.core.data.settings.DataStoreSettingsRepository
import com.cardgames.core.data.solitaire.InMemoryWinnableDealCache
import com.cardgames.core.data.theme.AssetThemeRepository
import com.cardgames.core.data.time.SystemClockProvider
import com.cardgames.core.domain.game.SeedProvider
import com.cardgames.core.domain.repository.SavedGameRepository
import com.cardgames.core.domain.repository.SettingsRepository
import com.cardgames.core.domain.repository.StatsRepository
import com.cardgames.core.domain.solitaire.WinnableDealCache
import com.cardgames.core.domain.theme.ThemeRepository
import com.cardgames.core.domain.time.ClockProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** One `@Binds @Singleton` per repository-ish interface in `:core:domain` (see CLAUDE.md). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindClockProvider(impl: SystemClockProvider): ClockProvider

    @Binds
    @Singleton
    abstract fun bindSeedProvider(impl: RandomSeedProvider): SeedProvider

    @Binds
    @Singleton
    abstract fun bindSavedGameRepository(impl: RoomSavedGameRepository): SavedGameRepository

    @Binds
    @Singleton
    abstract fun bindStatsRepository(impl: RoomStatsRepository): StatsRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindThemeRepository(impl: AssetThemeRepository): ThemeRepository

    @Binds
    @Singleton
    abstract fun bindWinnableDealCache(impl: InMemoryWinnableDealCache): WinnableDealCache
}
