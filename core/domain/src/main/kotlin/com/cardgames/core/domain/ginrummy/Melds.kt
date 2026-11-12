package com.cardgames.core.domain.ginrummy

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit

/**
 * Finds the meld partition of a hand with the least deadwood. A card that could join a run or a
 * set (5♠6♠7♠8♠ with 7♥7♦7♣) goes wherever leaves less deadwood across the whole hand, so this is
 * an exact search over candidate melds, memoized by a bitmask over the hand (at most 11 cards, so
 * 2,048 states).
 */
object Melds {
    /** The lowest-deadwood way to split [hand] into melds + leftovers. Assumes no duplicate cards. */
    fun bestPartition(hand: List<Card>): MeldPartition {
        if (hand.isEmpty()) return MeldPartition(emptyList(), emptyList())

        val indexOf = hand.withIndex().associate { (index, card) -> card to index }
        val candidates =
            candidateMelds(hand).map { meld ->
                meld to meld.cards.fold(0) { mask, card -> mask or (1 shl indexOf.getValue(card)) }
            }
        val memo = HashMap<Int, Pair<Int, List<Meld>>>()

        fun solve(remaining: Int): Pair<Int, List<Meld>> {
            if (remaining == 0) return 0 to emptyList()
            memo[remaining]?.let { return it }

            val cardBit = remaining and (-remaining)
            val card = hand[cardBit.countTrailingZeroBits()]

            var best = solve(remaining xor cardBit).let { (pips, melds) -> (pips + card.rank.pipValue) to melds }
            for ((meld, mask) in candidates) {
                // Only usable if it covers the card being resolved and all its cards are still free.
                val usable = mask and cardBit != 0 && mask and remaining == mask
                if (!usable) continue
                val (pips, melds) = solve(remaining xor mask)
                if (pips < best.first) best = pips to (melds + meld)
            }

            memo[remaining] = best
            return best
        }

        val fullMask = (1 shl hand.size) - 1
        val (_, melds) = solve(fullMask)
        val usedCards = melds.flatMap { it.cards }.toSet()
        return MeldPartition(melds = melds, deadwood = hand.filterNot { it in usedCards })
    }

    /** Every set/run [hand] could form — overlapping and redundant on purpose; the caller picks. */
    private fun candidateMelds(hand: List<Card>): List<Meld> {
        val sets =
            Rank.entries
                .flatMap { rank ->
                    val ofRank = hand.filter { it.rank == rank }
                    if (ofRank.size < SET_MIN) {
                        emptyList()
                    } else {
                        combinations(ofRank, SET_MIN) + if (ofRank.size == SET_MAX) listOf(ofRank) else emptyList()
                    }
                }.map { Meld.Set(it) }

        val runs =
            Suit.entries
                .flatMap { suit ->
                    consecutiveGroups(hand.filter { it.suit == suit }.sortedBy { it.rank.value })
                        .flatMap { group -> subRuns(group) }
                }.map { Meld.Run(it) }

        return sets + runs
    }

    /** Splits a suit's rank-sorted cards into maximal ace-low-consecutive runs. */
    private fun consecutiveGroups(sortedBySuit: List<Card>): List<List<Card>> {
        if (sortedBySuit.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<Card>>(mutableListOf(sortedBySuit.first()))
        for (card in sortedBySuit.drop(1)) {
            if (card.rank.isOneAbove(groups.last().last().rank)) {
                groups.last().add(card)
            } else {
                groups.add(mutableListOf(card))
            }
        }
        return groups
    }

    /** Every contiguous slice of [group] with at least [RUN_MIN] cards. */
    private fun subRuns(group: List<Card>): List<List<Card>> =
        buildList {
            for (start in group.indices) {
                for (end in start + RUN_MIN - 1 until group.size) {
                    add(group.subList(start, end + 1))
                }
            }
        }

    private fun <T> combinations(
        items: List<T>,
        size: Int,
    ): List<List<T>> {
        if (size == 0) return listOf(emptyList())
        if (items.size < size) return emptyList()
        val head = items.first()
        val tail = items.drop(1)
        return combinations(tail, size - 1).map { listOf(head) + it } + combinations(tail, size)
    }

    private const val SET_MIN = 3
    private const val SET_MAX = 4
    private const val RUN_MIN = 3
}
