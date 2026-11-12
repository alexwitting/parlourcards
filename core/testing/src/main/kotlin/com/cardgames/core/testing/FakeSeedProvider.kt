package com.cardgames.core.testing

import com.cardgames.core.domain.game.SeedProvider

/** Hands out [seed] every time (set it between calls to vary deals deterministically). */
class FakeSeedProvider(
    var seed: Long = 42L,
) : SeedProvider {
    override fun nextSeed(): Long = seed
}
