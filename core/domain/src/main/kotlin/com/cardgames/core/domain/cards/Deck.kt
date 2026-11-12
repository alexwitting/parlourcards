package com.cardgames.core.domain.cards

import kotlin.random.Random

object Deck {
    /** All 52 cards in a stable canonical order (suits × ranks, both in declaration order). */
    val standard52: List<Card> =
        Suit.entries.flatMap { suit ->
            Rank.entries.map { rank -> Card(suit, rank) }
        }

    /**
     * A full deck shuffled deterministically by [random]. Engines must obtain their deal
     * exclusively through this with a seeded [Random] so any deal is reproducible from its
     * seed (tests, bug reports, restart-same-deal).
     */
    fun shuffled(random: Random): List<Card> = standard52.shuffled(random)
}
