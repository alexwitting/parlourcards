package com.cardgames.feature.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.GameStats
import com.cardgames.core.domain.stats.Record
import com.cardgames.core.ui.theme.AppTheme

// HomeContent takes no ViewModel, so these render with no Hilt graph, no device, and no
// build/install step — same rationale as SolitaireScreenPreviews.kt.

@Preview(name = "Fresh install", showBackground = true, widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun HomeContentFreshPreview() {
    AppTheme {
        HomeContent(state = HomeState(), onIntent = {}, onOpenSettings = {}, onOpenStats = {}, greeting = "Good evening")
    }
}

@Preview(name = "Match to continue + stats", showBackground = true, widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun HomeContentWithStatsPreview() {
    AppTheme {
        HomeContent(state = SampleHomeState, onIntent = {}, onOpenSettings = {}, onOpenStats = {}, greeting = "Good evening")
    }
}

/** A Gin match to continue and both games played — shared with the screenshot test. */
internal val SampleHomeState =
    HomeState(
        continueGame =
            ContinueSummary.Gin(
                handNumber = 7,
                opponent = GinDifficulty.MEDIUM,
                yourScore = 80,
                theirScore = 48,
                target = 100,
                yourTurn = true,
            ),
        stats =
            mapOf(
                GameId.KLONDIKE to GameStats(gamesPlayed = 9, gamesWon = 2, bestDurationSeconds = 298),
                GameId.GIN_RUMMY to GameStats(gamesPlayed = 22, gamesWon = 12),
            ),
        ginRecord = GinDifficulty.MEDIUM to Record(wins = 12, losses = 10),
    )

private const val PREVIEW_WIDTH_DP = 412
private const val PREVIEW_HEIGHT_DP = 915
