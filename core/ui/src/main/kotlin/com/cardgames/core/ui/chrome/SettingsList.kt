package com.cardgames.core.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.theme.CardGamesTheme

// The grouped settings list on the felt: groups, rows, and their switch, radio and segmented choice.

/**
 * A titled group of settings or stats rows on the felt: a translucent dark container with
 * hairlines between its rows. Put [SettingsRow]-based rows (or any row) in [content]; without the
 * [surface], the content stands on the felt under the title ([SettingsChoiceCards]).
 */
@Composable
fun SettingsGroup(
    title: String?,
    modifier: Modifier = Modifier,
    surface: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardGamesTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) {
            Text(
                title.uppercase(),
                color = colors.onTable,
                style = CardGamesTheme.type.overline,
                modifier = Modifier.padding(start = CardGamesTheme.spacing.xs),
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (surface) Modifier.clip(ChromeShapes.Soft).background(colors.surfaceOnTable) else Modifier),
            content = content,
        )
    }
}

/** The hairline between two rows of a [SettingsGroup]. */
@Composable
fun SettingsDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CardGamesTheme.colors.onTable.copy(alpha = GROUP_LINE_ALPHA)))
}

/**
 * One row of a [SettingsGroup]: an optional [leading] picture, a label with an optional [caption]
 * under it, and [trailing] (a switch, radio, value or chevron) at the end. [modifier] carries the
 * row's click semantics. A [selected] row — the one whose detail is open beside the list (tablet) —
 * is cream with dark ink.
 */
@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    selected: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = CardGamesTheme.colors
    val ink = if (selected) colors.onPanel else colors.onTable
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (selected) Modifier.background(colors.panel) else Modifier)
                .heightIn(min = CardGamesTheme.spacing.rowMinHeight)
                .padding(ROW_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.md),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, color = ink, style = CardGamesTheme.type.rowLabel)
            if (caption != null) {
                Text(caption, color = ink.copy(alpha = CAPTION_ALPHA), style = CardGamesTheme.type.rowCaption)
            }
        }
        CompositionLocalProvider(LocalContentColor provides ink) { trailing() }
    }
}

/** A row with an on/off switch; the whole row toggles. */
@Composable
fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    SettingsRow(
        label = label,
        caption = caption,
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) { ParlourSwitch(checked) }
}

/** A row that is one choice of several; the whole row selects. */
@Composable
fun SettingsRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    SettingsRow(
        label = label,
        caption = caption,
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) { ParlourRadio(selected) }
}

/** A row that opens a further page, with an optional current [value] before the chevron. */
@Composable
fun SettingsChevronRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    value: String? = null,
    selected: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = CardGamesTheme.colors
    val muted = if (selected) colors.onPanelMuted else colors.onTableMuted
    SettingsRow(
        label = label,
        caption = caption,
        selected = selected,
        leading = leading,
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
    ) {
        if (value != null) Text(value, color = muted, style = CardGamesTheme.type.bodySmall)
        Icon(ParlourIcons.ChevronRight, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
    }
}

/**
 * One choice of several as cards side by side, each a [picture] with the radio beside it and the
 * [label] (and [caption]) beneath: Gin's rivals, Solitaire's draw modes (tablet). The chosen card is
 * a shade lighter and ringed in brass.
 */
@Composable
fun <T> SettingsChoiceCards(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    caption: (T) -> String? = { null },
    picture: @Composable (T) -> Unit,
) {
    val colors = CardGamesTheme.colors
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CHOICE_GAP)) {
        options.forEach { option ->
            val isSelected = option == selected
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .then(if (isSelected) Modifier.border(CHOICE_RING, colors.accent, ChromeShapes.Soft) else Modifier)
                        .clip(ChromeShapes.Soft)
                        .background(if (isSelected) colors.chipOnTableSoft else colors.surfaceOnTable)
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) }
                        .padding(CHOICE_PADDING),
                verticalArrangement = Arrangement.spacedBy(CHOICE_INNER_GAP),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(modifier = Modifier.weight(1f)) { picture(option) }
                    ParlourRadio(isSelected)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label(option), color = colors.onTable, style = CardGamesTheme.type.rowLabel)
                    caption(option)?.let {
                        Text(it, color = colors.onTable.copy(alpha = CAPTION_ALPHA), style = CardGamesTheme.type.rowCaption)
                    }
                }
            }
        }
    }
}

/** A row with a small segmented choice (Play to 50 / 100 / 250). */
@Composable
fun <T> SettingsSegmentedRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    SettingsRow(label = label, caption = caption, modifier = modifier) {
        SegmentedChoice(options, selected, optionLabel, onSelect)
    }
}

@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CardGamesTheme.colors
    Row(
        modifier =
            modifier
                .clip(ChromeShapes.Segment)
                .background(colors.chipOnTable)
                .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Text(
                text = optionLabel(option),
                color = if (isSelected) colors.onPanel else colors.onTable.copy(alpha = SEGMENT_IDLE_ALPHA),
                style = CardGamesTheme.type.segment,
                modifier =
                    Modifier
                        .clip(ChromeShapes.SegmentOption)
                        .background(if (isSelected) colors.panel else Color.Transparent)
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) }
                        .padding(horizontal = 11.dp, vertical = 5.dp),
            )
        }
    }
}

/** The Parlour switch: sage track when on, a faint one when off, an ivory thumb. Display only. */
@Composable
fun ParlourSwitch(checked: Boolean) {
    val colors = CardGamesTheme.colors
    Box(
        modifier =
            Modifier
                .size(SWITCH_WIDTH, SWITCH_HEIGHT)
                .clip(CircleShape)
                .background(if (checked) colors.hint else colors.lineOnTable),
    ) {
        Box(
            modifier =
                Modifier
                    .offset(x = if (checked) SWITCH_WIDTH - SWITCH_THUMB - SWITCH_INSET else SWITCH_INSET, y = SWITCH_INSET)
                    .size(SWITCH_THUMB)
                    .background(colors.cardFace, CircleShape),
        )
    }
}

/** The Parlour radio: a brass ring and dot when selected, a faint ring otherwise. Display only. */
@Composable
fun ParlourRadio(selected: Boolean) {
    val colors = CardGamesTheme.colors
    Box(
        modifier =
            Modifier
                .size(RADIO_SIZE)
                .border(2.dp, if (selected) colors.accent else colors.onTable.copy(alpha = RADIO_IDLE_ALPHA), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(modifier = Modifier.size(RADIO_DOT).background(colors.accent, CircleShape))
    }
}

private const val CAPTION_ALPHA = 0.65f
private val CHOICE_GAP = 10.dp
private val CHOICE_PADDING = 16.dp
private val CHOICE_INNER_GAP = 12.dp
private val CHOICE_RING = 2.5.dp
private const val GROUP_LINE_ALPHA = 0.1f
private const val SEGMENT_IDLE_ALPHA = 0.75f
private const val RADIO_IDLE_ALPHA = 0.4f
private val ROW_PADDING = PaddingValues(horizontal = 14.dp, vertical = 11.dp)
private val SWITCH_WIDTH: Dp = 44.dp
private val SWITCH_HEIGHT: Dp = 26.dp
private val SWITCH_THUMB: Dp = 20.dp
private val SWITCH_INSET: Dp = 3.dp
private val RADIO_SIZE: Dp = 22.dp
private val RADIO_DOT: Dp = 10.dp
