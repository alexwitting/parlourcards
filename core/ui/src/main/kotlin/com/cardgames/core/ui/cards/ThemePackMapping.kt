package com.cardgames.core.ui.cards

import androidx.compose.ui.graphics.Color
import com.cardgames.core.domain.theme.ThemeChromeColors
import com.cardgames.core.domain.theme.ThemePack

/**
 * A validated pack's manifest colors as a [CardTableTheme]. [images] defaults to null; cards
 * are then drawn in code from the colors.
 */
fun ThemePack.toCardTableTheme(images: CardImages? = null): CardTableTheme =
    CardTableTheme(
        colors =
            with(colors) {
                CardTableColors(
                    tableLight = Color(tableLight),
                    table = Color(table),
                    tableDark = Color(tableDark),
                    onTable = Color(onTable),
                    onTableMuted = Color(onTableMuted),
                    chipOnTable = Color(chipOnTable),
                    lineOnTable = Color(lineOnTable),
                    scrim = Color(scrim),
                    panel = Color(panel),
                    panelLine = Color(panelLine),
                    onPanel = Color(onPanel),
                    onPanelMuted = Color(onPanelMuted),
                    cardFace = Color(cardFace),
                    suitRed = Color(suitRed),
                    suitBlack = Color(suitBlack),
                    cardBack = Color(cardBack),
                    cardBackLattice = Color(cardBackLattice),
                    cardBackBorder = Color(cardBackBorder),
                    accent = Color(accent),
                    onAccent = Color(onAccent),
                    accentDeep = Color(accentDeep),
                    action = Color(action),
                    onAction = Color(onAction),
                    hint = Color(hint),
                    meld = meld.map { Color(it) },
                    good = Color(good),
                    goodBg = Color(goodBg),
                    bad = Color(bad),
                    badBg = Color(badBg),
                    opponent = Color(opponent),
                    opponentScore = Color(opponentScore),
                ).withChrome(chrome)
            },
        images = images,
    )

/** [chrome]'s overrides on top of the derived defaults. */
private fun CardTableColors.withChrome(chrome: ThemeChromeColors): CardTableColors = withTableChrome(chrome).withPanelChrome(chrome)

private fun CardTableColors.withTableChrome(chrome: ThemeChromeColors): CardTableColors =
    copy(
        chipOnTableSoft = chrome.chipOnTableSoft?.let { Color(it) } ?: chipOnTableSoft,
        surfaceOnTable = chrome.surfaceOnTable?.let { Color(it) } ?: surfaceOnTable,
        feedPill = chrome.feedPill?.let { Color(it) } ?: feedPill,
        feedEmphasis = chrome.feedEmphasis?.let { Color(it) } ?: feedEmphasis,
        accentOnTable = chrome.accentOnTable?.let { Color(it) } ?: accentOnTable,
        deepChip = chrome.deepChip?.let { Color(it) } ?: deepChip,
    )

private fun CardTableColors.withPanelChrome(chrome: ThemeChromeColors): CardTableColors =
    copy(
        toolTile = chrome.toolTile?.let { Color(it) } ?: toolTile,
        panelSoft = chrome.panelSoft?.let { Color(it) } ?: panelSoft,
        onPanelDisabled = chrome.onPanelDisabled?.let { Color(it) } ?: onPanelDisabled,
        meterOver = chrome.meterOver?.let { Color(it) } ?: meterOver,
        actionBase = chrome.actionBase?.let { Color(it) } ?: actionBase,
    )
