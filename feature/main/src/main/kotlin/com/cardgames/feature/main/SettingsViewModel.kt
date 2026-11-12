package com.cardgames.feature.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardgames.core.domain.ginrummy.GinMatchRules
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.AnimationSpeed
import com.cardgames.core.domain.repository.GinOptions
import com.cardgames.core.domain.repository.HintLimit
import com.cardgames.core.domain.repository.Settings
import com.cardgames.core.domain.repository.SettingsRepository
import com.cardgames.core.domain.theme.ThemePack
import com.cardgames.core.domain.theme.ThemeRepository
import com.cardgames.core.domain.theme.resolve
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val settings: Settings = Settings(),
    /** Validated theme packs; empty means only the dev placeholder exists. */
    val themePacks: List<ThemePack> = emptyList(),
) {
    /**
     * The pack that is effectively active, mirroring the app shell's resolution: the stored
     * id when it names an installed pack, else the default pack, else any pack. Null only when
     * no packs are installed. The picker marks this one selected even before a first choice.
     */
    val selectedThemeId: String?
        get() = themePacks.resolve(settings.themeId)?.id
}

sealed interface SettingsIntent {
    data class SetDrawCount(
        val drawCount: Int,
    ) : SettingsIntent

    data class SelectTheme(
        val themeId: String,
    ) : SettingsIntent

    data class SetSoundEnabled(
        val enabled: Boolean,
    ) : SettingsIntent

    data class SetHapticsEnabled(
        val enabled: Boolean,
    ) : SettingsIntent

    data class SetUseSolverForWinnableDeals(
        val enabled: Boolean,
    ) : SettingsIntent

    data class SetGinDifficulty(
        val difficulty: GinDifficulty,
    ) : SettingsIntent

    data class SetAnimationSpeed(
        val speed: AnimationSpeed,
    ) : SettingsIntent

    data class SetReduceMotion(
        val enabled: Boolean,
    ) : SettingsIntent

    data class SetSolitaireHints(
        val limit: HintLimit,
    ) : SettingsIntent

    data class SetGinHints(
        val limit: HintLimit,
    ) : SettingsIntent

    data class SetGinRules(
        val rules: GinMatchRules,
    ) : SettingsIntent

    data class SetGinConfirmKnock(
        val enabled: Boolean,
    ) : SettingsIntent

    data class SetGinMeldHints(
        val enabled: Boolean,
    ) : SettingsIntent
}

sealed interface SettingsEffect {
    data class ShowSnackbar(
        val message: String,
    ) : SettingsEffect
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        themeRepository: ThemeRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SettingsState())
        val state: StateFlow<SettingsState> = _state.asStateFlow()

        private val _effects = MutableSharedFlow<SettingsEffect>()
        val effects: SharedFlow<SettingsEffect> = _effects.asSharedFlow()

        init {
            viewModelScope.launch {
                val packs = runCatching { themeRepository.availablePacks() }.getOrDefault(emptyList())
                settingsRepository
                    .observeSettings()
                    .catch { Log.e(TAG, "Settings stream failed", it) }
                    .collect { settings ->
                        _state.value = SettingsState(settings = settings, themePacks = packs)
                    }
            }
        }

        fun onIntent(intent: SettingsIntent) {
            when (intent) {
                is SettingsIntent.SetDrawCount -> write { settingsRepository.setDrawCount(intent.drawCount) }
                is SettingsIntent.SelectTheme -> write { settingsRepository.setThemeId(intent.themeId) }
                is SettingsIntent.SetSoundEnabled -> write { settingsRepository.setSoundEnabled(intent.enabled) }
                is SettingsIntent.SetHapticsEnabled -> write { settingsRepository.setHapticsEnabled(intent.enabled) }
                is SettingsIntent.SetUseSolverForWinnableDeals ->
                    write { settingsRepository.setUseSolverForWinnableDeals(intent.enabled) }
                is SettingsIntent.SetGinDifficulty -> writeGin { it.copy(difficulty = intent.difficulty) }
                is SettingsIntent.SetAnimationSpeed -> write { settingsRepository.setAnimationSpeed(intent.speed) }
                is SettingsIntent.SetReduceMotion -> write { settingsRepository.setReduceMotion(intent.enabled) }
                is SettingsIntent.SetSolitaireHints -> write { settingsRepository.setSolitaireHints(intent.limit) }
                is SettingsIntent.SetGinHints -> writeGin { it.copy(hints = intent.limit) }
                is SettingsIntent.SetGinRules -> writeGin { it.copy(rules = intent.rules) }
                is SettingsIntent.SetGinConfirmKnock -> writeGin { it.copy(confirmKnock = intent.enabled) }
                is SettingsIntent.SetGinMeldHints -> writeGin { it.copy(meldHints = intent.enabled) }
            }
        }

        private fun writeGin(change: (GinOptions) -> GinOptions) {
            write { settingsRepository.setGinOptions(change(_state.value.settings.gin)) }
        }

        private fun write(block: suspend () -> Unit) {
            viewModelScope.launch {
                runCatching { block() }
                    .onFailure {
                        Log.e(TAG, "Couldn't save the setting", it)
                        _effects.emit(SettingsEffect.ShowSnackbar("Couldn't save the setting"))
                    }
            }
        }

        private companion object {
            const val TAG = "SettingsViewModel"
        }
    }
