package com.cardgames.feature.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.ui.cards.GameFan
import com.cardgames.core.ui.chrome.ChromeShapes
import com.cardgames.core.ui.chrome.PrimaryButton
import com.cardgames.core.ui.chrome.SectionLabel
import com.cardgames.core.ui.chrome.TableBackground
import com.cardgames.core.ui.chrome.felt
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.tween
import com.cardgames.core.ui.text.displayName
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * The tablet's Home: the greeting and title with Stats / Rules / Settings beside them (no bottom
 * row), the Continue card across the width, then a large card per game. The game cards share the
 * height left, at least [GAME_CARD_MIN] each, and scroll past that.
 */
@Composable
internal fun TabletHome(
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = PAGE_START, end = PAGE_END, top = HEADER_TOP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(greeting.uppercase(), color = colors.onTableMuted, style = CardGamesTheme.type.overline)
                    Text("Parlour Cards", color = colors.onTable, style = CardGamesTheme.type.title)
                }
                NavPill(
                    NavItem(ParlourIcons.Stats, "Stats", onOpenStats),
                    NavItem(ParlourIcons.Book, "Rules", onShowRules),
                    NavItem(ParlourIcons.Sliders, "Settings", onOpenSettings),
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = PAGE_START, end = PAGE_END, top = BODY_TOP, bottom = BODY_BOTTOM),
                verticalArrangement = Arrangement.spacedBy(BLOCK_GAP),
            ) {
                state.continueGame?.let { summary -> ContinueCard(summary) { onIntent(HomeIntent.Resume(summary.gameId)) } }
                SectionLabel("Games", modifier = Modifier.padding(start = 2.dp))
                GameCards(state, onIntent, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** One of the header's links. */
private class NavItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/** The header's links in one dark segmented pill. */
@Composable
private fun NavPill(vararg items: NavItem) {
    val colors = CardGamesTheme.colors
    Row(
        modifier = Modifier.clip(NAV_PILL_SHAPE).background(colors.chipOnTable).padding(NAV_PILL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(NAV_PILL_PADDING),
    ) {
        items.forEach { item ->
            Row(
                modifier =
                    Modifier
                        .clip(ChromeShapes.Icon)
                        .clickable(role = Role.Button, onClick = item.onClick)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm),
            ) {
                Icon(item.icon, contentDescription = null, tint = colors.onTable, modifier = Modifier.size(NAV_ICON))
                Text(item.label, color = colors.onTable, style = CardGamesTheme.type.pill)
            }
        }
    }
}

/** The game waiting to be picked up: its cards on a patch of felt, then what and where, the race so far, and Continue. */
@Composable
private fun ContinueCard(
    summary: ContinueSummary,
    onContinue: () -> Unit,
) {
    val colors = CardGamesTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(CARD_ELEVATION, CONTINUE_SHAPE)
                .background(colors.panel, CONTINUE_SHAPE)
                .padding(CONTINUE_PADDING),
        horizontalArrangement = Arrangement.spacedBy(CONTINUE_GAP),
    ) {
        FeltPanel(modifier = Modifier.size(CONTINUE_PREVIEW_WIDTH, CONTINUE_PREVIEW_HEIGHT), onCream = true) {
            GameFan(summary.gameId, cardWidth = FAN_CARD)
        }
        Column(modifier = Modifier.weight(1f).height(CONTINUE_PREVIEW_HEIGHT), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CONTINUE", color = colors.onPanelMuted, style = CardGamesTheme.type.overline, modifier = Modifier.weight(1f))
                Text(summary.whereLine(), color = colors.onPanelMuted, style = CardGamesTheme.type.actionSub)
            }
            Text(summary.title(), color = colors.onPanel, style = CardGamesTheme.type.heading)
            ProgressLine(summary)
            Spacer(modifier = Modifier.weight(1f))
            PrimaryButton(
                text = if (summary is ContinueSummary.Gin) "Continue match" else "Continue deal",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A card per game, sharing [modifier]'s height (at least [GAME_CARD_MIN] each), scrolling past it. */
@Composable
private fun GameCards(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val count = state.games.size
        val shared = (maxHeight - GAME_CARD_GAP * (count - 1)) / count
        val cardHeight = maxOf(shared, GAME_CARD_MIN)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GAME_CARD_GAP),
        ) {
            state.games.forEach { game ->
                val inProgress = state.continueGame?.gameId == game
                GameCard(
                    game = game,
                    statsLine = state.statsLine(game),
                    action =
                        when {
                            inProgress -> "Continue"
                            game == GameId.GIN_RUMMY -> "New match"
                            else -> "Play"
                        },
                    height = cardHeight,
                ) { onIntent(HomeIntent.PlayGame(game)) }
            }
        }
    }
}

/**
 * A game: its fan of cards on a patch of felt, then its name, what it is, a line from its stats
 * (or "Not played"), and a cream button. The whole card is the button; it gives a little when pressed.
 */
@Composable
private fun GameCard(
    game: GameId,
    statsLine: String?,
    action: String,
    height: Dp,
    onClick: () -> Unit,
) {
    val colors = CardGamesTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, CardGamesMotion.Instant.tween(), label = "press")
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(GAME_CARD_SHAPE)
                .background(colors.surfaceOnTable)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .padding(GAME_CARD_PADDING),
        horizontalArrangement = Arrangement.spacedBy(GAME_CARD_INNER_GAP),
    ) {
        FeltPanel(modifier = Modifier.width(GAME_FELT_WIDTH).fillMaxHeight()) { GameFan(game, cardWidth = FAN_CARD) }
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Text(game.displayName, color = colors.onTable, style = CardGamesTheme.type.title, maxLines = 1)
            Text(game.tagline, color = colors.onTableMuted, style = CardGamesTheme.type.body, maxLines = 1)
            Text(
                statsLine ?: "Not played",
                color = if (statsLine != null) colors.accentOnTable else colors.onTableMuted,
                style = CardGamesTheme.type.actionSub,
                maxLines = 1,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                action,
                color = colors.onPanel,
                style = CardGamesTheme.type.pill,
                modifier =
                    Modifier
                        .padding(top = 18.dp)
                        .background(colors.panel, ChromeShapes.Icon)
                        .padding(horizontal = 24.dp, vertical = 13.dp),
            )
        }
    }
}

