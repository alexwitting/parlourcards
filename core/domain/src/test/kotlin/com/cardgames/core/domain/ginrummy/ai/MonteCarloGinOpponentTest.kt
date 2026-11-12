package com.cardgames.core.domain.ginrummy.ai

import com.cardgames.core.domain.cards.Deck
import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.testing.card
import com.cardgames.core.testing.cards
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * The same contract as [HeuristicGinOpponentTest], on a small, fast [fastConfig]: these check
 * correctness, not search strength.
 */
class MonteCarloGinOpponentTest {
    // Shallow (depth 2) so a discard's deadwood advantage still shows: deeper, the greedy rollout
    // policy repairs a weak start back toward parity. The rounds are capped by roundCeiling, with a
    // budget that never binds, so the outcome doesn't depend on machine speed.
    private val fastConfig =
        MonteCarloConfig(shortlistSize = 4, roundCeiling = 40, rolloutDepth = 2, budgetMillis = 30_000, earlyKnockThreshold = 1)

    private fun opponent(seed: Long = 1L) = MonteCarloGinOpponent(fastConfig, Random(seed))

    // A realistic pre-discard 11-card hand, sized so ownHand + discardPile + opponentHandSize +
    // stockRemaining reconstitutes exactly one deck — chooseDiscard samples a hidden world, so
    // (unlike HeuristicGinOpponentTest's own hands) the counts must be consistent.
    private val clearCutHand =
        cards("A♠ 2♠ 3♠ 7♥ 7♦ 7♣ 4♥ 2♥ 3♣ 4♦ K♥")
    private val clearCutDiscardPile = cards("9♠")

    @Test
    fun `discards the card that minimizes resulting deadwood on a clear-cut hand`() {
        // Keeping the run + set intact and discarding the lone king is optimal — the
        // rule-based shortlist alone should already rank it first by a wide deadwood margin.
        val view =
            OpponentView(ownHand = clearCutHand, discardPile = clearCutDiscardPile, stockRemaining = 30, opponentHandSize = 10)

        assertEquals(card("K♥"), opponent().chooseDiscard(view))
    }

    @Test
    fun `never discards the card it must not discard, even if it's the best choice`() {
        val forbidden = card("K♥")
        val view =
            OpponentView(
                ownHand = clearCutHand,
                discardPile = clearCutDiscardPile,
                stockRemaining = 30,
                opponentHandSize = 10,
                mustNotDiscard = forbidden,
            )

        assertFalse(opponent().chooseDiscard(view) == forbidden)
    }

    @Test
    fun `a defensive config avoids feeding a card that builds on what the other seat picked up`() {
        // K♥ and Q♣ leave identical deadwood (19) — the only difference is that the other seat
        // visibly took the K♠, so the K♥ would hand it a pair toward a set.
        val hand =
            cards("A♠ 2♠ 3♠ 7♥ 7♦ 7♣ 2♥ 3♣ 4♦ K♥ Q♣")
        val view =
            OpponentView(
                ownHand = hand,
                discardPile = clearCutDiscardPile,
                stockRemaining = 30,
                opponentHandSize = 10,
                opponentKnownCards = cards("K♠"),
            )
        // Shortlist of 2: just the tied pair, so rollout noise from worse candidates can't interfere.
        val defensive = MonteCarloGinOpponent(fastConfig.copy(shortlistSize = 2, defensiveWeight = 100.0), Random(1L))

        assertEquals(card("Q♣"), defensive.chooseDiscard(view))
    }

    @Test
    fun `a defensive config still throws the knock card when it's about to knock`() {
        // Discarding K♥ leaves 8 deadwood with the stock nearly gone — a knock — even though the
        // other seat holds the K♠. The hand ends with this discard, so there's nothing to defend.
        val hand =
            cards("A♠ 2♠ 3♠ 7♥ 7♦ 7♣ 4♥ 2♥ A♣ A♦ K♥")
        val kingOfSpades = card("K♠")
        val stockRemaining = 3
        val discardPile =
            Deck.standard52
                .filterNot { it in hand || it == kingOfSpades }
                .take(Deck.standard52.size - hand.size - 10 - stockRemaining)
        val view =
            OpponentView(
                ownHand = hand,
                discardPile = discardPile,
                stockRemaining = stockRemaining,
                opponentHandSize = 10,
                opponentKnownCards = listOf(kingOfSpades),
            )
        val defensive = MonteCarloGinOpponent(fastConfig.copy(defensiveWeight = 100.0), Random(1L))

        assertTrue(defensive.shouldKnock(view))
        assertEquals(card("K♥"), defensive.chooseDiscard(view))
    }

