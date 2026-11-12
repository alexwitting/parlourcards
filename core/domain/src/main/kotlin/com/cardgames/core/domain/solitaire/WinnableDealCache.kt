package com.cardgames.core.domain.solitaire

/**
 * Keeps a small queue of solver-verified winnable deals ready per draw count — most "New Game"
 * taps should hit this instead of paying [KlondikeDealer.dealWinnable]'s ~1-2s search on demand.
 * Implemented in `:core:data` (`InMemoryWinnableDealCache`) even though there's no real data
 * source involved, matching this project's usual interface-in-domain/impl-in-data split (same as
 * [SeedProvider]/[ClockProvider]).
 */
interface WinnableDealCache {
    /**
     * Pops a ready deal for [drawCount] if one exists, and kicks off a background top-up either
     * way (whether this returned a deal or not). Null means none was ready — the caller falls
     * back to an on-demand [KlondikeDealer.dealWinnable] call.
     */
    suspend fun take(drawCount: Int): KlondikeState?

    /** Idempotent — safe to call repeatedly; no-ops if the queue for [drawCount] is already full or already filling. */
    fun ensureFilling(drawCount: Int)
}
