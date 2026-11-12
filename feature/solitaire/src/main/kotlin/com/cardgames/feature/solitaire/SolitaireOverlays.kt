package com.cardgames.feature.solitaire

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.ui.cards.LocalSmallCardFaces
import com.cardgames.core.ui.cards.PlayingCard
import com.cardgames.core.ui.chrome.ChromeShapes
import com.cardgames.core.ui.chrome.Confetti
import com.cardgames.core.ui.chrome.TableBackground
import com.cardgames.core.ui.chrome.TableIconButton
import com.cardgames.core.ui.chrome.TablePrimaryButton
import com.cardgames.core.ui.chrome.TableSecondaryButton
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.layout.FormFactor
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.DecelerateEasing
import com.cardgames.core.ui.motion.LocalReduceMotion
import com.cardgames.core.ui.motion.MotionSpec
import com.cardgames.core.ui.motion.scaledMillis
import com.cardgames.core.ui.motion.spring
import com.cardgames.core.ui.motion.tween
import com.cardgames.core.ui.text.formatDuration
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.feature.solitaire.board.FoundationSlots
import com.cardgames.feature.solitaire.board.solitaireMetrics
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The win: confetti drifting over the felt, the four kings home (each bobbing once), "Solved",
 * the time / moves / streak tiles and the best-time line, then Deal again or Back to games. Covers
 * the whole screen and swallows every touch that isn't one of its buttons. The kings sit at
 * [kingsTop] (the table's foundation row, in root pixels), so they stay put as this fades in over
 * the table; without it (previews, tests) they sit under the back button.
 */
@Composable
internal fun SolvedScreen(
    summary: SolvedSummary,
    onDealAgain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    kingsTop: Float? = null,
) {
    val spacing = CardGamesTheme.spacing
    val density = LocalDensity.current
    var ownTop by remember { mutableFloatStateOf(0f) }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onGloballyPositioned { ownTop = it.positionInRoot().y }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        TableBackground(modifier = Modifier.matchParentSize())
        Confetti(modifier = Modifier.matchParentSize())
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
            if (kingsTop != null) {
                Spacer(modifier = Modifier.height(with(density) { (kingsTop - ownTop).coerceAtLeast(0f).toDp() }))
            } else {
                Spacer(modifier = Modifier.statusBarsPadding().height(KINGS_TOP))
            }
            KingsHome()
            val tablet = CardGamesTheme.formFactor == FormFactor.TABLET
            if (tablet) SolvedCard(summary) else SolvedTitle(summary)
            Spacer(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier.padding(horizontal = if (tablet) TABLET_BUTTONS_INSET else spacing.lg).padding(bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TablePrimaryButton(
                    text = "Deal again",
                    onClick = onDealAgain,
                    modifier = Modifier.fillMaxWidth(),
                )
                TableSecondaryButton(text = "Back to games", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }
        TableIconButton(
            ParlourIcons.Back,
            contentDescription = "Back to games",
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(start = spacing.md, top = 6.dp),
        )
    }
}

/**
 * The four kings home, in the foundation row as the table draws it (same size, spacing and faces),
 * each bobbing up 8dp and back, 90ms apart.
 */
@Composable
private fun KingsHome(modifier: Modifier = Modifier) {
    val accent = CardGamesTheme.colors.accent
    val bob = CardGamesMotion.Settle.spring<Float>()
    val stagger = scaledMillis(CardGamesMotion.Stagger.REVEAL)
    val still = LocalReduceMotion.current
    CompositionLocalProvider(LocalSmallCardFaces provides true) {
        BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val board = minOf(maxWidth, CardGamesTheme.spacing.maxBoardWidth)
            FoundationSlots(solitaireMetrics.columnWidthFor(DpSize(board, maxHeight)), modifier = Modifier.width(board)) { suit ->
                val lift = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    if (still) return@LaunchedEffect
                    delay(suit.ordinal * stagger)
                    lift.animateTo(1f, bob)
                    lift.animateTo(0f, bob)
                }
                PlayingCard(
                    card = Card(suit, Rank.KING),
                    faceUp = true,
                    ring = accent.copy(alpha = KING_RING_ALPHA),
                    ringWidth = 1.5.dp,
                    modifier = Modifier.graphicsLayer { translationY = -KING_BOB.toPx() * lift.value },
                )
            }
        }
    }
}

/** "Solved" settling in (0.92 → 1, fading up), then the figures 120ms behind it. */
@Composable
private fun SolvedTitle(summary: SolvedSummary) {
    val colors = CardGamesTheme.colors
    val spacing = CardGamesTheme.spacing
    val title = remember { Animatable(0f) }
    val figures = remember { Animatable(0f) }
    val enter = MotionSpec(CardGamesMotion.Base.millis, DecelerateEasing).tween<Float>()
    val lag = scaledMillis(FIGURES_LAG)
    LaunchedEffect(Unit) { title.animateTo(1f, enter) }
    LaunchedEffect(Unit) {
        delay(lag)
        figures.animateTo(1f, enter)
    }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xxl)
                .padding(top = 46.dp)
                .graphicsLayer {
                    alpha = title.value
                    val scale = TITLE_START_SCALE + (1f - TITLE_START_SCALE) * title.value
                    scaleX = scale
                    scaleY = scale
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Klondike · Draw ${summary.drawCount}".uppercase(), color = colors.onTableMuted, style = CardGamesTheme.type.overline)
        Text("Solved", color = colors.accent, style = CardGamesTheme.type.victory)
    }
    Column(
        modifier =
            Modifier
                .padding(horizontal = spacing.lg)
                .padding(top = 28.dp)
                .graphicsLayer { alpha = figures.value },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Tile(formatDuration(summary.seconds), "Time", Modifier.weight(1f))
            Tile("${summary.moves}", "Moves", Modifier.weight(1f))
            Tile("${summary.streak}", "Streak", Modifier.weight(1f))
        }
        BestTimeRow(summary)
    }
}

