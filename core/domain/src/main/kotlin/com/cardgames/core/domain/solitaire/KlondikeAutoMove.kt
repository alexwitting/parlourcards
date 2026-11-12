package com.cardgames.core.domain.solitaire

import com.cardgames.core.domain.solitaire.KlondikeState.Companion.TABLEAU_PILE_COUNT

/**
 * Tap-to-auto-move policy: tapping a card sends it to the best legal spot — foundations first,
 * then tableau piles (occupied targets before empty ones, so kings only fill gaps when there's
 * nothing better). Pure and deterministic.
 */
object KlondikeAutoMove {
    sealed interface Source {
        data object Waste : Source

        data class Tableau(
            val pile: Int,
            val cardIndex: Int,
        ) : Source
    }

    fun bestMoveFor(
        state: KlondikeState,
        source: Source,
    ): KlondikeMove? =
        when (source) {
            is Source.Waste -> bestWasteMove(state)
            is Source.Tableau -> bestTableauMove(state, source)
        }

    private fun bestWasteMove(state: KlondikeState): KlondikeMove? =
        firstLegal(state, KlondikeMove.WasteToFoundation)
            ?: bestByTarget(state) { KlondikeMove.WasteToTableau(it) }

    private fun bestTableauMove(
        state: KlondikeState,
        source: Source.Tableau,
    ): KlondikeMove? =
        firstLegal(state, KlondikeMove.TableauToFoundation(source.pile))
            ?.takeIf { source.cardIndex == state.tableau[source.pile].cards.size - 1 }
            ?: bestByTarget(state) { KlondikeMove.TableauToTableau(source.pile, source.cardIndex, it) }

    private fun firstLegal(
        state: KlondikeState,
        move: KlondikeMove,
    ): KlondikeMove? = move.takeIf { KlondikeEngine.isLegal(state, it) }

    private fun bestByTarget(
        state: KlondikeState,
        moveTo: (Int) -> KlondikeMove,
    ): KlondikeMove? {
        val legalTargets =
            (0 until TABLEAU_PILE_COUNT)
                .map(moveTo)
                .filter { KlondikeEngine.isLegal(state, it) }
        return legalTargets.firstOrNull { !targetPileIsEmpty(state, it) } ?: legalTargets.firstOrNull()
    }

    private fun targetPileIsEmpty(
        state: KlondikeState,
        move: KlondikeMove,
    ): Boolean =
        when (move) {
            is KlondikeMove.TableauToTableau -> state.tableau[move.toPile].isEmpty
            is KlondikeMove.WasteToTableau -> state.tableau[move.toPile].isEmpty
            else -> false
        }
}
