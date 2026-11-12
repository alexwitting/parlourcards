package com.cardgames.feature.ginrummy.setup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.GinOptions
import com.cardgames.core.domain.repository.HintLimit
import com.cardgames.core.domain.repository.SavedGameRepository
import com.cardgames.core.domain.repository.SettingsRepository
import com.cardgames.core.domain.repository.StatsRepository
import com.cardgames.core.domain.stats.Record
import com.cardgames.core.domain.stats.recordAgainst
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Match setup: the choices so far, starting from the saved Gin options. */
data class GinSetupState(
    val options: GinOptions = GinOptions(),
    /** A match is saved mid-play — dealing a new one gives it up (and asks first). */
    val matchInProgress: Boolean = false,
    /** The player's record against each rival. */
    val records: Map<GinDifficulty, Record> = emptyMap(),
)

sealed interface GinSetupIntent {
    data class PickOpponent(
        val opponent: GinDifficulty,
    ) : GinSetupIntent

    data class PlayTo(
        val target: Int,
    ) : GinSetupIntent

    data class GinBonus(
        val on: Boolean,
    ) : GinSetupIntent

    data class BigGin(
        val on: Boolean,
    ) : GinSetupIntent

    data class Hints(
        val on: Boolean,
    ) : GinSetupIntent

    /** Save the choices and start the match. */
    data object Deal : GinSetupIntent
}

sealed interface GinSetupEffect {
    /** The choices are saved: open the table for a fresh match. */
    data object StartMatch : GinSetupEffect

    data class ShowSnackbar(
        val message: String,
    ) : GinSetupEffect
}

/**
 * Match setup — the rival, the target and the house rules — kept as the Gin options, so they're
 * also the next match's defaults. Nothing is saved until Deal.
 */
@HiltViewModel
class GinSetupViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        savedGameRepository: SavedGameRepository,
        statsRepository: StatsRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(GinSetupState())
        val state: StateFlow<GinSetupState> = _state.asStateFlow()

        private val _effects = MutableSharedFlow<GinSetupEffect>()
        val effects: SharedFlow<GinSetupEffect> = _effects.asSharedFlow()

        init {
            viewModelScope.launch {
                runCatching { settingsRepository.observeSettings().first() }
                    .onSuccess { settings -> _state.update { it.copy(options = settings.gin) } }
                    .onFailure { Log.e(TAG, "Couldn't read the Gin options", it) }
            }
            viewModelScope.launch {
                savedGameRepository
                    .observeInProgress()
                    .catch { Log.e(TAG, "Saved-game stream failed", it) }
                    .collect { inProgress -> _state.update { it.copy(matchInProgress = GameId.GIN_RUMMY in inProgress) } }
            }
            viewModelScope.launch {
                statsRepository
                    .observeResults(GameId.GIN_RUMMY)
                    .catch { Log.e(TAG, "Stats stream failed", it) }
                    .collect { results ->
                        val gin = results.map { it.result }
                        _state.update { state -> state.copy(records = GinDifficulty.entries.associateWith { gin.recordAgainst(it) }) }
                    }
            }
        }

        fun onIntent(intent: GinSetupIntent) {
            val options = _state.value.options
            when (intent) {
                is GinSetupIntent.PickOpponent -> choose(options.copy(difficulty = intent.opponent))
                is GinSetupIntent.PlayTo -> choose(options.copy(rules = options.rules.copy(target = intent.target)))
                is GinSetupIntent.GinBonus -> choose(options.copy(rules = options.rules.copy(ginBonus = intent.on)))
                is GinSetupIntent.BigGin -> choose(options.copy(rules = options.rules.copy(bigGin = intent.on)))
                is GinSetupIntent.Hints -> choose(options.copy(hints = if (intent.on) HintLimit.THREE else HintLimit.OFF))
                GinSetupIntent.Deal -> deal(options)
            }
        }

        private fun choose(options: GinOptions) = _state.update { it.copy(options = options) }

        private fun deal(options: GinOptions) {
            viewModelScope.launch {
                runCatching { settingsRepository.setGinOptions(options) }
                    .onSuccess { _effects.emit(GinSetupEffect.StartMatch) }
                    .onFailure {
                        Log.e(TAG, "Couldn't save the match setup", it)
                        _effects.emit(GinSetupEffect.ShowSnackbar("Couldn't save your choices"))
                    }
            }
        }

        private companion object {
            const val TAG = "GinSetupViewModel"
        }
    }
