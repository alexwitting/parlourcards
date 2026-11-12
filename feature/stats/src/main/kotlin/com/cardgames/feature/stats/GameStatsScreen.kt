package com.cardgames.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.stats.GinSummary
import com.cardgames.core.domain.stats.SolitaireSummary
import com.cardgames.core.ui.chrome.Avatar
import com.cardgames.core.ui.chrome.ChromeShapes
import com.cardgames.core.ui.chrome.SettingsChevronRow
import com.cardgames.core.ui.chrome.SettingsDivider
import com.cardgames.core.ui.chrome.SettingsGroup
import com.cardgames.core.ui.chrome.SettingsRow
import com.cardgames.core.ui.chrome.TableChip
import com.cardgames.core.ui.chrome.TableOutlineButton
import com.cardgames.core.ui.layout.FormFactor
import com.cardgames.core.ui.text.formatDuration
import com.cardgames.core.ui.text.initials
import com.cardgames.core.ui.text.levelName
import com.cardgames.core.ui.text.opponentName
import com.cardgames.core.ui.theme.CardGamesTheme
import java.util.Locale

/**
 * One game's stats page (A2b, A2c) — the same template for each: a headline, four tiles, one list.
 * It is [StatsScreen] opened at [game]: the page alone on a phone, beside the stats list on a tablet.
 */
@Composable
fun GameStatsScreen(
    game: GameId,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatsScreen(onNavigateBack = onNavigateBack, onOpenGame = {}, modifier = modifier, game = game)
}

/** A game's stats page with no ViewModel — what previews and tests render. */
@Composable
internal fun GameStatsContent(
    game: GameId,
    state: StatsState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatsContent(state, onNavigateBack, onOpenGame = {}, modifier = modifier, game = game)
}

/** A game's stats; [onShowAllMatches] opens Gin's match list in their place (tablet). */
@Composable
internal fun GameStats(
    game: GameId,
    state: StatsState,
    onShowAllMatches: () -> Unit,
) {
    when (game) {
        GameId.GIN_RUMMY -> GinStats(state.gin, state.ginMatches, onShowAllMatches)
        GameId.KLONDIKE -> SolitaireStats(state.solitaire, state.recentWins)
    }
}

@Composable
private fun GinStats(
    gin: GinSummary,
    matches: List<MatchLine>,
    onShowAllMatches: () -> Unit,
) {
    val tablet = CardGamesTheme.formFactor == FormFactor.TABLET
    var showingMatches by rememberSaveable { mutableStateOf(false) }
    Headline(
        figure = if (gin.matches == 0) "—" else "${gin.winPercent}%",
        title = "Matches won",
        detail = "${gin.wins} of ${gin.matches} · best streak ${gin.bestStreak}",
    )
    Tiles(
        "${gin.gins}" to "Gins",
        "${gin.undercuts}" to "Undercuts",
        (if (gin.bestHand > 0) "+${gin.bestHand}" else "—") to "Best hand",
        (gin.averageKnockDeadwood?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—") to "Avg deadwood at knock",
    )
    SettingsGroup(title = "By opponent") {
        GinDifficulty.entries.forEachIndexed { index, rival ->
            if (index > 0) SettingsDivider()
            OpponentRow(rival, gin.byOpponent.getValue(rival).wins, gin.byOpponent.getValue(rival).losses)
        }
        // The tablet opens the match list in this pane's place; the phone unfolds it below.
        if (tablet && matches.isNotEmpty()) {
            SettingsDivider()
            SettingsChevronRow(label = "All matches", onClick = onShowAllMatches)
        }
    }
    if (!tablet && matches.isNotEmpty()) {
        TableOutlineButton(
            text = if (showingMatches) "Hide matches" else "All matches",
            onClick = { showingMatches = !showingMatches },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (!tablet && showingMatches) MatchList(matches)
}

/** Every Gin match, newest first: won or lost, against whom, the final score, and when. */
@Composable
internal fun MatchList(matches: List<MatchLine>) {
    SettingsGroup(title = null) {
        matches.forEachIndexed { index, match ->
            if (index > 0) SettingsDivider()
            SettingsRow(
                label = (if (match.won) "Won" else "Lost") + (match.opponent?.let { " vs ${it.opponentName}" } ?: ""),
                caption = match.day,
            ) {
                Text(
                    "${match.yourScore}" + (match.theirScore?.let { " – $it" } ?: ""),
                    color = CardGamesTheme.colors.onTable,
                    style = CardGamesTheme.type.badge,
                )
            }
        }
    }
}

@Composable
private fun SolitaireStats(
    solitaire: SolitaireSummary,
    recentWins: List<WinLine>,
) {
    Headline(
        figure = if (solitaire.deals == 0) "—" else "${solitaire.solvedPercent}%",
        title = "Deals solved",
        detail = "${solitaire.solved} of ${solitaire.deals} · current streak ${solitaire.currentStreak}",
    )
    Tiles(
        (solitaire.bestSeconds?.let(::formatDuration) ?: "—") to "Best time",
        (solitaire.averageSeconds?.let(::formatDuration) ?: "—") to "Average time",
        (solitaire.fewestMoves?.toString() ?: "—") to "Fewest moves",
        "${solitaire.bestStreak}" to "Best streak",
    )
    SettingsGroup(title = "By draw mode") {
        solitaire.byDrawMode.entries.forEachIndexed { index, (mode, line) ->
            if (index > 0) SettingsDivider()
            SettingsRow(label = "Draw $mode", caption = "${line.solved} of ${line.played}") {
                Text(
                    "Best ${line.bestSeconds?.let(::formatDuration) ?: "—"} · Avg ${line.averageSeconds?.let(::formatDuration) ?: "—"}",
                    color = CardGamesTheme.colors.onTableMuted,
                    style = CardGamesTheme.type.bodySmall,
                )
            }
        }
    }
    if (recentWins.isNotEmpty()) {
        SettingsGroup(title = "Recent wins") {
            recentWins.forEachIndexed { index, win ->
                if (index > 0) SettingsDivider()
                SettingsRow(label = formatDuration(win.seconds), caption = win.day) {
                    if (win.best) TableChip("BEST", fill = CardGamesTheme.colors.accent, ink = CardGamesTheme.colors.onAccent)
                }
            }
        }
    }
}

/**
 * The page's one big number, what it is, and the counts behind it: in brass on the felt (phone), or
 * large on a cream card (tablet).
 */
@Composable
private fun Headline(
    figure: String,
    title: String,
    detail: String,
) {
    val colors = CardGamesTheme.colors
    if (CardGamesTheme.formFactor == FormFactor.TABLET) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.panel, ChromeShapes.Sheet)
                    .padding(horizontal = HEADLINE_CARD_SIDE, vertical = HEADLINE_CARD_TOP),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(figure, color = colors.onPanel, style = CardGamesTheme.type.figure)
            Text(title, color = colors.onPanel, style = CardGamesTheme.type.panelAction)
            Text(detail, color = colors.onPanelMuted, style = CardGamesTheme.type.bodySmall)
        }
        return
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(figure, color = colors.accentOnTable, style = CardGamesTheme.type.display)
        Column(modifier = Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = colors.onTable, style = CardGamesTheme.type.rowLabel)
            Text(detail, color = colors.onTableMuted, style = CardGamesTheme.type.bodySmall)
        }
    }
}

