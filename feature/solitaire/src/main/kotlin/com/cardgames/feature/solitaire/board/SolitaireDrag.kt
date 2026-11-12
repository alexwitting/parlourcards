package com.cardgames.feature.solitaire.board

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.solitaire.KlondikeEngine
import com.cardgames.core.domain.solitaire.KlondikeMove
import com.cardgames.core.domain.solitaire.KlondikeState

/** What the finger picked up. Drag payload for the shared drag-and-drop system. */
sealed interface SolitaireDragSource {
    /** The face-up run starting at [cardIndex] (index into the pile's full card list). */
    data class TableauRun(
        val pile: Int,
        val cardIndex: Int,
    ) : SolitaireDragSource

    /** The top card of the waste. */
    data object Waste : SolitaireDragSource

    /** The top card of [suit]'s foundation, coming back down. */
    data class Foundation(
        val suit: Suit,
    ) : SolitaireDragSource
}

/** Where a drag can land. Also used as the drop-target keys. */
sealed interface SolitaireDropTarget {
    data class Tableau(
        val pile: Int,
    ) : SolitaireDropTarget

    data class Foundation(
        val suit: Suit,
    ) : SolitaireDropTarget
}

/**
 * The engine move dropping [source] on [target] would perform, or null when the combination
 * is meaningless (wrong-suit foundation, mid-run card to a foundation, foundation to
 * foundation). Whether the returned move is legal stays the engine's call.
 */
fun KlondikeState.moveFor(
    source: SolitaireDragSource,
    target: SolitaireDropTarget,
): KlondikeMove? =
    when (target) {
        is SolitaireDropTarget.Tableau ->
            when (source) {
                is SolitaireDragSource.TableauRun ->
                    KlondikeMove.TableauToTableau(source.pile, source.cardIndex, target.pile)
                SolitaireDragSource.Waste -> KlondikeMove.WasteToTableau(target.pile)
                is SolitaireDragSource.Foundation ->
                    KlondikeMove.FoundationToTableau(source.suit, target.pile)
            }
        is SolitaireDropTarget.Foundation ->
            when (source) {
                is SolitaireDragSource.TableauRun -> {
                    val pile = tableau[source.pile]
                    val isTopCard = source.cardIndex == pile.cards.lastIndex
                    if (isTopCard && pile.topCard?.suit == target.suit) {
                        KlondikeMove.TableauToFoundation(source.pile)
                    } else {
                        null
                    }
                }
                SolitaireDragSource.Waste ->
                    if (waste.lastOrNull()?.suit == target.suit) KlondikeMove.WasteToFoundation else null
                is SolitaireDragSource.Foundation -> null
            }
    }

/** The cards the drag ghost should render for [source], bottom → top. */
fun KlondikeState.draggedCards(source: SolitaireDragSource): List<Card> =
    when (source) {
        is SolitaireDragSource.TableauRun -> tableau[source.pile].cards.drop(source.cardIndex)
        SolitaireDragSource.Waste -> listOfNotNull(waste.lastOrNull())
        is SolitaireDragSource.Foundation -> listOfNotNull(foundationTop(source.suit))
    }

/** Every place [source] could legally go right now — the sage rings while it's held or dragged. */
fun KlondikeState.legalTargets(source: SolitaireDragSource): Set<SolitaireDropTarget> =
    buildSet {
        repeat(KlondikeState.TABLEAU_PILE_COUNT) { add(SolitaireDropTarget.Tableau(it)) }
        Suit.entries
            .forEach { add(SolitaireDropTarget.Foundation(it)) }
    }.filter { target -> moveFor(source, target)?.let { KlondikeEngine.isLegal(this, it) } == true }
        .toSet()
