package com.cardgames.core.domain.game

/**
 * The single seam for "give me a fresh deal seed". Engines never call [kotlin.random.Random]
 * themselves — ViewModels obtain a seed here and pass it to the engine's deal function, so tests
 * (and restart-same-deal) can pin the exact shuffle.
 */
interface SeedProvider {
    fun nextSeed(): Long
}