@Composable
private fun Tile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = CardGamesTheme.colors
    Column(
        modifier =
            modifier
                .background(colors.surfaceOnTable, ChromeShapes.Group)
                .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, color = colors.onTable, style = CardGamesTheme.type.score)
        Text(label.uppercase(), color = colors.onTableMuted, style = CardGamesTheme.type.toolLabel)
    }
}

@Composable
private fun BestTimeRow(summary: SolvedSummary) {
    val colors = CardGamesTheme.colors
    val percent = if (summary.gamesPlayed == 0) 0 else summary.gamesWon * PERCENT / summary.gamesPlayed
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.feedPill, ChromeShapes.Tile)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.md),
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(colors.accent, ChromeShapes.Segment),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ParlourIcons.Stats, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (summary.isNewBest) "A new best time" else "Best time is still ${formatDuration(summary.bestSeconds)}",
                color = colors.onTable,
                style = CardGamesTheme.type.rowLabel,
            )
            Text(
                "Won ${summary.gamesWon} of ${summary.gamesPlayed} deals · $percent%",
                color = colors.onTableMuted,
                style = CardGamesTheme.type.rowCaption,
            )
        }
    }
}

/**
 * The tablet's Solved: one cream card, [TABLET_CARD_INSET] in from the sides — the overline,
 * "Solved", then time, moves and streak side by side, counting up from 0, and the best-time line.
 * It scales in 0.92 → 1 as it fades in, like the phone's title.
 */
@Composable
private fun SolvedCard(summary: SolvedSummary) {
    val colors = CardGamesTheme.colors
    val enter = MotionSpec(CardGamesMotion.Base.millis, DecelerateEasing).tween<Float>()
    val shown = remember { Animatable(0f) }
    val still = LocalReduceMotion.current
    val count = remember { Animatable(if (still) 1f else 0f) }
    val countUp = CardGamesMotion.CelebrateLong.tween<Float>()
    LaunchedEffect(Unit) { shown.animateTo(1f, enter) }
    LaunchedEffect(Unit) { count.animateTo(1f, countUp) }
    val counted = { value: Int -> (value * count.value).roundToInt() }
    Column(
        modifier =
            Modifier
                .padding(horizontal = TABLET_CARD_INSET)
                .padding(top = 46.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = shown.value
                    val scale = TITLE_START_SCALE + (1f - TITLE_START_SCALE) * shown.value
                    scaleX = scale
                    scaleY = scale
                }.background(colors.panel, ChromeShapes.Sheet)
                .padding(vertical = CardGamesTheme.spacing.xxl, horizontal = CardGamesTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.md),
    ) {
        Text("Klondike · Draw ${summary.drawCount}".uppercase(), color = colors.onPanelMuted, style = CardGamesTheme.type.overline)
        Text("Solved", color = colors.accent, style = CardGamesTheme.type.victory)
        Row(modifier = Modifier.fillMaxWidth().padding(top = CardGamesTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            CardFigure(formatDuration(counted(summary.seconds)), "Time", Modifier.weight(1f))
            Box(modifier = Modifier.width(1.dp).height(FIGURE_DIVIDER).background(colors.panelLine))
            CardFigure("${counted(summary.moves)}", "Moves", Modifier.weight(1f))
            Box(modifier = Modifier.width(1.dp).height(FIGURE_DIVIDER).background(colors.panelLine))
            CardFigure("${counted(summary.streak)}", "Streak", Modifier.weight(1f))
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.panelLine))
        val percent = if (summary.gamesPlayed == 0) 0 else summary.gamesWon * PERCENT / summary.gamesPlayed
        Text(
            if (summary.isNewBest) "A new best time" else "Best time is still ${formatDuration(summary.bestSeconds)}",
            color = colors.onPanel,
            style = CardGamesTheme.type.rowLabel,
        )
        Text(
            "Won ${summary.gamesWon} of ${summary.gamesPlayed} deals · $percent%",
            color = colors.onPanelMuted,
            style = CardGamesTheme.type.rowCaption,
        )
    }
}

/** A figure on the tablet's Solved card: the value in brass, its label under it. */
@Composable
private fun CardFigure(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = CardGamesTheme.colors.accentDeep, style = CardGamesTheme.type.score)
        Text(label.uppercase(), color = CardGamesTheme.colors.onPanelMuted, style = CardGamesTheme.type.toolLabel)
    }
}

/** The tablet's Solved card and its buttons, in from the sides. */
private val TABLET_CARD_INSET = 80.dp
private val TABLET_BUTTONS_INSET = 40.dp
private val FIGURE_DIVIDER = 36.dp

/** The kings' top under the back button, where there's no table to line them up with. */
private val KINGS_TOP = 66.dp
private val KING_BOB = 8.dp
private const val KING_RING_ALPHA = 0.8f
private const val TITLE_START_SCALE = 0.92f
private const val FIGURES_LAG = 120L
private const val PERCENT = 100
