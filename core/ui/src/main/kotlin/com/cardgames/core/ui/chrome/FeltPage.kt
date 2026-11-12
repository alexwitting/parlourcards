package com.cardgames.core.ui.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * A full page on the felt — Settings, a game's settings, Stats: a header row of the back button
 * and the [title] in Spectral (with an [overline] above it on a sub-page — "SETTINGS" over "Gin
 * Rummy"), then [content] scrolling in a column of groups.
 */
@Composable
fun FeltPage(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    overline: String? = null,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardGamesTheme.colors
    Box(modifier = modifier.fillMaxSize()) {
        TableBackground(modifier = Modifier.matchParentSize())
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CardGamesTheme.spacing.lg)
                    .padding(top = 6.dp, bottom = CardGamesTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.md)) {
                TableIconButton(ParlourIcons.Back, contentDescription = "Back", onClick = onNavigateBack)
                Column {
                    overline?.let { Text(it.uppercase(), color = colors.onTableMuted, style = CardGamesTheme.type.overline) }
                    Text(title, color = colors.onTable, style = CardGamesTheme.type.heading)
                }
            }
            content()
        }
        snackbarHostState?.let { SnackbarHost(it, modifier = Modifier.align(Alignment.BottomCenter)) }
    }
}
