package com.cardgames.core.domain.ginrummy.ai

/**
 * Builds the [GinOpponent] for one match at a given [GinDifficulty]. `GinViewModel` asks for a
 * fresh opponent at the start of every match — not once per screen — so a difficulty change in
 * Settings takes effect from the next match, including one started with New match on the same
 * screen. Which algorithm backs each tier stays a DI decision (see `GinAiModule`), same as
 * [GinOpponent] itself.
 */
fun interface GinOpponentFactory {
    fun create(difficulty: GinDifficulty): GinOpponent
}
