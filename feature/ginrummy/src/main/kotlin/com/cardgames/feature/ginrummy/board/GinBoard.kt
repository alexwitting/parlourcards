package com.cardgames.feature.ginrummy.board

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.ui.cards.DragDropState
import com.cardgames.core.ui.table.CardGlide
import com.cardgames.feature.ginrummy.GinIntent

// The gin table, top to bottom: the score race (in the top bar), the opponent's fan
// (GinOpponentFan.kt), the stock and discard piles (GinPiles.kt), the player's melds (GinMelds.kt)
// and hand (GinHand.kt). GinContent arranges these; this file holds what they share.

/** How the table dresses its cards this frame: each card's glide, and the rings. */
@Immutable
internal class GinLook(
    val glide: (Card) -> CardGlide,
    /** Brass ring and a lift: the card the player is about to throw. */
    val selected: Card? = null,
    /** Sage rings: the cards the discard would meld with, or the card a hint says to throw. */
    val matching: Set<Card> = emptySet(),
    /** The rings and chip on the stock and discard pile. */
    val piles: PileLook = PileLook(),
    /** Red ring and a shake: the card the player just tried, and wasn't allowed, to throw. */
    val rejected: Card? = null,
    /** The card just drawn: a sage ring, a NEW tag, a small lift — and its meld glows if it made one. */
    val drawn: Card? = null,
)

/**
 * Fades this to [alpha]. Unlike `Modifier.alpha` it doesn't clip to its bounds while fully shown,
 * so a card's tag, glow and lift still draw; below 1 it composites as one layer (so overlapping
 * cards don't show through each other), which does clip — keep anything that overhangs, like a
 * count badge, outside it.
 */
internal fun Modifier.fade(alpha: Float): Modifier = graphicsLayer { this.alpha = alpha }

/** Who can do what on the table this frame. */
internal class GinInput(
    val onIntent: (GinIntent) -> Unit,
    val dragState: DragDropState<GinDragSource>,
    val phase: GinPhase,
    /** The player has a decision and nothing is in flight. */
    val canAct: Boolean,
    val selected: Card?,
)

/** Whose seat [phase] is waiting on. */
internal fun GinPhase.waitingOn(): GinPlayer? =
    when (this) {
        is GinPhase.UpcardDecision -> deciding
        is GinPhase.AwaitingDraw -> toMove
        is GinPhase.AwaitingDiscard -> toMove
        else -> null
    }

/** Hand cards turn about a point below them, like cards held in a fan. */
internal val HAND_PIVOT = TransformOrigin(0.5f, 1.2f)
