package com.cardgames.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The dispatcher CPU-bound work runs on in production — `SolitaireViewModel`'s solver-backed
 * deals and `InMemoryWinnableDealCache`'s background pre-baking both need it, which is why this
 * lives in a shared module rather than a feature-local one (CLAUDE.md's rule for promoting
 * single-consumer bindings once they gain a second consumer). Unqualified — still only one
 * dispatcher need exists; add a qualifier if a second, differently-scoped one shows up.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DispatcherModule {
    @Provides
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
