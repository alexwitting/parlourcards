package com.cardgames.feature.ginrummy

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.game.GamePersistence
import com.cardgames.core.domain.game.PlayTimer
import com.cardgames.core.domain.game.SeedProvider
import com.cardgames.core.domain.game.UndoRedoHistory
import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinMatchHighlights
import com.cardgames.core.domain.ginrummy.GinMove
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinSnapshot
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.HandOutcome
import com.cardgames.core.domain.ginrummy.RecentMove
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.ginrummy.ai.GinOpponent
import com.cardgames.core.domain.ginrummy.ai.GinOpponentDriver
import com.cardgames.core.domain.ginrummy.ai.GinOpponentFactory
import com.cardgames.core.domain.repository.GinMatchRecord
import com.cardgames.core.domain.repository.GinOptions
import com.cardgames.core.domain.repository.SavedGameRepository
import com.cardgames.core.domain.repository.SettingsRepository
import com.cardgames.core.domain.repository.StatsRepository
import com.cardgames.core.domain.stats.recordAgainst
import com.cardgames.core.domain.time.ClockProvider
import com.cardgames.feature.ginrummy.board.GinDragSource
import com.cardgames.feature.ginrummy.board.GinDropTarget
import com.cardgames.feature.ginrummy.board.moveFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
import kotlin.math.roundToLong

