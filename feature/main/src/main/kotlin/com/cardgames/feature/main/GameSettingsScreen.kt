package com.cardgames.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.GinMatchRules
import com.cardgames.core.domain.ginrummy.GinScoring
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.HintLimit
import com.cardgames.core.ui.cards.PlayingCard
import com.cardgames.core.ui.chrome.ChromeShapes
import com.cardgames.core.ui.chrome.RivalAvatar
import com.cardgames.core.ui.chrome.SettingsChoiceCards
import com.cardgames.core.ui.chrome.SettingsDivider
import com.cardgames.core.ui.chrome.SettingsGroup
import com.cardgames.core.ui.chrome.SettingsRadioRow
import com.cardgames.core.ui.chrome.SettingsRow
import com.cardgames.core.ui.chrome.SettingsSegmentedRow
import com.cardgames.core.ui.chrome.SettingsToggleRow
import com.cardgames.core.ui.layout.FormFactor
import com.cardgames.core.ui.text.levelName
import com.cardgames.core.ui.text.opponentName
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * One game's own settings page (A1b), from that game's top bar. It is [SettingsScreen] opened at
 * [game]: the page alone on a phone, beside the settings list on a tablet.
 */
@Composable
fun GameSettingsScreen(
    game: GameId,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScreen(onNavigateBack = onNavigateBack, onOpenGame = {}, modifier = modifier, game = game)
}

/** A game's settings page with no ViewModel — what previews and tests render. */
@Composable
internal fun GameSettingsContent(
    game: GameId,
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SettingsContent(
        state,
        onIntent,
        onNavigateBack,
        onOpenGame = {},
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        game = game,
    )
}

/** A game's settings, as groups. */
@Composable
internal fun GameSettings(
    game: GameId,
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
) {
    when (game) {
        GameId.GIN_RUMMY -> GinSettings(state, onIntent)
        GameId.KLONDIKE -> SolitaireSettings(state, onIntent)
    }
}

/** Gin: the opponent, the match (both from the next match on), and the helpers (at once). */
@Composable
private fun GinSettings(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
) {
    val gin = state.settings.gin
    val rules = gin.rules
    val chooseRival = { rival: GinDifficulty -> onIntent(SettingsIntent.SetGinDifficulty(rival)) }
    when (CardGamesTheme.formFactor) {
        FormFactor.PHONE ->
            SettingsGroup(title = "Opponent") {
                GinDifficulty.entries.forEachIndexed { index, rival ->
                    if (index > 0) SettingsDivider()
                    SettingsRadioRow(
                        label = rival.opponentName,
                        caption = "${rival.levelName} · ${rival.style}",
                        selected = gin.difficulty == rival,
                        onClick = { chooseRival(rival) },
                    )
                }
            }
        // The rivals side by side, each with their avatar.
        FormFactor.TABLET ->
            SettingsGroup(title = "Opponent", surface = false) {
                SettingsChoiceCards(
                    options = GinDifficulty.entries,
                    selected = gin.difficulty,
                    onSelect = chooseRival,
                    label = { it.opponentName },
                    caption = { "${it.levelName} · ${it.style}" },
                ) { RivalAvatar(it, size = CHOICE_AVATAR) }
            }
    }
    SettingsGroup(title = "Match") {
        SettingsSegmentedRow(
            label = "Play to",
            options = GinMatchRules.TARGETS,
            selected = rules.target,
            optionLabel = { "$it" },
            onSelect = { onIntent(SettingsIntent.SetGinRules(rules.copy(target = it))) },
        )
        SettingsDivider()
        // Shown as its value, as the design has it; it's switched on and off in match setup.
        SettingsRow(label = "Gin bonus") {
            Text(
                if (rules.ginBonus) "+${GinScoring.GIN_BONUS}" else "Off",
                color = CardGamesTheme.colors.onTable,
                style = CardGamesTheme.type.badge,
            )
        }
        SettingsDivider()
        SettingsToggleRow(
            "Big Gin",
            checked = rules.bigGin,
            caption = "+${GinScoring.BIG_GIN_BONUS} for gin using all 11 cards",
            onCheckedChange = { onIntent(SettingsIntent.SetGinRules(rules.copy(bigGin = it))) },
        )
    }
    Footnote("Changes apply from your next match.")
    SettingsGroup(title = "Helpers") {
        SettingsToggleRow(
            "Meld hints",
            checked = gin.meldHints,
            caption = "Outline cards that make a meld",
            onCheckedChange = { onIntent(SettingsIntent.SetGinMeldHints(it)) },
        )
        SettingsDivider()
        SettingsToggleRow(
            "Confirm before knocking",
            checked = gin.confirmKnock,
            onCheckedChange = { onIntent(SettingsIntent.SetGinConfirmKnock(it)) },
        )
        SettingsDivider()
        HintsRow("Hints per hand", gin.hints) { onIntent(SettingsIntent.SetGinHints(it)) }
    }
}

