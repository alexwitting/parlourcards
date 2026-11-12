package com.cardgames.feature.ginrummy.board

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.ui.cards.label
import com.cardgames.core.ui.chrome.Avatar
import com.cardgames.core.ui.chrome.FeedPill
import com.cardgames.core.ui.chrome.TableChip
import com.cardgames.core.ui.chrome.softSurface
import com.cardgames.core.ui.motion.LocalReduceMotion
import com.cardgames.core.ui.text.initials
import com.cardgames.core.ui.text.levelName
import com.cardgames.core.ui.text.opponentName
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.feature.ginrummy.FEED_LENGTH
import com.cardgames.feature.ginrummy.GinFeedLine
import com.cardgames.feature.ginrummy.motion.GinMotionTokens

// The gin table's chrome on the felt: the score card under the top bar, the opponent's name and
// speech bubble over their fan, and the action message under the piles.

/**
 * The match on a soft card: your brass avatar, the two scores with the boxes between them and a
 * bar each towards the target, the opponent's avatar.
 */
@Composable
internal fun ScoreCard(
    game: GinState,
    opponent: GinDifficulty,
    modifier: Modifier = Modifier,
) {
    val colors = CardGamesTheme.colors
    val you = game.matchScores.getValue(GinPlayer.PLAYER)
    val them = game.matchScores.getValue(GinPlayer.OPPONENT)
    val target = game.rules.target.toFloat()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .softSurface(colors.surfaceOnTable)
                .padding(horizontal = 14.dp, vertical = SCORE_CARD_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.md),
    ) {
        val halo = colors.accent.copy(alpha = HALO_ALPHA)
        Avatar(
            "You",
            fill = colors.accent,
            ink = colors.onAccent,
            modifier = Modifier.drawBehind { drawCircle(halo, radius = size.minDimension / 2 + HALO.toPx()) },
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$you", color = colors.accent, style = CardGamesTheme.type.score)
                Text(
                    "BOXES ${game.boxes.getValue(GinPlayer.PLAYER)} – ${game.boxes.getValue(GinPlayer.OPPONENT)}",
                    color = colors.onTableMuted,
                    style = CardGamesTheme.type.toolLabel,
                    modifier = Modifier.weight(1f).padding(horizontal = CardGamesTheme.spacing.sm),
                    textAlign = TextAlign.Center,
                )
                Text("$them", color = colors.opponentScore, style = CardGamesTheme.type.score)
            }
            ScoreBar(you / target, colors.accent)
            ScoreBar(them / target, colors.opponent)
        }
        Avatar(opponent.initials, fill = colors.opponent, ink = colors.onTable)
    }
}

@Composable
private fun ScoreBar(
    fraction: Float,
    color: Color,
) {
    val shape = RoundedCornerShape(BAR_HEIGHT / 2)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .background(CardGamesTheme.colors.chipOnTableSoft, shape),
    ) {
        Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(BAR_HEIGHT).background(color, shape))
    }
}

/** What the opponent says to the player right now, or null when they're quiet. */
internal sealed interface Bubble {
    data class Says(
        val text: String,
    ) : Bubble

    data object Thinking : Bubble
}

/**
 * The opponent's seat: their name with a difficulty chip and a card-count chip on the left, a
 * speech bubble on the right, and [fan] — their hand — underneath.
 */
@Composable
internal fun OpponentSeat(
    opponent: GinDifficulty,
    cardCount: Int,
    bubble: Bubble?,
    modifier: Modifier = Modifier,
    fan: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        fan()
        SeatLabels(opponent, cardCount, bubble)
    }
}

/**
 * The seat's top line: the name and level chip at the left, the speech bubble at the right, and
 * the card-count chip after the level — left out when the bubble would cover it (larger text), as
 * the fan below shows the count anyway.
 */
@Composable
private fun SeatLabels(
    opponent: GinDifficulty,
    cardCount: Int,
    bubble: Bubble?,
) {
    val colors = CardGamesTheme.colors
    val gap = CardGamesTheme.spacing.sm
    // Subcomposed, so the count chip is only composed (shown and announced) when it fits.
    SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val lead =
            subcompose(SeatSlot.LEAD) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Text(opponent.opponentName, color = colors.onTable, style = CardGamesTheme.type.pill)
                    TableChip("${opponent.levelName} bot")
                }
            }.single().measure(loose)
        val said = bubble?.let { subcompose(SeatSlot.BUBBLE) { SpeechBubble(it) }.single().measure(loose) }
        val margin = SEAT_MARGIN.roundToPx()
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val bubbleLeft = width - margin - (said?.width ?: 0)
        val countLeft = margin + lead.width + gapPx
        val countText = if (cardCount == 1) "1 card" else "$cardCount cards"
        // Sized by a copy with no semantics; the real chip is composed only when it fits.
        val countWidth =
            subcompose(SeatSlot.COUNT_SIZER) { Box(Modifier.clearAndSetSemantics {}) { TableChip(countText, deep = true) } }
                .single()
                .measure(loose)
                .width
        val count =
            if (said == null || countLeft + countWidth + gapPx <= bubbleLeft) {
                subcompose(SeatSlot.COUNT) { TableChip(countText, deep = true) }.single().measure(loose)
            } else {
                null
            }
        val top = SEAT_LABELS_TOP.roundToPx()
        val bubbleTop = BUBBLE_TOP.roundToPx()
        val line = maxOf(lead.height, count?.height ?: 0)
        val height = maxOf(top + line, bubbleTop + (said?.height ?: 0))
        layout(width, height) {
            lead.place(margin, top + (line - lead.height) / 2)
            count?.place(countLeft, top + (line - count.height) / 2)
            said?.place(bubbleLeft, bubbleTop)
        }
    }
}

