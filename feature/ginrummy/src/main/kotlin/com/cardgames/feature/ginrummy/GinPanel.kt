package com.cardgames.feature.ginrummy

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.HandOutcome
import com.cardgames.core.domain.ginrummy.Melds
import com.cardgames.core.ui.cards.label
import com.cardgames.core.ui.chrome.ChromeShapes
import com.cardgames.core.ui.chrome.PrimaryButton
import com.cardgames.core.ui.chrome.SecondaryButton
import com.cardgames.core.ui.chrome.ToolButton
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.layout.FormFactor
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.tween
import com.cardgames.core.ui.theme.CardGamesTheme

/** What the bottom panel is doing right now — one mode at a time, the most pressing first. */
internal sealed interface GinPanel {
    /** The stock ran down to its reserve: nobody scores, the same dealer redeals. */
    data object Blocked : GinPanel

    /** A knock is being settled: the knocker's hand is up, layoffs are landing (none on a [gin]). */
    data class LayingOff(
        val knocker: GinPlayer,
        val gin: Boolean,
        val bigGin: Boolean,
    ) : GinPanel

    /** Going out is on offer (gin, or Big Gin). */
    data class GoingOut(
        val offer: GinOffer,
    ) : GinPanel

    /** Knock was pressed with "confirm before knocking" on. */
    data object ConfirmingKnock : GinPanel

    /** The player tried to throw back the card they just took. */
    data class CantDiscard(
        val card: Card,
    ) : GinPanel

    /** The player's say on the upcard. */
    data class Upcard(
        val card: Card,
    ) : GinPanel

    /** The turn as usual: deadwood, Undo, Hint, Knock. */
    data object Play : GinPanel
}

internal fun ginPanel(state: GinUiState): GinPanel {
    val game = state.game
    val phase = game.phase
    val offer = state.ginOffer
    val rejected = state.rejectedCard
    val upcard = game.discardPile.lastOrNull()
    return when {
        phase is GinPhase.HandOver && phase.result.outcome == HandOutcome.BLOCKED -> GinPanel.Blocked
        phase is GinPhase.AwaitingLayoff -> GinPanel.LayingOff(phase.knocker, gin = phase.isGin, bigGin = phase.isBigGin)
        state.confirmingKnock && state.canKnock -> GinPanel.ConfirmingKnock
        offer != null -> GinPanel.GoingOut(offer)
        rejected != null -> GinPanel.CantDiscard(rejected)
        state.isPlayerTurn && phase is GinPhase.UpcardDecision && upcard != null -> GinPanel.Upcard(upcard)
        else -> GinPanel.Play
    }
}

/** The panel's first row (or two), for the current [ginPanel] mode. */
@Composable
internal fun GinPanelStatus(
    state: GinUiState,
    dealing: Boolean,
) {
    val colors = CardGamesTheme.colors
    val game = state.game
    when (val panel = ginPanel(state)) {
        GinPanel.Blocked ->
            Message(
                title = "Hand blocked",
                detail = "Only two cards left in the stock, so nobody scores.",
            )
        is GinPanel.LayingOff ->
            Text(
                when {
                    panel.gin && panel.knocker == GinPlayer.PLAYER -> "You went ${if (panel.bigGin) "Big Gin" else "gin"}!"
                    panel.gin -> "${state.opponentName} went ${if (panel.bigGin) "Big Gin" else "gin"}!"
                    panel.knocker == GinPlayer.PLAYER -> "You knocked. ${state.opponentName} lays off what fits…"
                    else -> "${state.opponentName} knocked. Laying off what fits…"
                },
                color = colors.onPanel,
                style = CardGamesTheme.type.bodySmall,
            )
        is GinPanel.GoingOut ->
            when (panel.offer) {
                GinOffer.BigGin -> Message("Big Gin", accent = true)
                is GinOffer.Gin -> Message("Gin", accent = true)
            }
        GinPanel.ConfirmingKnock ->
            // Knock mode: the suggested throw is lifted; any other card can be picked, then Knock — or
            // dragged to the pile.
            Message("Knock with ${state.knockCard?.label().orEmpty()}?", note = "${knockDeadwood(state)} deadwood")
        is GinPanel.CantDiscard ->
            Text(
                "You can't throw back the ${panel.card.label()} you just took.",
                color = colors.bad,
                style = CardGamesTheme.type.bodySmall,
            )
        is GinPanel.Upcard -> {
            // The meld hint when the upcard would make one; otherwise the deadwood meter, as in play.
            val partners = game.discardMeldPartners()
            // On a tablet the choice is under the piles and the brass ring says it melds: the panel stays as in play.
            if (partners.isNotEmpty() && CardGamesTheme.formFactor == FormFactor.PHONE) {
                Text(
                    "${panel.card.label()} would meld with your ${partners.joinToString(" and ") { it.label() }}",
                    color = colors.onPanelMuted,
                    style = CardGamesTheme.type.bodySmall,
                )
            } else {
                DeadwoodRow(state, dealing)
            }
        }
        GinPanel.Play -> DeadwoodRow(state, dealing)
    }
}

