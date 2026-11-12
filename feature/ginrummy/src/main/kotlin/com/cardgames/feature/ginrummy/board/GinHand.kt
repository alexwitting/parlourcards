package com.cardgames.feature.ginrummy.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.ui.cards.CARD_ASPECT_RATIO
import com.cardgames.core.ui.cards.CARD_CORNER_PERCENT
import com.cardgames.core.ui.cards.CARD_RING_SELECT
import com.cardgames.core.ui.cards.cardRing
import com.cardgames.core.ui.cards.dragSource
import com.cardgames.core.ui.cards.label
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.LocalReduceMotion
import com.cardgames.core.ui.motion.scaledMillis
import com.cardgames.core.ui.motion.spring
import com.cardgames.core.ui.motion.table
import com.cardgames.core.ui.motion.tween
import com.cardgames.core.ui.table.LocalCardPivot
import com.cardgames.core.ui.table.MovableCard
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.feature.ginrummy.GinIntent
import com.cardgames.feature.ginrummy.motion.ginMotionDistances

/**
 * The hand: the cards not in a meld, as a held fan centred under the melds — each card turned about
 * a point below it, so the row arcs like a hand. Up to four loose cards sit large and nearly side by
 * side; more sit smaller and closer on a gentle curve, the end cards turned at most 9°, fitted to the
 * screen by their turned corners (the design's two layouts).
 * A tap lifts one with a brass ring and a tag saying what throwing it does to the deadwood
 * (`−8 pts`); a second tap (or a tap on the pile) throws it. The card just drawn waits at the right
 * end, ringed in sage with a NEW tag; cards that would meld with the pile card lift in sage.
 */
@Composable
internal fun InPlay(
    loose: List<Card>,
    deadwoodChange: (Card) -> Int,
    look: GinLook,
    input: GinInput,
    cards: (Card) -> MovableCard,
    modifier: Modifier = Modifier,
    present: (Card) -> Boolean = { true },
) {
    val m = LocalGinMetrics.current
    val few = loose.size <= FEW_LOOSE
    val cardWidth = if (few) m.handCard else m.handCardMany
    val fan = if (few) m.fewFan else m.manyFan
    CompositionLocalProvider(LocalCardPivot provides HAND_PIVOT) {
        Layout(
            modifier = modifier.fillMaxWidth().height(m.handHeight),
            content = {
                loose.forEachIndexed { index, card ->
                    if (!present(card)) return@forEachIndexed
                    // Keyed by card, as in the melds row: each card's lift and tag stay with it.
                    key(card) {
                        val change = deadwoodChange(card)
                        val tag =
                            when {
                                card == look.selected -> if (change > 0) "+$change pts" else "−${-change} pts"
                                card == look.drawn -> "NEW"
                                else -> null
                            }
                        cards(card)(
                            true,
                            look.glide(card),
                            fan.turnOf(index, loose.size),
                            Modifier.layoutId(index).handCard(card, look, input, tag = tag),
                        )
                    }
                }
            },
        ) { measurables, constraints ->
            val width = cardWidth.roundToPx()
            val height = (width / CARD_ASPECT_RATIO).toInt()
            val placeables = measurables.map { it.measure(Constraints.fixed(width, height)) }
            val count = loose.size
            // Fitted by the cards as drawn — each turned about a point below it, so the end cards'
            // corners swing outwards — to the row's width plus most of its margin, never past it.
            val first = turnedSpan(width, height, fan.turnOf(0, count))
            val last = turnedSpan(width, height, fan.turnOf(count - 1, count))
            val room = constraints.maxWidth + 2 * m.fanSpill.roundToPx()
            val ends = last.second - first.first
            val step = if (count > 1) minOf(width * fan.overlap, (room - ends) / (count - 1)).toInt() else 0
            val left = ((constraints.maxWidth - (ends + step * (count - 1))) / 2 - first.first).toInt()
            val mid = (count - 1) / 2f
            layout(constraints.maxWidth, m.handHeight.roundToPx()) {
                placeables.forEachIndexed { at, placeable ->
                    val index = measurables[at].layoutId as Int
                    // A gentle curve: the end cards sit lowest.
                    val edge = if (mid > 0f) (index - mid) / mid else 0f
                    val y = fan.rest.toPx() + fan.arch.toPx() * edge * edge
                    placeable.place(left + index * step, y.toInt())
                }
            }
        }
    }
}

/**
 * How far left and right of its own left edge a [width] × [height] card reaches once turned
 * [degrees] about [HAND_PIVOT].
 */
private fun turnedSpan(
    width: Int,
    height: Int,
    degrees: Float,
): Pair<Float, Float> {
    val radians = Math.toRadians(degrees.toDouble())
    val cos = kotlin.math.cos(radians).toFloat()
    val sin = kotlin.math.sin(radians).toFloat()
    val px = width * HAND_PIVOT.pivotFractionX
    val py = height * HAND_PIVOT.pivotFractionY
    val xs =
        listOf(0f to 0f, width.toFloat() to 0f, 0f to height.toFloat(), width.toFloat() to height.toFloat()).map { (x, y) ->
            px + (x - px) * cos - (y - py) * sin
        }
    return xs.min() to xs.max()
}

