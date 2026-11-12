package com.cardgames.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.repository.GameStats
import com.cardgames.core.ui.cards.GameArt
import com.cardgames.core.ui.chrome.ChromeShapes
import com.cardgames.core.ui.chrome.PrimaryButton
import com.cardgames.core.ui.chrome.SectionLabel
import com.cardgames.core.ui.chrome.TableBackground
import com.cardgames.core.ui.chrome.TableIconButton
import com.cardgames.core.ui.chrome.TableOutlineButton
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.layout.FormFactor
import com.cardgames.core.ui.rules.HowToPlaySheet
import com.cardgames.core.ui.text.displayName
import com.cardgames.core.ui.text.formatDuration
import com.cardgames.core.ui.text.lineAgainst
import com.cardgames.core.ui.text.opponentName
import com.cardgames.core.ui.theme.CardGamesTheme
import java.time.LocalTime

@Composable
fun HomeScreen(
    onPlayGame: (GameId) -> Unit,
    onOpenGinSetup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.NavigateToGame -> onPlayGame(effect.gameId)
                HomeEffect.NavigateToGinSetup -> onOpenGinSetup()
            }
        }
    }

    HomeContent(
        state = state,
        onIntent = viewModel::onIntent,
        onOpenSettings = onOpenSettings,
        onOpenStats = onOpenStats,
        greeting = greetingFor(LocalTime.now()),
        modifier = modifier,
    )
}

/**
 * The screen's entire UI, decoupled from the ViewModel — same split as `SolitaireContent` and
 * `GinContent`: everything here is a plain parameter, so it composes in a `@Preview` or a test
 * with no Hilt graph. [greeting] comes in from outside so a screenshot doesn't depend on the hour.
 */
@Composable
internal fun HomeContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    greeting: String,
    modifier: Modifier = Modifier,
) {
    var showingRules by rememberSaveable { mutableStateOf(false) }
    when (CardGamesTheme.formFactor) {
        FormFactor.PHONE ->
            PhoneHome(
                state,
                onIntent,
                onOpenSettings,
                onOpenStats,
                onShowRules = { showingRules = true },
                greeting,
                modifier,
            )
        FormFactor.TABLET ->
            TabletHome(
                state,
                onIntent,
                onOpenSettings,
                onOpenStats,
                onShowRules = { showingRules = true },
                greeting,
                modifier,
            )
    }
    if (showingRules) HowToPlaySheet(initial = state.games.first(), onDismiss = { showingRules = false })
}

