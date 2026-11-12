package com.cardgames.feature.ginrummy.board

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.ui.cards.CARD_RING_HINT
import com.cardgames.core.ui.cards.CARD_RING_SELECT
import com.cardgames.core.ui.cards.EmptyPileSlot
import com.cardgames.core.ui.cards.PlayingCard
import com.cardgames.core.ui.cards.cardRing
import com.cardgames.core.ui.cards.cardShade
import com.cardgames.core.ui.cards.dragSource
import com.cardgames.core.ui.cards.label
import com.cardgames.core.ui.chrome.CountBadge
import com.cardgames.core.ui.chrome.PrimaryButton
import com.cardgames.core.ui.chrome.SecondaryButton
import com.cardgames.core.ui.chrome.TableChip
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.tween
import com.cardgames.core.ui.table.MovableCard
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.feature.ginrummy.GinIntent

/**
 * The stock (three backs stacked, the lower two a shade darker, every other card stacked
 * invisibly beneath so a deal or draw glides from here, and a brass badge with the count) and the
 * discard pile (each card landed at its own slight tilt), 28dp apart, with their labels. The stock
 * dims while the player decides on the upcard. A discard that would make a meld wears a sage ring
 * and a "Makes a meld" chip.
 */
@Composable
internal fun Piles(
    game: GinState,
    look: GinLook,
    input: GinInput,
    cards: (Card) -> MovableCard,
    modifier: Modifier = Modifier,
) {
    val phase = game.phase
    val canDrawStock = input.canAct && phase is GinPhase.AwaitingDraw && game.stock.size > GinState.STOCK_RESERVE
    val canTakeUpcard = input.canAct && phase is GinPhase.UpcardDecision
    val canDrawDiscard = input.canAct && phase is GinPhase.AwaitingDraw
    val discarding = input.canAct && phase is GinPhase.AwaitingDiscard
    // Faded per draw, not through a layer — a layer would clip the count badge overhanging the stock.
    val stockAlpha by animateFloatAsState(
        if (canTakeUpcard) STOCK_WAITING_ALPHA else 1f,
        CardGamesMotion.StatusFade.tween(),
        label = "stock",
    )
    // Each pile's column is exactly the pile's width and its label hangs centred beneath: sized by
    // their labels ("Draw blind", "Tap to discard"), the columns would shift the piles as they change.
    val m = LocalGinMetrics.current
    val column = Modifier.width(m.pileWidth)
    val label = Modifier.wrapContentWidth(unbounded = true)
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(m.pileGap, Alignment.CenterHorizontally),
        ) {
            Column(column, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(m.pileLabelGap)) {
                Stock(
                    game,
                    look,
                    input,
                    cards,
                    canDrawStock,
                    modifier =
                        Modifier
                            .testTag(GinTableTags.STOCK)
                            .graphicsLayer {
                                alpha = stockAlpha
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            },
                )
                if (m.pileLabels) PileLabel(if (canDrawStock) "Draw blind" else "Stock", modifier = label)
            }
            Column(column, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(m.pileLabelGap)) {
                Box(
                    modifier =
                        Modifier
                            .width(LocalGinMetrics.current.pileWidth)
                            .testTag(GinTableTags.DISCARD)
                            .ginDropTarget(input.dragState, game, GinDropTarget.Discard, input.onIntent)
                            .clickable(
                                enabled = canTakeUpcard || canDrawDiscard || (discarding && input.selected != null),
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                val selected = input.selected
                                when {
                                    discarding && selected != null ->
                                        input.onIntent(
                                            GinIntent.DropCard(GinDragSource.HandCard(selected), GinDropTarget.Discard, carried = false),
                                        )
                                    canTakeUpcard -> input.onIntent(GinIntent.TakeUpcard)
                                    else -> input.onIntent(GinIntent.DrawFromDiscard)
                                }
                            },
                ) {
                    DiscardPile(game, look, input, cards, canTake = canTakeUpcard || canDrawDiscard)
                    if (look.piles.discardMakesMeld && m.pileLabels) {
                        TableChip(
                            "Makes a meld",
                            fill = CardGamesTheme.colors.hint,
                            ink = CardGamesTheme.colors.onAccent,
                            modifier =
                                Modifier
                                    .align(
                                        Alignment.BottomCenter,
                                    ).wrapContentWidth(unbounded = true)
                                    .offset(y = m.meldChipDrop)
                                    .zIndex(CHIP_Z),
                        )
                    }
                }
                val top = game.discardPile.lastOrNull()
                when {
                    !m.pileLabels -> Unit
                    look.piles.discardMakesMeld && top != null ->
                        // Nudged down past the chip by an offset, not padding: the row keeps its height, so the
                        // hand below doesn't drop for as long as the pile offers a meld.
                        PileLabel(
                            "Take ${top.label()}",
                            color = CardGamesTheme.colors.hint,
                            modifier = label.offset(y = m.meldChipDrop / 2),
                        )
                    discarding -> PileLabel("Tap to discard", modifier = label)
                    else -> PileLabel("Discard", modifier = label)
                }
            }
        }
        if (m.upcardOnTable) UpcardChoice(shown = canTakeUpcard, upcard = game.discardPile.lastOrNull(), onIntent = input.onIntent)
    }
}

/**
 * The tablet's say on the upcard, under the piles: Pass, or Take it. Its row keeps its height when
 * hidden, so nothing below moves as the choice comes and goes.
 */
