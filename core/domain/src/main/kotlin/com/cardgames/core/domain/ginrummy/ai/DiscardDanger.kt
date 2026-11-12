package com.cardgames.core.domain.ginrummy.ai

import com.cardgames.core.domain.cards.Card
import kotlin.math.abs

/**
 * How much discarding [card] would help the other seat build melds, judged against the cards it
 * is known to hold ([knownCards] — taken from the discard pile, see [OpponentView.opponentKnownCards]).
 * Each known card contributes:
 * - 2 if it's the same rank (a set in the making),
 * - 2 if it's the same suit, one rank away (a run in the making, directly extended),
 * - 1 if it's the same suit, two ranks away (fills or extends a run with a gap).
 *
 * 0 means the discard gives away nothing visible. Cards the seat merely passed on say much less,
 * so they don't count.
 */
internal fun discardDanger(
    card: Card,
    knownCards: List<Card>,
): Int =
    knownCards.sumOf { known ->
        val rankGap = abs(known.rank.ordinal - card.rank.ordinal)
        when {
            known == card -> 0
            known.rank == card.rank -> STRONG_LINK
            known.suit == card.suit && rankGap == 1 -> STRONG_LINK
            known.suit == card.suit && rankGap == 2 -> WEAK_LINK
            else -> 0
        }
    }

private const val STRONG_LINK = 2
private const val WEAK_LINK = 1
