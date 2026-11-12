package com.cardgames.core.ui.cards

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit

val Suit.glyph: Char
    get() =
        when (this) {
            Suit.CLUBS -> '♣'
            Suit.DIAMONDS -> '♦'
            Suit.HEARTS -> '♥'
            Suit.SPADES -> '♠'
        }

val Rank.glyph: String
    get() =
        when (this) {
            Rank.ACE -> "A"
            Rank.JACK -> "J"
            Rank.QUEEN -> "Q"
            Rank.KING -> "K"
            else -> value.toString()
        }

/**
 * A suit as text, with the text-presentation selector (U+FE0E): without it some phones draw the
 * suits as colour emoji — ♥ and ♦ red, ♣ and ♠ black — ignoring the text's own colour.
 */
val Suit.symbol: String
    get() = "$glyph\uFE0E"

/** A card's short name in running text ("Q♥"). */
fun Card.label(): String = "${rank.glyph}${suit.symbol}"
