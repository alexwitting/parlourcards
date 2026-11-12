package com.cardgames.feature.ginrummy.result

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.stats.Record
import com.cardgames.core.ui.chrome.ChromeShapes
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.text.lineAgainst
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.feature.ginrummy.opponentName
import kotlin.math.hypot

// The result screens' pieces: cream cards on the felt, the overline chip, the brass points pill,
// the sum as chips, the race, the final score, and the backdrops — confetti, felt rays and the
// burgundy tint for the opponent's moment.

/** A cream card on the felt holding part of a result. */
@Composable
internal fun ResultCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(CardGamesTheme.colors.panel, ChromeShapes.Sheet)
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/**
 * A result's headline in the result-title face: on one line where it fits at [TITLE_MIN] or larger
 * (the largest size that does), otherwise at full size over two lines — so "You win the hand!"
 * stays one line on a phone and leaves the page room, and a long undercut line still reads whole.
 */
@Composable
internal fun ResultTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    val style = CardGamesTheme.type.resultTitle.copy(color = CardGamesTheme.colors.onTable, textAlign = TextAlign.Center)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = constraints.maxWidth
        val fitted =
            remember(text, style, width) {
                generateSequence(style.fontSize.value) { it - TITLE_STEP }
                    .takeWhile { it >= TITLE_MIN }
                    .map { size -> style.copy(fontSize = size.sp, lineHeight = (size * TITLE_LINE).sp) }
                    .firstOrNull { measurer.measure(text, it, maxLines = 1, softWrap = false).size.width <= width }
            }
        Text(text, style = fitted ?: style, maxLines = if (fitted != null) 1 else Int.MAX_VALUE)
    }
}

/** "HAND 5 · YOU KNOCKED" in a soft pill. */
@Composable
internal fun OverlineChip(text: String) {
    Text(
        text.uppercase(),
        color = CardGamesTheme.colors.onTable,
        style = CardGamesTheme.type.overline,
        modifier =
            Modifier
                .background(CardGamesTheme.colors.chipOnTableSoft, ChromeShapes.Pill)
                .padding(horizontal = CardGamesTheme.spacing.md, vertical = 5.dp),
    )
}

/** The hand's points as a brass pill on its solid base ("+18 points"); a soft one when it's the opponent's. */
@Composable
internal fun PointsPill(
    text: String,
    yours: Boolean,
) {
    val colors = CardGamesTheme.colors
    val base = colors.actionBase
    Text(
        text,
        color = if (yours) colors.onAction else colors.opponentScore,
        style = CardGamesTheme.type.score,
        modifier =
            Modifier
                .padding(bottom = if (yours) PILL_BASE else 0.dp)
                .drawBehind {
                    if (yours) {
                        drawRoundRect(
                            base,
                            topLeft = Offset(0f, PILL_BASE.toPx()),
                            size = size,
                            cornerRadius = CornerRadius(size.height / 2),
                        )
                    }
                }.background(if (yours) colors.action else colors.chipOnTableSoft, ChromeShapes.Pill)
                .padding(horizontal = 18.dp, vertical = CardGamesTheme.spacing.sm),
    )
}

