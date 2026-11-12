package com.cardgames.feature.solitaire.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.domain.solitaire.TableauPile
import com.cardgames.core.ui.cards.CARD_RING_HINT
import com.cardgames.core.ui.cards.EmptyPileSlot
import com.cardgames.core.ui.cards.OverlappingColumn
import com.cardgames.core.ui.cards.cardRing
import com.cardgames.core.ui.cards.cardShade
import com.cardgames.core.ui.cards.dragSource
import com.cardgames.core.ui.cards.glyph
import com.cardgames.core.ui.cards.symbol
import com.cardgames.core.ui.cards.tapOrHold
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.LocalReduceMotion
import com.cardgames.core.ui.motion.scaledMillis
import com.cardgames.core.ui.motion.tween
import com.cardgames.core.ui.table.MovableCard
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.feature.solitaire.SolitaireIntent
import com.cardgames.feature.solitaire.motion.SolitaireMotionTokens
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Composable
internal fun TableauColumn(
    pile: TableauPile,
    pileIndex: Int,
    isTarget: Boolean,
    look: BoardLook,
    input: BoardInput,
    cards: (Card) -> MovableCard,
) {
    val m = solitaireMetrics
    OverlappingColumn(peekFraction = { index -> if (index < pile.faceDownCount) m.faceDownPeek else m.faceUpPeek }) {
        pile.cards.forEachIndexed { cardIndex, card ->
            val faceUp = cardIndex >= pile.faceDownCount
            val source = SolitaireDragSource.TableauRun(pileIndex, cardIndex)
            cards(card)(
                faceUp,
                look.glide(card),
                0f,
                if (faceUp) {
                    Modifier
                        .dragSource(input.dragState, source, enabled = input.enabled)
                        .tapOrHold(enabled = input.enabled, onHold = { input.onIntent(SolitaireIntent.Hold(source)) }) {
                            input.onIntent(SolitaireIntent.TapTableauCard(pileIndex, cardIndex))
                        }.dressed(card, look, isTargetTop = isTarget && cardIndex == pile.cards.lastIndex)
                } else {
                    Modifier
                },
            )
        }
    }
}

/**
 * Every stock card, stacked face down: the top one shows with the next peeking out beneath it,
 * the rest invisible (kept composed so a deal or draw glides from here). Tapping draws — or turns
 * the waste back over once the stock is empty.
 */
@Composable
internal fun StockPile(
    game: KlondikeState,
    look: BoardLook,
    input: BoardInput,
    cards: (Card) -> MovableCard,
) {
    val colors = CardGamesTheme.colors
    val stockPeek = solitaireMetrics.stockPeek
    val shake = rememberShuffleShake(look.stock.shuffling)
    Box(
        modifier =
            Modifier
                .testTag(SolitaireTags.STOCK)
                .graphicsLayer { translationX = shake.value }
                .clickable(
                    enabled = input.enabled && (game.stock.isNotEmpty() || game.waste.isNotEmpty()),
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { input.onIntent(SolitaireIntent.TapStock) },
    ) {
        EmptyPileSlot(
            glyph = if (game.waste.isNotEmpty()) RECYCLE_GLYPH else null,
            modifier = Modifier.targetRing(look.stock.ringed && game.stock.isEmpty()),
        )
        val stock = game.stock
        stock.forEachIndexed { index, card ->
            val depth = stock.lastIndex - index
            cards(card)(
                false,
                look.glide(card),
                0f,
                Modifier
                    .graphicsLayer {
                        alpha = if (depth <= 1 || look.stock.gathering) 1f else 0f
                        translationX = if (depth == 1) stockPeek.toPx() else 0f
                        translationY = if (depth == 1) stockPeek.toPx() else 0f
                    }.then(if (depth == 1) Modifier.cardShade(UNDER_BACK_SHADE) else Modifier)
                    .then(if (depth == 0 && look.stock.ringed) Modifier.cardRing(colors.hint, CARD_RING_HINT) else Modifier),
            )
        }
    }
}

/**
 * Every waste card: the shown ones (the top card in draw 1, up to three from the latest draw in
 * draw 3, fanned [SolitaireMetrics.wasteFanStep] apart) and the rest stacked invisibly under the
 * first. Only the top card can be played.
 */
@Composable
internal fun WastePile(
    game: KlondikeState,
    look: BoardLook,
    input: BoardInput,
    cards: (Card) -> MovableCard,
) {
    val waste = game.waste
    if (waste.isEmpty()) return
    val shown = if (game.drawCount == DRAW_THREE) minOf(maxOf(game.dealBatchRemaining, 1), DRAW_THREE, waste.size) else 1
    val firstShown = waste.size - shown
    val step = solitaireMetrics.wasteFanStep
    Layout(
        content = {
            waste.forEachIndexed { index, card ->
                val isTop = index == waste.lastIndex
                cards(card)(
                    true,
                    look.glide(card),
                    0f,
                    if (isTop) {
                        Modifier
                            .dragSource(input.dragState, SolitaireDragSource.Waste, enabled = input.enabled)
                            .tapOrHold(
                                enabled = input.enabled,
                                onHold = { input.onIntent(SolitaireIntent.Hold(SolitaireDragSource.Waste)) },
                            ) {
                                input.onIntent(SolitaireIntent.TapWaste)
                            }.dressed(card, look)
                    } else {
                        Modifier.alpha(if (index >= firstShown - 1) 1f else 0f)
                    },
                )
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = constraints.maxWidth, minHeight = 0)) }
        val stepPx = step.roundToPx()
        // Draw 1's single card sits where draw 3's top card would, so nothing shifts between modes.
        val start = if (game.drawCount == DRAW_THREE) 0 else (DRAW_THREE - 1) * stepPx
        layout(constraints.maxWidth, placeables.maxOf { it.height }) {
            placeables.forEachIndexed { index, placeable ->
                placeable.place(start + maxOf(index - firstShown, 0) * stepPx, 0)
            }
        }
    }
}