/** Solitaire: the draw mode and deals (from the next deal on), and hints. */
@Composable
private fun SolitaireSettings(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
) {
    val settings = state.settings
    val chooseDraw = { count: Int -> onIntent(SettingsIntent.SetDrawCount(count)) }
    val tablet = CardGamesTheme.formFactor == FormFactor.TABLET
    // The tablet shows the draw modes as pictures of the waste, above the deal's other setting.
    if (tablet) {
        SettingsGroup(title = "Draw mode", surface = false) {
            SettingsChoiceCards(
                options = DRAW_MODES,
                selected = settings.drawCount,
                onSelect = chooseDraw,
                label = { "Draw $it" },
            ) { DrawModePicture(it) }
        }
    }
    SettingsGroup(title = "Deals") {
        if (!tablet) {
            SettingsSegmentedRow(
                label = "Draw",
                options = DRAW_MODES,
                selected = settings.drawCount,
                optionLabel = { "$it" },
                onSelect = chooseDraw,
            )
            SettingsDivider()
        }
        SettingsToggleRow(
            "Winnable deals",
            checked = settings.useSolverForWinnableDeals,
            caption = "Every deal checked to be solvable",
            onCheckedChange = { onIntent(SettingsIntent.SetUseSolverForWinnableDeals(it)) },
        )
    }
    Footnote("A deal in progress keeps its own draw mode.")
    SettingsGroup(title = "Helpers") {
        HintsRow("Hints per deal", settings.solitaireHints) { onIntent(SettingsIntent.SetSolitaireHints(it)) }
    }
}

/** Hints as 0 / 3 / ∞. */
@Composable
private fun HintsRow(
    label: String,
    selected: HintLimit,
    onSelect: (HintLimit) -> Unit,
) {
    SettingsSegmentedRow(
        label = label,
        options = HintLimit.entries,
        selected = selected,
        optionLabel = { it.allowance?.toString() ?: "∞" },
        onSelect = onSelect,
    )
}

@Composable
private fun Footnote(text: String) {
    Text(
        text,
        color = CardGamesTheme.colors.onTableMuted,
        style = CardGamesTheme.type.rowCaption,
        modifier = Modifier.padding(start = CardGamesTheme.spacing.xs),
    )
}

/** How each rival plays, in a few words (the design's copy). */
private val GinDifficulty.style: String
    get() =
        when (this) {
            GinDifficulty.EASY -> "plays it safe"
            GinDifficulty.MEDIUM -> "knocks early"
            GinDifficulty.HARD -> "plays to win"
        }

private val DRAW_MODES = listOf(1, 3)
private val CHOICE_AVATAR = 42.dp

/** A draw mode as its waste on a patch of felt: one card, or the top three fanned. */
@Composable
private fun DrawModePicture(count: Int) {
    Box(
        modifier =
            Modifier
                .size(DRAW_PICTURE_WIDTH, DRAW_PICTURE_HEIGHT)
                .background(CardGamesTheme.colors.chipOnTable, ChromeShapes.Segment)
                .padding(DRAW_PICTURE_PADDING),
    ) {
        DRAW_PICTURE_CARDS.takeLast(count).forEachIndexed { i, card ->
            val slot = DRAW_PICTURE_CARDS.size - count + i
            PlayingCard(
                card = card,
                faceUp = true,
                modifier = Modifier.padding(start = DRAW_PICTURE_STEP * slot).width(DRAW_PICTURE_CARD),
            )
        }
    }
}

private val DRAW_PICTURE_CARDS = listOf(Card(Suit.DIAMONDS, Rank.SIX), Card(Suit.CLUBS, Rank.TWO), Card(Suit.DIAMONDS, Rank.THREE))
private val DRAW_PICTURE_WIDTH = 118.dp
private val DRAW_PICTURE_HEIGHT = 86.dp
private val DRAW_PICTURE_PADDING = 6.dp
private val DRAW_PICTURE_CARD = 52.dp
private val DRAW_PICTURE_STEP = 22.dp
