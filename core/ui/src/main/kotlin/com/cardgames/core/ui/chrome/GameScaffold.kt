package com.cardgames.core.ui.chrome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.layout.FormFactor
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.tween
import com.cardgames.core.ui.rules.HowToPlaySheet
import com.cardgames.core.ui.text.displayName
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * The in-game chrome every game screen shares (Parlour):
 *
 * - the table (pack art or the felt) filling the whole screen (immersive — the app hides the
 *   system nav bar app-wide),
 * - a top bar on the felt: Pause, the status [pill] (`● Hand 5 · First to 100`; the game's name
 *   when there's none), Settings, and the game's [scoreCard] — under the bar on a phone, beside
 *   Pause on a tablet,
 * - the game's [controls]: the cream [BottomPanel] across the bottom on a phone, the floating dock
 *   and panel ([FloatingControls]) on a tablet.
 *
 * Pause — and system back — opens the [PauseMenu] described by [pause], with How to play (this
 * [game]'s tab) and Settings added after the game's own rows. [onPausedChange] tells the game when
 * play stops and starts again (Solitaire's clock). With [tableOverPanel] the [content] draws over
 * the bottom panel — a held hand's lifted or tilted cards (and a card being dragged) then pass in
 * front of the panel's top edge instead of being cut off by it; [content] must keep its own touch
 * targets inside the padding it's given, so it doesn't take the panel's taps.
 *
 * What stays in each feature: the whole play area ([content] — board layout, drag-drop, win
 * overlays), effect/snackbar handling, and what its [controls] hold.
 */
@Composable
fun GameScaffold(
    game: GameId,
    pause: GamePause,
    onOpenSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    pill: String? = null,
    scoreCard: (@Composable () -> Unit)? = null,
    controls: GameControls = GameControls(),
    onPausedChange: (Boolean) -> Unit = {},
    tableOverPanel: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    var paused by rememberSaveable { mutableStateOf(false) }
    var showingRules by rememberSaveable { mutableStateOf(false) }
    val currentOnPausedChange by rememberUpdatedState(onPausedChange)
    LaunchedEffect(paused) { currentOnPausedChange(paused) }
    // In a game, system back pauses rather than leaving; the menu's own back resumes.
    BackHandler(enabled = !paused) { paused = true }

    Box(modifier = modifier) {
        TableBackground(modifier = Modifier.matchParentSize())
        TableScaffold(
            modifier = Modifier.fillMaxSize(),
            tableOverPanel = tableOverPanel,
            snackbarHost = { SnackbarHost(snackbarHostState) { ChromeSnackbar(it) } },
            topBar = { GameTopBar(game, pill, scoreCard, onPause = { paused = true }, onOpenSettings = onOpenSettings) },
            bottomBar = {
                when (CardGamesTheme.formFactor) {
                    FormFactor.PHONE ->
                        BottomPanel {
                            // A status line coming or going (a hint, an offer) grows and shrinks the panel
                            // smoothly. Only the status line animates its size: the animation clips to its
                            // bounds, and the actions row has badges overhanging its tiles.
                            if (controls.status != null) {
                                Box(modifier = Modifier.fillMaxWidth().animateContentSize(CardGamesMotion.StatusFade.tween())) {
                                    controls.status.invoke()
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = panelActionsMinHeight()),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                // The tool tiles and the button line up along their tops (the tiles' labels hang below).
                                verticalAlignment = Alignment.Top,
                            ) { controls.actions?.invoke(this) }
                        }
                    FormFactor.TABLET -> FloatingControls(controls.dock, controls.status, controls.actions, controls.panelWidth)
                }
            },
            content = content,
        )
        if (paused) {
            PauseMenu(
                overline = pause.overline,
                actions =
                    pause.actions.map { action ->
                        PauseAction(action.label, action.icon, action.enabled) {
                            paused = false
                            action.onClick()
                        }
                    } +
                        listOf(
                            PauseAction("How to play", ParlourIcons.Book) { showingRules = true },
                            PauseAction("Settings", ParlourIcons.Sliders) {
                                paused = false
                                onOpenSettings()
                            },
                        ),
                leaveLabel = pause.leaveLabel,
                onResume = { paused = false },
                onLeave = pause.onLeave,
            )
        }
    }
    if (showingRules) HowToPlaySheet(initial = game, onDismiss = { showingRules = false })
}

/**
 * What a game shows in its controls; [GameScaffold] places them for the form factor. [status] is the
 * line over the game's [actions] (Knock, Complete, Keep playing / Go Gin …). On a phone they fill the
 * bottom panel, and the actions row also carries the tools (Undo, Hint …) where the game shows them.
 * On a tablet the tools go in their own [dock], and the status and actions in a panel as wide as
 * [panelWidth] says (by default, all of the row beside the dock); a game builds the set for the form
 * factor it's drawn on.
 */
class GameControls(
    val status: (@Composable () -> Unit)? = null,
    val actions: (@Composable RowScope.() -> Unit)? = null,
    val dock: (@Composable RowScope.() -> Unit)? = null,
    val panelWidth: PanelWidth = PanelWidth.BesideDock,
)

/**
 * The top bar on the felt. Phone: Pause, the [pill] and Settings in a row, the [scoreCard] centred
 * under it. Tablet: one row — Pause and the score card on the left, the pill and Settings on the right.
 */
@Composable
private fun GameTopBar(
    game: GameId,
    pill: String?,
    scoreCard: (@Composable () -> Unit)?,
    onPause: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val metrics = chromeMetrics
    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = metrics.topBarTop)) {
        when (CardGamesTheme.formFactor) {
            FormFactor.PHONE ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.topBarInset),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TableIconButton(ParlourIcons.Pause, contentDescription = "Pause", onClick = onPause)
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TablePill(pill ?: game.displayName)
                    }
                    TableIconButton(ParlourIcons.Sliders, contentDescription = "Settings", onClick = onOpenSettings)
                }
            // Two groups pushed apart: Pause and the score card (as wide as it needs, up to its cap),
            // then the pill and Settings.
            FormFactor.TABLET ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.topBarInset),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(modifier = Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
                        TableIconButton(ParlourIcons.Pause, contentDescription = "Pause", onClick = onPause)
                        if (scoreCard != null) {
                            Spacer(modifier = Modifier.width(CardGamesTheme.spacing.md))
                            Box(modifier = Modifier.widthIn(max = metrics.scoreCardMaxWidth)) { scoreCard() }
                        }
                    }
                    Spacer(modifier = Modifier.width(CardGamesTheme.spacing.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TablePill(pill ?: game.displayName)
                        Spacer(modifier = Modifier.width(CardGamesTheme.spacing.md))
                        TableIconButton(ParlourIcons.Sliders, contentDescription = "Settings", onClick = onOpenSettings)
                    }
                }
        }
        // On a phone, the game's own figures sit on the felt under the bar.
        if (scoreCard != null && CardGamesTheme.formFactor == FormFactor.PHONE) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = TOP_CARD_GAP, start = CardGamesTheme.spacing.lg, end = CardGamesTheme.spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                scoreCard()
            }
        }
    }
}

