package com.cardgames.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.ui.cards.GameArt
import com.cardgames.core.ui.chrome.ChromeShapes
import com.cardgames.core.ui.chrome.ListDetailPage
import com.cardgames.core.ui.chrome.PaneHeader
import com.cardgames.core.ui.chrome.SettingsDivider
import com.cardgames.core.ui.chrome.SettingsGroup
import com.cardgames.core.ui.chrome.SettingsRow
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.layout.FormFactor
import com.cardgames.core.ui.text.displayName
import com.cardgames.core.ui.text.formatDuration
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * Stats: on a phone the list, or one [game]'s page when set; on a tablet both side by side, with
 * [game] (else Gin) open beside the list ([ListDetailPage]).
 */
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    onOpenGame: (GameId) -> Unit,
    modifier: Modifier = Modifier,
    game: GameId? = null,
) {
    val viewModel: StatsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatsContent(state = state, onNavigateBack = onNavigateBack, onOpenGame = onOpenGame, modifier = modifier, game = game)
}

/** What the tablet's right pane shows: a game's stats, or every Gin match. */
internal sealed interface StatsPane {
    data class Game(
        val game: GameId,
    ) : StatsPane

    data object AllMatches : StatsPane
}

/**
 * Stats (A2, and a game's page A2b/A2c) with no ViewModel — what previews and tests render. On a
 * phone, this week's games as a chart and a headline per game, each opening that game's page; or,
 * when [game] is set, that page. On a tablet, the list with the chosen game's page beside it.
 */
@Composable
internal fun StatsContent(
    state: StatsState,
    onNavigateBack: () -> Unit,
    onOpenGame: (GameId) -> Unit,
    modifier: Modifier = Modifier,
    game: GameId? = null,
) {
    val tablet = CardGamesTheme.formFactor == FormFactor.TABLET
    var picked by rememberSaveable { mutableStateOf(game ?: GameId.GIN_RUMMY) }
    var allMatches by rememberSaveable { mutableStateOf(false) }
    val pane: StatsPane? =
        when {
            tablet && allMatches -> StatsPane.AllMatches
            tablet -> StatsPane.Game(picked)
            else -> game?.let(StatsPane::Game)
        }
    ListDetailPage(
        title = "Stats",
        onNavigateBack = onNavigateBack,
        detail = pane,
        detailHeader = { shown ->
            when (shown) {
                is StatsPane.Game -> PaneHeader(shown.game.displayName, overline = "Stats")
                StatsPane.AllMatches -> PaneHeader("All matches", overline = "Gin Rummy", onBack = { allMatches = false })
            }
        },
        listWidth = LIST_WIDTH,
        list = {
            StatsList(
                state,
                picked = picked.takeIf { tablet },
                onOpenGame =
                    if (tablet) {
                        { chosen ->
                            picked = chosen
                            allMatches = false
                        }
                    } else {
                        onOpenGame
                    },
            )
        },
        detailContent = { shown ->
            when (shown) {
                is StatsPane.Game -> GameStats(shown.game, state, onShowAllMatches = { allMatches = true })
                StatsPane.AllMatches -> MatchList(state.ginMatches)
            }
        },
        modifier = modifier,
    )
}

/** The week's chart, then a row per game ([picked] marked, tablet). */
@Composable
private fun StatsList(
    state: StatsState,
    picked: GameId?,
    onOpenGame: (GameId) -> Unit,
) {
    WeekCard(state.week, state.todayIndex)
    SettingsGroup(title = "By game") {
        GameRow(
            GameId.GIN_RUMMY,
            caption =
                if (state.gin.matches ==
                    0
                ) {
                    null
                } else {
                    "${counted(state.gin.matches, "match", "matches")} · ${counted(state.gin.gins, "gin", "gins")}"
                },
            percent = state.gin.winPercent.takeIf { state.gin.matches > 0 },
            percentLabel = "matches won",
            selected = picked == GameId.GIN_RUMMY,
        ) { onOpenGame(GameId.GIN_RUMMY) }
        SettingsDivider()
        val solitaire = state.solitaire
        GameRow(
            GameId.KLONDIKE,
            caption =
                if (solitaire.deals == 0) {
                    null
                } else {
                    counted(solitaire.deals, "deal", "deals") + (solitaire.bestSeconds?.let { " · best ${formatDuration(it)}" } ?: "")
                },
            percent = solitaire.solvedPercent.takeIf { solitaire.deals > 0 },
            percentLabel = "deals solved",
            selected = picked == GameId.KLONDIKE,
        ) { onOpenGame(GameId.KLONDIKE) }
    }
}

/** This week on a soft card: the total, and a bar per day, Monday first. */
@Composable
private fun WeekCard(
    week: List<Int>,
    todayIndex: Int,
) {
    val colors = CardGamesTheme.colors
    val most = week.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(
        modifier = Modifier.fillMaxWidth().background(colors.surfaceOnTable, ChromeShapes.Soft).padding(CardGamesTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("THIS WEEK", color = colors.onTable, style = CardGamesTheme.type.overline, modifier = Modifier.weight(1f))
            Text("All games", color = colors.onTableMuted, style = CardGamesTheme.type.rowCaption)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.lg)) {
            Column {
                Text("${week.sum()}", color = colors.onTable, style = CardGamesTheme.type.resultTitle)
                Text(
                    if (week.sum() ==
                        1
                    ) {
                        "game played"
                    } else {
                        "games played"
                    },
                    color = colors.onTableMuted,
                    style = CardGamesTheme.type.bodySmall,
                )
            }
            Row(
                modifier = Modifier.weight(1f).height(CHART_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                week.forEachIndexed { index, count ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(BAR_MAX * count / most + BAR_MIN)
                                    .background(
                                        if (index ==
                                            todayIndex
                                        ) {
                                            colors.accent
                                        } else {
                                            colors.onTable.copy(alpha = BAR_ALPHA)
                                        },
                                        ChromeShapes.SegmentOption,
                                    ),
                        )
                        Text(DAY_INITIALS[index], color = colors.onTableMuted, style = CardGamesTheme.type.label)
                    }
                }
            }
        }
    }
}

/** A game's headline row: its cards, what's been played, and the win rate — or "—" before the first game. */
@Composable
private fun GameRow(
    game: GameId,
    caption: String?,
    percent: Int?,
    percentLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = CardGamesTheme.colors
    val (ink, muted) = if (selected) colors.onPanel to colors.onPanelMuted else colors.onTable to colors.onTableMuted
    SettingsRow(
        label = game.displayName,
        caption = caption ?: "Play a game to start tracking",
        selected = selected,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        leading = { GameArt(game, cardWidth = ROW_ART_CARD) },
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(percent?.let { "$it%" } ?: "—", color = ink, style = CardGamesTheme.type.tally)
            Text(percentLabel, color = muted, style = CardGamesTheme.type.label)
        }
        Icon(ParlourIcons.ChevronRight, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
    }
}

private val DAY_INITIALS = listOf("M", "T", "W", "T", "F", "S", "S")
private val CHART_HEIGHT = 64.dp
private val BAR_MAX = 38.dp
private val BAR_MIN = 4.dp
private const val BAR_ALPHA = 0.3f
private val ROW_ART_CARD = 28.dp

/** The tablet's stats list, beside the chosen game's page. */
private val LIST_WIDTH = 310.dp

/** "1 deal", "4 deals". */
private fun counted(
    count: Int,
    one: String,
    many: String,
): String = "$count ${if (count == 1) one else many}"
