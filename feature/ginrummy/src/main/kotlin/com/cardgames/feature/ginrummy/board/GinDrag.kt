package com.cardgames.feature.ginrummy.board

import androidx.compose.ui.Modifier
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinMove
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.ui.cards.DragDropState
import com.cardgames.core.ui.cards.dropTarget
import com.cardgames.feature.ginrummy.GinIntent

/** What the finger picked up. Drag payload for the shared drag-and-drop system. */
sealed interface GinDragSource {
    data class HandCard(
        val card: Card,
    ) : GinDragSource

    data object StockTop : GinDragSource

    data object DiscardTop : GinDragSource
}

/** The card [source] carries: a hand card, or the top of the stock or the discard pile. */
internal fun GinState.cardOf(source: GinDragSource): Card? =
    when (source) {
        is GinDragSource.HandCard -> source.card
        GinDragSource.StockTop -> stock.lastOrNull()
        GinDragSource.DiscardTop -> discardPile.lastOrNull()
    }

/** Where a drag can land. Also used as the drop-target keys. */
sealed interface GinDropTarget {
    data object Hand : GinDropTarget

    data object Discard : GinDropTarget
}

/**
 * The physical [Card] a drop of [source] would move, resolved against the pre-drop state — used
 * to key the destination's one-shot "just merged, don't re-glide from the pile" render
 * (a drop lands in place, see `GinMotionKind.DROP`).
 */
fun GinState.cardFor(source: GinDragSource): Card? =
    when (source) {
        is GinDragSource.HandCard -> source.card
        GinDragSource.StockTop -> stock.lastOrNull()
        GinDragSource.DiscardTop -> discardPile.lastOrNull()
    }

/**
 * The engine move dropping [source] on [target] would perform, or null when the combination is
 * meaningless (a hand card dropped back on the hand, the stock/discard dropped on themselves).
 * Whether the returned move is legal stays the engine's call, same split as solitaire's
 * `moveFor` (`SolitaireDrag.kt`). [GinDragSource.DiscardTop] maps to [GinMove.TakeUpcard] during
 * the opening upcard decision and [GinMove.DrawFromDiscard] afterward — the same phase-dependent
 * choice the tap-to-draw discard pile already makes (`GinScreen.kt`'s `StockDiscardRow`).
 */
fun GinState.moveFor(
    source: GinDragSource,
    target: GinDropTarget,
): GinMove? =
    when (target) {
        GinDropTarget.Hand ->
            when (source) {
                GinDragSource.StockTop -> GinMove.DrawFromStock
                GinDragSource.DiscardTop ->
                    if (phase is GinPhase.UpcardDecision) GinMove.TakeUpcard else GinMove.DrawFromDiscard
                is GinDragSource.HandCard -> null
            }
        GinDropTarget.Discard ->
            when (source) {
                is GinDragSource.HandCard -> GinMove.Discard(source.card)
                GinDragSource.StockTop, GinDragSource.DiscardTop -> null
            }
    }

/** Registers this node as [target]: accepts exactly what the engine deems legal. */
internal fun Modifier.ginDropTarget(
    dragState: DragDropState<GinDragSource>,
    game: GinState,
    target: GinDropTarget,
    onIntent: (GinIntent) -> Unit,
): Modifier =
    dropTarget(
        state = dragState,
        key = target,
        canAccept = { source -> game.moveFor(source, target)?.let { GinEngine.isLegal(game, it) } == true },
        onDrop = { source -> onIntent(GinIntent.DropCard(source, target)) },
    )
