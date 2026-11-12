package com.cardgames.core.domain.ginrummy

import com.cardgames.core.domain.cards.Card
import kotlinx.serialization.Serializable

/**
 * A completed gin rummy meld: either a same-rank [Set] (3–4 cards, distinct suits) or a
 * same-suit [Run] (3+ consecutive ranks, ace low only — see [com.cardgames.core.domain.cards.Rank]).
 * Melds are immutable and self-validating; building an invalid one throws. Both this interface
 * and each subclass must stay `@Serializable` — [GinPhase.AwaitingLayoff] (persisted on every
 * autosave) carries `List<Meld>`, and kotlinx-serialization only wires up sealed-polymorphic
 * dispatch automatically when the whole hierarchy opts in.
 */
@Serializable
sealed interface Meld {
    val cards: List<Card>

    @Serializable
    data class Set(
        override val cards: List<Card>,
    ) : Meld {
        init {
            require(cards.size in MIN_SET_SIZE..MAX_SET_SIZE) { "A set is 3-4 cards, got ${cards.size}" }
            require(cards.map { it.rank }.distinct().size == 1) { "A set must share one rank" }
            require(cards.map { it.suit }.distinct().size == cards.size) { "A set's suits must be distinct" }
        }
    }

    @Serializable
    data class Run(
        override val cards: List<Card>,
    ) : Meld {
        init {
            require(cards.size >= MIN_RUN_SIZE) { "A run is 3+ cards, got ${cards.size}" }
            require(cards.map { it.suit }.distinct().size == 1) { "A run must share one suit" }
            require(cards.zipWithNext().all { (a, b) -> b.rank.isOneAbove(a.rank) }) {
                "A run's ranks must be strictly consecutive ace-low"
            }
        }
    }

    private companion object {
        const val MIN_SET_SIZE = 3
        const val MAX_SET_SIZE = 4
        const val MIN_RUN_SIZE = 3
    }
}

/**
 * One way to split a hand into melds plus whatever's left over. [deadwoodPips] is the pip
 * total of [deadwood] — what [Melds.bestPartition] minimizes.
 */
data class MeldPartition(
    val melds: List<Meld>,
    val deadwood: List<Card>,
) {
    val deadwoodPips: Int
        get() = deadwood.sumOf { it.rank.pipValue }
}
