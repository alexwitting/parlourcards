package com.cardgames.feature.solitaire.board

import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.solitaire.KlondikeMove
import com.cardgames.core.domain.solitaire.KlondikeState

/** A spot on the table the hint highlight outlines — a suggested move's endpoints. */
sealed interface HintSpot {
    data object Stock : HintSpot

    data object Waste : HintSpot

    /** A specific card in a tableau pile (a move's source). */
    data class TableauCard(
        val pile: Int,
        val cardIndex: Int,
    ) : HintSpot

    /** A whole tableau pile (a move's destination). */
    data class TableauDestination(
        val pile: Int,
    ) : HintSpot

    data class Foundation(
        val suit: Suit,
    ) : HintSpot
}

/** The spots to outline for a suggested [move] in this position; empty for no suggestion. */
fun KlondikeState.hintSpots(move: KlondikeMove?): Set<HintSpot> =
    when (move) {
        null -> emptySet()
        KlondikeMove.DealFromStock, KlondikeMove.RecycleWaste -> setOf(HintSpot.Stock)
        is KlondikeMove.TableauToTableau ->
            setOf(
                HintSpot.TableauCard(move.fromPile, move.cardIndex),
                HintSpot.TableauDestination(move.toPile),
            )
        is KlondikeMove.WasteToTableau -> setOf(HintSpot.Waste, HintSpot.TableauDestination(move.toPile))
        is KlondikeMove.TableauToFoundation -> {
            val pile = tableau[move.fromPile]
            setOfNotNull(
                HintSpot.TableauCard(move.fromPile, pile.cards.lastIndex),
                pile.topCard?.let { HintSpot.Foundation(it.suit) },
            )
        }
        KlondikeMove.WasteToFoundation ->
            setOfNotNull(
                HintSpot.Waste,
                waste.lastOrNull()?.let { HintSpot.Foundation(it.suit) },
            )
        is KlondikeMove.FoundationToTableau ->
            setOf(HintSpot.Foundation(move.suit), HintSpot.TableauDestination(move.toPile))
    }
