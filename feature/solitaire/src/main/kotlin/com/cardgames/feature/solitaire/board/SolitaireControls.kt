package com.cardgames.feature.solitaire.board

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardgames.core.ui.chrome.GameControls
import com.cardgames.core.ui.chrome.PrimaryButton
import com.cardgames.core.ui.chrome.SecondaryButton
import com.cardgames.core.ui.chrome.ToolButton
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.layout.FormFactor
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.feature.solitaire.SolitaireIntent
import com.cardgames.feature.solitaire.SolitaireState

/**
 * Solitaire's controls for the form factor it's drawn on ([GameControls]). The panel only speaks for a
 * hint, or when there are no moves left (then Undo and New deal replace the tools).
 * - Phone: the four tools, 52dp wide and 6dp apart, then Complete across the rest of the row.
 * - Tablet: the tools in the dock and Auto-complete beside them, bottom right; the hint on the table
 *   ([solitaireTableExtras]).
 */
@Composable
internal fun solitaireControls(
    state: SolitaireState,
    onIntent: (SolitaireIntent) -> Unit,
    requestRestart: () -> Unit,
    requestNewGame: () -> Unit,
): GameControls {
    val stuck = state.isStuck && !state.canAutoComplete
    val status: (@Composable () -> Unit)? =
        when {
            stuck -> {
                { NoMovesMessage() }
            }
            else -> hintLine(state)?.let { line -> { StatusRow(line) } }
        }
    if (stuck) return GameControls(status = status, actions = { StuckActions(state, onIntent, requestNewGame) })
    val complete: @Composable RowScope.() -> Unit = {
        CompleteButton(state.canAutoComplete, onClick = { onIntent(SolitaireIntent.AutoComplete) }, modifier = Modifier.weight(1f))
    }
    return when (CardGamesTheme.formFactor) {
        FormFactor.PHONE ->
            GameControls(
                status = status,
                actions = {
                    SolitaireTools(
                        state,
                        onIntent,
                        requestRestart,
                        requestNewGame,
                        toolModifier = Modifier.width(TOOL_WIDTH),
                        gap = TOOL_GAP,
                    )
                    Spacer(modifier = Modifier.width(COMPLETE_GAP))
                    complete()
                },
            )
        // The hint is on the table (solitaireTableExtras); the tools in the dock, Auto-complete beside them.
        FormFactor.TABLET ->
            GameControls(
                actions = {
                    CompleteButton(
                        state.canAutoComplete,
                        onClick = { onIntent(SolitaireIntent.AutoComplete) },
                        label = "Auto-complete",
                        modifier = Modifier.weight(1f),
                    )
                },
                dock = { SolitaireTools(state, onIntent, requestRestart, requestNewGame) },
            )
    }
}

/** Undo, Hint, Restart and New, [gap] apart (none when the container spaces them). */
@Composable
private fun RowScope.SolitaireTools(
    state: SolitaireState,
    onIntent: (SolitaireIntent) -> Unit,
    requestRestart: () -> Unit,
    requestNewGame: () -> Unit,
    toolModifier: Modifier = Modifier,
    gap: Dp? = null,
) {
    val spacer: @Composable () -> Unit = { if (gap != null) Spacer(modifier = Modifier.width(gap)) }
    ToolButton(ParlourIcons.Undo, label = "Undo", enabled = state.canUndo, modifier = toolModifier, onClick = {
        onIntent(SolitaireIntent.Undo)
    })
    spacer()
    ToolButton(
        ParlourIcons.Lightbulb,
        label = "Hint",
        enabled = state.hintsLeft != 0,
        badge = state.hintsLeft?.takeIf { it > 0 },
        modifier = toolModifier,
        onClick = { onIntent(SolitaireIntent.Hint) },
    )
    spacer()
    ToolButton(ParlourIcons.Restart, label = "Restart", enabled = !state.isDealing, modifier = toolModifier, onClick = requestRestart)
    spacer()
    ToolButton(ParlourIcons.Shuffle, label = "New", enabled = !state.isDealing, modifier = toolModifier, onClick = requestNewGame)
}

/** No moves left: Undo, or a new deal. */
@Composable
private fun RowScope.StuckActions(
    state: SolitaireState,
    onIntent: (SolitaireIntent) -> Unit,
    requestNewGame: () -> Unit,
) {
    SecondaryButton(text = "Undo", onClick = { onIntent(SolitaireIntent.Undo) }, enabled = state.canUndo, modifier = Modifier.weight(1f))
    Spacer(modifier = Modifier.width(CardGamesTheme.spacing.sm))
    PrimaryButton(text = "New deal", onClick = requestNewGame, enabled = !state.isDealing, modifier = Modifier.weight(1f))
}

private val TOOL_WIDTH = 52.dp
private val TOOL_GAP = 6.dp

/** Between the last tool and Complete: the tools' gap and 4dp more. */
private val COMPLETE_GAP = 10.dp

/** The tablet's hint, on the felt just above the draw pile (on a phone it's the panel's status line). */
@Composable
internal fun solitaireHintOnTable(state: SolitaireState): (@Composable (columnWidth: Dp) -> Unit)? =
    if (CardGamesTheme.formFactor == FormFactor.TABLET) hintLine(state)?.let { line -> { HintPill(line) } } else null
