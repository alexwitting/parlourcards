package com.cardgames.core.testing

import com.cardgames.core.domain.repository.AnimationSpeed
import com.cardgames.core.domain.repository.GinOptions
import com.cardgames.core.domain.repository.HintLimit
import com.cardgames.core.domain.repository.Settings
import com.cardgames.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [SettingsRepository]; set [settings] directly to seed a test. */
class FakeSettingsRepository : SettingsRepository {
    private val settingsFlow = MutableStateFlow(Settings())

    var settings: Settings
        get() = settingsFlow.value
        set(value) {
            settingsFlow.value = value
        }

    override fun observeSettings(): Flow<Settings> = settingsFlow

    override suspend fun setDrawCount(drawCount: Int) {
        settingsFlow.update { it.copy(drawCount = drawCount) }
    }

    override suspend fun setThemeId(themeId: String) {
        settingsFlow.update { it.copy(themeId = themeId) }
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        settingsFlow.update { it.copy(soundEnabled = enabled) }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        settingsFlow.update { it.copy(hapticsEnabled = enabled) }
    }

    override suspend fun setUseSolverForWinnableDeals(enabled: Boolean) {
        settingsFlow.update { it.copy(useSolverForWinnableDeals = enabled) }
    }

    override suspend fun setAnimationSpeed(speed: AnimationSpeed) {
        settingsFlow.update { it.copy(animationSpeed = speed) }
    }

    override suspend fun setReduceMotion(enabled: Boolean) {
        settingsFlow.update { it.copy(reduceMotion = enabled) }
    }

    override suspend fun setSolitaireHints(limit: HintLimit) {
        settingsFlow.update { it.copy(solitaireHints = limit) }
    }

    override suspend fun setGinOptions(options: GinOptions) {
        settingsFlow.update { it.copy(gin = options) }
    }
}
