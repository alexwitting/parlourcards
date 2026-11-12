package com.cardgames.feature.ginrummy.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardgames.core.domain.ginrummy.GinMatchRules
import com.cardgames.core.domain.ginrummy.GinScoring
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.HintLimit
import com.cardgames.core.domain.stats.Record
import com.cardgames.core.ui.chrome.Avatar
import com.cardgames.core.ui.chrome.ChromeShapes
import com.cardgames.core.ui.chrome.SettingsDivider
import com.cardgames.core.ui.chrome.SettingsGroup
import com.cardgames.core.ui.chrome.SettingsToggleRow
import com.cardgames.core.ui.chrome.TableBackground
import com.cardgames.core.ui.chrome.TableChip
import com.cardgames.core.ui.chrome.TableIconButton
import com.cardgames.core.ui.chrome.TablePrimaryButton
import com.cardgames.core.ui.chrome.rememberConfirmAbandon
import com.cardgames.core.ui.chrome.rivalAvatarColors
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.text.initials
import com.cardgames.core.ui.text.levelName
import com.cardgames.core.ui.text.opponentName
import com.cardgames.core.ui.text.shortLine
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.feature.ginrummy.result.ResultBody
import com.cardgames.feature.ginrummy.result.ginResultMetrics
import kotlinx.coroutines.launch

/** New match: pick a rival, a target and the house rules, then deal. */
@Composable
fun GinSetupScreen(
    onNavigateBack: () -> Unit,
    onStartMatch: () -> Unit,
    onContinueMatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: GinSetupViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                GinSetupEffect.StartMatch -> onStartMatch()
                is GinSetupEffect.ShowSnackbar -> launch { snackbarHostState.showSnackbar(effect.message) }
            }
        }
    }
    GinSetupContent(
        state,
        viewModel::onIntent,
        onNavigateBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onContinueMatch = onContinueMatch,
    )
}

