package com.cardgames.core.domain.ginrummy.ai

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinMove
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.Melds
import kotlin.random.Random

/**
 * A determinized Monte Carlo (PIMC) [GinOpponent] for [chooseDiscard] and [chooseDraw];
 * [shouldKnock] stays rule-based ([ageRampedKnockThreshold]), with its patience set per
 * [GinDifficulty].
 *
 * Discards: [shortlistCandidates] ranks every discard cheaply, and only the shortlist is simulated
 * ([HiddenWorldSampler] deals a hidden world, [RolloutRunner] plays it out with
 * [HeuristicGinOpponent] as both seats' policy).
 *
 * Draws: a binary choice, and [HeuristicGinOpponent]'s one-step rule ("does taking the upcard
 * lower my deadwood?") is already strong, so it decides the clear cases — a large improvement or
 * none. Only a marginal improvement is simulated ([bestDrawByRollout]); simulating every draw plays
 * worse, as shallow rollouts add noise to close calls. Declining the upcard at the start of a hand
 * is simulated as a stock draw, the same as an ordinary turn.
 */
class MonteCarloGinOpponent(
    private val config: MonteCarloConfig = GinDifficulty.MEDIUM.toConfig(),
    private val random: Random = Random.Default,
) : GinOpponent {
    constructor(difficulty: GinDifficulty, random: Random = Random.Default) : this(difficulty.toConfig(), random)

    private val rolloutPolicy: GinOpponent = HeuristicGinOpponent()

    override fun chooseDraw(view: OpponentView): DrawPreference {
        val topOfDiscard = view.discardPile.lastOrNull() ?: return DrawPreference.STOCK
        // Best achievable deadwood after taking topOfDiscard and then discarding optimally from
        // the resulting 11-card hand — the same number HeuristicGinOpponent's own chooseDraw is
        // built on. Can never exceed currentDeadwood (discarding the very card just taken always
        // gets back to exactly that baseline), so "improvement" is never negative.
        val currentDeadwood = Melds.bestPartition(view.ownHand).deadwoodPips
        val handIfTaken = view.ownHand + topOfDiscard
        val bestAfterTaking = handIfTaken.minOf { candidate -> Melds.bestPartition(handIfTaken - candidate).deadwoodPips }
        val improvement = currentDeadwood - bestAfterTaking
        return when {
            improvement <= 0 -> DrawPreference.STOCK
            improvement > DRAW_AMBIGUITY_MARGIN -> DrawPreference.DISCARD
            else -> bestDrawByRollout(view)
        }
    }

    override fun chooseDiscard(view: OpponentView): Card {
        val candidates = view.ownHand.filterNot { it == view.mustNotDiscard }
        check(candidates.isNotEmpty()) { "chooseDiscard called with nothing legal to discard" }
        val defending = config.defensiveWeight > 0 && view.opponentKnownCards.isNotEmpty()
        // About to knock: this discard ends the hand, so there's nothing left to defend against —
        // and a defensive pick could be the one card that makes the knock illegal. Take the
        // knock card (least deadwood left behind) instead.
        if (defending && shouldKnock(view)) {
            return candidates.minWith(
                compareBy<Card> { Melds.bestPartition(view.ownHand - it).deadwoodPips }.thenByDescending { it.rank.pipValue },
            )
        }
        val shortlist = shortlistCandidates(view, candidates, defending)
        return if (shortlist.size == 1) shortlist.single() else bestByRollout(view, shortlist, defending)
    }

    override fun shouldKnock(view: OpponentView): Boolean =
        bestAchievableDeadwood(view) <= ageRampedKnockThreshold(view.stockRemaining, config.earlyKnockThreshold)

    /**
     * The lowest deadwood achievable by discarding optimally right now — deliberately the plain
     * greedy minimum (same building block [shortlistCandidates] ranks by), not whatever
     * [chooseDiscard] would pick (which may trade a little deadwood for the safety
     * tiebreak, or Monte Carlo's own evaluation). Knocking asks "is my hand good enough,"
     * period — it should use the objectively best achievable number, the same thing
     * [GinEngine.bestKnockCard] and [HeuristicGinOpponent] both use it for.
     */
    private fun bestAchievableDeadwood(view: OpponentView): Int =
        view.ownHand
            .filterNot { it == view.mustNotDiscard }
            .minOf { candidate -> Melds.bestPartition(view.ownHand - candidate).deadwoodPips }

    /**
     * Every legal discard ranked by the deadwood it leaves (ascending), ties broken by safety: a card
     * whose meld neighbours (its rank in other suits, the adjacent ranks in its suit) are mostly in
     * the discard pile is less likely to help the other seat. When [defending], cards that build on
     * what the other seat is known to hold ([discardDanger]) rank after equally good safer ones.
     * Only the top [MonteCarloConfig.shortlistSize] are simulated.
     */
    private fun shortlistCandidates(
        view: OpponentView,
        candidates: List<Card>,
        defending: Boolean,
    ): List<Card> =
        candidates
            .sortedWith(
                compareBy<Card> { candidate -> Melds.bestPartition(view.ownHand - candidate).deadwoodPips }
                    .thenBy { candidate -> if (defending) discardDanger(candidate, view.opponentKnownCards) else 0 }
                    .thenByDescending { candidate -> deadNeighborCount(candidate, view.discardPile) },
            ).take(config.shortlistSize)

    /** How many of [card]'s meld-neighbors have already appeared in [discardPile] — higher is safer to discard. */
    private fun deadNeighborCount(
        card: Card,
        discardPile: List<Card>,
    ): Int {
        val discarded = discardPile.toSet()
        val sameRankOtherSuits = Suit.entries.filter { it != card.suit }.map { Card(it, card.rank) }
        val runAdjacentSameSuit =
            listOfNotNull(
                Rank.entries.getOrNull(card.rank.ordinal - 1)?.let { Card(card.suit, it) },
                Rank.entries.getOrNull(card.rank.ordinal + 1)?.let { Card(card.suit, it) },
            )
        return (sameRankOtherSuits + runAdjacentSameSuit).count { it in discarded }
    }

    /**
     * Evaluates [shortlist] round-robin, one hidden world per round shared across every
     * candidate that round (common random numbers: isolates each discard's own effect from
     * world-sampling luck, rather than each candidate facing an independently unlucky/lucky
     * deal) — up to [MonteCarloConfig.roundCeiling] rounds, cut short the instant the shared
     * wall-clock budget runs out, wherever that lands mid-round. When [defending], each candidate's
     * average is docked [MonteCarloConfig.defensiveWeight] points per unit of [discardDanger].
     */
    private fun bestByRollout(
        view: OpponentView,
        shortlist: List<Card>,
        defending: Boolean,
    ): Card {
        val deadline = System.nanoTime() + config.budgetMillis * NANOS_PER_MILLI
        val discardPhase = GinPhase.AwaitingDiscard(GinPlayer.PLAYER, mustNotDiscard = view.mustNotDiscard)
        val totals = DoubleArray(shortlist.size)
        val counts = IntArray(shortlist.size)

        var round = 0
        while (round < config.roundCeiling && System.nanoTime() < deadline) {
            val world = HiddenWorldSampler.sample(view, discardPhase, random)
            for (index in shortlist.indices) {
                if (System.nanoTime() >= deadline) break
                val afterDiscard = GinEngine.apply(world, GinMove.Discard(shortlist[index]))
                val outcome = RolloutRunner.rollout(afterDiscard, rolloutPolicy, config.rolloutDepth)
                totals[index] += RolloutRunner.scoreForPlayer(outcome)
                counts[index]++
            }
            round++
        }

        fun penalty(card: Card): Double = if (defending) config.defensiveWeight * discardDanger(card, view.opponentKnownCards) else 0.0
        val bestIndex =
            shortlist.indices.maxByOrNull { index ->
                if (counts[index] > 0) totals[index] / counts[index] - penalty(shortlist[index]) else Double.NEGATIVE_INFINITY
            }
        return shortlist[bestIndex ?: 0]
    }

    /**
     * Same round-robin/common-random-numbers shape as [bestByRollout], just over the fixed
     * two-candidate choice (stock vs. the discard pile's top card) rather than a ranked shortlist
     * — nothing to prune when there are only ever two options.
     */
    private fun bestDrawByRollout(view: OpponentView): DrawPreference {
        val deadline = System.nanoTime() + config.budgetMillis * NANOS_PER_MILLI
        val drawPhase = GinPhase.AwaitingDraw(GinPlayer.PLAYER)
        var stockTotal = 0.0
        var stockCount = 0
        var discardTotal = 0.0
        var discardCount = 0

        var round = 0
        while (round < config.roundCeiling && System.nanoTime() < deadline) {
            val world = HiddenWorldSampler.sample(view, drawPhase, random)
            if (System.nanoTime() < deadline) {
                val outcome = RolloutRunner.rollout(GinEngine.apply(world, GinMove.DrawFromStock), rolloutPolicy, config.rolloutDepth)
                stockTotal += RolloutRunner.scoreForPlayer(outcome)
                stockCount++
            }
            if (System.nanoTime() < deadline) {
                val outcome = RolloutRunner.rollout(GinEngine.apply(world, GinMove.DrawFromDiscard), rolloutPolicy, config.rolloutDepth)
                discardTotal += RolloutRunner.scoreForPlayer(outcome)
                discardCount++
            }
            round++
        }

        val stockAvg = if (stockCount > 0) stockTotal / stockCount else Double.NEGATIVE_INFINITY
        val discardAvg = if (discardCount > 0) discardTotal / discardCount else Double.NEGATIVE_INFINITY
        return if (discardAvg > stockAvg) DrawPreference.DISCARD else DrawPreference.STOCK
    }

    companion object {
        /**
         * How far rollouts look ahead, the same for every [GinDifficulty]. Deeper is worse: the
         * rollout policy is deterministic and self-correcting, so long play-outs drift back to
         * parity and erase the difference between discards that the rollout is there to measure.
         */
        internal const val CALIBRATED_ROLLOUT_DEPTH = 3

        private const val NANOS_PER_MILLI = 1_000_000L

        /**
         * A deadwood-pip improvement at or under this is "marginal" — ambiguous enough to spend
         * rollout budget on rather than trust the one-step number outright.
         */
        private const val DRAW_AMBIGUITY_MARGIN = 2
    }
}
