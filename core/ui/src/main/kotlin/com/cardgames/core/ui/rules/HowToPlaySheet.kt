package com.cardgames.core.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.ui.cards.PlayingCard
import com.cardgames.core.ui.chrome.ChromeShapes
import com.cardgames.core.ui.chrome.HideNavigationBarInWindow
import com.cardgames.core.ui.chrome.chromeMetrics
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.text.displayName
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * How to play: a cream sheet over the dimmed screen with a tab per game, opening on [initial]
 * (the game in play, or the first one from Home). Swipe down, the close button or back dismisses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToPlaySheet(
    initial: GameId,
    onDismiss: () -> Unit,
) {
    val colors = CardGamesTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = chromeMetrics.sheetRadius, topEnd = chromeMetrics.sheetRadius),
        containerColor = colors.panel,
        contentColor = colors.onPanel,
        scrimColor = colors.scrim,
        dragHandle = {
            Box(
                modifier =
                    Modifier
                        .padding(top = 10.dp)
                        .size(width = 40.dp, height = 5.dp)
                        .background(colors.panelLine, ChromeShapes.Pill),
            )
        },
    ) {
        HideNavigationBarInWindow()
        // One height for every game, so the sheet (and its top edge) stays put as the tabs switch.
        HowToPlayContent(
            initial = initial,
            onClose = onDismiss,
            modifier = Modifier.height(sheetHeight()),
        )
    }
}

/** The sheet's inside — a plain composable, so previews and tests can render it without the sheet. */
@Composable
fun HowToPlayContent(
    initial: GameId,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CardGamesTheme.colors
    var game by rememberSaveable { mutableStateOf(initial) }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = chromeMetrics.sheetPadding).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("How to play", color = colors.onPanel, style = CardGamesTheme.type.heading, modifier = Modifier.weight(1f))
            Box(
                modifier =
                    Modifier
                        .size(CLOSE_SIZE)
                        .clip(CircleShape)
                        .background(colors.toolTile)
                        .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ParlourIcons.Close, contentDescription = "Close", tint = colors.onPanel, modifier = Modifier.size(18.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GameId.entries.sortedBy { it != GameId.GIN_RUMMY }.forEach { tab ->
                val selected = tab == game
                Text(
                    tab.displayName,
                    color = colors.onPanel,
                    style = CardGamesTheme.type.segment,
                    modifier =
                        Modifier
                            .clip(ChromeShapes.Pill)
                            .background(if (selected) colors.action else colors.toolTile)
                            .clickable(role = Role.Tab) { game = tab }
                            .padding(horizontal = CardGamesTheme.spacing.md, vertical = 6.dp),
                )
            }
        }
        // Each game's rules start at the top, rather than at the other game's scroll position.
        key(game) {
            Column(
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = CardGamesTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm),
            ) {
                game.rules.forEach { block -> RulesBlockView(block) }
            }
        }
    }
}

@Composable
private fun RulesBlockView(block: RulesBlock) {
    val colors = CardGamesTheme.colors
    when (block) {
        is RulesBlock.Heading ->
            Text(block.text, color = colors.onPanel, style = CardGamesTheme.type.subhead, modifier = Modifier.padding(top = 6.dp))
        is RulesBlock.Paragraph -> Text(block.text, color = colors.onPanel, style = CardGamesTheme.type.body)
        is RulesBlock.Cards ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                block.groups.forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        group.forEach { card -> PlayingCard(card = card, faceUp = true, modifier = Modifier.width(EXAMPLE_CARD)) }
                    }
                }
            }
        is RulesBlock.Table ->
            Column {
                block.rows.forEachIndexed { index, (what, worth) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.md),
                    ) {
                        Text(what, color = colors.onPanel, style = CardGamesTheme.type.bodySmall, modifier = Modifier.weight(1f))
                        Text(worth, color = colors.onPanel, style = CardGamesTheme.type.badge, textAlign = TextAlign.End)
                    }
                    if (index < block.rows.lastIndex) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.toolTile))
                }
            }
    }
}

private val CLOSE_SIZE = 36.dp

/** The sheet's share of the screen's height, whichever game's rules it shows: clear of the status bar. */
private val EXAMPLE_CARD = 34.dp

/** The sheet's height: down from [ChromeMetrics.sheetTop] when set, else a share of the window. */
@Composable
private fun sheetHeight(): Dp {
    val window = CardGamesTheme.window
    return chromeMetrics.sheetTop?.let { window.height - it } ?: (window.height * chromeMetrics.sheetHeightFraction)
}
