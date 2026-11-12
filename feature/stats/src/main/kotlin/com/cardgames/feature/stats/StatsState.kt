package com.cardgames.feature.stats

import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.GameResult
import com.cardgames.core.domain.stats.GinSummary
import com.cardgames.core.domain.stats.SolitaireSummary
import com.cardgames.core.domain.stats.ginSummary
import com.cardgames.core.domain.stats.solitaireSummary

data class StatsState(
    /** Games finished on each day of this week, Monday first, both games together. */
    val week: List<Int> = List(DAYS_IN_WEEK) { 0 },
    /** Today's place in [week] (0 = Monday) — its bar is brass. */
    val todayIndex: Int = 0,
    val gin: GinSummary = emptyList<GameResult>().ginSummary(),
    val solitaire: SolitaireSummary = emptyList<GameResult>().solitaireSummary(),
    /** Every Gin match, newest first — the "All matches" list. */
    val ginMatches: List<MatchLine> = emptyList(),
    /** Solitaire's last few wins, newest first. */
    val recentWins: List<WinLine> = emptyList(),
)

/** One finished Gin match in the list: who against, the final scores, and when. */
data class MatchLine(
    val won: Boolean,
    val opponent: GinDifficulty?,
    val yourScore: Int,
    val theirScore: Int?,
    val day: String,
)

/** One solved deal: its time, when, and whether it's the best. */
data class WinLine(
    val seconds: Int,
    val day: String,
    val best: Boolean,
)

internal const val DAYS_IN_WEEK = 7
