package com.cardgames.feature.ginrummy

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.ui.theme.AppTheme

// GinContent takes no ViewModel, so these render with no Hilt graph, no device, and no
// build/install step — same rationale as SolitaireScreenPreviews.kt.

@Preview(name = "Fresh hand", showBackground = true, widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun GinContentFreshHandPreview() {
    AppTheme {
        GinContent(
            state = GinUiState(game = GinEngine.newMatch(seed = 1L)),
            onIntent = {},
            onNavigateBack = {},
            onOpenSettings = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

private const val PREVIEW_WIDTH_DP = 412
private const val PREVIEW_HEIGHT_DP = 915
