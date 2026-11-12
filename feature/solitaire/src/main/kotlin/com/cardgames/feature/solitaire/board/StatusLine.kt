package com.cardgames.feature.solitaire.board

import com.cardgames.core.domain.solitaire.KlondikeMove
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.ui.cards.label
import com.cardgames.feature.solitaire.SolitaireState

/**
 * The bottom panel's first row: [text], with a brass dot before it when it's a hint ([isHint]) and
 * an optional [trailing] note at the end ("2 hints left").
 */
internal data class StatusLine(
    val text: String,
    val isHint: Boolean = false,
    val trailing: String? = null,
)

/**
 * The panel's one line of text, shown only while a hint is up: the hint in words, with the hints
 * left. (The panel otherwise carries no running tips.)
 */
internal fun hintLine(state: SolitaireState): StatusLine? =
    state.hint?.let { StatusLine(state.game.describe(it), isHint = true, trailing = hintsLeftNote(state.hintsLeft)) }

/** "2 hints left" / "No hints left"; nothing when hints are unlimited. */
internal fun hintsLeftNote(hintsLeft: Int?): String? =
    when (hintsLeft) {
        null -> null
        0 -> "No hints left"
        1 -> "1 hint left"
        else -> "$hintsLeft hints left"
    }

/** A hint in words, from the position it was given for. */
internal fun KlondikeState.describe(move: KlondikeMove): String =
    when (move) {
        KlondikeMove.DealFromStock -> "Draw from the stock."
        KlondikeMove.RecycleWaste -> "Turn the waste back over."
        is KlondikeMove.TableauToFoundation -> "${tableau[move.fromPile].topCard?.label()} can go home to its foundation."
        KlondikeMove.WasteToFoundation -> "${waste.lastOrNull()?.label()} can go home to its foundation."
        is KlondikeMove.TableauToTableau ->
            "${tableau[move.fromPile].cards[move.cardIndex].label()} fits on ${columnName(move.toPile)}."
        is KlondikeMove.WasteToTableau ->
            "${waste.lastOrNull()?.label()} fits on ${columnName(move.toPile)}."
        is KlondikeMove.FoundationToTableau ->
            "${foundationTop(move.suit)?.label()} can come down onto ${columnName(move.toPile)}."
    }

/** A column by its top card ("10♦"), or "the empty column". */
private fun KlondikeState.columnName(pile: Int): String = tableau[pile].topCard?.label() ?: "the empty column"
