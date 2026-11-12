package com.cardgames.feature.solitaire

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.game.GamePersistence
import com.cardgames.core.domain.game.PlayTimer
import com.cardgames.core.domain.game.SeedProvider
import com.cardgames.core.domain.game.UndoRedoHistory
import com.cardgames.core.domain.repository.GameStats
import com.cardgames.core.domain.repository.SavedGameRepository
import com.cardgames.core.domain.repository.Settings
import com.cardgames.core.domain.repository.SettingsRepository
import com.cardgames.core.domain.repository.StatsRepository
import com.cardgames.core.domain.solitaire.KlondikeAutoComplete
import com.cardgames.core.domain.solitaire.KlondikeAutoMove
import com.cardgames.core.domain.solitaire.KlondikeDealer
import com.cardgames.core.domain.solitaire.KlondikeEngine
import com.cardgames.core.domain.solitaire.KlondikeHints
import com.cardgames.core.domain.solitaire.KlondikeMove
import com.cardgames.core.domain.solitaire.KlondikeSnapshot
import com.cardgames.core.domain.solitaire.KlondikeSolver
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.domain.solitaire.RestartMark
import com.cardgames.core.domain.solitaire.WinnableDealCache
import com.cardgames.core.domain.time.ClockProvider
import com.cardgames.feature.solitaire.board.SolitaireDragSource
import com.cardgames.feature.solitaire.board.SolitaireDropTarget
import com.cardgames.feature.solitaire.board.Tableau
import com.cardgames.feature.solitaire.board.moveFor
import com.cardgames.feature.solitaire.motion.SolitaireMotionTokens
import com.cardgames.feature.solitaire.motion.allInStock
import com.cardgames.feature.solitaire.motion.gathered
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToLong

@HiltViewModel
class SolitaireViewModel
    @Inject
    constructor(
        private val seedProvider: SeedProvider,
        clockProvider: ClockProvider,
        savedGameRepository: SavedGameRepository,
        private val statsRepository: StatsRepository,
        private val settingsRepository: SettingsRepository,
        private val dealCache: WinnableDealCache,
        /**
         * Where [KlondikeDealer]/[KlondikeSolver]'s CPU-bound search runs. Defaulted
         * (not Hilt-qualified) so production gets [Dispatchers.Default] for free; tests
         * pass [Dispatchers.Unconfined] for deterministic, synchronous-feeling execution.
         */
        private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : ViewModel() {
        /** Redo history is discarded by any new-branch action (a move, restart, or auto-complete). */
        private val history = UndoRedoHistory<KlondikeState>()
        private val timer = PlayTimer(clockProvider)

        private val finishFinder = FinishFinder(defaultDispatcher)

        /** Restarts still undoable (see [RestartMark]), oldest first. */
        private val restarts = mutableListOf<RestartMark>()
        private val persistence =
            GamePersistence(
                GameId.KLONDIKE,
                savedGameRepository,
                statsRepository,
                KlondikeSnapshot::toJson,
                KlondikeSnapshot::fromJson,
                defaultDispatcher,
            )
        private var isScreenVisible = false

        /** The pause menu is up — the timer stops, like a hidden screen. */
        private var isPaused = false

        /** Draw mode for new deals; a dealt game keeps its own frozen drawCount. */
        private var latestDrawCount = Settings.DEFAULT_DRAW_COUNT

        /** Mirrors [latestDrawCount]'s pattern — updated from the settings-collector below. */
        private var latestUseSolver = Settings().useSolverForWinnableDeals
        private var latestHintLimit = Settings().solitaireHints
        private var latestPace = Settings().animationSpeed.durationScale
        private var latestReduceMotion = Settings().reduceMotion

        /** A restart or new deal is sending the cards home: play input waits until the deal starts. */
        private var resetting = false

        /** How long the stock shuffles before a new deal, at normal speed. Tests set 0. */
        @VisibleForTesting
        internal var shuffleMillis = SolitaireMotionTokens.SHUFFLE_MILLIS
        private var latestStats = GameStats()

        /** Hints taken this deal. */
        private var hintsUsed = 0

        /** Guards against double-recording one deal's result (undo the win, win again). */
        private var resultRecorded = false

        /** What the landing of the pending motion commits beyond the position itself. */
        private var onLanded: (() -> Unit)? = null
        private var nextMotionId = 0L

        private val stuckChecker = StuckChecker(defaultDispatcher)

        private val _state =
            MutableStateFlow(
                SolitaireState(
                    game = KlondikeEngine.deal(seedProvider.nextSeed(), latestDrawCount),
                    loaded = false,
                    hintsLeft = latestHintLimit.remaining(0),
                ),
            )
        val state: StateFlow<SolitaireState> = _state.asStateFlow()

        private val _effects = MutableSharedFlow<SolitaireEffect>()
        val effects: SharedFlow<SolitaireEffect> = _effects.asSharedFlow()

        init {
            restoreSavedGame()
            viewModelScope.launch {
                statsRepository
                    .observeStats(GameId.KLONDIKE)
                    .catch { Log.e(TAG, "Stats stream failed", it) }
                    .collect { latestStats = it }
            }
        }

        fun onIntent(intent: SolitaireIntent) {
            when (intent) {
                is SolitaireIntent.MoveLanded -> land(intent.id)
                SolitaireIntent.ScreenVisible, SolitaireIntent.ScreenHidden -> {
                    isScreenVisible = intent == SolitaireIntent.ScreenVisible
                    syncTimer(isWon = _state.value.isWon)
                }
                is SolitaireIntent.Paused -> {
                    isPaused = intent.paused
                    syncTimer(isWon = _state.value.isWon)
                }
                else -> {
                    if (resetting) return
                    // Interruption: anything still in flight lands at once, then the new input counts.
                    _state.value.pending?.let { land(it.id) }
                    onPlayIntent(intent)
                }
            }
        }

        private fun onPlayIntent(intent: SolitaireIntent) {
            when (intent) {
                is SolitaireIntent.DropCards -> {
                    val move = _state.value.game.moveFor(intent.source, intent.target)
                    if (move != null) applyMove(move, MotionKind.DROP)
                }
                SolitaireIntent.Undo -> onUndo()
                SolitaireIntent.Redo -> stepHistory(history::redo)
                SolitaireIntent.NewGame -> onNewGame()
                SolitaireIntent.RestartDeal -> onRestartDeal()
                SolitaireIntent.Hint -> onHint()
                SolitaireIntent.AutoComplete -> onAutoComplete()
                else ->
                    when (val action = tapAction(intent, _state.value)) {
                        is TapAction.Play -> applyMove(action.move, action.kind)
                        is TapAction.Select -> _state.update { it.copy(selection = action.source, hint = null) }
                        TapAction.PutDown -> _state.update { it.copy(selection = null) }
                        null -> Unit
                    }
            }
        }

        /**
         * Resume the saved game if one exists; otherwise deal a fresh, winnable-guaranteed game
         * at the configured draw count once settings first arrive (the eagerly-dealt placeholder
         * from the constructor is untouched, so replacing it is invisible). Then keep following
         * the setting for future untouched deals — never for a game already in progress.
         */
        private fun restoreSavedGame() {
            viewModelScope.launch {
                runCatching { persistence.load() }
                    .onSuccess { snapshot ->
                        if (snapshot != null) {
                            history.restore(undo = snapshot.undoStack, redo = snapshot.redoStack)
                            restarts.clear()
                            restarts += snapshot.restarts
                            timer.reset(snapshot.elapsedSeconds)
                            hintsUsed = snapshot.hintsUsed
                            show(snapshot.game, save = false)
                        }
                    }.onFailure { Log.e(TAG, "Couldn't load the saved game; starting fresh", it) }
                settingsRepository
                    .observeSettings()
                    .catch { Log.e(TAG, "Settings stream failed; keeping draw-$latestDrawCount", it) }
                    .collect { settings ->
                        latestDrawCount = settings.drawCount
                        latestUseSolver = settings.useSolverForWinnableDeals
                        latestPace = settings.animationSpeed.durationScale
                        latestReduceMotion = settings.reduceMotion
                        latestHintLimit = settings.solitaireHints
                        _state.update { it.copy(hintsLeft = latestHintLimit.remaining(hintsUsed)) }
                        // Keeps the queue warm from app start onward — a no-op if already full/filling.
                        if (latestUseSolver) dealCache.ensureFilling(settings.drawCount)
                        val current = _state.value
                        val untouched = current.game.moveCount == 0 && !current.canUndo && current.pending == null
                        // A seed proven winnable at one draw count isn't necessarily winnable at
                        // another — always searches a fresh seed rather than re-cutting the deal.
                        if (untouched && current.game.drawCount != settings.drawCount) {
                            show(_state.dealForNewGame(seedProvider, settings.drawCount, defaultDispatcher, latestUseSolver, dealCache))
                        }
                        // Restored, or the stand-in kept as the first deal: either way the table is real now.
                        if (!_state.value.loaded) _state.update { it.copy(loaded = true) }
                    }
            }
        }

        /** Starts [move]'s motion; its sound, save and any stuck check wait for the landing. */
        private fun applyMove(
            move: KlondikeMove,
            kind: MotionKind,
        ) {
            val game = _state.value.game
            if (!KlondikeEngine.isLegal(game, move)) return
            history.record(game)
            val next = KlondikeEngine.apply(game, move)
            stuckChecker.recordMove(game, next)
            startMotion(next, kind) {
                if (!KlondikeEngine.isWon(next)) emitEffect(feedbackFor(move, before = game, after = next))
                if (move is KlondikeMove.RecycleWaste) {
                    // Debounced to a whole stock cycle going nowhere; a late result is dropped
                    // once the player has moved on.
                    viewModelScope.launch {
                        if (stuckChecker.checkAfterRecycle(next) && _state.value.game == next) {
                            _state.update { it.copy(isStuck = true) }
                        }
                    }
                }
            }
        }

        /**
         * Undo, which across a restart also gives back the time the restart set aside — added to
         * the time spent on the replay, so a deal's time only ever grows.
         */
        private fun onUndo() {
            val restart = restarts.lastOrNull()?.takeIf { it.before == history.peekUndo() }
            if (restart != null) {
                restarts.removeAt(restarts.lastIndex)
                timer.reset(restart.elapsedSeconds + timer.elapsedSeconds)
                syncTimer(isWon = _state.value.isWon)
            }
            stepHistory(history::undo)
        }

        /** Shared undo/redo mechanics: [step] the history from the current position and fly back to where it lands. */
        private fun stepHistory(step: (current: KlondikeState) -> KlondikeState?) {
            val target = step(_state.value.game) ?: return
            startMotion(target, MotionKind.UNDO) { emitEffect(SolitaireEffect.CardMoved) }
        }

        /**
         * An abandoned in-progress deal counts as a loss before the fresh one starts. Guarded by
         * [SolitaireState.isDealing] so a stacked tap mid-search is a no-op — the old board stays
         * on screen throughout (no flash-then-swap).
         */
        private fun onNewGame() {
            val current = _state.value
            if (current.isDealing) return
            val abandoned = current.game.takeIf { !current.isWon && it.moveCount > 0 && !resultRecorded }
            val duration = timer.elapsedSeconds
            history.clear()
            restarts.clear()
            timer.reset()
            resultRecorded = false
            hintsUsed = 0
            viewModelScope.launch {
                val dealt = _state.dealForNewGame(seedProvider, latestDrawCount, defaultDispatcher, latestUseSolver, dealCache)
                // The old game's loss and the new deal's save go together, before the cards move:
                // leaving mid-animation must not reopen a game already counted (to be counted again).
                abandoned?.let { game ->
                    runPersisting("Couldn't record the abandoned game") {
                        persistence.recordResult(
                            won = false,
                            durationSeconds = duration,
                            moves = game.moveCount,
                            drawCount = game.drawCount,
                        )
                    }
                }
                persistPosition(dealt)
                // The old cards fly home, the stock shuffles, then the new deal goes out from it.
                resetting = true
                startMotion(_state.value.game.allInStock(), MotionKind.GATHER) {
                    _state.update { it.copy(shuffling = true) }
                    emitEffect(SolitaireEffect.Shuffled)
                    val deal = {
                        resetting = false
                        startMotion(dealt, MotionKind.DEAL, from = dealt.gathered())
                    }
                    // Reduce motion draws no shake, so there's nothing to wait for.
                    val pause = if (latestReduceMotion) 0L else (shuffleMillis * latestPace).roundToLong()
                    if (pause > 0) {
                        viewModelScope.launch {
                            delay(pause)
                            deal()
                        }
                    } else {
                        deal()
                    }
                }
            }
        }

        /**
         * Same seed, move zero: every card flies back to the stock, then the deal replays. Pushed
         * onto the undo stack so a stray tap isn't fatal; the timer starts again from 0 with the deal.
         */
        private fun onRestartDeal() {
            val game = _state.value.game
            if (_state.value.isDealing) return
            history.record(game)
            restarts += RestartMark(before = game, elapsedSeconds = timer.elapsedSeconds)
            val dealt = KlondikeEngine.deal(game.seed, game.drawCount)
            val gathered = dealt.gathered()
            resetting = true
            startMotion(gathered, MotionKind.GATHER) {
                resetting = false
                timer.reset()
                emitEffect(SolitaireEffect.Shuffled)
                startMotion(dealt, MotionKind.DEAL, from = gathered)
            }
        }

        private fun onHint() {
            val current = _state.value
            if (current.hintsLeft == 0) return
            val hint = KlondikeHints.suggest(current.game) ?: return
            hintsUsed++
            _state.update { it.copy(hint = hint, selection = null, hintsLeft = latestHintLimit.remaining(hintsUsed)) }
            persistPosition(current.game)
        }

        /** One undo step returns to the position before auto-complete started. */
        private fun onAutoComplete() {
            val start = _state.value.game
            val moves = finishFinder.finishFor(start) ?: KlondikeAutoComplete.playOut(start) ?: return
            history.record(start)
            startMotion(moves.fold(start, KlondikeEngine::apply), MotionKind.AUTO_COMPLETE)
        }

        /**
         * Shows [next] with the cards in flight from [from] (by default the position on screen).
         * [whenLanded] runs once [SolitaireIntent.MoveLanded] arrives for this motion.
         */
        private fun startMotion(
            next: KlondikeState,
            kind: MotionKind,
            from: KlondikeState = _state.value.game,
            whenLanded: () -> Unit = {},
        ) {
            val motion = PendingMotion(id = ++nextMotionId, kind = kind, from = from)
            onLanded = whenLanded
            show(next, motion)
        }

        /** Commits the pending motion [id]: the position counts from here (save, sound, win). */
        private fun land(id: Long) {
            val current = _state.value
            if (current.pending?.id != id) return
            val after = onLanded
            onLanded = null
            // Gathered for a restart is only a waypoint: never settled, saved or judged stuck.
            if (current.pending.kind != MotionKind.GATHER) show(current.game)
            after?.invoke()
        }

        /** The timer runs exactly when the screen is visible, not paused, and the game isn't won. */
        private fun syncTimer(isWon: Boolean) {
            val refresh = { _state.update { it.copy(elapsedSeconds = timer.elapsedSeconds) } }
            if (isScreenVisible && !isPaused && !isWon) timer.start(viewModelScope, refresh) else timer.stop()
            refresh()
        }

        /**
         * Puts [game] on the board. With [motion] the cards are on their way and nothing is
         * committed; without one the position is settled — saved, or won.
         */
        private fun show(
            game: KlondikeState,
            motion: PendingMotion? = null,
            // False for a position just restored from the save — writing it straight back is wasted work.
            save: Boolean = true,
        ) {
            val settled = motion == null
            val isWon = settled && KlondikeEngine.isWon(game)
            syncTimer(isWon = isWon)
            _state.value =
                SolitaireState(
                    game = game,
                    pending = motion,
                    canUndo = history.canUndo,
                    canRedo = history.canRedo,
                    isWon = isWon,
                    canAutoComplete = settled && finishFinder.finishFor(game) != null,
                    elapsedSeconds = timer.elapsedSeconds,
                    hintsLeft = latestHintLimit.remaining(hintsUsed),
                    abandonCountsAsLoss = !isWon && game.moveCount > 0 && !resultRecorded,
                )
            if (settled) {
                if (isWon) {
                    onGameWon(game)
                } else if (save) {
                    persistPosition(game)
                }
                finishFinder.lookFor(game, viewModelScope) {
                    _state.update { if (it.game == game && it.pending == null) it.copy(canAutoComplete = true) else it }
                }
            }
        }

        /**
         * Autosave on every settled position (move, undo, restore, new deal). The undo/redo
         * history rides along too, capped (see [UndoRedoHistory.PERSISTED_LIMIT]) so a long game's
         * autosave doesn't re-encode an ever-growing payload on every move; the live history stays
         * unlimited for the session.
         */
        private fun persistPosition(game: KlondikeState) {
            val snapshot =
                KlondikeSnapshot(
                    game = game,
                    elapsedSeconds = timer.elapsedSeconds,
                    undoStack = history.persistedUndo(),
                    redoStack = history.persistedRedo(),
                    hintsUsed = hintsUsed,
                    restarts = restarts.toList(),
                )
            runPersisting("Couldn't save your game") { persistence.save(snapshot) }
        }

        private fun onGameWon(game: KlondikeState) {
            emitEffect(SolitaireEffect.GameWon)
            val duration = timer.elapsedSeconds
            val alreadyRecorded = resultRecorded
            resultRecorded = true
            _state.update { it.copy(solved = latestStats.solvedSummary(game, duration, counted = alreadyRecorded)) }
            runPersisting("Couldn't record the win") {
                persistence.clear()
                if (!alreadyRecorded) {
                    persistence.recordResult(won = true, durationSeconds = duration, moves = game.moveCount, drawCount = game.drawCount)
                }
            }
        }

        private fun emitEffect(effect: SolitaireEffect) {
            viewModelScope.launch { _effects.emit(effect) }
        }

        private fun runPersisting(
            errorMessage: String,
            block: suspend () -> Unit,
        ) {
            viewModelScope.launch {
                runCatching { block() }
                    .onFailure {
                        Log.e(TAG, errorMessage, it)
                        _effects.emit(SolitaireEffect.ShowSnackbar(errorMessage))
                    }
            }
        }

        /** Test seam: pin an exact position without replaying moves. Clears undo/redo history. */
        internal fun overrideGameForTest(game: KlondikeState) {
            history.clear()
            restarts.clear()
            onLanded = null
            show(game)
        }

        private companion object {
            const val TAG = "SolitaireViewModel"
        }
    }

/** What a tap (or hold) on the table does, given whether a card is held up. */
internal sealed interface TapAction {
    data class Play(
        val move: KlondikeMove,
        val kind: MotionKind,
    ) : TapAction

    data class Select(
        val source: SolitaireDragSource,
    ) : TapAction

    /** Put the held card down without moving it. */
    data object PutDown : TapAction
}

/**
 * Taps and holds as moves. With nothing held: a tap on a card sends it to its best place, the
 * stock draws (or turns the waste over), a hold picks a card up. With a card held: a tap on a
 * place it can legally go moves it there; any other tap puts it down. Null: nothing happens.
 */
internal fun tapAction(
    intent: SolitaireIntent,
    state: SolitaireState,
): TapAction? {
    val game = state.game
    val selection = state.selection
    if (intent is SolitaireIntent.Hold) return TapAction.Select(intent.source)
    if (selection == null) return freeTap(intent, game)
    val target =
        when (intent) {
            is SolitaireIntent.TapTableauCard ->
                SolitaireDropTarget
                    .Tableau(
                        intent.pile,
                    ).takeIf { selection != SolitaireDragSource.TableauRun(intent.pile, intent.cardIndex) }
            is SolitaireIntent.TapEmptyTableau -> SolitaireDropTarget.Tableau(intent.pile)
            is SolitaireIntent.TapFoundation -> SolitaireDropTarget.Foundation(intent.suit)
            else -> null
        }
    val move = target?.let { game.moveFor(selection, it) }?.takeIf { KlondikeEngine.isLegal(game, it) }
    return if (move == null) TapAction.PutDown else TapAction.Play(move, MotionKind.MOVE)
}

/** A tap with nothing held. */
private fun freeTap(
    intent: SolitaireIntent,
    game: KlondikeState,
): TapAction? {
    val move =
        when (intent) {
            SolitaireIntent.TapStock -> if (game.stock.isNotEmpty()) KlondikeMove.DealFromStock else KlondikeMove.RecycleWaste
            SolitaireIntent.TapWaste -> KlondikeAutoMove.bestMoveFor(game, KlondikeAutoMove.Source.Waste)
            is SolitaireIntent.TapTableauCard ->
                KlondikeAutoMove.bestMoveFor(game, KlondikeAutoMove.Source.Tableau(intent.pile, intent.cardIndex))
            else -> null
        } ?: return null
    val kind =
        when (move) {
            KlondikeMove.DealFromStock -> MotionKind.DRAW
            KlondikeMove.RecycleWaste -> MotionKind.RECYCLE
            else -> MotionKind.MOVE
        }
    return TapAction.Play(move, kind)
}

/** The Solved figures: these stats plus this win of [game] in [seconds], unless it's [counted] in them already. */
internal fun GameStats.solvedSummary(
    game: KlondikeState,
    seconds: Int,
    counted: Boolean,
): SolvedSummary {
    val previousBest = bestDurationSeconds
    val add = if (counted) 0 else 1
    return SolvedSummary(
        seconds = seconds,
        moves = game.moveCount,
        drawCount = game.drawCount,
        streak = currentStreak + add,
        bestSeconds = minOf(previousBest ?: seconds, seconds),
        isNewBest = previousBest == null || seconds < previousBest,
        gamesWon = gamesWon + add,
        gamesPlayed = gamesPlayed + add,
    )
}

/**
 * A new deal. With [useSolver] off, an ordinary deal. Otherwise a winnable one: from [dealCache]
 * if it has one ready (instant), else from [KlondikeDealer]'s search, run on [dispatcher] with
 * `isDealing` set while it works.
 */
private suspend fun MutableStateFlow<SolitaireState>.dealForNewGame(
    seedProvider: SeedProvider,
    drawCount: Int,
    dispatcher: CoroutineDispatcher,
    useSolver: Boolean,
    dealCache: WinnableDealCache,
): KlondikeState {
    if (!useSolver) return KlondikeEngine.deal(seedProvider.nextSeed(), drawCount)
    dealCache.take(drawCount)?.let { return it }
    update { it.copy(isDealing = true) }
    return withContext(dispatcher) { KlondikeDealer.dealWinnable(seedProvider, drawCount) }
}

/**
 * Decides when to say "No moves left". Tracks whether anything has changed since the current stock
 * cycle began; once a whole cycle passes with no progress, a bounded solver search looks for a way
 * forward. [checkAfterRecycle] returning true means that search found none — enough for a message
 * the player can ignore, not a proof that the deal is lost.
 */
private class StuckChecker(
    private val dispatcher: CoroutineDispatcher,
) {
    private var hasProgressed = false

    /** A foundation gaining a card, or a tableau flip — the two ways a stock cycle can matter. */
    fun recordMove(
        before: KlondikeState,
        after: KlondikeState,
    ) {
        if (after.foundations.values.sum() > before.foundations.values.sum() ||
            after.tableau.sumOf { it.faceDownCount } < before.tableau.sumOf { it.faceDownCount }
        ) {
            hasProgressed = true
        }
    }

    /** Call when [KlondikeMove.RecycleWaste] lands; suspends only when this cycle made zero progress. */
    suspend fun checkAfterRecycle(state: KlondikeState): Boolean {
        val stuckSoFar = !hasProgressed
        hasProgressed = false
        if (!stuckSoFar) return false
        val result =
            withContext(dispatcher) {
                val deadline = System.nanoTime() + STUCK_CHECK_DEADLINE_MILLIS * NANOS_PER_MILLI
                KlondikeSolver.solve(state, nodeBudget = STUCK_NODE_BUDGET, deadlineNanos = deadline)
            }
        return result is KlondikeSolver.Result.NotFound
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L

        /** A stuck check should feel like a background thought, not a pause — small budget, no user is waiting on it directly. */
        const val STUCK_CHECK_DEADLINE_MILLIS = 500L
        const val STUCK_NODE_BUDGET = 50_000
    }
}

/**
 * The finish for a position with nothing hidden in the tableau (see [KlondikeAutoComplete]), worked
 * out off the main thread — a draw-3 one can take a search — and kept for Complete to play.
 */
private class FinishFinder(
    private val dispatcher: CoroutineDispatcher,
) {
    private var found: Pair<KlondikeState, List<KlondikeMove>>? = null
    private var search: Job? = null

    /** The moves that finish [game], once they've been found. */
    fun finishFor(game: KlondikeState): List<KlondikeMove>? = found?.takeIf { it.first == game }?.second

    /** Looks for [game]'s finish in [scope] (dropping any earlier search), and calls [onFound] if there is one. */
    fun lookFor(
        game: KlondikeState,
        scope: CoroutineScope,
        onFound: () -> Unit,
    ) {
        search?.cancel()
        if (finishFor(game) != null || KlondikeEngine.isWon(game) || game.tableau.any { it.faceDownCount > 0 }) return
        search =
            scope.launch {
                val moves = withContext(dispatcher) { KlondikeAutoComplete.playOut(game) } ?: return@launch
                found = game to moves
                onFound()
            }
    }
}
