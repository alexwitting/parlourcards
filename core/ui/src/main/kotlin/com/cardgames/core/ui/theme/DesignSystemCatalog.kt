package com.cardgames.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.ui.cards.CARD_RING_HINT
import com.cardgames.core.ui.cards.CardTableColors
import com.cardgames.core.ui.cards.PlayingCard
import com.cardgames.core.ui.chrome.Avatar
import com.cardgames.core.ui.chrome.BottomPanel
import com.cardgames.core.ui.chrome.ChromeShapes
import com.cardgames.core.ui.chrome.ConfirmCard
import com.cardgames.core.ui.chrome.CountBadge
import com.cardgames.core.ui.chrome.FeedPill
import com.cardgames.core.ui.chrome.PrimaryButton
import com.cardgames.core.ui.chrome.SecondaryButton
import com.cardgames.core.ui.chrome.SectionLabel
import com.cardgames.core.ui.chrome.SettingsChevronRow
import com.cardgames.core.ui.chrome.SettingsDivider
import com.cardgames.core.ui.chrome.SettingsGroup
import com.cardgames.core.ui.chrome.SettingsRadioRow
import com.cardgames.core.ui.chrome.SettingsSegmentedRow
import com.cardgames.core.ui.chrome.SettingsToggleRow
import com.cardgames.core.ui.chrome.TableChip
import com.cardgames.core.ui.chrome.TableIconButton
import com.cardgames.core.ui.chrome.TablePill
import com.cardgames.core.ui.chrome.TablePrimaryButton
import com.cardgames.core.ui.chrome.TableSecondaryButton
import com.cardgames.core.ui.chrome.ToolButton
import com.cardgames.core.ui.chrome.felt
import com.cardgames.core.ui.chrome.softSurface
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.motion.CardGamesMotion

/**
 * Every design-system token and shared component on one page, for the active theme: colors,
 * type roles, icons, cards, the chrome kit, and the motion table. Its previews and screenshot
 * baselines (`DesignSystemCatalogScreenshotTest`, one per Parlour theme) are the place to check a
 * design change against the design files before touching any screen.
 */
