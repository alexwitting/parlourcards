package com.cardgames.core.domain.stats

import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.GameResult
import com.cardgames.core.domain.repository.RecordedResult
import com.cardgames.core.domain.repository.toGameStats
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// The stats pages' figures, worked out from the recorded results — pure, so Home, match setup and
// Stats all count the same way.

/** Matches (or deals) won and lost. */
data class Record(
    val wins: Int,
    val losses: Int,
) {
    val played: Int
        get() = wins + losses
}

/** The player's record against [opponent] across [this] Gin results. */
fun List<GameResult>.recordAgainst(opponent: GinDifficulty): Record {
    val against = filter { it.gin?.opponent == opponent }
    return Record(wins = against.count { it.won }, losses = against.count { !it.won })
}

/** Gin's stats page: the headline, the four tiles, and the record per opponent. */
data class GinSummary(
    val matches: Int,
    val wins: Int,
    val bestStreak: Int,
    val gins: Int,
    val undercuts: Int,
    val bestHand: Int,
    /** Average deadwood held when going out; null before the first knock. */
    val averageKnockDeadwood: Double?,
    val byOpponent: Map<GinDifficulty, Record>,
) {
    val winPercent: Int
        get() = percent(wins, matches)
}

/** [this] Gin results (oldest first) summed up. */
fun List<GameResult>.ginSummary(): GinSummary {
    val records = mapNotNull { it.gin }
    val knocks = records.sumOf { it.knocks }
    return GinSummary(
        matches = size,
        wins = count { it.won },
        bestStreak = toGameStats().bestStreak,
        gins = records.sumOf { it.gins },
        undercuts = records.sumOf { it.undercuts },
        bestHand = records.maxOfOrNull { it.bestHand } ?: 0,
        averageKnockDeadwood = if (knocks == 0) null else records.sumOf { it.knockDeadwood }.toDouble() / knocks,
        byOpponent = GinDifficulty.entries.associateWith { recordAgainst(it) },
    )
}

/** One draw mode's line on Solitaire's stats page. */
data class DrawModeSummary(
    val solved: Int,
    val played: Int,
    val bestSeconds: Int?,
    val averageSeconds: Int?,
)

/** Solitaire's stats page: the headline, the four tiles, and each draw mode. */
data class SolitaireSummary(
    val deals: Int,
    val solved: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val bestSeconds: Int?,
    val averageSeconds: Int?,
    val fewestMoves: Int?,
    val byDrawMode: Map<Int, DrawModeSummary>,
) {
    val solvedPercent: Int
        get() = percent(solved, deals)
}

/** [this] Solitaire results (oldest first) summed up; times are over solved deals only. */
fun List<GameResult>.solitaireSummary(): SolitaireSummary {
    val stats = toGameStats()
    val wins = filter { it.won }
    return SolitaireSummary(
        deals = size,
        solved = wins.size,
        currentStreak = stats.currentStreak,
        bestStreak = stats.bestStreak,
        bestSeconds = stats.bestDurationSeconds,
        averageSeconds = wins.averageSeconds(),
        fewestMoves = stats.fewestMoves,
        byDrawMode =
            DRAW_MODES.associateWith { mode ->
                val inMode = filter { it.drawCount == mode }
                val solvedInMode = inMode.filter { it.won }
                DrawModeSummary(
                    solved = solvedInMode.size,
                    played = inMode.size,
                    bestSeconds = solvedInMode.minOfOrNull { it.durationSeconds },
                    averageSeconds = solvedInMode.averageSeconds(),
                )
            },
    )
}

/** How many results ended on each day of the week holding [today], Monday first (seven counts). */
fun List<RecordedResult>.perDayThisWeek(
    today: LocalDate,
    zone: ZoneId,
): List<Int> {
    val monday = today.with(DayOfWeek.MONDAY)
    val days = map { Instant.ofEpochMilli(it.endedAtEpochMillis).atZone(zone).toLocalDate() }
    return (0 until DAYS_IN_WEEK).map { offset -> days.count { it == monday.plusDays(offset.toLong()) } }
}

private fun List<GameResult>.averageSeconds(): Int? = if (isEmpty()) null else sumOf { it.durationSeconds } / size

private fun percent(
    part: Int,
    whole: Int,
): Int = if (whole == 0) 0 else part * PERCENT / whole

private val DRAW_MODES = listOf(1, 3)
private const val DAYS_IN_WEEK = 7
private const val PERCENT = 100
