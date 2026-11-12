package com.cardgames.core.domain.cards

/**
 * Ranks in ace-low order: `ordinal + 1` is the rank's position (ace = 1 … king = 13), which
 * both launch games rely on — Klondike builds A→K on foundations, gin runs treat ace as low.
 */
enum class Rank {
    ACE,
    TWO,
    THREE,
    FOUR,
    FIVE,
    SIX,
    SEVEN,
    EIGHT,
    NINE,
    TEN,
    JACK,
    QUEEN,
    KING,
    ;

    /** Ace-low position, 1..13. */
    val value: Int
        get() = ordinal + 1

    /** Gin deadwood pip count: ace = 1, face cards = 10. */
    val pipValue: Int
        get() = minOf(value, PIP_CAP)

    /** True when this rank is exactly one above [other] (ace-low), e.g. FOUR.isOneAbove(THREE). */
    fun isOneAbove(other: Rank): Boolean = value == other.value + 1

    private companion object {
        const val PIP_CAP = 10
    }
}
