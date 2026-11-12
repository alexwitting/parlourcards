package com.cardgames.core.domain.solitaire

import com.cardgames.core.domain.game.SeedProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The "gives up and falls back" case takes real wall-clock time to run (no injectable
 * clock) — a deliberate trade-off over threading a fake clock through just for this test.
 */
class KlondikeDealerTest {
    private class SequenceSeedProvider(
        private val seeds: List<Long>,
    ) : SeedProvider {
        var callCount = 0
            private set

        override fun nextSeed(): Long {
            val seed = seeds[callCount.coerceAtMost(seeds.size - 1)]
            callCount++
            return seed
        }
    }

    @Test
    fun `accepts the first seed if it's already solvable within budget`() {
        // seed=8, draw-1 solves in ~16ms / a few hundred nodes (benchmarked) — comfortably inside
        // a single attempt, so the loop should never ask for a second seed. A generous deadline
        // (well beyond the production default) keeps this assertion from depending on how fast
        // the machine running it happens to be — the search itself is deterministic, so the
        // production-tight budget only ever added flakiness here, never real signal.
        val provider = SequenceSeedProvider(listOf(8L))

        val result =
            KlondikeDealer.dealWinnable(
                provider,
                drawCount = 1,
                attemptDeadlineMillis = GENEROUS_DEADLINE_MILLIS,
                overallBudgetMillis = GENEROUS_DEADLINE_MILLIS,
            )

        assertEquals(8L, result.seed)
        assertEquals(1, provider.callCount)
    }

    @Test
    fun `falls back to the last-dealt seed once the overall budget is exhausted`() {
        // seed=3, draw-1 didn't solve even in a single 3s attempt in benchmarking — it shouldn't
        // solve across KlondikeDealer's own (shorter, sliced) budget either. Repeating the same
        // hard seed means the fallback deal's seed is deterministic to assert on.
        val provider = SequenceSeedProvider(listOf(3L))

        val result = KlondikeDealer.dealWinnable(provider, drawCount = 1)

        assertEquals(3L, result.seed)
        assertTrue(provider.callCount > 1, "expected more than one attempt before giving up")
    }

    private companion object {
        const val GENEROUS_DEADLINE_MILLIS = 10_000L
    }
}