@Composable
internal fun DesignSystemCatalog(modifier: Modifier = Modifier) {
    val colors = CardGamesTheme.colors
    val spacing = CardGamesTheme.spacing
    Column(
        modifier = modifier.felt(colors).padding(vertical = spacing.pageGutter),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.pageGutter),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            SectionLabel("Colors")
            ColorSwatches(colors)
            SectionLabel("Type")
            TypeSamples()
            SectionLabel("Icons", hint = "${ParlourIcons.All.size}")
            IconGrid()
            SectionLabel("Cards", hint = "78 · 70 · 48 · 44 · 40")
            CardSamples()
            SectionLabel("Table chrome", hint = "on the felt")
            Row(verticalAlignment = Alignment.CenterVertically) {
                TableIconButton(ParlourIcons.Pause, contentDescription = "Pause", onClick = {})
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { TablePill("Hand 5 · First to 100") }
                TableIconButton(ParlourIcons.Sliders, contentDescription = "Settings", onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Avatar("You", fill = colors.accent, ink = colors.onAccent)
                Avatar("Ro", fill = colors.opponent, ink = colors.onTable)
                TableChip("Medium bot")
                TableChip("10 cards", deep = true)
                CountBadge(21)
                CountBadge(3, fill = colors.opponent, ink = colors.panel, size = 20.dp)
            }
            FeedPill(
                initials = "Ro",
                avatarFill = colors.opponent,
                avatarInk = colors.onTable,
                text =
                    buildAnnotatedString {
                        append("Rosa discarded ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.feedEmphasis)) { append("K♣") }
                        append(" · passed on 6♥")
                    },
            )
            Row(
                modifier = Modifier.fillMaxWidth().softSurface(colors.surfaceOnTable).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text("3:12", style = CardGamesTheme.type.score, color = colors.accent)
                Text("64", style = CardGamesTheme.type.score, color = colors.accent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                TablePrimaryButton(text = "Deal again", onClick = {}, modifier = Modifier.weight(1f))
                TableSecondaryButton(text = "Back to games", onClick = {}, modifier = Modifier.weight(1f))
            }
            SettingsSample()
            Text("Confirm", style = CardGamesTheme.type.overline, color = colors.onTableMuted)
            ConfirmCard(
                title = "Start a new game?",
                detail = "Your current game will count as a loss.",
                confirmLabel = "New game",
                onConfirm = {},
                onCancel = {},
            )
            SectionLabel("Motion", hint = "normal speed")
            MotionTable()
        }
        BottomPanel {
            Text("Your turn · draw a card", style = CardGamesTheme.type.bodySmall, color = colors.onPanel)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                ToolButton(ParlourIcons.Undo, label = "Undo", onClick = {})
                ToolButton(ParlourIcons.Lightbulb, label = "Hint", onClick = {}, badge = 3)
                PrimaryButton(text = "Knock", sublabel = "Throw 8♥ and go out", onClick = {}, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SecondaryButton(text = "Pass", onClick = {}, modifier = Modifier.weight(1f))
                PrimaryButton(
                    text = "Knock",
                    sublabel = "Get deadwood to 10 or less",
                    enabled = false,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ColorSwatches(colors: CardTableColors) {
    val roles =
        listOf(
            "tableLight" to colors.tableLight,
            "table" to colors.table,
            "tableDark" to colors.tableDark,
            "onTable" to colors.onTable,
            "onTableMuted" to colors.onTableMuted,
            "chipOnTable" to colors.chipOnTable,
            "lineOnTable" to colors.lineOnTable,
            "accentOnTable" to colors.accentOnTable,
            "scrim" to colors.scrim,
            "panel" to colors.panel,
            "panelLine" to colors.panelLine,
            "onPanel" to colors.onPanel,
            "onPanelMuted" to colors.onPanelMuted,
            "cardFace" to colors.cardFace,
            "suitRed" to colors.suitRed,
            "suitBlack" to colors.suitBlack,
            "cardBack" to colors.cardBack,
            "cardBackLattice" to colors.cardBackLattice,
            "accent" to colors.accent,
            "accentDeep" to colors.accentDeep,
            "action" to colors.action,
            "hint" to colors.hint,
            "meld 1" to colors.meld[0],
            "meld 2" to colors.meld[1],
            "meld 3" to colors.meld[2],
            "good" to colors.good,
            "goodBg" to colors.goodBg,
            "bad" to colors.bad,
            "badBg" to colors.badBg,
            "opponent" to colors.opponent,
            "opponentScore" to colors.opponentScore,
            "chipOnTableSoft" to colors.chipOnTableSoft,
            "surfaceOnTable" to colors.surfaceOnTable,
            "feedPill" to colors.feedPill,
            "toolTile" to colors.toolTile,
            "panelSoft" to colors.panelSoft,
            "meterOver" to colors.meterOver,
            "actionBase" to colors.actionBase,
        )
    Column(verticalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.xs)) {
        roles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm)) {
                pair.forEach { (name, color) -> Swatch(name, color, Modifier.weight(1f)) }
                if (pair.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Swatch(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .background(color, ChromeShapes.Segment)
                    .border(1.dp, CardGamesTheme.colors.onTable.copy(alpha = SWATCH_EDGE_ALPHA), ChromeShapes.Segment),
        )
        Text(
            name,
            style = CardGamesTheme.type.bodySmall,
            color = CardGamesTheme.colors.onTable,
            modifier = Modifier.padding(start = CardGamesTheme.spacing.sm),
        )
    }
}

@Composable
private fun TypeSamples() {
    val type = CardGamesTheme.type
    val roles: List<Pair<String, TextStyle>> =
        listOf(
            "Display" to type.display,
            "Result title" to type.resultTitle,
            "Heading" to type.heading,
            "Subhead" to type.subhead,
            "Score 0123456789" to type.score,
            "Action label" to type.actionLabel,
            "Body — form melds with your ten cards." to type.body,
            "Body small — Rosa is thinking…" to type.bodySmall,
            "Row label" to type.rowLabel,
            "Row caption" to type.rowCaption,
            "Segment" to type.segment,
            "Pill · Hand 5 · First to 100" to type.pill,
            "Action sub · Throw 8♥ and go out" to type.actionSub,
            "Tool label" to type.toolLabel,
            "CHIP" to type.chip,
            "Badge 21" to type.badge,
            "OVERLINE · HAND 5" to type.overline,
            "LABEL" to type.label,
        )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.xs),
    ) {
        roles.forEach { (name, style) -> Text(name, style = style, color = CardGamesTheme.colors.onTable) }
    }
}

@Composable
private fun IconGrid() {
    val colors = CardGamesTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm)) {
        ParlourIcons.All.chunked(ICONS_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.md)) {
                row.forEach { (name, icon) ->
                    Column(modifier = Modifier.width(ICON_CELL), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, contentDescription = null, tint = colors.onTable, modifier = Modifier.size(24.dp))
                        Text(name, style = CardGamesTheme.type.label, color = colors.onTableMuted, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardSamples() {
    val colors = CardGamesTheme.colors
    val spacing = CardGamesTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        // Every size the design uses; under 60dp the face drops its centre pip.
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.Bottom) {
            PlayingCard(card = Card(Suit.HEARTS, Rank.QUEEN), faceUp = true, modifier = Modifier.width(78.dp))
            PlayingCard(card = Card(Suit.SPADES, Rank.ACE), faceUp = true, modifier = Modifier.width(70.dp))
            PlayingCard(card = Card(Suit.CLUBS, Rank.TEN), faceUp = true, modifier = Modifier.width(48.dp))
            PlayingCard(card = Card(Suit.DIAMONDS, Rank.SEVEN), faceUp = true, modifier = Modifier.width(44.dp))
            PlayingCard(card = Card(Suit.CLUBS, Rank.TWO), faceUp = false, modifier = Modifier.width(40.dp))
        }
        // Selected, hint, illegal, lifted; then the meld edges.
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.Bottom) {
            PlayingCard(card = Card(Suit.HEARTS, Rank.NINE), faceUp = true, ring = colors.accent, modifier = Modifier.width(48.dp))
            PlayingCard(
                card = Card(Suit.SPADES, Rank.EIGHT),
                faceUp = true,
                ring = colors.hint,
                ringWidth = CARD_RING_HINT,
                modifier = Modifier.width(48.dp),
            )
            PlayingCard(card = Card(Suit.CLUBS, Rank.KING), faceUp = true, ring = colors.bad, modifier = Modifier.width(48.dp))
            PlayingCard(card = Card(Suit.DIAMONDS, Rank.JACK), faceUp = true, lifted = true, modifier = Modifier.width(48.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.Bottom) {
            colors.meld.forEachIndexed { i, edge ->
                PlayingCard(card = Card(Suit.SPADES, Rank.entries[i + 2]), faceUp = true, meldEdge = edge, modifier = Modifier.width(44.dp))
            }
        }
    }
}

@Composable
private fun SettingsSample() {
    SettingsGroup(title = "Settings list") {
        SettingsToggleRow(label = "Sounds", checked = true, onCheckedChange = {})
        SettingsDivider()
        SettingsToggleRow(label = "Reduce motion", caption = "Fades instead of card flights", checked = false, onCheckedChange = {})
        SettingsDivider()
        SettingsRadioRow(label = "Rosa", caption = "Medium · knocks early", selected = true, onClick = {})
        SettingsDivider()
        SettingsRadioRow(label = "Vex", caption = "Hard · plays to win", selected = false, onClick = {})
        SettingsDivider()
        SettingsSegmentedRow(
            label = "Play to",
            options = listOf(50, 100, 250),
            selected = 100,
            optionLabel = { "$it" },
            onSelect = {},
        )
        SettingsDivider()
        SettingsChevronRow(label = "Solitaire", caption = "Draw 1 · winnable deals", value = "Edit", onClick = {})
    }
}

@Composable
private fun MotionTable() {
    val tweens =
        listOf(
            "Instant" to CardGamesMotion.Instant,
            "Quick" to CardGamesMotion.Quick,
            "Base" to CardGamesMotion.Base,
            "Flip" to CardGamesMotion.Flip,
            "Flight" to CardGamesMotion.Flight,
            "CelebrateLong" to CardGamesMotion.CelebrateLong,
            "ReducedFade" to CardGamesMotion.ReducedFade,
            "Shake" to CardGamesMotion.Shake,
        )
    val springs =
        listOf(
            "Lift" to CardGamesMotion.Lift,
            "Land" to CardGamesMotion.Land,
            "Settle" to CardGamesMotion.Settle,
            "Pop" to CardGamesMotion.Pop,
        )
    Column(verticalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.xxs)) {
        tweens.forEach { (name, spec) ->
            Text("$name · ${spec.millis}ms", style = CardGamesTheme.type.rowCaption, color = CardGamesTheme.colors.onTable)
        }
        springs.forEach { (name, spring) ->
            Text(
                "$name · spring ${spring.dampingRatio} / ${spring.stiffness.toInt()}",
                style = CardGamesTheme.type.rowCaption,
                color = CardGamesTheme.colors.onTable,
            )
        }
    }
}

private const val SWATCH_EDGE_ALPHA = 0.3f
private const val ICONS_PER_ROW = 6
private val ICON_CELL = 46.dp
