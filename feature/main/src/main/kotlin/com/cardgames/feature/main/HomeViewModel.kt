package com.cardgames.feature.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinSnapshot
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.GameResult
import com.cardgames.core.domain.repository.SavedGame
import com.cardgames.core.domain.repository.SavedGameRepository
import com.cardgames.core.domain.repository.StatsRepository
import com.cardgames.core.domain.solitaire.KlondikeSnapshot
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        savedGameRepository: SavedGameRepository,
        statsRepository: StatsRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HomeState())
        val state: StateFlow<HomeState> = _state.asStateFlow()

        /** Games with a save in progress — Gin's row resumes its match rather than opening setup. */
        private var inProgress: Set<GameId> = emptySet()

        private val _effects = MutableSharedFlow<HomeEffect>()
        val effects: SharedFlow<HomeEffect> = _effects.asSharedFlow()

        init {
            viewModelScope.launch {
                val statsFlows = GameId.entries.map { statsRepository.observeStats(it) }
                combine(
                    savedGameRepository.observeInProgress().map { ids ->
                        inProgress = ids
                        latestSave(savedGameRepository, ids)
                    },
                    combine(statsFlows) { all -> GameId.entries.zip(all.toList()).toMap() },
                    statsRepository.observeResults(GameId.GIN_RUMMY).map { results -> latestRivalRecord(results.map { it.result }) },
                ) { latest, stats, ginRecord ->
                    HomeState(continueGame = latest?.let(::summarise), stats = stats, ginRecord = ginRecord)
                    // An uncaught exception here would silently end the flow forever (see CLAUDE.md);
                    // the screen keeps its last state and new games/stats just stop updating.
                }.catch { Log.e(TAG, "Home data stream failed", it) }
                    .collect { _state.value = it }
            }
        }

        fun onIntent(intent: HomeIntent) {
            val effect =
                when (intent) {
                    is HomeIntent.Resume -> HomeEffect.NavigateToGame(intent.gameId)
                    is HomeIntent.PlayGame ->
                        // Gin's row starts a match from setup — unless one is under way, which it resumes
                        // (a new one is a step away in its pause menu).
                        if (intent.gameId == GameId.GIN_RUMMY && GameId.GIN_RUMMY !in inProgress) {
                            HomeEffect.NavigateToGinSetup
                        } else {
                            HomeEffect.NavigateToGame(intent.gameId)
                        }
                }
            viewModelScope.launch { _effects.emit(effect) }
        }

        private companion object {
            const val TAG = "HomeViewModel"
        }
    }

/** The record against whoever the player last played at Gin; null before the first match. */
private fun latestRivalRecord(gin: List<GameResult>): Pair<GinDifficulty, Record>? =
    gin
        .lastOrNull { it.gin != null }
        ?.gin
        ?.opponent
        ?.let { rival -> rival to gin.recordAgainst(rival) }

/** The most recently saved of the in-progress [ids]; a save that won't load is skipped. */
private suspend fun latestSave(
    repository: SavedGameRepository,
    ids: Set<GameId>,
): SavedGame? = ids.mapNotNull { runCatching { repository.load(it) }.getOrNull() }.maxByOrNull { it.updatedAtEpochMillis }

/** The Continue card's figures from a save; null when it doesn't parse (the game itself will start fresh). */
internal fun summarise(save: SavedGame): ContinueSummary? =
    when (save.gameId) {
        GameId.GIN_RUMMY ->
            GinSnapshot.fromJson(save.stateJson)?.let { snapshot ->
                val game = snapshot.game
                ContinueSummary.Gin(
                    handNumber = game.handNumber,
                    opponent = snapshot.opponent ?: GinDifficulty.MEDIUM,
                    yourScore = game.matchScores.getValue(GinPlayer.PLAYER),
                    theirScore = game.matchScores.getValue(GinPlayer.OPPONENT),
                    target = game.rules.target,
                    yourTurn = game.phase.let { it is GinPhase.HandOver || it.isPlayers() },
                )
            }
        GameId.KLONDIKE ->
            KlondikeSnapshot.fromJson(save.stateJson)?.let { snapshot ->
                ContinueSummary.Solitaire(
                    drawCount = snapshot.game.drawCount,
                    moves = snapshot.game.moveCount,
                    elapsedSeconds = snapshot.elapsedSeconds,
                    cardsHome =
                        snapshot.game.foundations.values
                            .sum(),
                )
            }
    }

private fun GinPhase.isPlayers(): Boolean =
    when (this) {
        is GinPhase.UpcardDecision -> deciding == GinPlayer.PLAYER
        is GinPhase.AwaitingDraw -> toMove == GinPlayer.PLAYER
        is GinPhase.AwaitingDiscard -> toMove == GinPlayer.PLAYER
        else -> false
    }

sealed interface HomeIntent {
    /** A game's row: its table — or, for Gin with no match under way, match setup. */
    data class PlayGame(
        val gameId: GameId,
    ) : HomeIntent

    /** The Continue card: back to the saved game. */
    data class Resume(
        val gameId: GameId,
    ) : HomeIntent
}

sealed interface HomeEffect {
    data class NavigateToGame(
        val gameId: GameId,
    ) : HomeEffect

    data object NavigateToGinSetup : HomeEffect
}
