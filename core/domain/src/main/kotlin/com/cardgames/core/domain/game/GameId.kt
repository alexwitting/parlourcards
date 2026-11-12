package com.cardgames.core.domain.game

/**
 * Stable identifier for each game in the app. Persisted (saved games, stats rows), so entry
 * names must never change once shipped; adding a game is additive.
 */
enum class GameId {
    KLONDIKE,
    GIN_RUMMY,
}
