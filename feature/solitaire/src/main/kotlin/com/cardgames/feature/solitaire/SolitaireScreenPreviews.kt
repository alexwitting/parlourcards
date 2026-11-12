package com.cardgames.feature.solitaire

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.solitaire.KlondikeEngine
import com.cardgames.core.domain.solitaire.KlondikeMove
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.domain.solitaire.TableauPile
import com.cardgames.core.ui.theme.AppTheme

// SolitaireContent takes no ViewModel, so these render with no Hilt graph, no device, and no
// build/install step — open this file in Android Studio and the preview pane updates live as
// layout code changes. Drag gestures and animations don't run in a static preview; everything
// about sizing, spacing, and bar placement does. PREVIEW_WIDTH_DP/HEIGHT_DP roughly match a
// modern tall phone (close to the Galaxy S24+ used for on-device review).

@Preview(name = "Mid-game — draw 1", showBackground = true, widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun SolitaireContentDrawOnePreview() {
    AppTheme {
        SolitaireContent(
            state = previewState(drawCount = 1),
            onIntent = {},
            onNavigateBack = {},
            onOpenSettings = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@Preview(name = "Mid-game — draw 3", showBackground = true, widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun SolitaireContentDrawThreePreview() {
    AppTheme {
        SolitaireContent(
            state = previewState(drawCount = 3),
            onIntent = {},
            onNavigateBack = {},
            onOpenSettings = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@Preview(name = "Won", showBackground = true, widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun SolitaireContentWonPreview() {
    AppTheme {
        SolitaireContent(
            state =
                SolitaireState(
                    game =
                        KlondikeState(
                            seed = 1L,
                            drawCount = 1,
                            tableau = List(7) { TableauPile(emptyList(), 0) },
                            foundations = Suit.entries.associateWith { KlondikeState.FOUNDATION_MAX },
                            stock = emptyList(),
                            waste = emptyList(),
                            moveCount = 118,
                        ),
                    isWon = true,
                    elapsedSeconds = 245,
                    solved =
                        SolvedSummary(
                            seconds = 245,
                            moves = 118,
                            drawCount = 1,
                            streak = 4,
                            bestSeconds = 198,
                            isNewBest = false,
                            gamesWon = 41,
                            gamesPlayed = 63,
                        ),
                ),
            onIntent = {},
            onNavigateBack = {},
            onOpenSettings = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

/** A fresh deal with one stock draw already played, so the waste pile isn't empty. */
private fun previewState(drawCount: Int): SolitaireState {
    val dealt = KlondikeEngine.deal(seed = 1L, drawCount = drawCount)
    val game = KlondikeEngine.apply(dealt, KlondikeMove.DealFromStock)
    return SolitaireState(game = game, canUndo = true, elapsedSeconds = 205)
}

private const val PREVIEW_WIDTH_DP = 412
private const val PREVIEW_HEIGHT_DP = 915
