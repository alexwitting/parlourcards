package com.cardgames.core.ui.chrome

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * The pause menu: a cream card over a dark scrim — an [overline] chip ("HAND 5 · VS ROSA"),
 * "Paused", Resume, the [actions], then the one red action, [leaveLabel]. A tap on the scrim or
 * back resumes.
 */
@Composable
fun PauseMenu(
    overline: String,
    actions: List<PauseAction>,
    leaveLabel: String,
    onResume: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CardGamesTheme.colors
    BackHandler(onBack = onResume)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.scrim.copy(alpha = chromeMetrics.pauseScrimAlpha))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onResume),
        contentAlignment = Alignment.Center,
    ) {
        DialogCard(
            maxWidth = chromeMetrics.pauseMaxWidth,
            contentPadding = PaddingValues(start = CardGamesTheme.spacing.xl, end = CardGamesTheme.spacing.xl, top = 22.dp, bottom = 10.dp),
        ) {
            Text(
                overline.uppercase(),
                color = colors.onPanel,
                style = CardGamesTheme.type.chip,
                modifier =
                    Modifier
                        .background(
                            colors.toolTile,
                            ChromeShapes.Pill,
                        ).padding(horizontal = CardGamesTheme.spacing.md, vertical = 5.dp),
            )
            Text("Paused", color = colors.onPanel, style = CardGamesTheme.type.resultTitle, modifier = Modifier.padding(top = 2.dp))
            PrimaryButton(text = "Resume", onClick = onResume, modifier = Modifier.fillMaxWidth().padding(top = CardGamesTheme.spacing.lg))
            Column(modifier = Modifier.padding(top = CardGamesTheme.spacing.sm)) {
                actions.forEach { action ->
                    MenuRow(action.label, action.icon, colors.onPanel, enabled = action.enabled, chevron = true, onClick = action.onClick)
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.toolTile))
                }
                MenuRow(leaveLabel, ParlourIcons.Exit, colors.bad, enabled = true, chevron = false, onClick = onLeave)
            }
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    icon: ImageVector,
    ink: Color,
    enabled: Boolean,
    chevron: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else DISABLED_ROW_ALPHA)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
        Text(label, color = ink, style = CardGamesTheme.type.rowLabel, modifier = Modifier.weight(1f))
        if (chevron) {
            Icon(
                ParlourIcons.ChevronRight,
                contentDescription = null,
                tint = CardGamesTheme.colors.onPanelMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The theme's scrim at 72% — darker than a sheet's, so the menu reads as a stop. */
private const val DISABLED_ROW_ALPHA = 0.4f