/** Four figures, two by two, on soft tiles. */
@Composable
private fun Tiles(vararg tiles: Pair<String, String>) {
    val colors = CardGamesTheme.colors
    val tileFigure = if (CardGamesTheme.formFactor == FormFactor.TABLET) CardGamesTheme.type.title else CardGamesTheme.type.heading
    Column(verticalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm)) {
        tiles.toList().chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm)) {
                pair.forEach { (value, label) ->
                    Column(
                        modifier =
                            Modifier
                                .weight(
                                    1f,
                                ).background(colors.surfaceOnTable, ChromeShapes.Icon)
                                .padding(CardGamesTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(value, color = colors.onTable, style = tileFigure)
                        Text(label, color = colors.onTableMuted, style = CardGamesTheme.type.rowCaption)
                    }
                }
            }
        }
    }
}

/** A rival: avatar, then their name and level with the record, over a bar of the share won. */
@Composable
private fun OpponentRow(
    rival: GinDifficulty,
    wins: Int,
    losses: Int,
) {
    val colors = CardGamesTheme.colors
    val (fill, ink) = rivalColors(rival)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.md),
    ) {
        Avatar(rival.initials, fill = fill, ink = ink, size = RIVAL_AVATAR)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(rival.opponentName, color = colors.onTable, style = CardGamesTheme.type.rowLabel)
                Text(
                    " ${rival.levelName}",
                    color = colors.onTableMuted,
                    style = CardGamesTheme.type.rowCaption,
                    modifier = Modifier.weight(1f).padding(bottom = 1.dp),
                )
                Text(if (wins + losses == 0) "—" else "$wins – $losses", color = colors.onTable, style = CardGamesTheme.type.badge)
            }
            Box(modifier = Modifier.fillMaxWidth().height(RECORD_BAR).background(colors.chipOnTableSoft, ChromeShapes.Pill)) {
                if (wins + losses > 0) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(
                                    wins / (wins + losses).toFloat(),
                                ).height(RECORD_BAR)
                                .background(colors.accent, ChromeShapes.Pill),
                    )
                }
            }
        }
    }
}

/** Each rival's avatar colours: Milo green, Rosa burgundy, Vex deep brass. */
@Composable
private fun rivalColors(rival: GinDifficulty): Pair<Color, Color> {
    val colors = CardGamesTheme.colors
    return when (rival) {
        GinDifficulty.EASY -> colors.good to colors.panel
        GinDifficulty.MEDIUM -> colors.opponent to colors.panel
        GinDifficulty.HARD -> colors.accentDeep to colors.panel
    }
}

private val RIVAL_AVATAR = 34.dp

/** The tablet's headline card: in from its sides, and from its top and bottom. */
private val HEADLINE_CARD_SIDE = 24.dp
private val HEADLINE_CARD_TOP = 22.dp
private val RECORD_BAR = 4.dp
