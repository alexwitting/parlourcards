package com.cardgames.core.domain.repository

/** How many hints a game allows per deal (Solitaire) or per hand (Gin). */
enum class HintLimit(
    /** Hints allowed; null means unlimited. */
    val allowance: Int?,
) {
    OFF(allowance = 0),
    THREE(allowance = 3),
    UNLIMITED(allowance = null),
    ;

    /** Hints still available after [used]; null when unlimited. */
    fun remaining(used: Int): Int? = allowance?.let { (it - used).coerceAtLeast(0) }
}