/** The panel's actions for the current [ginPanel] mode — with the tools in play, unless they're in a dock ([withTools] false). */
@Composable
internal fun RowScope.GinPanelActions(
    state: GinUiState,
    onIntent: (GinIntent) -> Unit,
    withTools: Boolean = true,
) {
    val gap = Modifier.width(CardGamesTheme.spacing.sm)
    when (val panel = ginPanel(state)) {
        GinPanel.Blocked ->
            PrimaryButton(
                text = "Redeal",
                onClick = { onIntent(GinIntent.NextHand) },
                modifier = Modifier.weight(1f),
            )
        is GinPanel.GoingOut -> {
            SecondaryButton(text = "Keep playing", onClick = { onIntent(GinIntent.DismissGinOffer) }, modifier = Modifier.weight(1f))
            Spacer(modifier = gap)
            PrimaryButton(
                text = if (panel.offer == GinOffer.BigGin) "Go Big Gin" else "Go Gin",
                onClick = { onIntent(GinIntent.GoGin) },
                modifier = Modifier.weight(1f),
            )
        }
        GinPanel.ConfirmingKnock -> {
            SecondaryButton(text = "Not yet", onClick = { onIntent(GinIntent.CancelKnock) }, modifier = Modifier.weight(1f))
            Spacer(modifier = gap)
            PrimaryButton(
                text = "Knock",
                onClick = { onIntent(GinIntent.ConfirmKnock) },
                modifier = Modifier.weight(1f),
            )
        }
        is GinPanel.Upcard ->
            if (CardGamesTheme.formFactor == FormFactor.TABLET) {
                PlayActions(state, onIntent, withTools)
            } else {
                SecondaryButton(text = "Pass", onClick = { onIntent(GinIntent.PassUpcard) }, modifier = Modifier.weight(1f))
                Spacer(modifier = gap)
                PrimaryButton(
                    text = "Take ${panel.card.label()}",
                    onClick = { onIntent(GinIntent.TakeUpcard) },
                    modifier = Modifier.weight(1f),
                )
            }
        is GinPanel.LayingOff, is GinPanel.CantDiscard, GinPanel.Play -> PlayActions(state, onIntent, withTools)
    }
}

/** Undo, Hint (unless they're in a dock, [withTools] false), and Knock. */
@Composable
private fun RowScope.PlayActions(
    state: GinUiState,
    onIntent: (GinIntent) -> Unit,
    withTools: Boolean = true,
) {
    if (withTools) {
        GinTools(state, onIntent, spaced = true)
        Spacer(modifier = Modifier.width(CardGamesTheme.spacing.md))
    }
    // Label only: whether it's enabled, and the deadwood meter above it, say the rest.
    PrimaryButton(
        text = if (state.isOpponentsTurn && CardGamesTheme.formFactor == FormFactor.TABLET) "${state.opponentName}'s turn" else "Knock",
        enabled = state.canKnock,
        onClick = { onIntent(GinIntent.Knock) },
        modifier = Modifier.weight(1f),
    )
}

/** A title (brass when it's good news), a line under it, and an optional note at the end. */
@Composable
private fun Message(
    title: String,
    detail: String? = null,
    note: String? = null,
    accent: Boolean = false,
) {
    val colors = CardGamesTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = if (accent) colors.accentDeep else colors.onPanel,
                style = CardGamesTheme.type.subhead,
                modifier = Modifier.weight(1f),
            )
            note?.let { Text(it, color = colors.onPanel, style = CardGamesTheme.type.rowLabel) }
        }
        detail?.let { Text(it, color = colors.onPanelMuted, style = CardGamesTheme.type.bodySmall) }
    }
}

/** The deadwood a knock would leave. */
private fun knockDeadwood(state: GinUiState): Int = state.knockCard?.let(state.game::deadwoodAfterDiscarding) ?: 0

/**
 * The play row, the deadwood meter: the deadwood — after the best (or the chosen) discard while
 * the player holds 11 — red over 10 and green at 10 or less; ten segments (all rose while over,
 * else one sage segment per point); and a chip saying how far over, or that a knock is ready.
 */