    @Test
    fun `takes the discard pile when it strictly improves the hand`() {
        // Holding 4s5s6s, an offered 7s completes a longer run — an obvious improvement, and
        // (unlike the deep-rollout case) this signal should survive even a shallow test rollout
        // since it's decisive from the very first simulated tick onward.
        val view =
            OpponentView(
                ownHand =
                    cards("4♠ 5♠ 6♠ K♥ Q♣ J♦ 2♥ 3♣ 4♦ 9♠"),
                discardPile = cards("7♠"),
                stockRemaining = 31,
                opponentHandSize = 10,
            )

        assertEquals(DrawPreference.DISCARD, opponent().chooseDraw(view))
    }

    @Test
    fun `passes on a discard pile card that would not help`() {
        val view =
            OpponentView(
                ownHand =
                    cards("A♠ 2♠ 3♠ 7♥ 7♦ 7♣ 4♥ 9♣ Q♦ 9♠"),
                discardPile = cards("K♥"), // no interaction with this hand
                stockRemaining = 31,
                opponentHandSize = 10,
            )

        assertEquals(DrawPreference.STOCK, opponent().chooseDraw(view))
    }

    @Test
    fun `always knocks a gin hand, at any point in the stock`() {
        val ginHand =
            cards("A♠ 2♠ 3♠ 4♠ 7♥ 7♦ 7♣ 9♥ 9♦ 9♣ K♥")
        val view = OpponentView(ownHand = ginHand, discardPile = emptyList(), stockRemaining = 31, opponentHandSize = 10)

        assertTrue(opponent().shouldKnock(view))
    }

    @Test
    fun `knocks early only with a very strong hand, but relaxes as the stock depletes`() {
        // Run (0) + set (0) + one isolated 6-pip deadwood card, no competing meld options —
        // matches fastConfig's earlyKnockThreshold=1, i.e. HeuristicGinOpponent's own tuned value.
        val handWithDeadwoodSix =
            cards("2♠ 3♠ 4♠ 9♥ 9♦ 9♣ 6♦ K♣")
        val earlyView =
            OpponentView(ownHand = handWithDeadwoodSix, discardPile = emptyList(), stockRemaining = 31, opponentHandSize = 10)
        val lateView = earlyView.copy(stockRemaining = 3)

        assertFalse(opponent().shouldKnock(earlyView))
        assertTrue(opponent().shouldKnock(lateView))
    }

    @Test
    fun `a higher earlyKnockThreshold knocks more eagerly on the same middling hand`() {
        // Deadwood 4 (run + set + one isolated 4-pip card), early in the stock: patient at
        // MEDIUM's tuned threshold (1), but a more eager tier should already take it.
        val handWithDeadwoodFour =
            cards("2♠ 3♠ 4♠ 9♥ 9♦ 9♣ 4♦ K♣")
        val view = OpponentView(ownHand = handWithDeadwoodFour, discardPile = emptyList(), stockRemaining = 31, opponentHandSize = 10)
        val patient = MonteCarloGinOpponent(fastConfig.copy(earlyKnockThreshold = 1), Random(1))
        val eager = MonteCarloGinOpponent(fastConfig.copy(earlyKnockThreshold = 5), Random(1))

        assertFalse(patient.shouldKnock(view))
        assertTrue(eager.shouldKnock(view))
    }

    @Test
    fun `many seeds each terminate one hand with a sane result against the heuristic`() {
        val heuristic = HeuristicGinOpponent()
        for (seed in 1L..SEED_COUNT) {
            val monteCarlo = opponent(seed)
            var state = GinEngine.newMatch(seed)
            var ticks = 0
            while (!GinEngine.isHandOver(state)) {
                val before = state
                state = GinOpponentDriver.driveOneDecision(state, GinPlayer.PLAYER, monteCarlo)
                state = GinOpponentDriver.driveOneDecision(state, GinPlayer.OPPONENT, heuristic)
                ticks++
                check(ticks <= MAX_TICKS) { "seed $seed: did not terminate within $MAX_TICKS ticks" }
                check(state != before) { "seed $seed: no progress on tick $ticks" }
            }
        }
    }

    private companion object {
        // Kept small: this is a correctness/termination sweep, not a strength benchmark — that
        // kind of test runs far more seeds against the real config.
        const val SEED_COUNT = 15L
        const val MAX_TICKS = 400
    }
}
