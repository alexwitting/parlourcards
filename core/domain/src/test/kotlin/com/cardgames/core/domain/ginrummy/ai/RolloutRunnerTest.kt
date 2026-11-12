package com.cardgames.core.domain.ginrummy.ai

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.HandOutcome
import com.cardgames.core.domain.ginrummy.HandResult
import com.cardgames.core.testing.cards
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.random.Random

class RolloutRunnerTest {
    private val policy = HeuristicGinOpponent()

    /** A minimal, otherwise-irrelevant-field state — only [phase] (and hands, for the fallback branch) matter here. */
    private fun stateWithPhase(
        phase: GinPhase,
        playerHand: List<Card> = emptyList(),
        opponentHand: List<Card> = emptyList(),
    ) = GinState(
        seed = 0L,
        handNumber = 1,
        dealer = GinPlayer.PLAYER,
        playerHand = playerHand,
        opponentHand = opponentHand,
        stock = emptyList(),
        discardPile = emptyList(),
        phase = phase,
        matchScores = GinPlayer.entries.associateWith { 0 },
        boxes = GinPlayer.entries.associateWith { 0 },
    )

    @Test
    fun `many seeds each roll out to a hand-over result within a generous tick cap, never throwing`() {
        for (seed in 1L..SEED_COUNT) {
            val result = RolloutRunner.rollout(GinEngine.newMatch(seed), policy, maxTicks = GENEROUS_TICK_CAP)
            assert(GinEngine.isHandOver(result)) { "seed $seed: didn't reach hand-over within $GENEROUS_TICK_CAP ticks" }
        }
    }

    @Test
    fun `a small tick cap really does cut the rollout short`() {
        val result = RolloutRunner.rollout(GinEngine.newMatch(seed = 7L), policy, maxTicks = 2)

        assertFalse(GinEngine.isHandOver(result), "2 ticks can't possibly finish a whole hand")
    }

    @Test
    fun `sampled hidden worlds also roll out cleanly end to end`() {
        for (seed in 1L..INTEGRATION_SEED_COUNT) {
            val real = GinEngine.newMatch(seed)
            val view = real.opponentView(GinPlayer.PLAYER)
            val phase = GinPhase.AwaitingDraw(GinPlayer.PLAYER)
            val sampled = HiddenWorldSampler.sample(view, phase, Random(seed))

            val result = RolloutRunner.rollout(sampled, policy, maxTicks = GENEROUS_TICK_CAP)
            assert(GinEngine.isHandOver(result)) { "seed $seed: sampled world didn't reach hand-over" }
        }
    }

    @Test
    fun `scores a knock win for PLAYER as positive points`() {
        val result = HandResult(HandOutcome.KNOCK, winner = GinPlayer.PLAYER, pointsScored = 12, knockerDeadwood = 3, defenderDeadwood = 15)
        val state = stateWithPhase(GinPhase.HandOver(result))

        assertEquals(12, RolloutRunner.scoreForPlayer(state))
    }

    @Test
    fun `scores an opponent win as negative points`() {
        val result = HandResult(HandOutcome.GIN, winner = GinPlayer.OPPONENT, pointsScored = 30, knockerDeadwood = 0, defenderDeadwood = 18)
        val state = stateWithPhase(GinPhase.HandOver(result))

        assertEquals(-30, RolloutRunner.scoreForPlayer(state))
    }

    @Test
    fun `scores a blocked hand as zero`() {
        val result = HandResult(HandOutcome.BLOCKED, winner = null, pointsScored = 0, knockerDeadwood = 0, defenderDeadwood = 0)
        val state = stateWithPhase(GinPhase.HandOver(result))

        assertEquals(0, RolloutRunner.scoreForPlayer(state))
    }

    @Test
    fun `scores a PLAYER match win as the flat match-win score`() {
        val state = stateWithPhase(GinPhase.MatchOver(GinPlayer.PLAYER))

        assertEquals(MATCH_WIN_SCORE, RolloutRunner.scoreForPlayer(state))
    }

    @Test
    fun `scores an OPPONENT match win as the negative flat match-win score`() {
        val state = stateWithPhase(GinPhase.MatchOver(GinPlayer.OPPONENT))

        assertEquals(-MATCH_WIN_SCORE, RolloutRunner.scoreForPlayer(state))
    }

    @Test
    fun `a horizon-cut mid-hand state falls back to the current deadwood margin`() {
        // PLAYER: a complete run, 0 deadwood. OPPONENT: three isolated cards, all deadwood.
        val playerHand = cards("4♠ 5♠ 6♠")
        val opponentHand = cards("K♥ Q♣ 2♦")
        val state = stateWithPhase(GinPhase.AwaitingDraw(GinPlayer.PLAYER), playerHand, opponentHand)

        // Opponent deadwood (10 + 10 + 2 = 22) minus player deadwood (0) = 22, favoring PLAYER.
        assertEquals(22, RolloutRunner.scoreForPlayer(state))
    }

    private companion object {
        const val SEED_COUNT = 100L
        const val INTEGRATION_SEED_COUNT = 30L
        const val GENEROUS_TICK_CAP = 400
        const val MATCH_WIN_SCORE = 1000
    }
}