@HiltViewModel
class GinViewModel
    @Inject
    constructor(
        private val seedProvider: SeedProvider,
        private val clockProvider: ClockProvider,
        private val opponentFactory: GinOpponentFactory,
        savedGameRepository: SavedGameRepository,
        private val statsRepository: StatsRepository,
        private val settingsRepository: SettingsRepository,
        private val pacing: GinPacing,
        private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val persistence =
            GamePersistence(GameId.GIN_RUMMY, savedGameRepository, statsRepository, GinSnapshot::toJson, GinSnapshot::fromJson, dispatcher)

        /**
         * Pushed only by the player's own moves — never on the opponent's — which alone gives
         * gin's undo semantics ("rewinds to the start of the player's previous action, including
         * the AI's reply in between"). Cleared on a new hand or match. Persisted (bounded).
         */
        private val history = UndoRedoHistory<GinState>()

        /** Times the current match, silently, for the duration recorded at match end. */
        private val matchTimer = PlayTimer(clockProvider).apply { start() }

        /**
         * The current match's opponent, built from the difficulty setting at the match's first AI
         * decision and kept until a new match — a Settings change applies from the next match.
         */
        private var matchOpponent: GinOpponent? = null

        /** True once the player (not just the AI) has made a real move in the current match. */
        private var playerHasActed = false

        /** Hints taken this hand. */
        private var hintsUsed = 0

        /** The live Gin options (hints and confirm-before-knock apply at once; the rest per match). */
        private var options = GinOptions()

        /** This match's opponent, fixed when it's dealt. */
        private var matchDifficulty = GinDifficulty.MEDIUM

        /** The player's highlights this match, and the last hand already counted in them. */
        private var highlights = GinMatchHighlights()

        /** The moves the action message describes, saved with the position so it survives a restart. */
        private var recentMoves: List<RecentMove> = emptyList()
        private var countedHand: Pair<Long, Int>? = null

        /** What the landing of the pending motion commits beyond the position itself. */
        private var onLanded: (() -> Unit)? = null
        private var nextMotionId = 0L

        private val _state = MutableStateFlow(GinUiState(game = GinEngine.newMatch(seedProvider.nextSeed()), loaded = false))
        val state: StateFlow<GinUiState> = _state.asStateFlow()

        private val _effects = MutableSharedFlow<GinEffect>()
        val effects: SharedFlow<GinEffect> = _effects.asSharedFlow()

        init {
            restoreSavedGameThenDeal()
        }

        fun onIntent(intent: GinIntent) {
            if (intent is GinIntent.MoveLanded) {
                land(intent.id)
                return
            }
            val pending = _state.value.pending
            if (intent == GinIntent.NewMatch || intent is GinIntent.NewMatchAgainst) {
                // Leaving the match drops whatever is in flight, the opponent's turn included.
                onLanded = null
                if (intent is GinIntent.NewMatchAgainst) {
                    // The chosen opponent plays the next match, and becomes the default after it.
                    options = options.copy(difficulty = intent.opponent)
                    runPersisting("Couldn't save the setting") { settingsRepository.setGinOptions(options) }
                }
                onNewMatch()
                return
            }
            if (intent == GinIntent.RestartHand) {
                onLanded = null
                onRestartHand()
                return
            }
            // The opponent's turn locks the table until its card lands; the player's own move in
            // flight lands at once (an interrupting tap snaps it) and the new input counts.
            if (pending?.byOpponent == true || _state.value.isOpponentThinking) return
            pending?.let { land(it.id) }
            onPlayIntent(intent)
        }

        private fun onPlayIntent(intent: GinIntent) {
            // Selection and the panel's questions only change what's shown.
            _state.value.answering(intent, options.confirmKnock)?.let { answered ->
                _state.value = answered
                return
            }
            when (intent) {
                GinIntent.TakeUpcard -> applyMove(GinMove.TakeUpcard, GinMotionKind.DRAW)
                GinIntent.PassUpcard -> applyMove(GinMove.PassUpcard, GinMotionKind.SETTLE)
                GinIntent.DrawFromStock -> applyMove(GinMove.DrawFromStock, GinMotionKind.DRAW)
                GinIntent.DrawFromDiscard -> applyMove(GinMove.DrawFromDiscard, GinMotionKind.DRAW)
                is GinIntent.DropCard -> onDrop(intent)
                GinIntent.Knock, GinIntent.ConfirmKnock ->
                    _state.value.knockCard?.let {
                        applyMove(
                            GinMove.Knock(it),
                            GinMotionKind.DISCARD,
                        )
                    }
                GinIntent.GoGin -> _state.value.goingOut()?.let { (move, kind) -> applyMove(move, kind) }
                GinIntent.Hint -> onHint()
                GinIntent.Undo -> stepHistory(history::undo)
                GinIntent.Redo -> stepHistory(history::redo)
                GinIntent.NextHand -> onNextHand()
                else -> Unit
            }
        }

        /**
         * Restore first, then either resume the save as it stands or deal the fresh match out — as
         * one coroutine, so the fresh deal's own opponent turn can never autosave over a real save
         * that's still loading.
         */
        private fun restoreSavedGameThenDeal() {
            viewModelScope.launch {
                runCatching { settingsRepository.observeSettings().first() }.getOrNull()?.let { options = it.gin }
                matchDifficulty = options.difficulty
                // Follow the live options (hints, confirm-before-knock) from here on.
                launch {
                    settingsRepository
                        .observeSettings()
                        .catch { Log.e(TAG, "Settings stream failed", it) }
                        .collect { settings ->
                            options = settings.gin
                            _state.update { it.copy(hintsLeft = options.hints.remaining(hintsUsed), meldHints = options.meldHints) }
                        }
                }
                val snapshot =
                    runCatching { persistence.load() }
                        .onFailure { Log.e(TAG, "Couldn't load the saved game; starting fresh", it) }
                        .getOrNull()
                if (snapshot != null) {
                    matchTimer.reset()
                    matchTimer.start()
                    // In progress once the player has moved (their moves are the history) or a hand
                    // has been played — not a fresh deal reopened untouched, which isn't a loss to leave.
                    val game = snapshot.game
                    playerHasActed =
                        snapshot.undoStack.isNotEmpty() ||
                        snapshot.redoStack.isNotEmpty() ||
                        game.handNumber > 1 ||
                        game.matchScores.values.any { it > 0 }
                    hintsUsed = snapshot.hintsUsed
                    matchDifficulty = snapshot.opponent ?: options.difficulty
                    highlights = snapshot.highlights
                    countedHand = snapshot.game.seed to snapshot.game.handNumber
                    history.restore(undo = snapshot.undoStack, redo = snapshot.redoStack)
                    // Mid-discard, the card just drawn is the one the position before the draw lacks.
                    val drawn =
                        snapshot.undoStack.lastOrNull()?.let { before ->
                            (snapshot.game.playerHand - before.playerHand.toSet()).singleOrNull()
                        }
                    // The action message as it stood, told again from the moves it described.
                    recentMoves = snapshot.recentMoves
                    val feed = recentMoves.mapNotNull { feedLine(it.before, it.after) }
                    _state.update { it.copy(drawnCard = drawn, feed = feed) }
                    show(snapshot.game, save = false)
                    // Arrived from match setup: the saved match gives way to a new one (recorded as
                    // a loss if it was still going) — once, not again on process recreation.
                    if (savedStateHandle.get<Boolean>(NEW_MATCH_ARG) == true) {
                        savedStateHandle[NEW_MATCH_ARG] = false
                        onNewMatch()
                    }
                } else {
                    // Nothing played yet: the opening match is re-dealt under the chosen rules.
                    _state.update { it.copy(game = GinEngine.newMatch(seedProvider.nextSeed(), options.rules)) }
                    startMotion(_state.value.game, GinMotionKind.DEAL)
                }
            }
        }

        /**
         * The screen pre-filters with the same `moveFor` + `isLegal`, but `applyMove` re-checks.
         * Throwing back the card just taken from the pile is refused out loud: the card shakes
         * with a red ring and the panel says why. In knock mode a throw is a knock with that card,
         * and a card that can't go out is refused the same way.
         */
        private fun onDrop(intent: GinIntent.DropCard) {
            val game = _state.value.game
            val phase = game.phase
            val source = intent.source
            val takenBack = (phase as? GinPhase.AwaitingDiscard)?.mustNotDiscard
            if (intent.target == GinDropTarget.Discard && source is GinDragSource.HandCard && source.card == takenBack) {
                _state.update { it.copy(rejectedCard = source.card, selectedCard = null) }
                emitEffect(GinEffect.CantDiscard)
                return
            }
            // Knock mode: whatever's thrown on the pile goes out with it — refused if it can't.
            if (_state.value.confirmingKnock && intent.target == GinDropTarget.Discard && source is GinDragSource.HandCard) {
                val knock = GinMove.Knock(source.card)
                if (!GinEngine.isLegal(game, knock)) {
                    _state.update { it.copy(rejectedCard = source.card) }
                    return
                }
                applyMove(knock, if (intent.carried) GinMotionKind.DROP else GinMotionKind.DISCARD)
                return
            }
            val move = game.moveFor(source, intent.target) ?: return
            val kind =
                when {
                    intent.carried -> GinMotionKind.DROP
                    intent.target == GinDropTarget.Discard -> GinMotionKind.DISCARD
                    else -> GinMotionKind.DRAW
                }
            applyMove(move, kind)
        }

        private fun applyMove(
            move: GinMove,
            kind: GinMotionKind,
        ) {
            val game = _state.value.game
            if (!GinEngine.isLegal(game, move)) return
            playerHasActed = true
            history.record(game)
            val next = GinEngine.apply(game, move)
            // Marked before the motion goes out, so the card flies in already wearing its NEW ring.
            (next.playerHand - game.playerHand.toSet()).singleOrNull()?.let { drawn -> _state.update { it.copy(drawnCard = drawn) } }
            startMotion(next, kind) { emitEffect(effectFor(before = game, after = next)) }
        }

        private fun onHint() {
            val current = _state.value
            if (!current.isPlayerTurn || current.hintsLeft == 0) return
            val hint = current.game.suggestion() ?: return
            hintsUsed++
            _state.update { it.copy(hint = hint, hintsLeft = options.hints.remaining(hintsUsed)) }
            persistPosition(current.game)
        }

        /** Shared undo/redo mechanics: [step] the history and fly back to where it lands. */
        private fun stepHistory(step: (current: GinState) -> GinState?) {
            val target = step(_state.value.game) ?: return
            startMotion(target, GinMotionKind.UNDO) { emitEffect(GinEffect.CardMoved) }
        }

        private fun onNextHand() {
            val game = _state.value.game
            if (game.phase !is GinPhase.HandOver) return
            history.clear()
            hintsUsed = 0
            emitEffect(GinEffect.NewHandDealt)
            startMotion(GinEngine.dealNextHand(game, seedProvider.nextSeed()), GinMotionKind.DEAL)
        }

        /** The hand in play dealt again from the start — mid-turn too, the opponent's included. */
        private fun onRestartHand() {
            val game = _state.value.game
            if (game.phase is GinPhase.HandOver || game.phase is GinPhase.MatchOver) return
            history.clear()
            hintsUsed = 0
            emitEffect(GinEffect.NewHandDealt)
            startMotion(GinEngine.redealHand(game), GinMotionKind.DEAL)
        }

        /**
         * An abandoned in-progress match counts as a loss before the fresh one starts — gauged by
         * [playerHasActed], not [GinState.moveCount], which also climbs on the AI's own turns.
         */
        private fun onNewMatch() {
            val current = _state.value.game
            if (playerHasActed && current.phase !is GinPhase.MatchOver) {
                val duration = matchTimer.elapsedSeconds
                runPersisting("Couldn't record the abandoned match") {
                    persistence.recordResult(
                        won = false,
                        durationSeconds = duration,
                        score = current.matchScores.getValue(GinPlayer.PLAYER),
                        gin = matchRecord(current, highlights, matchDifficulty),
                    )
                }
            }
            playerHasActed = false
            matchOpponent = null
            matchDifficulty = options.difficulty
            highlights = GinMatchHighlights()
            countedHand = null
            hintsUsed = 0
            matchTimer.reset()
            matchTimer.start()
            history.clear()
            emitEffect(GinEffect.NewHandDealt)
            val dealt = GinEngine.newMatch(seedProvider.nextSeed(), options.rules)
            startMotion(dealt, GinMotionKind.DEAL)
            // Saved at once, not when the deal lands: the old match is already recorded, so leaving
            // mid-deal must not reopen it (to be finished, or abandoned, and counted again).
            persistPosition(dealt)
        }

        /**
         * Shows [next] with its cards in flight from the position on screen. [whenLanded] runs once
         * [GinIntent.MoveLanded] arrives for this motion.
         */
        private fun startMotion(
            next: GinState,
            kind: GinMotionKind,
            byOpponent: Boolean = false,
            whenLanded: () -> Unit = {},
        ) {
            val from = _state.value.game
            val motion = GinMotion(id = ++nextMotionId, kind = kind, from = from, byOpponent = byOpponent)
            onLanded = whenLanded
            val line = feedLine(from, next)
            recentMoves =
                when {
                    kind == GinMotionKind.DEAL || kind == GinMotionKind.UNDO -> emptyList()
                    line != null -> (recentMoves + RecentMove(from, next)).takeLast(FEED_LENGTH)
                    else -> recentMoves
                }
            _state.update { it.copy(feed = it.feed.after(kind, line)) }
            show(next, motion)
        }

        /** Commits the pending motion [id]: the position counts from here (save, score, the next turn). */
        private fun land(id: Long) {
            val current = _state.value
            if (current.pending?.id != id) return
            val after = onLanded
            onLanded = null
            show(current.game)
            after?.invoke()
        }

        /**
         * Puts [next] on the table. With [motion] the cards are on their way and nothing is
         * committed; without one the position is settled — saved (or the match recorded), and the
         * opponent takes its turn if it's due.
         */
        private fun show(
            next: GinState,
            motion: GinMotion? = null,
            // False for a position just restored from the save — writing it straight back is wasted work.
            save: Boolean = true,
        ) {
            _state.update {
                it.copy(
                    game = next,
                    loaded = true,
                    pending = motion,
                    selectedCard = null,
                    hint = null,
                    isOpponentThinking = false,
                    canUndo = history.canUndo,
                    canRedo = history.canRedo,
                    hintsLeft = options.hints.remaining(hintsUsed),
                    abandonCountsAsLoss = playerHasActed && next.phase !is GinPhase.MatchOver,
                    opponent = matchDifficulty,
                    rejectedCard = null,
                    ginOfferDismissed = false,
                    confirmingKnock = false,
                    meldHints = options.meldHints,
                    drawnCard =
                        it.drawnCard.takeIf { card ->
                            card in next.playerHand &&
                                next.phase.let { p -> p is GinPhase.AwaitingDiscard && p.toMove == GinPlayer.PLAYER }
                        },
                    overallRecord = it.overallRecord.takeIf { next.phase is GinPhase.MatchOver },
                )
            }
            if (motion != null) return
            countHighlights(next)
            val phase = next.phase
            if (phase is GinPhase.MatchOver) {
                val duration = matchTimer.elapsedSeconds
                runPersisting("Couldn't record the match result") {
                    persistence.clear()
                    persistence.recordResult(
                        won = phase.winner == GinPlayer.PLAYER,
                        durationSeconds = duration,
                        score = next.matchScores.getValue(GinPlayer.PLAYER),
                        gin = matchRecord(next, highlights, matchDifficulty),
                    )
                    val gin = statsRepository.observeResults(GameId.GIN_RUMMY).first().map { it.result }
                    _state.update { it.copy(overallRecord = gin.recordAgainst(matchDifficulty)) }
                }
            } else if (save) {
                persistPosition(next)
            }
            driveIfNeeded(next)
        }

        /** A finished hand joins the match's highlights — once, however often its position is shown. */
        private fun countHighlights(game: GinState) {
            val result =
                when (val phase = game.phase) {
                    is GinPhase.HandOver -> phase.result
                    is GinPhase.MatchOver -> phase.lastHand
                    else -> null
                } ?: return
            val key = game.seed to game.handNumber
            if (countedHand == key) return
            countedHand = key
            highlights = highlights.plus(result)
            _state.update { it.copy(highlights = highlights) }
        }

        /**
         * Autosave on every settled position — the undo/redo history rides along too, capped (see
         * [UndoRedoHistory.PERSISTED_LIMIT]); the live history stays unlimited for the session.
         */
        private fun persistPosition(game: GinState) {
            runPersisting("Couldn't save your game") {
                persistence.save(
                    GinSnapshot(
                        game = game,
                        undoStack = history.persistedUndo(),
                        redoStack = history.persistedRedo(),
                        hintsUsed = hintsUsed,
                        opponent = matchDifficulty,
                        highlights = highlights,
                        recentMoves = recentMoves,
                    ),
                )
            }
        }

        /**
         * The opponent's turn, one decision at a time: a think (700–1100ms) before it draws or
         * decides on the upcard, a shorter beat before it discards, and a plain beat before a
         * layoff resolves (laying off every card that fits is always right, so that's auto-driven
         * for a human defender too). Each decision flies as the opponent's own motion; the next
         * one waits for it to land.
         */
        private fun driveIfNeeded(game: GinState) {
            val seat = autoDriveSeat(game.phase) ?: return
            viewModelScope.launch {
                val settings = settingsRepository.observeSettings().first()
                val pace = settings.animationSpeed.durationScale
                val opponent = matchOpponent ?: opponentFactory.create(matchDifficulty).also { matchOpponent = it }
                val phase = game.phase
                val pause =
                    when (phase) {
                        is GinPhase.AwaitingLayoff -> pacing.layoffPauseMillis
                        is GinPhase.AwaitingDiscard -> pacing.stepMillis
                        else -> pacing.thinkMillis()
                    }
                if (phase !is GinPhase.AwaitingLayoff) _state.update { it.copy(isOpponentThinking = true) }
                // The search is real CPU work — off Main, alongside the pause (a floor on the
                // turn's length, not added on top of the search).
                val next =
                    coroutineScope {
                        val decision = async(dispatcher) { GinOpponentDriver.driveOneDecision(game, seat, opponent) }
                        delay((pause * pace).roundToLong())
                        decision.await()
                    }
                // The paused-on position may no longer be current (the player undid, a save
                // loaded) — whatever replaced it has run its own turn logic already.
                if (_state.value.game != game || _state.value.pending != null) {
                    _state.update { it.copy(isOpponentThinking = false) }
                    return@launch
                }
                startMotion(next, kindFor(before = game, after = next), byOpponent = true) {
                    emitEffect(effectFor(before = game, after = next))
                }
            }
        }

        private fun emitEffect(effect: GinEffect) {
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
                        _effects.emit(GinEffect.ShowSnackbar(errorMessage))
                    }
            }
        }

        /** Test seam: pin an exact position without going through a real deal. */
        internal fun overrideGameForTest(game: GinState) {
            onLanded = null
            show(game)
        }

        companion object {
            private const val TAG = "GinViewModel"

            /** The navigation argument asking for a fresh match (from match setup) rather than the saved one. */
            const val NEW_MATCH_ARG = "newMatch"
        }
    }