/** A cream bubble, its tail corner bottom right. */
@Composable
private fun SpeechBubble(
    bubble: Bubble,
    modifier: Modifier = Modifier,
) {
    val colors = CardGamesTheme.colors
    Row(
        modifier =
            modifier
                .shadow(BUBBLE_ELEVATION, BubbleShape, ambientColor = BUBBLE_SHADOW, spotColor = BUBBLE_SHADOW)
                .background(colors.panel, BubbleShape)
                .padding(horizontal = CardGamesTheme.spacing.md, vertical = CardGamesTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm),
    ) {
        when (bubble) {
            is Bubble.Says -> Text(bubble.text, color = colors.onPanel, style = CardGamesTheme.type.pill)
            Bubble.Thinking -> {
                Text("Thinking", color = colors.onPanel, style = CardGamesTheme.type.pill)
                ThinkingDots(colors.opponent)
            }
        }
    }
}

/** Three dots pulsing 0.25 ↔ 1, each 160ms behind the last; still under Reduce motion. */
@Composable
private fun ThinkingDots(color: Color) {
    val still = LocalReduceMotion.current
    val pulse = rememberInfiniteTransition(label = "thinking")
    Row(horizontalArrangement = Arrangement.spacedBy(DOT_GAP)) {
        repeat(DOT_COUNT) { index ->
            val alpha by pulse.animateFloat(
                initialValue = DOT_DIM,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        tween(GinMotionTokens.THINKING_DOT_PULSE, delayMillis = index * GinMotionTokens.THINKING_DOT_LAG),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "dot$index",
            )
            Box(
                modifier =
                    Modifier
                        .size(DOT_SIZE)
                        .alpha(if (still) 1f - index * STILL_DOT_FADE else alpha)
                        .background(color, CircleShape),
            )
        }
    }
}

/**
 * The action message: one line, always — the last move, joined to the one before when both were
 * the same player's ("Rosa drew from the stock · discarded **K♣**"). Its height is kept even when
 * empty, so the table below never jumps as the message comes and goes.
 */
@Composable
internal fun ActionFeed(
    feed: List<GinFeedLine>,
    opponent: GinDifficulty,
    modifier: Modifier = Modifier,
) {
    val colors = CardGamesTheme.colors
    Box(modifier = modifier.height(FEED_HEIGHT), contentAlignment = Alignment.Center) {
        val last = feed.lastOrNull() ?: return@Box
        val mine = last.actor == GinPlayer.PLAYER
        val emphasis = colors.feedEmphasis
        val sameActor = feed.size >= FEED_LENGTH && feed.first().actor == last.actor
        val text =
            buildAnnotatedString {
                append(if (mine) "You" else opponent.opponentName)
                if (sameActor) {
                    feed.first().appendTo(this, emphasis)
                    append(" ·")
                }
                last.appendTo(this, emphasis)
            }
        FeedPill(
            initials = if (mine) "You" else opponent.initials,
            avatarFill = if (mine) colors.accent else colors.opponent,
            avatarInk = if (mine) colors.onAccent else colors.onTable,
            text = text,
        )
    }
}

/** " discarded **K♣**", " took **K♣** · and made a new meld" — a line's words after the name. */
private fun GinFeedLine.appendTo(
    builder: AnnotatedString.Builder,
    emphasis: Color,
) = with(builder) {
    append(" $verb")
    card?.let {
        append(" ")
        withStyle(SpanStyle(color = emphasis, fontWeight = FontWeight.Bold)) { append(it.label()) }
    }
    tail?.let { append(" · $it") }
}

private val BubbleShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp)
private val BUBBLE_ELEVATION = 12.dp

/** The bubble's shadow: dark felt, so it lifts clear of the fan and chips beneath it. */
private val BUBBLE_SHADOW = Color(0xFF081610)
private val SEAT_MARGIN = 18.dp
private val SEAT_LABELS_TOP = 10.dp

private enum class SeatSlot { LEAD, COUNT_SIZER, COUNT, BUBBLE }

/** Sets the bubble level with the name row beside it (their centres line up). */
private val BUBBLE_TOP = 4.dp
private val BAR_HEIGHT = 6.dp
private val SCORE_CARD_PADDING = 7.dp
private val HALO = 3.dp
private const val HALO_ALPHA = 0.35f
private val FEED_HEIGHT = 34.dp
private const val DOT_COUNT = 3
private val DOT_SIZE = 6.dp
private val DOT_GAP = 4.dp
private const val DOT_DIM = 0.25f
private const val STILL_DOT_FADE = 0.35f
