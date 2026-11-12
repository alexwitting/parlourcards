package com.cardgames.core.testing

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit

/**
 * A card from how it's written on the table: a rank (`A`, `2`–`10`, `J`, `Q`, `K`) then a suit
 * (`♣ ♦ ♥ ♠`, or `C D H S`) — `card("10♥")`, `card("KS")`. Keeps test hands readable at a glance.
 */
fun card(text: String): Card {
    val trimmed = text.trim().replace(TEXT_PRESENTATION, "")
    require(trimmed.length >= 2) { "Not a card: \"$text\"" }
    val suit =
        SUITS[trimmed.last().uppercaseChar()]
            ?: throw IllegalArgumentException("No suit in \"$text\"")
    val rank =
        RANKS[trimmed.dropLast(1).uppercase()]
            ?: throw IllegalArgumentException("No rank in \"$text\"")
    return Card(suit, rank)
}

/** Space-separated [card]s, in order: `cards("A♣ 2♣ 3♣ K♠")`. */
fun cards(text: String): List<Card> =
    text
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .map(::card)

private const val TEXT_PRESENTATION = "︎"

private val SUITS =
    mapOf(
        '♣' to Suit.CLUBS,
        'C' to Suit.CLUBS,
        '♦' to Suit.DIAMONDS,
        'D' to Suit.DIAMONDS,
        '♥' to Suit.HEARTS,
        'H' to Suit.HEARTS,
        '♠' to Suit.SPADES,
        'S' to Suit.SPADES,
    )

private val RANKS =
    Rank.entries.associateBy {
        when (it) {
            Rank.ACE -> "A"
            Rank.JACK -> "J"
            Rank.QUEEN -> "Q"
            Rank.KING -> "K"
            else -> it.value.toString()
        }
    }