/** The sum as chips — `19 − 1 = 18` — the total in brass. */
@Composable
internal fun SumChips(terms: List<String>) {
    val colors = CardGamesTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        terms.forEachIndexed { index, term ->
            val operator = term.first().let { !it.isDigit() }
            if (operator) {
                Text(term, color = colors.onTableMuted, style = CardGamesTheme.type.rowLabel)
            } else {
                val total = index == terms.lastIndex
                Text(
                    term,
                    color = if (total) colors.onAction else colors.onTable,
                    style = CardGamesTheme.type.rowLabel,
                    modifier =
                        Modifier
                            .background(if (total) colors.action else colors.chipOnTableSoft, ChromeShapes.Segment)
                            .padding(horizontal = CardGamesTheme.spacing.md, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * The race after a hand on a soft card: a bar each towards the target — the points this hand
 * added shown lighter on the winner's — and how far the player is from the match.
 */
@Composable
internal fun RaceCard(
    game: GinState,
    opponentName: String,
    gained: Pair<GinPlayer, Int>?,
) {
    val colors = CardGamesTheme.colors
    val target = game.rules.target
    val you = game.matchScores.getValue(GinPlayer.PLAYER)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceOnTable, ChromeShapes.Button)
                .padding(horizontal = 14.dp, vertical = CardGamesTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm),
    ) {
        GinPlayer.entries.forEach { player ->
            val score = game.matchScores.getValue(player)
            val before = score - (gained?.takeIf { it.first == player }?.second ?: 0)
            val mine = player == GinPlayer.PLAYER
            RaceRow(
                name = if (mine) "You" else opponentName,
                score = score,
                before = before,
                target = target,
                fill = if (mine) colors.accent else colors.opponent,
                ink = if (mine) colors.accent else colors.opponentScore,
            )
        }
        Text(
            buildString {
                if (target > you) append("${target - you} points from the match · ")
                append("boxes ${game.boxes.getValue(GinPlayer.PLAYER)} – ${game.boxes.getValue(GinPlayer.OPPONENT)}")
            },
            color = colors.onTableMuted,
            style = CardGamesTheme.type.toolLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RaceRow(
    name: String,
    score: Int,
    before: Int,
    target: Int,
    fill: Color,
    ink: Color,
) {
    val colors = CardGamesTheme.colors
    val gainFill = lerp(fill, colors.panel, GAIN_LIGHTEN)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(name, color = colors.onTable, style = CardGamesTheme.type.chip, maxLines = 1, modifier = Modifier.width(RACE_NAME))
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(RACE_BAR)
                    .clip(RoundedCornerShape(RACE_BAR / 2))
                    .background(colors.chipOnTableSoft)
                    .drawBehind {
                        val radius = CornerRadius(size.height / 2)
                        val now = (score / target.toFloat()).coerceIn(0f, 1f)
                        val then = (before / target.toFloat()).coerceIn(0f, 1f)
                        drawRoundRect(gainFill, size = Size(size.width * now, size.height), cornerRadius = radius)
                        drawRoundRect(fill, size = Size(size.width * then, size.height), cornerRadius = radius)
                    },
        )
        Text("$score", color = ink, style = CardGamesTheme.type.tally, textAlign = TextAlign.End, modifier = Modifier.width(RACE_SCORE))
    }
}

/** The final race on a cream card: both totals, the player's bar against a goal marker, boxes and goal. */
@Composable
internal fun FinalScoreCard(
    you: Int,
    them: Int,
    opponentName: String,
    game: GinState,
) {
    val colors = CardGamesTheme.colors
    val target = game.rules.target
    ResultCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FinalFigure("$you", "You", colors.onPanel, Modifier.weight(1f))
            Text("FINAL", color = colors.onPanelDisabled, style = CardGamesTheme.type.overline)
            FinalFigure("$them", opponentName, colors.bad, Modifier.weight(1f))
        }
        val goal = colors.onPanel
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(FINAL_BAR)
                    .drawBehind {
                        val radius = CornerRadius(size.height / 2)
                        drawRoundRect(colors.toolTile, cornerRadius = radius)
                        drawRoundRect(
                            colors.accent,
                            size = Size(size.width * (you / target.toFloat()).coerceIn(0f, 1f), size.height),
                            cornerRadius = radius,
                        )
                        val marker = GOAL_MARKER.toPx()
                        val overhang = (GOAL_MARKER_HEIGHT.toPx() - size.height) / 2
                        drawRect(goal, topLeft = Offset(size.width - marker, -overhang), size = Size(marker, size.height + overhang * 2))
                    },
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Boxes ${game.boxes.getValue(GinPlayer.PLAYER)} – ${game.boxes.getValue(GinPlayer.OPPONENT)}",
                color = colors.onPanelMuted,
                style = CardGamesTheme.type.toolLabel,
                modifier = Modifier.weight(1f),
            )
            Text("Goal $target", color = colors.onPanelMuted, style = CardGamesTheme.type.toolLabel)
        }
    }
}

@Composable
private fun FinalFigure(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, style = CardGamesTheme.type.resultTitle)
        Text(label, color = CardGamesTheme.colors.onPanelMuted, style = CardGamesTheme.type.actionSub)
    }
}

