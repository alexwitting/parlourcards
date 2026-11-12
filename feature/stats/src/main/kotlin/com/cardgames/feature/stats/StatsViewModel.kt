package com.cardgames.feature.stats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.repository.RecordedResult
import com.cardgames.core.domain.repository.StatsRepository
import com.cardgames.core.domain.stats.ginSummary
import com.cardgames.core.domain.stats.perDayThisWeek
import com.cardgames.core.domain.stats.solitaireSummary
import com.cardgames.core.domain.time.ClockProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

/** The stats pages — the week, and each game's figures — worked out from every recorded result. */
@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        statsRepository: StatsRepository,
        private val clockProvider: ClockProvider,
    ) : ViewModel() {
        private val _state = MutableStateFlow(StatsState())
        val state: StateFlow<StatsState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                combine(
                    statsRepository.observeResults(GameId.GIN_RUMMY),
                    statsRepository.observeResults(GameId.KLONDIKE),
                ) { gin, solitaire -> statsState(gin, solitaire, clockProvider.today(), ZoneId.systemDefault()) }
                    // An uncaught exception here would silently end the flow forever (see CLAUDE.md);
                    // the screen keeps its last state and stats just stop updating.
                    .catch { Log.e(TAG, "Stats data stream failed", it) }
                    .collect { _state.value = it }
            }
        }

        private companion object {
            const val TAG = "StatsViewModel"
        }
    }

/** The pages' state from both games' results (oldest first). */
internal fun statsState(
    gin: List<RecordedResult>,
    solitaire: List<RecordedResult>,
    today: LocalDate,
    zone: ZoneId,
): StatsState {
    val dayOf = { result: RecordedResult -> dayLabel(Instant.ofEpochMilli(result.endedAtEpochMillis).atZone(zone).toLocalDate(), today) }
    val wins = solitaire.filter { it.result.won }
    val best = wins.minOfOrNull { it.result.durationSeconds }
    return StatsState(
        week = (gin + solitaire).perDayThisWeek(today, zone),
        todayIndex = today.dayOfWeek.value - 1,
        gin = gin.map { it.result }.ginSummary(),
        solitaire = solitaire.map { it.result }.solitaireSummary(),
        ginMatches =
            gin.asReversed().map { recorded ->
                MatchLine(
                    won = recorded.result.won,
                    opponent = recorded.result.gin?.opponent,
                    yourScore = recorded.result.score ?: 0,
                    theirScore = recorded.result.gin?.opponentScore,
                    day = dayOf(recorded),
                )
            },
        recentWins =
            wins.asReversed().take(RECENT_WINS).map { recorded ->
                WinLine(recorded.result.durationSeconds, dayOf(recorded), best = recorded.result.durationSeconds == best)
            },
    )
}

/** "Today", "Yesterday", "Mon" within the week, else "12 Oct". */
internal fun dayLabel(
    day: LocalDate,
    today: LocalDate,
): String {
    val daysAgo = ChronoUnit.DAYS.between(day, today)
    return when {
        daysAgo == 0L -> "Today"
        daysAgo == 1L -> "Yesterday"
        daysAgo in 2 until DAYS_IN_WEEK -> day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        else -> "${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
    }
}

private const val RECENT_WINS = 4
