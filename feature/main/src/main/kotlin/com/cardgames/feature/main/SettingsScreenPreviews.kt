package com.cardgames.feature.main

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.ui.theme.AppTheme

// SettingsContent takes no ViewModel, so these render with no Hilt graph, no device, and no
// build/install step — same rationale as SolitaireScreenPreviews.kt.

@Preview(name = "Default", showBackground = true, widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun SettingsContentDefaultPreview() {
    AppTheme {
        SettingsContent(
            state = SettingsState(),
            onIntent = {},
            onNavigateBack = {},
            onOpenGame = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@Preview(name = "Gin Rummy", showBackground = true, widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun GinSettingsPreview() {
    AppTheme {
        GameSettingsContent(
            game = GameId.GIN_RUMMY,
            state = SettingsState(),
            onIntent = {},
            onNavigateBack = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

private const val PREVIEW_WIDTH_DP = 412
private const val PREVIEW_HEIGHT_DP = 915
