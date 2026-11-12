package com.cardgames.core.data.solitaire

import com.cardgames.core.testing.FakeSeedProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * [Dispatchers.Unconfined] (not a `TestDispatcher`) so `ensureFilling`'s background work runs
 * synchronously on the calling thread — same philosophy `SolitaireViewModelTest` already uses
 * for this exact kind of solver-backed async work.
 */
class InMemoryWinnableDealCacheTest {
    private val seedProvider = FakeSeedProvider(seed = 42L)
    private val cache = InMemoryWinnableDealCache(seedProvider, Dispatchers.Unconfined)

    @Test
    fun `take is null before anything has ever been filled`() =
        runTest {
            assertNull(cache.take(drawCount = 1))
        }

    @Test
    fun `take returns a ready deal once ensureFilling has run, and re-triggers a top-up`() =
        runTest {
            cache.ensureFilling(drawCount = 1)

            val first = cache.take(drawCount = 1)
            assertEquals(42L, first?.seed)

            // Draining doesn't leave the queue empty for long — ensureFilling ran again inside
            // take() itself, and FakeSeedProvider always hands back the same seed.
            val second = cache.take(drawCount = 1)
            assertEquals(42L, second?.seed)
        }

    @Test
    fun `separate draw counts get separate queues`() =
        runTest {
            cache.ensureFilling(drawCount = 1)
            cache.ensureFilling(drawCount = 3)

            assertEquals(1, cache.take(drawCount = 1)?.drawCount)
            assertEquals(3, cache.take(drawCount = 3)?.drawCount)
        }
}