/**
 * Whichever seat must act next when nobody at the screen is choosing anything: the AI's own turn,
 * or an [GinPhase.AwaitingLayoff] resolution (always auto-driven, see [GinOpponentDriver]). Null
 * means the player has a real decision waiting.
 */
private fun autoDriveSeat(phase: GinPhase): GinPlayer? =
    when (phase) {
        is GinPhase.UpcardDecision -> phase.deciding.takeIf { it == GinPlayer.OPPONENT }
        is GinPhase.AwaitingDraw -> phase.toMove.takeIf { it == GinPlayer.OPPONENT }
        is GinPhase.AwaitingDiscard -> phase.toMove.takeIf { it == GinPlayer.OPPONENT }
        is GinPhase.AwaitingLayoff -> phase.knocker.other
        is GinPhase.HandOver, is GinPhase.MatchOver -> null
    }

/** What a match ending at [game] adds to the stats, from the player's [highlights]. */
private fun matchRecord(
    game: GinState,
    highlights: GinMatchHighlights,
    opponent: GinDifficulty,
) = GinMatchRecord(
    opponent = opponent,
    opponentScore = game.matchScores.getValue(GinPlayer.OPPONENT),
    gins = highlights.gins,
    undercuts = highlights.undercuts,
    bestHand = highlights.bestHand,
    knocks = highlights.knocks,
    knockDeadwood = highlights.knockDeadwood,
    hands = highlights.handsPlayed,
)