/** A hand card's touch and dress: drag to throw, tap to lift, tap again to throw; rings and lift. */
@Composable
internal fun Modifier.handCard(
    card: Card,
    look: GinLook,
    input: GinInput,
    loose: Boolean = true,
    tag: String? = null,
): Modifier {
    val colors = CardGamesTheme.colors
    val selected = card == look.selected
    val rejected = card == look.rejected
    val discarding = input.canAct && input.phase is GinPhase.AwaitingDiscard
    val shake = remember(card) { Animatable(0f) }
    val shakeMillis = CardGamesMotion.Shake.scaledMillis()
    val shakePx = with(LocalDensity.current) { CardGamesMotion.table.shakeDistance.toPx() }
    val still = LocalReduceMotion.current
    LaunchedEffect(rejected) {
        // Reduce motion: the red ring alone says no.
        if (rejected && !still) {
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = shakeMillis
                    val beat = shakeMillis / (CardGamesMotion.SHAKE_CYCLES * 2 + 1)
                    repeat(CardGamesMotion.SHAKE_CYCLES) { cycle ->
                        shakePx at beat * (cycle * 2 + 1)
                        -shakePx at beat * (cycle * 2 + 2)
                    }
                },
            )
        }
    }
    // A card in a meld only shows as selected; its meld's glow says it was just drawn.
    val newOrMatching = loose && (card == look.drawn || card in look.matching)
    val distances = ginMotionDistances
    val lift =
        when {
            selected -> distances.selectedLift
            newOrMatching && card == look.drawn -> distances.drawnLift
            newOrMatching -> distances.matchLift
            else -> 0.dp
        }
    // Rising and settling on the lift spring, not jumping a frame.
    val lifted by animateDpAsState(lift, CardGamesMotion.Lift.spring(Dp.VisibilityThreshold), label = "lift")
    return this
        .graphicsLayer {
            translationY = -lifted.toPx()
            translationX = shake.value
        }.dragSource(input.dragState, GinDragSource.HandCard(card), enabled = discarding)
        .clickable(enabled = discarding, interactionSource = remember { MutableInteractionSource() }, indication = null) {
            if (selected) {
                input.onIntent(GinIntent.DropCard(GinDragSource.HandCard(card), GinDropTarget.Discard, carried = false))
            } else {
                input.onIntent(GinIntent.SelectHandCard(card))
            }
        }.fade(if (input.dragState.payload == GinDragSource.HandCard(card)) 0f else 1f)
        // Outside the ring, so the ring's stroke along the top edge doesn't run through it.
        .pointsTag(tag, sage = card != look.selected)
        .then(
            when {
                rejected -> Modifier.cardRing(colors.bad, CARD_RING_SELECT)
                selected -> Modifier.cardRing(colors.accent, SELECTED_RING)
                newOrMatching -> Modifier.cardGlow(colors.hint).cardRing(colors.hint, MATCH_RING)
                else -> Modifier
            },
        )
}

/**
 * A small pill on a hand card: brass, centred 30dp above it, for the points a throw saves
 * ("−8 pts") — clear of the neighbouring cards — or, [sage], centred over its top edge for the
 * card just drawn ("NEW"). It fades in once the card has risen, and out as it drops.
 */
@Composable
private fun Modifier.pointsTag(
    text: String?,
    sage: Boolean = false,
): Modifier {
    val colors = CardGamesTheme.colors
    val measurer = rememberTextMeasurer()
    val style = CardGamesTheme.type.chip.copy(color = colors.onAccent)
    // The last tag shown (a plain holder, not state), so it can still be drawn while it fades out.
    val last = remember { arrayOfNulls<String>(1) }
    if (text != null) last[0] = text
    val alpha by animateFloatAsState(
        if (text != null) 1f else 0f,
        CardGamesMotion.Quick.tween(delay = CardGamesMotion.Quick.takeIf { text != null }),
        label = "tag",
    )
    val label = last[0] ?: return this
    val rise = LocalGinMetrics.current.tagRise
    return drawWithContent {
        drawContent()
        if (alpha == 0f) return@drawWithContent
        val layout = measurer.measure(label, style)
        val padX = TAG_PAD_X.toPx()
        val padY = TAG_PAD_Y.toPx()
        val w = layout.size.width + padX * 2
        val h = layout.size.height + padY * 2
        val left = (size.width - w) / 2
        val top = if (sage) -h / 2 else -rise.toPx()
        drawRoundRect(if (sage) colors.hint else colors.accent, Offset(left, top), Size(w, h), CornerRadius(h / 2), alpha = alpha)
        drawText(layout, topLeft = Offset(left + padX, top + padY), alpha = alpha)
    }
}

/** A soft glow in [color] around a card — a hint that it matters right now (it melds, or it's new). */
internal fun Modifier.cardGlow(color: Color): Modifier =
    shadow(GLOW_ELEVATION, RoundedCornerShape(percent = CARD_CORNER_PERCENT), clip = false, ambientColor = color, spotColor = color)

/** The fan's tilt for card [index] of [count]: 3.5° per step from the middle. */
internal fun fanTilt(
    index: Int,
    count: Int,
): Float = (index - (count - 1) / 2f) * OPPONENT_TILT

private const val OPPONENT_TILT = 4f
private val SELECTED_RING: Dp = 4.dp
private val MATCH_RING: Dp = 3.dp
private val GLOW_ELEVATION: Dp = 12.dp
private const val FEW_LOOSE = 4

private val TAG_PAD_X = 7.dp
private val TAG_PAD_Y = 2.dp
