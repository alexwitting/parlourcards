package com.cardgames.core.domain.ginrummy.ai

import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.HandOutcome
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * An AI-vs-AI simulation: both seats played by the same heuristic, over many seeds. "No illegal moves" isn't a separate assertion — [GinEngine.apply]
 * throws on any illegal move, and nothing here catches that, so an illegal move fails the test
 * on its own. What's asserted directly is termination (bounded move count, real progress
 * every tick — no silent hang) and sane scores (deadwood/points/winners all in range).
 */
class GinAiSimulationTest {
    private val ai = HeuristicGinOpponent()

    @Test
    fun `many seeds each terminate one hand with a sane result`() {
        for (seed in 1L..SEED_COUNT) {
            val state = playHandToCompletion(GinEngine.newMatch(seed))
            assertSaneOutcome(state, seed)
        }
    }

    @Test
    fun `a full match terminates with a sane winner and non-negative scores`() {
        var state = GinEngine.newMatch(seed = 4242L)
        var hands = 0
        var nextSeed = 10_000L

        while (!GinEngine.isMatchOver(state)) {
            state = playHandToCompletion(state)
            hands++
            check(hands <= MAX_HANDS) { "Match did not reach MatchOver within $MAX_HANDS hands" }
            if (!GinEngine.isMatchOver(state)) {
                state = GinEngine.dealNextHand(state, seed = nextSeed++)
            }
        }

        val winner = (state.phase as GinPhase.MatchOver).winner
        assertTrue(state.matchScores.getValue(winner) >= GinState.MATCH_TARGET)
        assertTrue(state.matchScores.values.all { it >= 0 })
        // Each decided hand awards its winner exactly one box; a blocked hand awards none.
        assertTrue(state.boxes.values.sum() <= hands)
    }

    private fun playHandToCompletion(start: GinState): GinState {
        var state = start
        var ticks = 0
        while (!GinEngine.isHandOver(state)) {
            val before = state
            state = GinOpponentDriver.driveOneDecision(state, GinPlayer.PLAYER, ai)
            state = GinOpponentDriver.driveOneDecision(state, GinPlayer.OPPONENT, ai)
            ticks++
            check(ticks <= MAX_TICKS_PER_HAND) { "Hand (seed ${start.seed}) did not terminate within $MAX_TICKS_PER_HAND ticks" }
            check(state != before) { "Hand (seed ${start.seed}) made no progress on tick $ticks — stuck" }
        }
        return state
    }

    private fun assertSaneOutcome(
        state: GinState,
        seed: Long,
    ) {
        when (val phase = state.phase) {
            is GinPhase.HandOver -> {
                val result = phase.result
                when (result.outcome) {
                    HandOutcome.BLOCKED -> {
                        assertTrue(result.winner == null, "seed $seed: blocked hand should have no winner")
                        assertTrue(result.pointsScored == 0, "seed $seed: blocked hand should score 0")
                    }
                    HandOutcome.KNOCK, HandOutcome.GIN, HandOutcome.UNDERCUT -> {
                        assertTrue(result.winner != null, "seed $seed: a decided hand needs a winner")
                        assertTrue(result.pointsScored > 0, "seed $seed: a decided hand must score positive points")
                        assertTrue(result.knockerDeadwood in 0..GinState.KNOCK_THRESHOLD, "seed $seed: knocker deadwood out of range")
                        assertTrue(result.defenderDeadwood >= 0, "seed $seed: defender deadwood can't be negative")
                    }
                }
            }
            is GinPhase.MatchOver -> {
                // An extreme single hand can theoretically cross the match target outright.
                assertTrue(
                    state.matchScores.getValue(phase.winner) >= GinState.MATCH_TARGET,
                    "seed $seed: match ended early with too few points",
                )
            }
            else -> error("seed $seed: loop only exits on HandOver/MatchOver, got $phase")
        }
    }

    private companion object {
        const val SEED_COUNT = 300L
        const val MAX_TICKS_PER_HAND = 400
        const val MAX_HANDS = 200
    }
}