/** The feed once a [kind] motion starts: a deal or an undo clears it, a move adds its [line]. */
private fun List<GinFeedLine>.after(
    kind: GinMotionKind,
    line: GinFeedLine?,
): List<GinFeedLine> =
    when (kind) {
        GinMotionKind.DEAL, GinMotionKind.UNDO -> emptyList()
        else -> (this + listOfNotNull(line)).takeLast(FEED_LENGTH)
    }

/** How an automatic decision should fly: a card drawn, a card thrown, or the table settling (a pass, layoffs). */
private fun kindFor(
    before: GinState,
    after: GinState,
): GinMotionKind =
    when {
        after.discardPile.size > before.discardPile.size -> GinMotionKind.DISCARD
        after.stock.size < before.stock.size || after.discardPile.size < before.discardPile.size -> GinMotionKind.DRAW
        else -> GinMotionKind.SETTLE
    }

/** The sound-and-haptic cue for a move landing: a match won, a hand won or lost (or blocked), or a plain card move. */
private fun effectFor(
    before: GinState,
    after: GinState,
): GinEffect {
    val phase = after.phase
    return when {
        // Only the player's own match win gets the fanfare; losing one lands like a hand ending.
        phase is GinPhase.MatchOver -> if (phase.winner == GinPlayer.PLAYER) GinEffect.MatchWon else GinEffect.Knocked
        phase is GinPhase.HandOver && before.phase !is GinPhase.HandOver ->
            when {
                phase.result.outcome == HandOutcome.BLOCKED -> GinEffect.HandBlocked
                phase.result.winner == GinPlayer.PLAYER -> GinEffect.HandWon
                else -> GinEffect.Knocked
            }
        else -> GinEffect.CardMoved
    }
}

