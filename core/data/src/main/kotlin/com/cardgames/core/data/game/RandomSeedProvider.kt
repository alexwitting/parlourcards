package com.cardgames.core.data.game

import com.cardgames.core.domain.game.SeedProvider
import javax.inject.Inject
import kotlin.random.Random

class RandomSeedProvider
    @Inject
    constructor() : SeedProvider {
        override fun nextSeed(): Long = Random.nextLong()
    }
