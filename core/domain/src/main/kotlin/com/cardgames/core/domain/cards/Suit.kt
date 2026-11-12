package com.cardgames.core.domain.cards

enum class Suit {
    CLUBS,
    DIAMONDS,
    HEARTS,
    SPADES,
    ;

    val isRed: Boolean
        get() = this == DIAMONDS || this == HEARTS

    val isBlack: Boolean
        get() = !isRed
}
