package com.cardgames.core.domain.cards

import kotlinx.serialization.Serializable

/**
 * One of the 52 canonical playing cards. Face-up/face-down is positional state owned by the
 * pile a card sits in, never by the card itself.
 */
@Serializable
data class Card(
    val suit: Suit,
    val rank: Rank,
) {
    val isRed: Boolean
        get() = suit.isRed

    override fun toString(): String = "${rank.name.lowercase()} of ${suit.name.lowercase()}"
}
