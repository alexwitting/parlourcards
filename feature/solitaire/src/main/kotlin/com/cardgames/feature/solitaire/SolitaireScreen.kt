package com.cardgames.feature.solitaire

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Deck
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.ui.cards.DragDropContainer
import com.cardgames.core.ui.cards.rememberDragDropState
import com.cardgames.core.ui.chrome.GamePause
import com.cardgames.core.ui.chrome.GameScaffold
import com.cardgames.core.ui.chrome.PauseAction
import com.cardgames.core.ui.chrome.rememberConfirmAbandon
import com.cardgames.core.ui.feedback.CardSound
import com.cardgames.core.ui.feedback.GameHaptic
import com.cardgames.core.ui.feedback.LocalGameFeedback
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.layout.CapFontScale
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.LocalReduceMotion
import com.cardgames.core.ui.motion.MotionSpec
import com.cardgames.core.ui.motion.StandardEasing
import com.cardgames.core.ui.motion.scaledMillis
import com.cardgames.core.ui.motion.table
import com.cardgames.core.ui.motion.tween
import com.cardgames.core.ui.table.CardGlide
import com.cardgames.core.ui.table.rememberMovableCards
import com.cardgames.core.ui.table.rememberTableReveal
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.feature.solitaire.board.BoardInput
import com.cardgames.feature.solitaire.board.BoardLook
import com.cardgames.feature.solitaire.board.DragGhost
import com.cardgames.feature.solitaire.board.HintSpot
import com.cardgames.feature.solitaire.board.ScoreCard
import com.cardgames.feature.solitaire.board.SolitaireDragSource
import com.cardgames.feature.solitaire.board.SolitaireDropTarget
import com.cardgames.feature.solitaire.board.SolitaireTable
import com.cardgames.feature.solitaire.board.StockLook
import com.cardgames.feature.solitaire.board.draggedCards
import com.cardgames.feature.solitaire.board.hintSpots
import com.cardgames.feature.solitaire.board.legalTargets
import com.cardgames.feature.solitaire.board.solitaireControls
import com.cardgames.feature.solitaire.board.solitaireHintOnTable
import com.cardgames.feature.solitaire.motion.CardZone
import com.cardgames.feature.solitaire.motion.choreograph
import com.cardgames.feature.solitaire.motion.spots
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SolitaireScreen(
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SolitaireViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val feedback = LocalGameFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Play timer pauses whenever the screen leaves the foreground.
    LifecycleStartEffect(viewModel) {
        viewModel.onIntent(SolitaireIntent.ScreenVisible)
        onStopOrDispose { viewModel.onIntent(SolitaireIntent.ScreenHidden) }
    }

    LaunchedEffect(viewModel, feedback) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SolitaireEffect.ShowSnackbar -> launch { snackbarHostState.showSnackbar(effect.message) }
                else -> feedback.reactTo(effect)
            }
        }
    }

    SolitaireContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        onOpenSettings = onOpenSettings,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        // A real screen arrives by navigation: its table waits for the transition, then fades in.
        revealAfterTransition = true,
    )
}

/**
 * The screen's entire UI, decoupled from the ViewModel — everything here is a plain parameter or
 * Compose-local state, so it composes in a `@Preview` with no Hilt graph.
 *
 * Moves arrive as a [PendingMotion]: the board shows the new position with each card's timing
 * from [choreograph], and once the last card is down the screen reports
 * [SolitaireIntent.MoveLanded] so the ViewModel commits it. A new deal first shows its cards
 * gathered in the stock for one frame, so they deal out from there.
 */
