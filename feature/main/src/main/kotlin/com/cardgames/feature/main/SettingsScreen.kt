package com.cardgames.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.repository.AnimationSpeed
import com.cardgames.core.domain.repository.Settings
import com.cardgames.core.domain.theme.ThemePack
import com.cardgames.core.ui.cards.CARD_ASPECT_RATIO
import com.cardgames.core.ui.cards.GameArt
import com.cardgames.core.ui.cards.toCardTableTheme
import com.cardgames.core.ui.chrome.ChromeShapes
import com.cardgames.core.ui.chrome.ListDetailPage
import com.cardgames.core.ui.chrome.PaneHeader
import com.cardgames.core.ui.chrome.SettingsChevronRow
import com.cardgames.core.ui.chrome.SettingsDivider
import com.cardgames.core.ui.chrome.SettingsGroup
import com.cardgames.core.ui.chrome.SettingsRow
import com.cardgames.core.ui.chrome.SettingsSegmentedRow
import com.cardgames.core.ui.chrome.SettingsToggleRow
import com.cardgames.core.ui.chrome.felt
import com.cardgames.core.ui.layout.FormFactor
import com.cardgames.core.ui.rules.HowToPlaySheet
import com.cardgames.core.ui.text.displayName
import com.cardgames.core.ui.text.opponentName
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.core.ui.theme.ParlourTheme

/**
 * Settings: on a phone the list, or one [game]'s page when set; on a tablet both side by side, with
 * [game] (else Gin) open beside the list ([ListDetailPage]).
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenGame: (GameId) -> Unit,
    modifier: Modifier = Modifier,
    game: GameId? = null,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }
    SettingsContent(state, viewModel::onIntent, onNavigateBack, onOpenGame, snackbarHostState, modifier, game)
}

/**
 * Settings (A1, and a game's page A1b) with no ViewModel — what previews and tests render. On a
 * phone, the list (the table theme, a row per game opening its own page, sound & feel, about) or,
 * when [game] is set, that game's page. On a tablet, the list with the chosen game's page beside it;
 * choosing another game changes the page in place.
 */
@Composable
internal fun SettingsContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenGame: (GameId) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    game: GameId? = null,
) {
    var showingRules by rememberSaveable { mutableStateOf(false) }
    val tablet = CardGamesTheme.formFactor == FormFactor.TABLET
    var picked by rememberSaveable { mutableStateOf(game ?: GameId.GIN_RUMMY) }
    ListDetailPage(
        title = "Settings",
        onNavigateBack = onNavigateBack,
        detail = if (tablet) picked else game,
        detailHeader = { PaneHeader(it.displayName, overline = "Settings") },
        listWidth = LIST_WIDTH,
        list = {
            SettingsList(
                state = state,
                onIntent = onIntent,
                picked = picked.takeIf { tablet },
                onOpenGame = if (tablet) ({ picked = it }) else onOpenGame,
                onShowRules = { showingRules = true },
            )
        },
        detailContent = { shown -> GameSettings(shown, state, onIntent) },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
    if (showingRules) HowToPlaySheet(initial = picked, onDismiss = { showingRules = false })
}

/** The settings list: the table theme, the games ([picked] marked, tablet), sound & feel, and about. */
@Composable
private fun SettingsList(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    picked: GameId?,
    onOpenGame: (GameId) -> Unit,
    onShowRules: () -> Unit,
) {
    val settings = state.settings
    if (state.themePacks.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm)) {
            Text(
                "TABLE THEME",
                color = CardGamesTheme.colors.onTableMuted,
                style = CardGamesTheme.type.overline,
                modifier = Modifier.padding(start = CardGamesTheme.spacing.xs),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.themePacks
                    .sortedBy { pack ->
                        ParlourTheme.entries.indexOfFirst { it.id == pack.id }.let {
                            if (it <
                                0
                            ) {
                                Int.MAX_VALUE
                            } else {
                                it
                            }
                        }
                    }.forEach { pack ->
                        ThemeSwatch(pack, selected = state.selectedThemeId == pack.id, modifier = Modifier.weight(1f)) {
                            onIntent(SettingsIntent.SelectTheme(pack.id))
                        }
                    }
            }
        }
    }
    SettingsGroup(title = "Games") {
        listOf(GameId.GIN_RUMMY, GameId.KLONDIKE).forEachIndexed { index, game ->
            if (index > 0) SettingsDivider()
            SettingsChevronRow(
                label = game.displayName,
                caption = settings.summary(game),
                selected = game == picked,
                onClick = { onOpenGame(game) },
                leading = { GameArt(game, cardWidth = ROW_ART_CARD) },
            )
        }
    }
    SettingsGroup(title = "Sound & feel") {
        SettingsToggleRow("Sounds", checked = settings.soundEnabled, onCheckedChange = { onIntent(SettingsIntent.SetSoundEnabled(it)) })
        SettingsDivider()
        SettingsToggleRow(
            "Haptics",
            checked = settings.hapticsEnabled,
            onCheckedChange = { onIntent(SettingsIntent.SetHapticsEnabled(it)) },
        )
        SettingsDivider()
        SettingsSegmentedRow(
            label = "Card speed",
            options = AnimationSpeed.entries,
            selected = settings.animationSpeed,
            optionLabel = { it.displayName },
            onSelect = { onIntent(SettingsIntent.SetAnimationSpeed(it)) },
        )
        SettingsDivider()
        SettingsToggleRow(
            "Reduce motion",
            checked = settings.reduceMotion,
            caption = "Fades instead of card flights",
            onCheckedChange = { onIntent(SettingsIntent.SetReduceMotion(it)) },
        )
    }
    SettingsGroup(title = "About") {
        SettingsChevronRow(label = "How to play", onClick = onShowRules)
        SettingsDivider()
        SettingsRow(label = "Version") {
            Text(appVersion(), color = CardGamesTheme.colors.onTableMuted, style = CardGamesTheme.type.bodySmall)
        }
    }
}

