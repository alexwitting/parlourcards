package com.cardgames.core.testing

import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.domain.solitaire.WinnableDealCache

/**
 * In-memory [WinnableDealCache]; seed [queued] directly to simulate a warm cache.
 * [ensureFilling] is a no-op by default — tests aren't surprised by a background fill they didn't
 * ask for (same "sociable fake, not a mock" convention as every other Fake* here).
 */
class FakeWinnableDealCache : WinnableDealCache {
    val queued = mutableMapOf<Int, ArrayDeque<KlondikeState>>()
    var ensureFillingCalls = mutableListOf<Int>()
        private set

    override suspend fun take(drawCount: Int): KlondikeState? = queued[drawCount]?.removeFirstOrNull()

    override fun ensureFilling(drawCount: Int) {
        ensureFillingCalls += drawCount
    }
}
