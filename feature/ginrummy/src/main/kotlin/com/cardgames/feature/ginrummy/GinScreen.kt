package com.cardgames.feature.ginrummy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.ui.cards.DragDropContainer
import com.cardgames.core.ui.cards.PlayingCard
import com.cardgames.core.ui.cards.rememberDragDropState
import com.cardgames.core.ui.chrome.GameControls
import com.cardgames.core.ui.chrome.GamePause
import com.cardgames.core.ui.chrome.GameScaffold
import com.cardgames.core.ui.chrome.PanelWidth
import com.cardgames.core.ui.chrome.PauseAction
import com.cardgames.core.ui.feedback.GameHaptic
import com.cardgames.core.ui.feedback.LocalGameFeedback
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.layout.CapFontScale
import com.cardgames.core.ui.layout.FormFactor
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.LocalReduceMotion
import com.cardgames.core.ui.motion.MotionSpec
import com.cardgames.core.ui.motion.StandardEasing
import com.cardgames.core.ui.motion.scaledMillis
import com.cardgames.core.ui.motion.tween
import com.cardgames.core.ui.table.CardGlide
import com.cardgames.core.ui.table.rememberMovableCards
import com.cardgames.core.ui.table.rememberStaticCards
import com.cardgames.core.ui.table.rememberTableHandoff
import com.cardgames.core.ui.table.rememberTableReveal
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.feature.ginrummy.board.GinDragSource
import com.cardgames.feature.ginrummy.board.GinTable
import com.cardgames.feature.ginrummy.board.ScoreCard
import com.cardgames.feature.ginrummy.board.cardFor
import com.cardgames.feature.ginrummy.board.cardOf
import com.cardgames.feature.ginrummy.board.fade
import com.cardgames.feature.ginrummy.motion.GinMotionTokens
import com.cardgames.feature.ginrummy.motion.ginChoreography
import com.cardgames.feature.ginrummy.motion.ginMotionDistances
import com.cardgames.feature.ginrummy.motion.midDeal
import com.cardgames.feature.ginrummy.motion.rememberDealProgress
import com.cardgames.feature.ginrummy.result.GinResultOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GinScreen(
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onNewMatch: () -> Unit = {},
) {
    val viewModel: GinViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val feedback = LocalGameFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, feedback) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is GinEffect.ShowSnackbar -> launch { snackbarHostState.showSnackbar(effect.message) }
                else -> feedback.reactTo(effect)
            }
        }
    }

    GinContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        onOpenSettings = onOpenSettings,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        onNewMatch = onNewMatch,
        // A real screen arrives by navigation: its table waits for the transition, then fades in.
        revealAfterTransition = true,
    )
}

/**
 * The screen's entire UI, decoupled from the ViewModel — a plain parameter/state composable, so
 * it previews with no Hilt graph (same split as Solitaire's `SolitaireContent`).
 *
 * Every move — the player's and the opponent's — arrives as a [GinMotion]: the table shows the new
 * position with each card's timing from [ginChoreography] (a fresh deal plays out card by card,
 * see `GinDeal.kt`), and once the last card is down the screen reports [GinIntent.MoveLanded].
 */
