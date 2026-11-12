package com.cardgames.core.data.solitaire

import com.cardgames.core.domain.game.SeedProvider
import com.cardgames.core.domain.solitaire.KlondikeDealer
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.domain.solitaire.WinnableDealCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory only — lives for the app process's lifetime as a `@Singleton`, same as
 * `RandomSeedProvider`; no explicit teardown, nothing persisted across process death (a fresh
 * queue just refills itself on next use). [dispatcher] is a real thread-pool dispatcher in
 * production, so genuine concurrent access to [ready]/[filling] is possible — both are guarded
 * by [mutex].
 */
@Singleton
internal class InMemoryWinnableDealCache
    @Inject
    constructor(
        private val seedProvider: SeedProvider,
        private val dispatcher: CoroutineDispatcher,
    ) : WinnableDealCache {
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val mutex = Mutex()
        private val ready = mutableMapOf<Int, ArrayDeque<KlondikeState>>()
        private val filling = mutableSetOf<Int>()

        override suspend fun take(drawCount: Int): KlondikeState? {
            val deal = mutex.withLock { ready[drawCount]?.removeFirstOrNull() }
            ensureFilling(drawCount)
            return deal
        }

        override fun ensureFilling(drawCount: Int) {
            scope.launch {
                val shouldFill =
                    mutex.withLock {
                        if (drawCount in filling) {
                            false
                        } else {
                            filling += drawCount
                            true
                        }
                    }
                if (!shouldFill) return@launch
                try {
                    while (mutex.withLock { (ready[drawCount]?.size ?: 0) } < TARGET_SIZE) {
                        val deal = KlondikeDealer.dealWinnable(seedProvider, drawCount)
                        mutex.withLock { ready.getOrPut(drawCount) { ArrayDeque() }.addLast(deal) }
                    }
                } finally {
                    mutex.withLock { filling -= drawCount }
                }
            }
        }

        private companion object {
            /** A concrete steady-state target, tunable. */
            const val TARGET_SIZE = 2
        }
    }