/**
 * Every card of [suit]'s foundation, stacked, with the top one playable back down. The slot rings
 * briefly when a card arrives (0 → 4dp → 0), and shows the suit faintly while empty.
 */
@Composable
internal fun FoundationPile(
    game: KlondikeState,
    suit: Suit,
    look: BoardLook,
    input: BoardInput,
    cards: (Card) -> MovableCard,
) {
    val colors = CardGamesTheme.colors
    val count = game.foundationCount(suit)
    val target = SolitaireDropTarget.Foundation(suit)
    val isTarget = target in look.targets
    val pulse = remember(suit) { Animatable(0f) }
    val pulseIn = CardGamesMotion.Quick.tween<Float>()
    val lastCount = remember(suit) { intArrayOf(count) }
    LaunchedEffect(count) {
        if (count > lastCount[0]) {
            pulse.animateTo(1f, pulseIn)
            pulse.animateTo(0f, pulseIn)
        }
        lastCount[0] = count
    }
    Box(
        modifier =
            Modifier
                .solitaireDropTarget(input, game, target)
                .clickable(
                    enabled = input.enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { input.onIntent(SolitaireIntent.TapFoundation(suit)) },
    ) {
        EmptyPileSlot(
            glyph = suit.symbol,
            modifier =
                Modifier
                    .targetRing(isTarget && count == 0)
                    .then(
                        if (pulse.value >
                            0f
                        ) {
                            Modifier.cardRing(colors.accent.copy(alpha = pulse.value), PULSE_WIDTH * pulse.value)
                        } else {
                            Modifier
                        },
                    ),
        )
        val lowestMoving = (0 until count).firstOrNull { Card(suit, Rank.entries[it]) in look.moving }
        repeat(count) { index ->
            val card = Card(suit, Rank.entries[index])
            val isTop = index == count - 1
            cards(card)(
                true,
                look.glide(card),
                0f,
                if (isTop) {
                    val source = SolitaireDragSource.Foundation(suit)
                    Modifier
                        .dragSource(input.dragState, source, enabled = input.enabled)
                        .tapOrHold(enabled = input.enabled, onHold = { input.onIntent(SolitaireIntent.Hold(source)) }) {
                            input.onIntent(SolitaireIntent.TapFoundation(suit))
                        }.dressed(card, look, isTargetTop = isTarget)
                } else {
                    Modifier.alpha(if (foundationCardShown(index, count, lowestMoving)) 1f else 0f)
                },
            )
        }
    }
}

/** The sage ring on an empty slot that's a valid destination. */
@Composable
internal fun Modifier.targetRing(show: Boolean): Modifier = if (show) cardRing(CardGamesTheme.colors.hint, CARD_RING_HINT) else this

private const val UNDER_BACK_SHADE = 0.2f
private val PULSE_WIDTH = 4.dp
private const val RECYCLE_GLYPH = "↺"

/** The stock's shuffle: three quick 4dp shakes, [SolitaireMotionTokens.SHUFFLE_MILLIS] in all; still under Reduce motion. */
@Composable
private fun rememberShuffleShake(shuffling: Boolean): Animatable<Float, AnimationVector1D> {
    val shake = remember { Animatable(0f) }
    val distance = with(LocalDensity.current) { SolitaireMotionTokens.SHUFFLE_SHAKE.toPx() }
    val still = LocalReduceMotion.current
    val millis = scaledMillis(SolitaireMotionTokens.SHUFFLE_MILLIS)
    LaunchedEffect(shuffling) {
        if (!shuffling || still) return@LaunchedEffect
        // Every beat, the return to centre included, fits inside the shuffle's time — and if the deal
        // starts anyway (cancelling this), the stock is put back: a shake cut short left it 4dp left.
        val beat = (millis / (SolitaireMotionTokens.SHUFFLE_SHAKES * 2 + 1)).toInt()
        try {
            repeat(SolitaireMotionTokens.SHUFFLE_SHAKES) {
                shake.animateTo(distance, tween(beat))
                shake.animateTo(-distance, tween(beat))
            }
            shake.animateTo(0f, tween(beat))
        } finally {
            withContext(NonCancellable) { shake.snapTo(0f) }
        }
    }
    return shake
}

/** Test tags on the table, for UI tests and the Baseline Profile journeys. */
internal object SolitaireTags {
    const val STOCK = "solitaire:stock"
    const val MOVES = "solitaire:moves"
    const val TIME = "solitaire:time"
}

/**
 * Whether the foundation card at [index] of [count] is drawn. At rest only the top two (the second
 * shows as the top one lifts away); the rest are covered. While a move carries cards up — Complete
 * sends many at once — every card from just below the lowest one still on its way ([lowestMoving])
 * is drawn too: hidden by their final places, the cards in flight were invisible and the pile looked
 * emptied until the last two arrived.
 */
internal fun foundationCardShown(
    index: Int,
    count: Int,
    lowestMoving: Int?,
): Boolean = index >= count - 2 || (lowestMoving != null && index >= lowestMoving - 1)
