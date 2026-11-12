package com.cardgames.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.GameResult
import com.cardgames.core.domain.repository.GinMatchRecord
import com.cardgames.core.domain.repository.RecordedResult
import com.cardgames.core.ui.theme.AppTheme
import java.time.LocalDate
import java.time.ZoneOffset

// StatsContent takes no ViewModel, so these render with no Hilt graph — same rationale as
// SolitaireScreenPreviews.kt.

@Preview(name = "Fresh install", showBackground = true, widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun StatsContentFreshPreview() {
    AppTheme { StatsContent(state = StatsState(), onNavigateBack = {}, onOpenGame = {}) }
}

@Preview(name = "Played", showBackground = true, widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun StatsContentPlayedPreview() {
    AppTheme { StatsContent(state = SampleStatsState, onNavigateBack = {}, onOpenGame = {}) }
}

@Preview(name = "Gin Rummy", showBackground = true, widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun GinStatsPreview() {
    AppTheme { GameStatsContent(GameId.GIN_RUMMY, SampleStatsState, onNavigateBack = {}) }
}

/** The fixed "today" the sample results are spread back from. */
internal val SampleToday: LocalDate = LocalDate.of(2026, 10, 29)

/** A week of play in both games — shared with the screenshot and smoke tests. */
internal val SampleStatsState: StatsState by lazy {
    val day = { daysAgo: Long ->
        SampleToday
            .minusDays(daysAgo)
            .atTime(20, 0)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }
    val gin =
        listOf(
            Triple(GinDifficulty.EASY, true, 6L),
            Triple(GinDifficulty.MEDIUM, false, 3L),
            Triple(GinDifficulty.MEDIUM, true, 1L),
            Triple(GinDifficulty.HARD, false, 0L),
        ).map { (rival, won, ago) ->
            RecordedResult(
                GameResult(
                    GameId.GIN_RUMMY,
                    won = won,
                    durationSeconds = 900,
                    score = if (won) 104 else 87,
                    gin =
                        GinMatchRecord(
                            opponent = rival,
                            opponentScore = if (won) 48 else 101,
                            gins = 1,
                            undercuts = if (won) 1 else 0,
                            bestHand = if (won) 47 else 22,
                            knocks = 3,
                            knockDeadwood = 13,
                            hands = 6,
                        ),
                ),
                day(ago),
            )
        }
    val solitaire =
        listOf(298 to 5L, 371 to 4L, null to 2L, 412 to 1L, null to 1L, 333 to 0L).map { (seconds, ago) ->
            RecordedResult(
                GameResult(GameId.KLONDIKE, won = seconds != null, durationSeconds = seconds ?: 600, moves = 118, drawCount = 1),
                day(ago),
            )
        }
    statsState(gin, solitaire, SampleToday, ZoneOffset.UTC)
}

private const val PREVIEW_WIDTH_DP = 412
private const val PREVIEW_HEIGHT_DP = 915