@Composable
internal fun GinContent(
    state: GinUiState,
    onIntent: (GinIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    revealAfterTransition: Boolean = false,
    onNewMatch: () -> Unit = {},
) {
    // Text grows with the phone's font size only so far: the table is laid out on fixed card geometry.
    CapFontScale {
        val feedback = LocalGameFeedback.current
        val dragState = rememberDragDropState<GinDragSource>()
        // At a new deal, the previous hand stays drawn underneath and fades out.
        val handoff = rememberTableHandoff(state, dealKey = state.game.seed) { it.game.moveCount > 0 }
        val reveal = rememberTableReveal(state.loaded, afterTransition = revealAfterTransition)
        // Each deal's motion id, kept once it lands — so a restarted hand (same seed) deals out afresh.
        val lastDeal = remember { longArrayOf(-1L) }
        state.pending?.takeIf { it.kind == GinMotionKind.DEAL }?.let { lastDeal[0] = it.id }
        val dealProgress = rememberDealProgress(state.game, onTable = reveal.shown, dealKey = state.game.seed to lastDeal[0])
        val glide = rememberGinMotion(state, dealProgress, onIntent)
        val board = if (dealProgress != null) state.copy(game = state.game.midDeal(dealProgress), selectedCard = null) else state
        val game = board.game
        val phase = state.game.phase
        val handInPlay = phase !is GinPhase.HandOver && phase !is GinPhase.MatchOver

        Box(modifier = modifier) {
            GameScaffold(
                game = GameId.GIN_RUMMY,
                pill = "Hand ${game.handNumber} · First to ${game.rules.target}",
                pause =
                    GamePause(
                        overline = "Hand ${game.handNumber} · vs ${state.opponentName}",
                        leaveLabel = "Leave match",
                        onLeave = onNavigateBack,
                        actions =
                            listOf(
                                PauseAction(
                                    "Restart this hand",
                                    ParlourIcons.Restart,
                                    enabled = handInPlay,
                                ) { onIntent(GinIntent.RestartHand) },
                                PauseAction(
                                    "Redo",
                                    ParlourIcons.Redo,
                                    enabled = state.canRedo && state.isPlayerTurn,
                                ) { onIntent(GinIntent.Redo) },
                                // Setup first: this match is only given up if a new one is dealt.
                                PauseAction("New match", ParlourIcons.Shuffle) { onNewMatch() },
                            ),
                    ),
                onOpenSettings = onOpenSettings,
                snackbarHostState = snackbarHostState,
                scoreCard = { ScoreCard(game, state.opponent) },
                // The panel cross-fades when its mode changes (the deal ending on "Take the upcard?",
                // knock mode, a gin offer) instead of swapping in one frame; each side keeps its own state.
                // On a tablet, Undo and Hint stay in the dock through every mode.
                controls =
                    run {
                        val docked = CardGamesTheme.formFactor == FormFactor.TABLET
                        GameControls(
                            status = {
                                val dealing = dealProgress != null
                                val fade = panelFade()
                                // Every mode is at least as tall as the Deadwood row (measured, never drawn), so
                                // switching modes doesn't move the panel — or the hand above it.
                                AtLeastAsTallAs(sizer = { GinPanelStatusSizer(board) }) {
                                    AnimatedContent(
                                        targetState = board,
                                        contentKey = { ginPanel(it)::class },
                                        transitionSpec = { fade },
                                        contentAlignment = Alignment.CenterStart,
                                        label = "panel status",
                                    ) { shown -> GinPanelStatus(shown, dealing = dealing) }
                                }
                            },
                            actions = {
                                val fade = panelFade()
                                // Likewise as tall as the tool tiles with their captions, whatever buttons show.
                                AtLeastAsTallAs(
                                    sizer = { Row { GinPanelActionsSizer(board, withTools = !docked) } },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    AnimatedContent(
                                        targetState = board,
                                        contentKey = { ginPanel(it)::class },
                                        transitionSpec = { fade },
                                        label = "panel actions",
                                    ) { shown ->
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                            GinPanelActions(shown, onIntent, withTools = !docked)
                                        }
                                    }
                                }
                            },
                            dock = if (docked) ({ GinTools(board, onIntent, spaced = false) }) else null,
                            panelWidth =
                                if (ginPanel(
                                        board,
                                    ) is GinPanel.GoingOut
                                ) {
                                    PanelWidth.BesideDock
                                } else {
                                    PanelWidth.Fixed(DEADWOOD_PANEL_WIDTH)
                                },
                        )
                    },
                // The held hand's lifted and tilted cards pass in front of the panel's top edge.
                tableOverPanel = true,
            ) { innerPadding ->
                // Nothing on the felt until the match is known — the stand-in would deal and then vanish.
                if (!reveal.shown) return@GameScaffold
                DragDropContainer(
                    state = dragState,
                    onLift = { feedback.haptic(GameHaptic.Lift) },
                    onReject = { feedback.haptic(GameHaptic.Reject) },
                    landingCards = { source -> listOfNotNull(game.cardOf(source)) },
                    ghost = { source -> GinDragGhost(game, source) },
                    modifier = Modifier.fillMaxSize().padding(innerPadding).graphicsLayer { alpha = reveal.alpha() },
                ) {
                    // The hand leaving at a new deal, beneath the new one: plain cards, no live controls.
                    handoff.outgoing?.let { outgoing ->
                        GinTable(
                            board = outgoing,
                            cards = rememberStaticCards(),
                            glide = { DefaultGlide },
                            dragState = rememberDragDropState(),
                            onIntent = {},
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = handoff.alpha() },
                        )
                    }
                    LookaheadScope {
                        GinTable(
                            board = board,
                            cards = rememberMovableCards(dealKey = game.seed, lookaheadScope = this),
                            glide = glide,
                            dragState = dragState,
                            onIntent = onIntent,
                            dealt = state.game.takeIf { dealProgress != null },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            // The end of a hand or match covers the whole screen, chrome included.
            GinResultOverlay(state = state, onIntent = onIntent, onHome = onNavigateBack, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Plays the pending motion and reports it landed: a deal once [dealProgress] finishes, anything
 * else after its choreography's length. Returns each card's glide for this frame.
 */
@Composable
private fun rememberGinMotion(
    state: GinUiState,
    dealProgress: Int?,
    onIntent: (GinIntent) -> Unit,
): (Card) -> CardGlide {
    val currentOnIntent by rememberUpdatedState(onIntent)
    val pending = state.pending
    val choreography = remember(pending?.id) { pending?.let { ginChoreography(it.kind, it.from, state.game) } }
    val reduceMotion = LocalReduceMotion.current
    val total = scaledMillis((if (reduceMotion) CardGamesMotion.ReducedFade.millis else choreography?.totalMillis ?: 0).toLong())
    val dealing = dealProgress != null
    val discardArc = ginMotionDistances.discardArc
    LaunchedEffect(pending?.id, dealing) {
        if (pending == null) return@LaunchedEffect
        if (pending.kind == GinMotionKind.DEAL) {
            if (!dealing) currentOnIntent(GinIntent.MoveLanded(pending.id))
        } else {
            delay(total)
            currentOnIntent(GinIntent.MoveLanded(pending.id))
        }
    }
    val timings = choreography?.timings.orEmpty()
    return { card ->
        when {
            dealing -> DealGlide
            else ->
                timings[card]?.let { t ->
                    CardGlide.Tween(
                        MotionSpec(t.glideMillis, StandardEasing),
                        delayMillis = t.delayMillis,
                        flipDelayMillis = t.flipDelayMillis,
                        arc = if (t.arcs) discardArc else 0.dp,
                        land = CardGamesMotion.Land.takeIf { t.arcs },
                    )
                } ?: DefaultGlide
        }
    }
}

/** The card following the finger during a drag — a plain, lifted copy in a brass ring. */
@Composable
private fun GinDragGhost(
    game: GinState,
    source: GinDragSource,
) {
    val card = game.cardFor(source) ?: return
    PlayingCard(card = card, faceUp = true, lifted = true, ring = CardGamesTheme.colors.accent)
}

/** Cards outside a motion's choreography (the hand re-flowing, a fan closing up) glide along the flight token. */
private val DefaultGlide: CardGlide = CardGlide.Tween(CardGamesMotion.Flight)

/** A dealt card flies to its seat, the player's turning over partway. */
private val DealGlide: CardGlide = CardGlide.Tween(CardGamesMotion.Flight, flipDelayMillis = GinMotionTokens.FLIP_IN_FLIGHT)

/** A panel mode giving way to the next: a plain cross-fade on the status token. */
@Composable
private fun panelFade(): ContentTransform =
    fadeIn(CardGamesMotion.StatusFade.tween()) togetherWith fadeOut(CardGamesMotion.StatusFade.tween())

/** The tablet's deadwood panel; offering gin, it widens left up to the dock. */
private val DEADWOOD_PANEL_WIDTH = 290.dp