/**
 * What a game puts in its pause menu: the [overline] chip ("Hand 5 · vs Rosa"), its own [actions]
 * (Restart this hand, Redo), and how leaving is worded ([leaveLabel]) and done ([onLeave]).
 */
class GamePause(
    val overline: String,
    val leaveLabel: String,
    val onLeave: () -> Unit,
    val actions: List<PauseAction> = emptyList(),
)

/** A row in the pause menu (Restart this hand, Redo, How to play, Settings). */
class PauseAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * The top bar, the bottom panel and the table between them. The table is measured full screen and
 * told the bars' heights as padding (as Material's Scaffold does), and placed after the panel when
 * [tableOverPanel] so it draws in front of it. A [snackbarHost] sits just above the panel.
 */
@Composable
private fun TableScaffold(
    tableOverPanel: Boolean,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
    modifier: Modifier = Modifier,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val top = subcompose(ScaffoldSlot.Top, topBar).map { it.measure(loose) }
        val bottom = subcompose(ScaffoldSlot.Bottom, bottomBar).map { it.measure(loose) }
        val topHeight = top.maxOfOrNull { it.height } ?: 0
        val bottomHeight = bottom.maxOfOrNull { it.height } ?: 0
        val padding = PaddingValues(top = topHeight.toDp(), bottom = bottomHeight.toDp())
        val table = subcompose(ScaffoldSlot.Table) { content(padding) }.map { it.measure(constraints) }
        val snackbars = subcompose(ScaffoldSlot.Snackbar, snackbarHost).map { it.measure(loose) }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        layout(width, height) {
            // Placement order is drawing order.
            val placeBottom = { bottom.forEach { it.place(0, height - bottomHeight) } }
            if (!tableOverPanel) table.forEach { it.place(0, 0) }
            placeBottom()
            if (tableOverPanel) table.forEach { it.place(0, 0) }
            top.forEach { it.place(0, 0) }
            snackbars.forEach { it.place((width - it.width) / 2, height - bottomHeight - it.height) }
        }
    }
}

private enum class ScaffoldSlot { Top, Bottom, Table, Snackbar }

/** A short notice ("You can't discard…") as a small panel above the bottom panel. */
@Composable
private fun ChromeSnackbar(data: SnackbarData) {
    val colors = CardGamesTheme.colors
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CardGamesTheme.spacing.xl, vertical = CardGamesTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = data.visuals.message,
            color = colors.onPanel,
            style = CardGamesTheme.type.bodySmall,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .shadow(SNACKBAR_ELEVATION, ChromeShapes.Icon)
                    .background(colors.panel, ChromeShapes.Icon)
                    .padding(horizontal = CardGamesTheme.spacing.lg, vertical = 10.dp),
        )
    }
}

private val TOP_CARD_GAP = 6.dp
private val SNACKBAR_ELEVATION = 6.dp