/**
 * A theme pack as a small square of its felt with a card back and a face on it; the chosen one is
 * ringed in brass, its name in brass beneath.
 */
@Composable
private fun ThemeSwatch(
    pack: ThemePack,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = CardGamesTheme.colors
    val packColors = remember(pack) { pack.toCardTableTheme().colors }
    Column(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .then(if (selected) Modifier.border(SWATCH_RING, colors.accent, ChromeShapes.Segment) else Modifier)
                    .padding(if (selected) SWATCH_RING else 0.dp)
                    .clip(ChromeShapes.Segment)
                    .felt(packColors),
            contentAlignment = Alignment.Center,
        ) {
            // A back and a face in the pack's own colours — plain shapes, no image decoding for a thumbnail.
            val shape = RoundedCornerShape(percent = SWATCH_CARD_CORNER_PERCENT)
            Box(
                modifier =
                    Modifier
                        .offset(x = -SWATCH_CARD_SHIFT)
                        .graphicsLayer { rotationZ = -SWATCH_TILT }
                        .size(SWATCH_CARD, SWATCH_CARD / CARD_ASPECT_RATIO)
                        .background(packColors.cardBack, shape)
                        .border(SWATCH_BACK_BORDER, packColors.cardBackBorder, shape),
            )
            Box(
                modifier =
                    Modifier
                        .offset(x = SWATCH_CARD_SHIFT)
                        .graphicsLayer { rotationZ = SWATCH_TILT }
                        .size(SWATCH_CARD, SWATCH_CARD / CARD_ASPECT_RATIO)
                        .background(packColors.cardFace, shape),
            )
        }
        Text(
            pack.displayName,
            color = if (selected) colors.accent else colors.onTableMuted,
            style = CardGamesTheme.type.toolLabel,
            maxLines = 1,
        )
    }
}

/** A game's settings in a line: "Rosa · to 100 · meld hints on", "Draw 1 · winnable deals". */
internal fun Settings.summary(game: GameId): String =
    when (game) {
        GameId.GIN_RUMMY -> "${gin.difficulty.opponentName} · to ${gin.rules.target} · meld hints ${if (gin.meldHints) "on" else "off"}"
        GameId.KLONDIKE -> "Draw $drawCount · ${if (useSolverForWinnableDeals) "winnable deals" else "random deals"}"
    }

/** The app's version name — plain display of app metadata, so no repository behind it. */
@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
}

internal val AnimationSpeed.displayName: String
    get() =
        when (this) {
            AnimationSpeed.SLOW -> "Slow"
            AnimationSpeed.NORMAL -> "Normal"
            AnimationSpeed.FAST -> "Fast"
        }

private val ROW_ART_CARD = 28.dp

/** The tablet's settings list, beside the chosen game's page. */
private val LIST_WIDTH = 330.dp
private val SWATCH_RING = 2.5.dp
private val SWATCH_CARD = 18.dp
private val SWATCH_CARD_SHIFT = 5.dp
private const val SWATCH_TILT = 8f
private const val SWATCH_CARD_CORNER_PERCENT = 20
private val SWATCH_BACK_BORDER = 1.5.dp
