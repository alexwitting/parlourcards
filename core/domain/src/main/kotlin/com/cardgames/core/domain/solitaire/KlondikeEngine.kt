package com.cardgames.core.domain.solitaire

import com.cardgames.core.domain.cards.Deck
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.solitaire.KlondikeState.Companion.TABLEAU_PILE_COUNT
import kotlin.random.Random

/**
 * Pure Klondike rules facade: no coroutines, no time, no I/O. Every function is a pure function
 * of the state; [apply] throws on illegal moves, so callers gate on [isLegal] (or pick from
 * [legalMoves]). Legality lives in [KlondikeRules], state transitions in [KlondikeMutations].
 */
object KlondikeEngine {
    fun deal(
        seed: Long,
        drawCount: Int = 1,
    ): KlondikeState {
        val deck = Deck.shuffled(Random(seed))
        var cursor = 0
        val tableau =
            List(TABLEAU_PILE_COUNT) { pileIndex ->
                val size = pileIndex + 1
                val cards = deck.subList(cursor, cursor + size).toList()
                cursor += size
                TableauPile(cards = cards, faceDownCount = pileIndex)
            }
        return KlondikeState(
            seed = seed,
            drawCount = drawCount,
            tableau = tableau,
            foundations = Suit.entries.associateWith { 0 },
            stock = deck.subList(cursor, deck.size).toList(),
            waste = emptyList(),
        )
    }

    fun isLegal(
        state: KlondikeState,
        move: KlondikeMove,
    ): Boolean = KlondikeRules.isLegal(state, move)

    fun apply(
        state: KlondikeState,
        move: KlondikeMove,
    ): KlondikeState {
        require(isLegal(state, move)) { "Illegal move $move" }
        return KlondikeMutations.apply(state, move).copy(moveCount = state.moveCount + 1)
    }

    fun legalMoves(state: KlondikeState): List<KlondikeMove> =
        buildList {
            for (pile in 0 until TABLEAU_PILE_COUNT) add(KlondikeMove.TableauToFoundation(pile))
            add(KlondikeMove.WasteToFoundation)
            addAll(tableauToTableauCandidates(state))
            for (suit in Suit.entries) {
                for (toPile in 0 until TABLEAU_PILE_COUNT) add(KlondikeMove.FoundationToTableau(suit, toPile))
            }
            for (toPile in 0 until TABLEAU_PILE_COUNT) add(KlondikeMove.WasteToTableau(toPile))
            add(KlondikeMove.DealFromStock)
            add(KlondikeMove.RecycleWaste)
        }.filter { isLegal(state, it) }

    fun isWon(state: KlondikeState): Boolean = state.foundations.values.sum() == Deck.standard52.size

    private fun tableauToTableauCandidates(state: KlondikeState): List<KlondikeMove> =
        (0 until TABLEAU_PILE_COUNT).flatMap { fromPile -> tableauMovesFrom(state, fromPile) }

    private fun tableauMovesFrom(
        state: KlondikeState,
        fromPile: Int,
    ): List<KlondikeMove> {
        val from = state.tableau[fromPile]
        val result = mutableListOf<KlondikeMove>()
        for (cardIndex in from.faceDownCount until from.cards.size) {
            for (toPile in 0 until TABLEAU_PILE_COUNT) {
                if (toPile != fromPile) result += KlondikeMove.TableauToTableau(fromPile, cardIndex, toPile)
            }
        }
        return result
    }
}
