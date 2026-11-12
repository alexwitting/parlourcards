package com.cardgames.core.domain.solitaire

import com.cardgames.core.domain.cards.Suit

/** Every move a player can make in Klondike. Pile indices are 0-based tableau positions. */
sealed interface KlondikeMove {
    /** Flip [KlondikeState.drawCount] cards from the stock onto the waste. */
    data object DealFromStock : KlondikeMove

    /** Turn the exhausted stock back over: waste becomes the new stock. */
    data object RecycleWaste : KlondikeMove

    /**
     * Move the face-up run starting at [cardIndex] (index into the source pile's full card
     * list) from [fromPile] onto [toPile].
     */
    data class TableauToTableau(
        val fromPile: Int,
        val cardIndex: Int,
        val toPile: Int,
    ) : KlondikeMove

    data class WasteToTableau(
        val toPile: Int,
    ) : KlondikeMove

    data class TableauToFoundation(
        val fromPile: Int,
    ) : KlondikeMove

    data object WasteToFoundation : KlondikeMove

    /** Bring the top foundation card of [suit] back down onto [toPile]. */
    data class FoundationToTableau(
        val suit: Suit,
        val toPile: Int,
    ) : KlondikeMove
}
