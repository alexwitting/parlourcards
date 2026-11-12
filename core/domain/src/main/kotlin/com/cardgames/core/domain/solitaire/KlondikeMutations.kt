package com.cardgames.core.domain.solitaire

import com.cardgames.core.domain.cards.Card

/**
 * State transitions for Klondike — the "what happens" half of [KlondikeEngine]. Legality is
 * the caller's responsibility ([KlondikeEngine.apply] checks it first); move counting too.
 */
internal object KlondikeMutations {
    fun apply(
        state: KlondikeState,
        move: KlondikeMove,
    ): KlondikeState =
        when (move) {
            is KlondikeMove.DealFromStock -> dealFromStock(state)
            is KlondikeMove.RecycleWaste ->
                state.copy(stock = state.waste.reversed(), waste = emptyList(), dealBatchRemaining = 0)
            is KlondikeMove.TableauToTableau -> tableauToTableau(state, move)
            is KlondikeMove.WasteToTableau -> wasteToTableau(state, move)
            is KlondikeMove.TableauToFoundation -> tableauToFoundation(state, move)
            is KlondikeMove.WasteToFoundation -> wasteToFoundation(state)
            is KlondikeMove.FoundationToTableau -> foundationToTableau(state, move)
        }

    private fun dealFromStock(state: KlondikeState): KlondikeState {
        val n = minOf(state.drawCount, state.stock.size)
        return state.copy(
            stock = state.stock.dropLast(n),
            waste = state.waste + state.stock.takeLast(n).reversed(),
            dealBatchRemaining = n,
        )
    }

    private fun tableauToTableau(
        state: KlondikeState,
        move: KlondikeMove.TableauToTableau,
    ): KlondikeState {
        val from = state.tableau[move.fromPile]
        val moving = from.cards.subList(move.cardIndex, from.cards.size).toList()
        return state.copy(
            tableau =
                state.tableau.mapIndexed { index, pile ->
                    when (index) {
                        move.fromPile -> pile.withRemovedFrom(move.cardIndex)
                        move.toPile -> pile.copy(cards = pile.cards + moving)
                        else -> pile
                    }
                },
        )
    }

    private fun wasteToTableau(
        state: KlondikeState,
        move: KlondikeMove.WasteToTableau,
    ): KlondikeState =
        state.copy(
            waste = state.waste.dropLast(1),
            tableau = state.tableau.withCardAdded(move.toPile, state.waste.last()),
            dealBatchRemaining = maxOf(0, state.dealBatchRemaining - 1),
        )

    private fun tableauToFoundation(
        state: KlondikeState,
        move: KlondikeMove.TableauToFoundation,
    ): KlondikeState {
        val from = state.tableau[move.fromPile]
        val card = from.topCard ?: error("caller checks legality")
        return state.copy(
            tableau =
                state.tableau.mapIndexed { index, pile ->
                    if (index == move.fromPile) pile.withRemovedFrom(pile.cards.size - 1) else pile
                },
            foundations = state.foundations + (card.suit to state.foundationCount(card.suit) + 1),
        )
    }

    private fun wasteToFoundation(state: KlondikeState): KlondikeState {
        val card = state.waste.last()
        return state.copy(
            waste = state.waste.dropLast(1),
            foundations = state.foundations + (card.suit to state.foundationCount(card.suit) + 1),
            dealBatchRemaining = maxOf(0, state.dealBatchRemaining - 1),
        )
    }

    private fun foundationToTableau(
        state: KlondikeState,
        move: KlondikeMove.FoundationToTableau,
    ): KlondikeState {
        val card = KlondikeRules.foundationTopCard(state, move.suit) ?: error("caller checks legality")
        return state.copy(
            foundations = state.foundations + (move.suit to state.foundationCount(move.suit) - 1),
            tableau = state.tableau.withCardAdded(move.toPile, card),
        )
    }

    /** Remove cards from [fromIndex] up, auto-flipping the newly exposed top card if needed. */
    private fun TableauPile.withRemovedFrom(fromIndex: Int): TableauPile {
        val remaining = cards.subList(0, fromIndex).toList()
        val newFaceDown = if (remaining.isEmpty()) 0 else minOf(faceDownCount, remaining.size - 1)
        return TableauPile(cards = remaining, faceDownCount = newFaceDown)
    }

    private fun List<TableauPile>.withCardAdded(
        pileIndex: Int,
        card: Card,
    ): List<TableauPile> =
        mapIndexed { index, pile ->
            if (index == pileIndex) pile.copy(cards = pile.cards + card) else pile
        }
}
