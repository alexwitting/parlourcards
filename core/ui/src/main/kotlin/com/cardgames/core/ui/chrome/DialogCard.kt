package com.cardgames.core.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * The cream card a popup sits on (the pause menu, a "start over?" confirm): the spacing scale's
 * [ChromeMetrics.dialogMargin] from the screen's edges, no wider than [maxWidth], raised on a soft
 * shadow. Taps on it stay on it, so they never reach the scrim behind.
 */
@Composable
internal fun DialogCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(CardGamesTheme.spacing.xl),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    maxWidth: Dp = chromeMetrics.dialogMaxWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .padding(horizontal = chromeMetrics.dialogMargin)
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .shadow(DIALOG_ELEVATION, ChromeShapes.Soft)
                .background(CardGamesTheme.colors.panel, ChromeShapes.Soft)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

private val DIALOG_ELEVATION = 20.dp