/** The setup page's UI, with no ViewModel — what previews and tests render. */
@Composable
internal fun GinSetupContent(
    state: GinSetupState,
    onIntent: (GinSetupIntent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onContinueMatch: () -> Unit = {},
) {
    val colors = CardGamesTheme.colors
    val options = state.options
    val deal =
        rememberConfirmAbandon(
            needsConfirmation = state.matchInProgress,
            title = "Start a new match?",
            confirmLabel = "New match",
            // "Keep playing" here means back to the match in progress, not just staying on setup.
            onKept = onContinueMatch,
        ) { onIntent(GinSetupIntent.Deal) }
    Box(modifier = modifier.fillMaxSize()) {
        TableBackground(modifier = Modifier.matchParentSize())
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = CardGamesTheme.spacing.md, end = CardGamesTheme.spacing.md, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TableIconButton(ParlourIcons.Back, contentDescription = "Back", onClick = onNavigateBack)
                Text(
                    "New match",
                    color = colors.onTable,
                    style = CardGamesTheme.type.rowLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(TOP_BUTTON))
            }
            ResultBody(
                modifier = Modifier.padding(horizontal = ginResultMetrics.setupPadding),
                verticalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm),
            ) {
                Text("Gin Rummy", color = colors.onTable, style = CardGamesTheme.type.resultTitle)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GinDifficulty.entries.forEach { rival ->
                        RivalCard(rival, state.records[rival], selected = rival == options.difficulty, modifier = Modifier.weight(1f)) {
                            onIntent(GinSetupIntent.PickOpponent(rival))
                        }
                    }
                }
                Text("Play to", color = colors.onTable, style = CardGamesTheme.type.subhead)
                TargetChoice(options.rules.target) { onIntent(GinSetupIntent.PlayTo(it)) }
                Text("House rules", color = colors.onTable, style = CardGamesTheme.type.subhead)
                SettingsGroup(title = null) {
                    SettingsToggleRow(
                        "Gin bonus",
                        checked = options.rules.ginBonus,
                        caption = "+${GinScoring.GIN_BONUS} for going out with zero deadwood",
                        onCheckedChange = { onIntent(GinSetupIntent.GinBonus(it)) },
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        "Big Gin",
                        checked = options.rules.bigGin,
                        caption = "+${GinScoring.BIG_GIN_BONUS} for gin using all 11 cards",
                        onCheckedChange = { onIntent(GinSetupIntent.BigGin(it)) },
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        "Hints",
                        checked = options.hints != HintLimit.OFF,
                        caption = options.hints.allowance?.let { if (it == 0) "Off" else "$it per hand" } ?: "Unlimited",
                        onCheckedChange = { onIntent(GinSetupIntent.Hints(it)) },
                    )
                }
            }
            Column(
                modifier =
                    Modifier
                        .padding(
                            horizontal = ginResultMetrics.setupPadding,
                        ).padding(top = CardGamesTheme.spacing.sm, bottom = CardGamesTheme.spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm),
            ) {
                TablePrimaryButton(text = "Deal the cards", onClick = deal, modifier = Modifier.fillMaxWidth())
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** A rival on a soft card — cream with a brass ring and check when chosen. */
@Composable
private fun RivalCard(
    rival: GinDifficulty,
    record: Record?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = CardGamesTheme.colors
    val (fill, ink) = rivalAvatarColors(rival)
    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (selected) Modifier.border(3.dp, colors.accent, ChromeShapes.Soft) else Modifier)
                    .clip(ChromeShapes.Soft)
                    .background(if (selected) colors.panel else colors.surfaceOnTable)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                    .padding(
                        start = CardGamesTheme.spacing.sm,
                        end = CardGamesTheme.spacing.sm,
                        top = CardGamesTheme.spacing.md,
                        bottom = CardGamesTheme.spacing.sm,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.xs),
        ) {
            Avatar(rival.initials, fill = fill, ink = ink, size = ginResultMetrics.rivalAvatar, style = CardGamesTheme.type.actionLabel)
            Text(rival.opponentName, color = if (selected) colors.onPanel else colors.onTable, style = CardGamesTheme.type.rowLabel)
            TableChip(
                rival.levelName,
                fill = if (selected) colors.toolTile else null,
                ink = if (selected) colors.onPanel else null,
            )
            record?.let {
                Text(
                    it.shortLine(),
                    color = if (selected) colors.onPanelMuted else colors.onTableMuted,
                    style = CardGamesTheme.type.toolLabel,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (selected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(CHECK_BADGE)
                        .background(colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ParlourIcons.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Play to 50 / 100 / 250 on a soft track; the choice is brass on its solid base. */
@Composable
private fun TargetChoice(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = CardGamesTheme.colors
    val base = colors.actionBase
    Row(
        modifier = Modifier.fillMaxWidth().background(colors.surfaceOnTable, ChromeShapes.Tile).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GinMatchRules.TARGETS.forEach { target ->
            val chosen = target == selected
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .drawBehind {
                            if (chosen) {
                                val radius = CornerRadius(ChromeShapes.Icon.topStart.toPx(size, this))
                                drawRoundRect(base, topLeft = Offset(0f, TARGET_BASE.toPx()), size = size, cornerRadius = radius)
                            }
                        }.clip(ChromeShapes.Icon)
                        .background(if (chosen) colors.action else Color.Transparent)
                        .selectable(selected = chosen, role = Role.RadioButton) { onSelect(target) }
                        .padding(vertical = CardGamesTheme.spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val ink = if (chosen) colors.onAction else colors.onTable
                Text("$target", color = ink, style = CardGamesTheme.type.actionLabel)
                Text(targetName(target), color = if (chosen) ink else colors.onTableMuted, style = CardGamesTheme.type.toolLabel)
            }
        }
    }
}

/** "Quick", "Classic", "Marathon". */
private fun targetName(target: Int): String =
    when {
        target < GinMatchRules().target -> "Quick"
        target == GinMatchRules().target -> "Classic"
        else -> "Marathon"
    }

private val TOP_BUTTON = 42.dp
private val CHECK_BADGE = 24.dp
private val TARGET_BASE = 3.dp