@Composable
private fun UpcardChoice(
    shown: Boolean,
    upcard: Card?,
    onIntent: (GinIntent) -> Unit,
) {
    val alpha by animateFloatAsState(if (shown) 1f else 0f, CardGamesMotion.StatusFade.tween(), label = "upcard choice")
    Row(
        modifier =
            Modifier
                .padding(top = CardGamesTheme.spacing.lg)
                .width(UPCARD_CHOICE_WIDTH)
                .graphicsLayer { this.alpha = alpha },
        horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm),
    ) {
        SecondaryButton(text = "Pass", onClick = { onIntent(GinIntent.PassUpcard) }, enabled = shown, modifier = Modifier.weight(1f))
        PrimaryButton(
            text = upcard?.let { "Take ${it.label()}" } ?: "Take",
            onClick = { onIntent(GinIntent.TakeUpcard) },
            enabled = shown,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The tablet's Pass / Take row. */
private val UPCARD_CHOICE_WIDTH = 360.dp

@Composable
private fun Stock(
    game: GinState,
    look: GinLook,
    input: GinInput,
    cards: (Card) -> MovableCard,
    canDraw: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = CardGamesTheme.colors
    val m = LocalGinMetrics.current
    Box(
        modifier =
            modifier
                .width(m.pileWidth)
                .clickable(enabled = canDraw, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    input.onIntent(GinIntent.DrawFromStock)
                },
    ) {
        EmptyPileSlot()
        // The darker backs beneath are scenery, not particular cards: were they the next cards
        // down, each draw would slide the new top card out of its peek and lighten it — the
        // pile visibly shuffling under the card in flight.
        for (depth in (game.stock.size - 1).coerceAtMost(STOCK_SHOWN - 1) downTo 1) {
            PlayingCard(
                card = game.stock.first(),
                faceUp = false,
                modifier =
                    Modifier
                        .graphicsLayer {
                            translationX = m.stockPeek.toPx() * depth
                            translationY = m.stockPeek.toPx() * depth
                        }.cardShade(UNDER_BACK_SHADE),
            )
        }
        game.stock.forEachIndexed { index, card ->
            val top = index == game.stock.lastIndex
            cards(card)(
                false,
                look.glide(card),
                0f,
                if (top) {
                    Modifier
                        .dragSource(input.dragState, GinDragSource.StockTop, enabled = canDraw)
                        .fade(if (input.dragState.payload == GinDragSource.StockTop) 0f else 1f)
                        .then(if (look.piles.stockRinged) Modifier.cardRing(colors.hint, CARD_RING_HINT) else Modifier)
                } else {
                    Modifier.fade(0f)
                },
            )
        }
        if (game.stock.isNotEmpty()) {
            CountBadge(
                count = game.stock.size,
                size = m.stockBadge,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = m.stockBadgeOverhang, y = -m.stockBadgeOverhang).zIndex(CHIP_Z),
            )
        }
    }
}

@Composable
private fun DiscardPile(
    game: GinState,
    look: GinLook,
    input: GinInput,
    cards: (Card) -> MovableCard,
    canTake: Boolean,
) {
    val colors = CardGamesTheme.colors
    EmptyPileSlot()
    game.discardPile.forEachIndexed { index, card ->
        val depth = game.discardPile.lastIndex - index
        cards(card)(
            true,
            look.glide(card),
            discardTilt(card),
            Modifier
                // Three deep, so the card under the top stays put while a new one flies in over it.
                .fade(if (depth < DISCARD_SHOWN) 1f else 0f)
                .then(
                    if (depth == 0) {
                        Modifier
                            .dragSource(input.dragState, GinDragSource.DiscardTop, enabled = canTake)
                            .fade(if (input.dragState.payload == GinDragSource.DiscardTop) 0f else 1f)
                            .then(if (look.piles.discardRinged) Modifier.cardRing(colors.hint, CARD_RING_SELECT) else Modifier)
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

@Composable
private fun PileLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CardGamesTheme.colors.onTable.copy(alpha = PILE_LABEL_ALPHA),
) {
    Text(text, color = color, style = CardGamesTheme.type.actionSub, modifier = modifier)
}

/** How the stock and discard pile are dressed this frame. */
@Immutable
internal class PileLook(
    /** Sage ring on the discard pile's top card: it makes a meld (or the hint says take it). */
    val discardRinged: Boolean = false,
    /** The discard pile's top card would make a meld — its "Makes a meld" chip and "Take K♣" label. */
    val discardMakesMeld: Boolean = false,
    /** Sage ring on the stock: the hint says draw blind. */
    val stockRinged: Boolean = false,
)

/**
 * Each discarded card's own landing tilt, −6° to +6°, fixed per card so it doesn't wobble on
 * recomposition. From the card's suit and rank, not `hashCode()` — an enum's hash changes every
 * launch, which turned a resumed game's pile to new angles. Stepping by [TILT_STRIDE] gives
 * neighbouring cards different tilts.
 */
internal fun discardTilt(card: Card): Float {
    val index = card.suit.ordinal * Rank.entries.size + card.rank.ordinal
    return (index * TILT_STRIDE % TILT_STEPS - DISCARD_TILT_RANGE).toFloat()
}

private const val STOCK_SHOWN = 3
private const val DISCARD_SHOWN = 3
private const val UNDER_BACK_SHADE = 0.2f
private const val CHIP_Z = 1f
private const val STOCK_WAITING_ALPHA = 0.45f
private const val PILE_LABEL_ALPHA = 0.8f
private const val DISCARD_TILT_RANGE = 6
private const val TILT_STEPS = DISCARD_TILT_RANGE * 2 + 1
private const val TILT_STRIDE = 5