/** A patch of felt with its picture centred: a darker shade of the felt around it, or, on a cream card ([onCream]), the table's own felt. */
@Composable
private fun FeltPanel(
    modifier: Modifier = Modifier,
    onCream: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = CardGamesTheme.colors
    Box(
        modifier = modifier.clip(FELT_SHAPE).then(if (onCream) Modifier.felt(colors) else Modifier.background(colors.chipOnTable)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private val PAGE_START = 28.dp
private val PAGE_END = 24.dp
private val HEADER_TOP = 12.dp
private val BODY_TOP = 24.dp
private val BODY_BOTTOM = 34.dp
private val BLOCK_GAP = 22.dp

private val NAV_PILL_SHAPE = RoundedCornerShape(20.dp)
private val NAV_PILL_PADDING = 6.dp
private val NAV_ICON = 18.dp

private val CONTINUE_SHAPE = RoundedCornerShape(28.dp)
private val CONTINUE_PADDING = 22.dp
private val CONTINUE_GAP = 24.dp
private val CONTINUE_PREVIEW_WIDTH = 320.dp
private val CONTINUE_PREVIEW_HEIGHT = 236.dp
private val CARD_ELEVATION = 12.dp

private val GAME_CARD_SHAPE = RoundedCornerShape(26.dp)
private val GAME_CARD_MIN = 190.dp
private val GAME_CARD_GAP = 16.dp
private val GAME_CARD_PADDING = 18.dp
private val GAME_CARD_INNER_GAP = 26.dp
private val GAME_FELT_WIDTH = 300.dp
private val FELT_SHAPE = RoundedCornerShape(18.dp)

/** The fan's cards, on Continue and on each game. */
private val FAN_CARD = 84.dp

/** A game card's size while pressed. */
private const val PRESSED_SCALE = 0.98f