/** The phone's Home: the greeting and title, the Continue card, the games as rows, then Stats / Rules / Settings. */
@Composable
private fun PhoneHome(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onShowRules: () -> Unit,
    greeting: String,
    modifier: Modifier = Modifier,
) {
    val colors = CardGamesTheme.colors
    Box(modifier = modifier.fillMaxSize()) {
        TableBackground(modifier = Modifier.matchParentSize())
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().windowInsetsPadding(WindowInsets.navigationBars)) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = CardGamesTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        greeting.uppercase(),
                        color = colors.onTableMuted,
                        style = CardGamesTheme.type.overline,
                        modifier = Modifier.weight(1f),
                    )
                    TableIconButton(ParlourIcons.Sliders, contentDescription = "Settings", onClick = onOpenSettings)
                }
                Text("Parlour Cards", color = colors.onTable, style = CardGamesTheme.type.resultTitle)
                state.continueGame?.let { summary ->
                    ContinueCard(summary, modifier = Modifier.padding(top = 14.dp)) { onIntent(HomeIntent.Resume(summary.gameId)) }
                }
                SectionLabel("Games", modifier = Modifier.padding(top = CardGamesTheme.spacing.md, start = 2.dp))
                state.games.forEach { game ->
                    GameRow(game, state.statsLine(game)) { onIntent(HomeIntent.PlayGame(game)) }
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = CardGamesTheme.spacing.lg, vertical = CardGamesTheme.spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm),
            ) {
                TableOutlineButton(text = "Stats", onClick = onOpenStats, modifier = Modifier.weight(1f))
                TableOutlineButton(text = "Rules", onClick = onShowRules, modifier = Modifier.weight(1f))
                TableOutlineButton(text = "Settings", onClick = onOpenSettings, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** A game's line under its name: Gin's record against the latest rival, else one from its stats; null before it's been played. */
internal fun HomeState.statsLine(game: GameId): String? =
    ginRecord
        ?.takeIf { game == GameId.GIN_RUMMY }
        ?.let { (rival, record) -> record.lineAgainst(rival.opponentName) }
        ?: stats[game]?.summaryLine(game)

/** The game waiting to be picked up, on a cream card: what and where, the race so far, and Continue. */
@Composable
private fun ContinueCard(
    summary: ContinueSummary,
    modifier: Modifier = Modifier,
    onContinue: () -> Unit,
) {
    val colors = CardGamesTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().background(colors.panel, ChromeShapes.Soft).padding(CardGamesTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CONTINUE", color = colors.onPanelMuted, style = CardGamesTheme.type.overline, modifier = Modifier.weight(1f))
            Text(summary.whereLine(), color = colors.onPanelMuted, style = CardGamesTheme.type.actionSub)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GameArt(summary.gameId, cardWidth = MINI_CARD, modifier = Modifier.padding(vertical = 4.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(summary.title(), color = colors.onPanel, style = CardGamesTheme.type.subhead)
                ProgressLine(summary)
            }
        }
        PrimaryButton(
            text = if (summary is ContinueSummary.Gin) "Continue match" else "Continue deal",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Gin: your score, a bar towards the target, theirs. Solitaire: the cards home, a bar towards 52. */
@Composable
internal fun ProgressLine(summary: ContinueSummary) {
    val colors = CardGamesTheme.colors
    val (left, fraction, right) =
        when (summary) {
            is ContinueSummary.Gin -> Triple("${summary.yourScore}", summary.yourScore / summary.target.toFloat(), "${summary.theirScore}")
            is ContinueSummary.Solitaire -> Triple("${summary.cardsHome}", summary.cardsHome / DECK_SIZE, "52")
        }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm)) {
        Text(left, color = colors.accentDeep, style = CardGamesTheme.type.badge)
        Box(modifier = Modifier.weight(1f).height(PROGRESS_BAR).background(colors.panelLine, ChromeShapes.Pill)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(
                            fraction.coerceIn(0f, 1f),
                        ).height(PROGRESS_BAR)
                        .background(colors.accent, ChromeShapes.Pill),
            )
        }
        Text(right, color = if (summary is ContinueSummary.Gin) colors.opponent else colors.onPanelMuted, style = CardGamesTheme.type.badge)
    }
}

/** A game in the list, outlined over a faint fill: its two cards, name, tagline and a line from its stats. */
@Composable
private fun GameRow(
    game: GameId,
    statsLine: String?,
    onClick: () -> Unit,
) {
    val colors = CardGamesTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(ChromeShapes.Tile)
                .background(colors.surfaceOnTable.copy(alpha = colors.surfaceOnTable.alpha * ROW_FILL_SCALE))
                .border(1.dp, colors.lineOnTable, ChromeShapes.Tile)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GameArt(game, cardWidth = MINI_CARD, modifier = Modifier.padding(vertical = 4.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(game.displayName, color = colors.onTable, style = CardGamesTheme.type.actionLabel)
            Text(game.tagline, color = colors.onTableMuted, style = CardGamesTheme.type.bodySmall)
            statsLine?.let { Text(it, color = colors.accentOnTable, style = CardGamesTheme.type.actionSub) }
        }
    }
}

/** "Hand 7 · your turn", "Draw 1 · 3:25". */
internal fun ContinueSummary.whereLine(): String =
    when (this) {
        is ContinueSummary.Gin -> "Hand $handNumber · ${if (yourTurn) "your turn" else "${opponent.opponentName}'s turn"}"
        is ContinueSummary.Solitaire -> "$moves moves · ${formatDuration(elapsedSeconds)}"
    }

/** "Gin Rummy vs Rosa", "Solitaire · Draw 3". */
internal fun ContinueSummary.title(): String =
    when (this) {
        is ContinueSummary.Gin -> "Gin Rummy vs ${opponent.opponentName}"
        is ContinueSummary.Solitaire -> "Solitaire · Draw $drawCount"
    }

/** The game's line from its stats, or null before it's been played. */
private fun GameStats.summaryLine(game: GameId): String? =
    when {
        gamesPlayed == 0 -> null
        game == GameId.GIN_RUMMY -> "Won $gamesWon of $gamesPlayed matches"
        else -> bestDurationSeconds?.let { "Best time ${formatDuration(it)}" } ?: "Won $gamesWon of $gamesPlayed deals"
    }

/** "Good morning", "Good afternoon", "Good evening". */
internal fun greetingFor(time: LocalTime): String =
    when (time.hour) {
        in MORNING -> "Good morning"
        in AFTERNOON -> "Good afternoon"
        else -> "Good evening"
    }

internal val GameId.tagline: String
    get() =
        when (this) {
            GameId.KLONDIKE -> "Klondike · draw 1 or 3"
            GameId.GIN_RUMMY -> "Melds and knocks vs the computer"
        }

private val MINI_CARD = 40.dp
private val PROGRESS_BAR = 4.dp
private const val DECK_SIZE = 52f
private val MORNING = 5..11
private val AFTERNOON = 12..17

/** A game row's fill: half the usual soft surface, so the outline leads. */
private const val ROW_FILL_SCALE = 0.5f