/** A highlight on a soft tile: a figure and what it counts. */
@Composable
internal fun HighlightTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = CardGamesTheme.colors
    Column(
        modifier =
            modifier
                .background(
                    colors.surfaceOnTable,
                    ChromeShapes.Tile,
                ).padding(vertical = CardGamesTheme.spacing.sm, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, color = colors.onTable, style = CardGamesTheme.type.score)
        Text(label, color = colors.onTableMuted, style = CardGamesTheme.type.toolLabel, textAlign = TextAlign.Center)
    }
}

/** The record against this opponent over every match: "Still ahead overall · You lead Rosa 12 – 10 in matches". */
@Composable
internal fun OverallRecordRow(
    record: Record,
    opponentName: String,
) {
    val colors = CardGamesTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.feedPill, ChromeShapes.Tile)
                .padding(horizontal = 14.dp, vertical = CardGamesTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.md),
    ) {
        Box(modifier = Modifier.size(RECORD_ICON).background(colors.hint, ChromeShapes.Segment), contentAlignment = Alignment.Center) {
            Icon(ParlourIcons.Stats, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                when {
                    record.wins > record.losses -> "Still ahead overall"
                    record.wins < record.losses -> "Behind overall"
                    else -> "Level overall"
                },
                color = colors.onTable,
                style = CardGamesTheme.type.rowLabel,
            )
            Text("${record.lineAgainst(opponentName)} in matches", color = colors.onTableMuted, style = CardGamesTheme.type.rowCaption)
        }
    }
}

/** The square Home button beside a result's primary action. */
@Composable
internal fun HomeTile(onClick: () -> Unit) {
    val colors = CardGamesTheme.colors
    Box(
        modifier =
            Modifier
                .size(ginResultMetrics.homeTile)
                .clip(ChromeShapes.Soft)
                .background(colors.chipOnTableSoft)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ParlourIcons.Home, contentDescription = "Home", tint = colors.onTable, modifier = Modifier.size(24.dp))
    }
}

/** Faint felt rays fanning out from behind "GIN!": 9° wedges of light, 18° apart. */
@Composable
internal fun FeltRays(modifier: Modifier = Modifier) {
    val ray = CardGamesTheme.colors.onTable.copy(alpha = RAY_ALPHA)
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height * RAY_ORIGIN)
        val reach = hypot(size.width, size.height)
        // Half a wedge off the horizontal, so no ray edge runs straight across behind "GIN!".
        var angle = -RAY_SWEEP / 2
        while (angle < FULL_TURN) {
            drawArc(
                ray,
                startAngle = angle,
                sweepAngle = RAY_SWEEP,
                useCenter = true,
                topLeft = Offset(center.x - reach, center.y - reach),
                size = Size(reach * 2, reach * 2),
            )
            angle += RAY_SWEEP * 2
        }
    }
}

/** The table turned burgundy for the opponent's moment (their knock, their match). */
@Composable
internal fun OpponentTint(modifier: Modifier = Modifier) {
    val colors = CardGamesTheme.colors
    val deep = lerp(colors.opponent, colors.cardBack, TINT_DEEP)
    Canvas(modifier = modifier) {
        drawRect(
            Brush.radialGradient(
                0f to colors.opponent,
                TINT_MID to deep,
                1f to colors.table,
                center = Offset(size.width / 2, size.height * RAY_ORIGIN),
                radius = size.height * TINT_REACH,
            ),
        )
    }
}

private val PILL_BASE = 4.dp
private const val GAIN_LIGHTEN = 0.45f
private val RACE_NAME = 40.dp
private val RACE_BAR = 10.dp
private val RACE_SCORE = 44.dp
private val FINAL_BAR = 12.dp
private val GOAL_MARKER = 2.dp
private val GOAL_MARKER_HEIGHT = 22.dp
private val RECORD_ICON = 36.dp
private const val FULL_TURN = 360f
private const val TITLE_MIN = 30f
private const val TITLE_STEP = 2f
private const val TITLE_LINE = 1.05f

private const val RAY_ALPHA = 0.07f
private const val RAY_SWEEP = 9f
private const val RAY_ORIGIN = 0.3f
private const val TINT_MID = 0.45f
private const val TINT_DEEP = 0.5f
private const val TINT_REACH = 0.6f
