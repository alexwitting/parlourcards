package com.cardgames.feature.ginrummy.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.ui.cards.CARD_ASPECT_RATIO
import com.cardgames.core.ui.cards.CARD_CORNER_PERCENT
import com.cardgames.core.ui.cards.meldEdge
import com.cardgames.core.ui.table.LocalCardPivot
import com.cardgames.core.ui.table.MovableCard
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * The player's melds, centred above the hand: each meld the engine finds as a small fan (44dp
 * cards, 22dp apart, tilted −6° to +6°) with its colour stripe — sage, rose, brass in turn — 14dp
 * between melds, squeezed to fit.
 */
@Composable
internal fun MeldsRow(
    melds: List<List<Card>>,
    look: GinLook,
    input: GinInput?,
    cards: (Card) -> MovableCard,
    modifier: Modifier = Modifier,
    loose: List<Card> = emptyList(),
    present: (Card) -> Boolean = { true },
) {
    val groups = melds + listOfNotNull(loose.takeIf { it.isNotEmpty() })
    val m = LocalGinMetrics.current
    // The meld the card just drawn completed glows, so the new meld is easy to spot.
    val fresh = melds.indexOfFirst { look.drawn != null && look.drawn in it }
    val colors = CardGamesTheme.colors
    Layout(
        modifier = modifier.fillMaxWidth().height(m.meldRowHeight),
        content = {
            var slot = 0
            groups.forEachIndexed { groupIndex, group ->
                val edge = colors.meld[groupIndex % colors.meld.size].takeIf { groupIndex < melds.size }
                group.forEachIndexed { i, card ->
                    val id = slot++
                    if (!present(card)) return@forEachIndexed
                    // Keyed by card: a card's lift, tag and shake state is its own, not its slot's —
                    // otherwise, when a card leaves, the next one inherits its lift and tag for a frame.
                    key(card) {
                        val tilt = if (group.size > 1) MELD_TILT * (2f * i / (group.size - 1) - 1f) else 0f
                        val touch = if (input != null) Modifier.handCard(card, look, input, loose = groupIndex >= melds.size) else Modifier
                        val glow = if (groupIndex == fresh) Modifier.cardGlow(colors.hint) else Modifier
                        CompositionLocalProvider(LocalCardPivot provides HAND_PIVOT) {
                            cards(card)(
                                true,
                                look.glide(card),
                                tilt,
                                Modifier
                                    .layoutId(id)
                                    .then(glow)
                                    .then(touch)
                                    .meldEdge(edge),
                            )
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val width = m.meldCard.roundToPx()
        val height = (width / CARD_ASPECT_RATIO).toInt()
        val placeables = measurables.map { it.measure(Constraints.fixed(width, height)) }
        val gap = m.meldGap.roundToPx()
        val sizes = groups.map { it.size }
        val naturalStep = m.meldStep.roundToPx()
        val natural = sizes.sumOf { width + naturalStep * (it - 1) } + gap * (sizes.size - 1).coerceAtLeast(0)
        val steps = sizes.sumOf { it - 1 }
        val step =
            if (natural <= constraints.maxWidth || steps == 0) {
                naturalStep
            } else {
                ((constraints.maxWidth - sizes.size * width - gap * (sizes.size - 1)) / steps).coerceAtLeast(0)
            }
        // Centred: the melds sit directly above the hand.
        val used = sizes.sumOf { width + step * (it - 1) } + gap * (sizes.size - 1).coerceAtLeast(0)
        val slots =
            buildList {
                var x = ((constraints.maxWidth - used) / 2).coerceAtLeast(0)
                sizes.forEach { size ->
                    repeat(size) { i -> add(x + i * step) }
                    x += width + step * (size - 1) + gap
                }
            }
        layout(constraints.maxWidth, m.meldRowHeight.roundToPx()) {
            placeables.forEachIndexed { index, placeable ->
                placeable.place(slots[measurables[index].layoutId as Int], m.meldRowTop.roundToPx())
            }
        }
    }
}

/**
 * Where melds will go, before there are any: three dashed card outlines in a meld's own small fan
 * (its card size, step and tilt), faint on the felt.
 */
@Composable
internal fun MeldOutline(modifier: Modifier = Modifier) {
    val m = LocalGinMetrics.current
    val color = CardGamesTheme.colors.onTable.copy(alpha = MELD_OUTLINE_ALPHA)
    val width = m.meldCard
    val height = width / CARD_ASPECT_RATIO
    Canvas(modifier = modifier.size(width + m.meldStep * (MELD_OUTLINE_CARDS - 1), height)) {
        val w = width.toPx()
        val h = height.toPx()
        val radius = CornerRadius(w * CARD_CORNER_PERCENT / PERCENT)
        val stroke =
            Stroke(
                width = MELD_OUTLINE_STROKE.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(MELD_OUTLINE_DASH.toPx(), MELD_OUTLINE_GAP.toPx())),
            )
        repeat(MELD_OUTLINE_CARDS) { i ->
            val left = m.meldStep.toPx() * i
            val tilt = MELD_TILT * (2f * i / (MELD_OUTLINE_CARDS - 1) - 1f)
            rotate(tilt, pivot = Offset(left + w / 2f, h * HAND_PIVOT.pivotFractionY)) {
                drawRoundRect(color, topLeft = Offset(left, 0f), size = Size(w, h), cornerRadius = radius, style = stroke)
            }
        }
    }
}

private const val MELD_TILT = 6f
private const val MELD_OUTLINE_CARDS = 3
private const val MELD_OUTLINE_ALPHA = 0.3f
private val MELD_OUTLINE_STROKE = 1.5.dp
private val MELD_OUTLINE_DASH = 5.dp
private val MELD_OUTLINE_GAP = 4.dp
private const val PERCENT = 100f
