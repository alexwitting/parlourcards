package com.cardgames.feature.ginrummy.di

import com.cardgames.core.domain.ginrummy.ai.GinOpponentFactory
import com.cardgames.core.domain.ginrummy.ai.MonteCarloGinOpponent
import com.cardgames.feature.ginrummy.GinPacing
import com.cardgames.feature.ginrummy.GinViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/**
 * Gin's opponents, for [com.cardgames.feature.ginrummy.GinViewModel] only (so the module lives in
 * this feature). It provides a factory rather than an opponent: the ViewModel outlives a match, so
 * it builds a fresh [MonteCarloGinOpponent] at the difficulty current when each match starts.
 */
@Module
@InstallIn(ViewModelComponent::class)
object GinAiModule {
    @Provides
    fun provideGinOpponentFactory(): GinOpponentFactory = GinOpponentFactory { difficulty -> MonteCarloGinOpponent(difficulty) }

    /** The opponent's turn beats; tests construct the ViewModel with [GinPacing.Instant] instead. */
    @Provides
    fun provideGinPacing(): GinPacing = GinPacing.Normal
}