@Composable
internal fun SolitaireContent(
    state: SolitaireState,
    onIntent: (SolitaireIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    revealAfterTransition: Boolean = false,
) {
    // Text grows with the phone's font size only so far: the table is laid out on fixed card geometry.
    CapFontScale {
        val feedback = LocalGameFeedback.current
        val dragState = rememberDragDropState<SolitaireDragSource>()
        val currentOnIntent by rememberUpdatedState(onIntent)
        val reveal = rememberTableReveal(state.loaded, afterTransition = revealAfterTransition)
        // Where the foundations are drawn, so the Solved screen's kings land exactly on them.
        var foundationTop by remember { mutableStateOf<Float?>(null) }
        val requestNewGame =
            rememberConfirmAbandon(
                needsConfirmation = state.abandonCountsAsLoss,
                title = "Start a new game?",
                confirmLabel = "New game",
            ) { onIntent(SolitaireIntent.NewGame) }
        // Restart is undoable, so it only asks once a move has been made, and says it can be undone.
        val requestRestart =
            rememberConfirmAbandon(
                needsConfirmation = state.game.moveCount > 0,
                title = "Restart this deal?",
                confirmLabel = "Restart",
                detail = "The same deal starts again. Undo brings your game back.",
            ) { onIntent(SolitaireIntent.RestartDeal) }

        val pending = state.pending
        val pilePositions = remember { mutableMapOf<CardZone, Offset>() }
        val density = LocalDensity.current
        val tableMotion = CardGamesMotion.table
        val flight: (CardZone, CardZone) -> Int = { from, to ->
            val a = pilePositions[from]
            val b = pilePositions[to]
            if (a == null || b == null) {
                CardGamesMotion.Flight.millis
            } else {
                tableMotion.flightMillisFor(with(density) { (a - b).getDistance().toDp() })
            }
        }
        val motion = rememberMotion(state, pending, flight) { id -> currentOnIntent(SolitaireIntent.MoveLanded(id)) }
        val shown = motion.shown

        val input =
            BoardInput(
                pilePositions = pilePositions,
                onIntent = { intent ->
                    if (intent is SolitaireIntent.Hold) feedback.haptic(GameHaptic.Lift)
                    onIntent(intent)
                },
                dragState = dragState,
                enabled = !state.isDealing && !state.isWon,
            )
        val dragging = dragState.payload
        val look = boardLook(state, shown, dragging, motion.glide, motion.moving)

        GameScaffold(
            game = GameId.KLONDIKE,
            pill = "Klondike · Draw ${state.game.drawCount}",
            pause =
                GamePause(
                    overline = "Klondike · Draw ${state.game.drawCount}",
                    leaveLabel = "Leave game",
                    onLeave = onNavigateBack,
                    actions =
                        listOf(
                            PauseAction("Restart this deal", ParlourIcons.Restart, enabled = !state.isDealing) { requestRestart() },
                            PauseAction("Redo", ParlourIcons.Redo, enabled = state.canRedo) { onIntent(SolitaireIntent.Redo) },
                        ),
                ),
            onPausedChange = { paused -> onIntent(SolitaireIntent.Paused(paused)) },
            onOpenSettings = onOpenSettings,
            snackbarHostState = snackbarHostState,
            modifier = modifier,
            scoreCard = { ScoreCard(state.elapsedSeconds, state.game.moveCount) },
            controls = solitaireControls(state, onIntent, requestRestart, requestNewGame),
        ) { innerPadding ->
            // Nothing on the felt until the game is known — the stand-in deal would show, then swap.
            if (!reveal.shown) return@GameScaffold
            // The board keeps to the panel's smallest height, so the draw row stays put when a hint
            // line grows the panel — the panel rises into the felt left under the draw row.
            val panelInset = innerPadding.calculateBottomPadding()
            var restingInset by remember { mutableStateOf(panelInset) }
            LaunchedEffect(panelInset) { if (panelInset < restingInset) restingInset = panelInset }
            DragDropContainer(
                state = dragState,
                onLift = { feedback.haptic(GameHaptic.Lift) },
                onReject = { feedback.haptic(GameHaptic.Reject) },
                landingCards = { source -> shown.draggedCards(source) },
                ghost = { source -> DragGhost(shown, source) },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding(), bottom = restingInset)
                        .graphicsLayer { alpha = reveal.alpha() }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            onIntent(SolitaireIntent.TapTable)
                        },
            ) {
                LookaheadScope {
                    // A window resize (split screen, a foldable) re-keys the cards, so they snap to their new
                    // places instead of flying from the old layout — and don't fade in, as after a skip.
                    val window = CardGamesTheme.window
                    val firstWindow = remember { window }
                    val cards =
                        rememberMovableCards(
                            dealKey = Triple(rememberTableKey(shown), motion.snapCount, window),
                            lookaheadScope = this,
                            fadeIn = motion.snapCount == 0 && window == firstWindow,
                        )
                    SolitaireTable(
                        shown,
                        look,
                        input,
                        cards,
                        modifier = Modifier.fillMaxSize().align(Alignment.TopCenter),
                        foundationModifier = Modifier.onGloballyPositioned { foundationTop = it.positionInRoot().y },
                        aboveDrawRow = solitaireHintOnTable(state),
                    )
                    // Auto-complete: any tap puts every card home at once.
                    if (pending?.kind == MotionKind.AUTO_COMPLETE && motion.isRunning) {
                        Box(
                            modifier =
                                Modifier
                                    .matchParentSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { motion.skip() },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.isWon && state.solved != null,
            enter = fadeIn(CardGamesMotion.Base.tween()),
            exit = fadeOut(CardGamesMotion.Quick.tween()),
        ) {
            state.solved?.let { summary ->
                SolvedScreen(
                    summary = summary,
                    kingsTop = foundationTop,
                    onDealAgain = { onIntent(SolitaireIntent.NewGame) },
                    onBack = onNavigateBack,
                )
            }
        }
    }
}

/** The motion in flight: the position to draw, each card's glide, and how to cut it short. */
private class BoardMotion(
    val shown: KlondikeState,
    val glide: (Card) -> CardGlide,
    val isRunning: Boolean,
    /** Bumped when a skip snaps every card into place (re-keys the cards, without a fade-in). */
    val snapCount: Int,
    /** The cards this motion carries; empty at rest. */
    val moving: Set<Card>,
    val skip: () -> Unit,
)

@Composable
private fun rememberMotion(
    state: SolitaireState,
    pending: PendingMotion?,
    flight: (CardZone, CardZone) -> Int,
    onLanded: (Long) -> Unit,
): BoardMotion {
    // A deal shows its cards gathered in the stock for one frame first, so they deal out from there.
    var staged by remember(pending?.id) { mutableStateOf(pending?.kind != MotionKind.DEAL) }
    LaunchedEffect(pending?.id) {
        if (!staged) {
            withFrameNanos {}
            staged = true
        }
    }
    var snapCount by remember { mutableIntStateOf(0) }
    val running = pending != null && staged
    val choreography =
        remember(pending?.id, staged) { if (running) choreograph(pending.kind, pending.from, state.game, flight) else null }
    val reduceMotion = LocalReduceMotion.current
    val totalMillis = scaledMillis((if (reduceMotion) CardGamesMotion.ReducedFade.millis else choreography?.totalMillis ?: 0).toLong())
    LaunchedEffect(pending?.id, staged) {
        if (running) {
            delay(totalMillis)
            onLanded(pending.id)
        }
    }
    val timings = choreography?.timings.orEmpty()
    // Complete: each card's landing sounds as it reaches its foundation — a ripple of cards going home.
    val feedback = LocalGameFeedback.current
    val landings =
        if (running && pending.kind == MotionKind.AUTO_COMPLETE && !reduceMotion) {
            timings.values.map { scaledMillis((it.delayMillis + it.glideMillis).toLong()) }.sorted()
        } else {
            emptyList()
        }
    LaunchedEffect(pending?.id, staged) {
        var elapsed = 0L
        landings.forEach { at ->
            delay(at - elapsed)
            elapsed = at
            feedback.play(CardSound.Place)
        }
    }
    return BoardMotion(
        shown = if (pending != null && !staged) pending.from else state.game,
        glide = { card ->
            timings[card]?.let { t ->
                CardGlide.Tween(MotionSpec(t.glideMillis, StandardEasing), delayMillis = t.delayMillis, flipDelayMillis = t.flipDelayMillis)
            } ?: DefaultGlide
        },
        isRunning = running,
        snapCount = snapCount,
        moving = if (running) timings.keys else emptySet(),
        skip = {
            if (pending != null) {
                snapCount++
                onLanded(pending.id)
            }
        },
    )
}

/** Rings, lifts and hidden cards for this frame: a drag, else a held card, else a hint. */
private fun boardLook(
    state: SolitaireState,
    shown: KlondikeState,
    dragging: SolitaireDragSource?,
    glide: (Card) -> CardGlide,
    moving: Set<Card>,
): BoardLook {
    val selection = state.selection
    val hint = state.hint
    val hidden = dragging?.let { shown.draggedCards(it).toSet() }.orEmpty()
    return when {
        dragging != null -> BoardLook(glide, moving = moving, targets = shown.legalTargets(dragging), hidden = hidden)
        selection != null ->
            BoardLook(
                glide,
                moving = moving,
                raised = shown.draggedCards(selection).toSet(),
                targets = shown.legalTargets(selection),
            )
        hint != null -> {
            val spots = shown.hintSpots(hint)
            BoardLook(
                glide,
                moving = moving,
                raised =
                    spots
                        .mapNotNull { spot ->
                            when (spot) {
                                is HintSpot.TableauCard -> shown.tableau[spot.pile].cards[spot.cardIndex]
                                HintSpot.Waste -> shown.waste.lastOrNull()
                                else -> null
                            }
                        }.toSet(),
                targets =
                    spots
                        .mapNotNull { spot ->
                            when (spot) {
                                is HintSpot.TableauDestination -> SolitaireDropTarget.Tableau(spot.pile)
                                is HintSpot.Foundation -> SolitaireDropTarget.Foundation(spot.suit)
                                else -> null
                            }
                        }.toSet(),
                stock = StockLook(ringed = HintSpot.Stock in spots),
            )
        }
        else ->
            BoardLook(
                glide,
                moving = moving,
                stock =
                    StockLook(
                        gathering = state.pending?.kind == MotionKind.GATHER,
                        shuffling = state.shuffling,
                        dealing = state.pending?.kind == MotionKind.DEAL,
                    ),
            )
    }
}

/** Cards outside a motion's choreography (a fan closing up, a resize) glide along the flight token. */
private val DefaultGlide: CardGlide = CardGlide.Tween(CardGamesMotion.Flight)

/**
 * Which table the cards belong to: a new deal is normally a fresh table (new cards, nothing glides
 * across from the old deal), but a deal that goes out from a stock the old cards were just gathered
 * into keeps the same cards — they're already stacked there, so they simply deal out again.
 */
@Composable
private fun rememberTableKey(shown: KlondikeState): Long {
    // Plain holders, read and updated as each frame composes: the key, and the last table shown.
    val last = remember { arrayOf<KlondikeState?>(null) }
    val key = remember { longArrayOf(shown.seed) }
    val previous = last[0]
    if (previous != null && previous.seed != shown.seed) {
        val carriedOver = previous.isAllInStock() && shown.isAllInStock()
        if (!carriedOver) key[0] = shown.seed
    }
    last[0] = shown
    return key[0]
}

private fun KlondikeState.isAllInStock(): Boolean = stock.size == Deck.standard52.size
