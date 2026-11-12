package com.cardgames.core.domain.solitaire

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.solitaire.KlondikeState.Companion.TABLEAU_PILE_COUNT

/** Move legality for Klondike — the "may I?" half of [KlondikeEngine]. */
internal object KlondikeRules {
    fun isLegal(
        state: KlondikeState,
        move: KlondikeMove,
    ): Boolean =
        when (move) {
            is KlondikeMove.DealFromStock -> state.stock.isNotEmpty()
            is KlondikeMove.RecycleWaste -> state.stock.isEmpty() && state.waste.isNotEmpty()
            is KlondikeMove.TableauToTableau -> isLegalTableauToTableau(state, move)
            is KlondikeMove.WasteToTableau -> isLegalWasteToTableau(state, move)
            is KlondikeMove.TableauToFoundation -> isLegalTableauToFoundation(state, move)
            is KlondikeMove.WasteToFoundation ->
                state.waste.isNotEmpty() && canPlaceOnFoundation(state, state.waste.last())
            is KlondikeMove.FoundationToTableau -> isLegalFoundationToTableau(state, move)
        }

    fun canPlaceOnTableau(
        state: KlondikeState,
        card: Card,
        toPile: Int,
    ): Boolean {
        val top = state.tableau[toPile].topCard ?: return card.rank == Rank.KING
        return top.rank.isOneAbove(card.rank) && top.isRed != card.isRed
    }

    fun canPlaceOnFoundation(
        state: KlondikeState,
        card: Card,
    ): Boolean = state.foundationCount(card.suit) == card.rank.value - 1

    /** The implied top card of a suit's foundation, or null when that foundation is empty. */
    fun foundationTopCard(
        state: KlondikeState,
        suit: Suit,
    ): Card? = state.foundationTop(suit)

    private fun isValidPileIndex(index: Int): Boolean = index in 0 until TABLEAU_PILE_COUNT

    private fun isLegalTableauToTableau(
        state: KlondikeState,
        move: KlondikeMove.TableauToTableau,
    ): Boolean {
        if (!isValidPileIndex(move.fromPile) || !isValidPileIndex(move.toPile)) return false
        if (move.fromPile == move.toPile) return false
        val from = state.tableau[move.fromPile]
        if (move.cardIndex !in from.faceDownCount until from.cards.size) return false
        return canPlaceOnTableau(state, from.cards[move.cardIndex], move.toPile)
    }

    private fun isLegalWasteToTableau(
        state: KlondikeState,
        move: KlondikeMove.WasteToTableau,
    ): Boolean {
        if (!isValidPileIndex(move.toPile)) return false
        val card = state.waste.lastOrNull() ?: return false
        return canPlaceOnTableau(state, card, move.toPile)
    }

    private fun isLegalTableauToFoundation(
        state: KlondikeState,
        move: KlondikeMove.TableauToFoundation,
    ): Boolean {
        if (!isValidPileIndex(move.fromPile)) return false
        val card = state.tableau[move.fromPile].topCard ?: return false
        return canPlaceOnFoundation(state, card)
    }

    private fun isLegalFoundationToTableau(
        state: KlondikeState,
        move: KlondikeMove.FoundationToTableau,
    ): Boolean {
        if (!isValidPileIndex(move.toPile)) return false
        val card = foundationTopCard(state, move.suit) ?: return false
        return canPlaceOnTableau(state, card, move.toPile)
    }
}