/**
 * [intent]'s effect when it only changes what the table shows, or null when it's a real move:
 * choosing a card, the "Keep playing" and "Not yet" answers, and — with [confirmKnock] on — a
 * Knock that asks first.
 */
private fun GinUiState.answering(
    intent: GinIntent,
    confirmKnock: Boolean,
): GinUiState? =
    when (intent) {
        is GinIntent.SelectHandCard -> copy(selectedCard = intent.card.takeIf { it != selectedCard }, rejectedCard = null)
        GinIntent.CancelKnock -> copy(confirmingKnock = false, selectedCard = null)
        GinIntent.DismissGinOffer -> copy(ginOfferDismissed = true)
        GinIntent.Knock ->
            when {
                // Nothing lifted: lift the suggested throw and ask, rather than throw it unseen.
                selectedCard == null && canKnock -> copy(selectedCard = knockCard, confirmingKnock = true)
                confirmKnock && canKnock -> copy(confirmingKnock = true)
                else -> null
            }
        else -> null
    }

/** The move going out takes, as offered: Big Gin, or gin throwing the offered card. */
private fun GinUiState.goingOut(): Pair<GinMove, GinMotionKind>? =
    when (val offer = ginOffer) {
        GinOffer.BigGin -> GinMove.BigGin to GinMotionKind.SETTLE
        is GinOffer.Gin -> GinMove.Knock(offer.throwing) to GinMotionKind.DISCARD
        null -> null
    }
