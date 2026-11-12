package com.cardgames.core.domain.ginrummy.ai

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.Melds

/**
 * A greedy opponent with no lookahead: every decision comes from [Melds.bestPartition], keeping
 * whichever ten cards leave the least deadwood. Its knock patience relaxes as the hand goes on
 * ([knockThreshold]). It doesn't weigh what its discards give away; that's
 * [MonteCarloGinOpponent]'s defensive play ([discardDanger]). It is also [MonteCarloGinOpponent]'s
 * rollout policy.
 */
class HeuristicGinOpponent : GinOpponent {
    override fun chooseDraw(view: OpponentView): DrawPreference {
        val topOfDiscard = view.discardPile.lastOrNull() ?: return DrawPreference.STOCK
        val currentDeadwood = Melds.bestPartition(view.ownHand).deadwoodPips
        val ifTaken = view.ownHand + topOfDiscard
        val afterTaking = bestDiscard(ifTaken, excluding = null).deadwoodAfter
        return if (afterTaking < currentDeadwood) DrawPreference.DISCARD else DrawPreference.STOCK
    }

    override fun chooseDiscard(view: OpponentView): Card = bestDiscard(view.ownHand, view.mustNotDiscard).card

    override fun shouldKnock(view: OpponentView): Boolean {
        val bestAfterDiscard = bestDiscard(view.ownHand, view.mustNotDiscard).deadwoodAfter
        return bestAfterDiscard <= knockThreshold(view.stockRemaining)
    }

    /**
     * Conservative early (only a near-gin hand is worth interrupting for — plenty of stock
     * remains to draw into gin proper), relaxing toward the full legal threshold as the stock
     * nears its reserve: a blocked hand scores nobody anything, so grabbing partial points late
     * beats risking a wash. Ramp shape (and why it's squared, not linear) documented on
     * [ageRampedKnockThreshold] itself, shared with [MonteCarloGinOpponent]'s difficulty tiers.
     */
    private fun knockThreshold(stockRemaining: Int): Int = ageRampedKnockThreshold(stockRemaining, EARLY_THRESHOLD)

    private data class DiscardCandidate(
        val card: Card,
        val deadwoodAfter: Int,
    )

    /**
     * The discard (from [hand], excluding [excluding]) that leaves the least deadwood behind;
     * ties broken by shedding the highest-pip card — cheap deadwood is safer to keep sitting
     * on than expensive deadwood, so when indifferent, get rid of the costly card first.
     */
    private fun bestDiscard(
        hand: List<Card>,
        excluding: Card?,
    ): DiscardCandidate =
        hand
            .filterNot { it == excluding }
            .map { candidate -> DiscardCandidate(candidate, Melds.bestPartition(hand - candidate).deadwoodPips) }
            .minWithOrNull(compareBy<DiscardCandidate> { it.deadwoodAfter }.thenByDescending { it.card.rank.pipValue })
            ?: error("chooseDiscard called with nothing legal to discard")

    private companion object {
        /** Effectively "wait for gin or as close to it as makes no difference." */
        const val EARLY_THRESHOLD = 1
    }
}