@Composable
private fun DeadwoodRow(
    state: GinUiState,
    dealing: Boolean,
) {
    val colors = CardGamesTheme.colors
    val game = state.game
    val phase = game.phase
    val discarding = phase is GinPhase.AwaitingDiscard && phase.toMove == GinPlayer.PLAYER
    val deadwood =
        when {
            dealing -> null
            discarding ->
                state.selectedCard?.let(game::deadwoodAfterDiscarding)
                    ?: game.playerHand.filter { it != phase.mustNotDiscard }.minOfOrNull(game::deadwoodAfterDiscarding)
            else -> Melds.bestPartition(game.playerHand).deadwoodPips
        }
    val limit = GinState.KNOCK_THRESHOLD
    val over = deadwood != null && deadwood > limit
    // While dealing, the figure and chip are laid out but unseen, so the row (and the panel, and the
    // hand above it) keeps its height when they appear; they fade in as the deal lands.
    val shown by animateFloatAsState(if (deadwood == null) 0f else 1f, CardGamesMotion.StatusFade.tween(), label = "deadwood")
    val reveal = Modifier.graphicsLayer { alpha = shown }
    val figure = deadwood ?: 0
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Deadwood", color = colors.onPanel, style = CardGamesTheme.type.pill, modifier = Modifier.alignByBaseline())
            Text(
                "$figure",
                color = if (over) colors.bad else colors.good,
                style = CardGamesTheme.type.panelAction,
                modifier = Modifier.alignByBaseline().then(reveal),
            )
        }
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP)) {
            repeat(limit) { index ->
                val fill =
                    when {
                        deadwood == null -> colors.panelLine
                        over -> colors.meterOver
                        index < deadwood -> colors.hint
                        else -> colors.panelLine
                    }
                Box(modifier = Modifier.weight(1f).height(SEGMENT_HEIGHT).background(fill, ChromeShapes.Pill))
            }
        }
        when {
            over -> MeterChip("${figure - limit} over", fill = colors.badBg, ink = colors.bad, modifier = reveal)
            else -> MeterChip("Knock ready", fill = colors.goodBg, ink = colors.good, modifier = reveal)
        }
    }
}

@Composable
private fun MeterChip(
    text: String,
    fill: Color,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        color = ink,
        style = CardGamesTheme.type.actionSub,
        maxLines = 1,
        modifier = modifier.background(fill, ChromeShapes.Pill).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

private val SEGMENT_GAP = 3.dp
private val SEGMENT_HEIGHT = 8.dp

/** The Deadwood row as it would show for [state], for measuring only (see [AtLeastAsTallAs]). */
@Composable
internal fun GinPanelStatusSizer(state: GinUiState) = DeadwoodRow(state, dealing = false)

/** The Undo / Hint / Knock row, for measuring only (see [AtLeastAsTallAs]). */
@Composable
internal fun RowScope.GinPanelActionsSizer(
    state: GinUiState,
    withTools: Boolean = true,
) = PlayActions(state, onIntent = {}, withTools = withTools)

/** Undo and Hint — in the panel's row on a phone ([spaced] apart), in the dock on a tablet. */
@Composable
internal fun RowScope.GinTools(
    state: GinUiState,
    onIntent: (GinIntent) -> Unit,
    spaced: Boolean,
) {
    val phase = state.game.phase
    val handFinished = phase is GinPhase.HandOver || phase is GinPhase.MatchOver || phase is GinPhase.AwaitingLayoff
    ToolButton(
        ParlourIcons.Undo,
        label = "Undo",
        enabled = state.canUndo && !handFinished && state.pending?.kind != GinMotionKind.DEAL && !state.isOpponentsTurn,
        onClick = { onIntent(GinIntent.Undo) },
    )
    if (spaced) Spacer(modifier = Modifier.width(CardGamesTheme.spacing.sm))
    ToolButton(
        ParlourIcons.Lightbulb,
        label = "Hint",
        enabled = state.isPlayerTurn && state.hintsLeft != 0,
        badge = state.hintsLeft?.takeIf { it > 0 },
        onClick = { onIntent(GinIntent.Hint) },
    )
}

/**
 * [content], made at least as tall as [sizer] — which is measured but never placed, so it neither
 * draws nor takes touches. Panel modes stay one height, so nothing above the panel shifts as they change.
 */
@Composable
internal fun AtLeastAsTallAs(
    sizer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(contents = listOf(sizer, content), modifier = modifier) { (sizers, contents), constraints ->
        val loose = constraints.copy(minHeight = 0)
        val floor = sizers.maxOfOrNull { it.measure(loose).height } ?: 0
        val placeables =
            contents.map {
                it.measure(
                    constraints.copy(minHeight = floor.coerceIn(constraints.minHeight, constraints.maxHeight)),
                )
            }
        val width = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
        val height = maxOf(floor, placeables.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) { placeables.forEach { it.place(0, 0) } }
    }
}
