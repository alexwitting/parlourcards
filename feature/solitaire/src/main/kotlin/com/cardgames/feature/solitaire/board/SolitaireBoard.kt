package com.cardgames.feature.solitaire.board

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Deck
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.solitaire.KlondikeEngine
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.ui.cards.CARD_ASPECT_RATIO
import com.cardgames.core.ui.cards.CARD_RING_HINT
import com.cardgames.core.ui.cards.CARD_RING_SELECT
import com.cardgames.core.ui.cards.DragDropState
import com.cardgames.core.ui.cards.EmptyPileSlot
import com.cardgames.core.ui.cards.OverlappingColumn
import com.cardgames.core.ui.cards.PlayingCard
import com.cardgames.core.ui.cards.cardRing
import com.cardgames.core.ui.cards.dropTarget
import com.cardgames.core.ui.cards.glyph
import com.cardgames.core.ui.chrome.CountBadge
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.scaledMillis
import com.cardgames.core.ui.motion.tween
import com.cardgames.core.ui.table.CardGlide
import com.cardgames.core.ui.table.MovableCard
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.feature.solitaire.SolitaireIntent
import com.cardgames.feature.solitaire.motion.CardZone
import com.cardgames.feature.solitaire.motion.SolitaireMotionTokens

// The solitaire table: the foundations across the top, the seven columns, and the draw pile at the
// bottom centre. Every card is
// composed wherever it lies — even under the top of a pile — so any move glides from where it was.

/**
 * How the board dresses its cards this frame. [glide] is each card's way into place (the
 * choreography of a move in flight); [raised] cards wear the brass ring and sit lifted (a held or
 * hinted card); [targets] are the places that get a sage ring; [hidden] are cards a drag has
 * picked up (kept composed, just invisible); [stock] is how the stock is dressed.
 */
@Immutable
internal class BoardLook(
    val glide: (Card) -> CardGlide,
    val raised: Set<Card> = emptySet(),
    val targets: Set<SolitaireDropTarget> = emptySet(),
    val hidden: Set<Card> = emptySet(),
    val stock: StockLook = StockLook(),
    /** The cards the move in flight is carrying, which a pile keeps visible until they've landed. */
    val moving: Set<Card> = emptySet(),
)

/** How the stock is dressed this frame. */
@Immutable
internal class StockLook(
    /** Sage ring: the hint says draw. */
    val ringed: Boolean = false,
    /** A restart or new deal is sending every card to the stock: all of them show there, so none vanishes mid-flight. */
    val gathering: Boolean = false,
    /** The stock is shuffling before a new deal: it shakes side to side three times. */
    val shuffling: Boolean = false,
    /** A deal is going out from the stock: its badge counts the cards away as they leave. */
    val dealing: Boolean = false,
)

/** What a card's touch does — routed to intents by the screen. */
internal class BoardInput(
    val onIntent: (SolitaireIntent) -> Unit,
    val dragState: DragDropState<SolitaireDragSource>,
    /** False while the whole table is locked (a deal search, the Solved screen). */
    val enabled: Boolean = true,
    /** Where each pile sits on screen (its top-left, in root pixels) — so a flight's length can follow its distance. */
    val pilePositions: MutableMap<CardZone, Offset> = mutableMapOf(),
)

/** Records where this pile sits, for [BoardInput.pilePositions]. */
private fun Modifier.trackPile(
    input: BoardInput,
    zone: CardZone,
): Modifier = onGloballyPositioned { input.pilePositions[zone] = it.positionInRoot() }

/** The cards following the finger during a drag — plain copies in brass rings, never the movable originals. */
@Composable
internal fun DragGhost(
    game: KlondikeState,
    source: SolitaireDragSource,
) {
    val accent = CardGamesTheme.colors.accent
    val faceUpPeek = solitaireMetrics.faceUpPeek
    OverlappingColumn(peekFraction = { faceUpPeek }) {
        game.draggedCards(source).forEach { card ->
            PlayingCard(card = card, faceUp = true, lifted = true, ring = accent)
        }
    }
}

/** Registers this node as [target]: accepts exactly what the engine deems legal. */
internal fun Modifier.solitaireDropTarget(
    input: BoardInput,
    game: KlondikeState,
    target: SolitaireDropTarget,
): Modifier =
    dropTarget(
        state = input.dragState,
        key = target,
        canAccept = { source ->
            game.moveFor(source, target)?.let { KlondikeEngine.isLegal(game, it) } == true
        },
        onDrop = { source -> input.onIntent(SolitaireIntent.DropCards(source, target)) },
    )

/** Brass ring and a small lift for a raised card; sage ring for a destination's top card. */
@Composable
internal fun Modifier.dressed(
    card: Card,
    look: BoardLook,
    isTargetTop: Boolean = false,
): Modifier {
    val colors = CardGamesTheme.colors
    val lift = solitaireMetrics.selectLift
    return when {
        card in look.raised -> graphicsLayer { translationY = -lift.toPx() }.cardRing(colors.accent, CARD_RING_SELECT)
        isTargetTop -> cardRing(colors.hint, CARD_RING_HINT)
        else -> this
    }.alpha(if (card in look.hidden) 0f else 1f)
}

/** The four foundations across the top, each a drop target ([FoundationSlots]). */
@Composable
internal fun FoundationRow(
    game: KlondikeState,
    look: BoardLook,
    input: BoardInput,
    cards: (Card) -> MovableCard,
    columnWidth: Dp,
    modifier: Modifier = Modifier,
) {
    FoundationSlots(columnWidth, modifier, slotModifier = { Modifier.trackPile(input, CardZone.Foundation(it)) }) { suit ->
        FoundationPile(game, suit, look, input, cards)
    }
}

/**
 * The foundations' four places across the top: 1.25× a column's width, spread over the tableau's
 * width (inset 3dp each side). Shared by the table and the Solved screen, so the kings there sit
 * at the size and spacing they were played to.
 */
@Composable
internal fun FoundationSlots(
    columnWidth: Dp,
    modifier: Modifier = Modifier,
    slotModifier: (Suit) -> Modifier = { Modifier },
    slot: @Composable (Suit) -> Unit,
) {
    val m = solitaireMetrics
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = m.boardMargin + m.foundationInset),
        horizontalArrangement =
            m.foundationSpacing?.let { Arrangement.spacedBy(it, Alignment.CenterHorizontally) } ?: Arrangement.SpaceBetween,
    ) {
        Suit.entries.forEach { suit ->
            Box(modifier = Modifier.pileBox(columnWidth * m.foundationScale).then(slotModifier(suit))) { slot(suit) }
        }
    }
}

/**
 * The draw pile at the bottom centre, within thumb reach: the stock (its count under it) and the
 * waste beside it, each 1.5× a column's width; draw 3 fans the waste out to the right.
 */
@Composable
internal fun DrawRow(
    game: KlondikeState,
    look: BoardLook,
    input: BoardInput,
    cards: (Card) -> MovableCard,
    columnWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val m = solitaireMetrics
    val width = columnWidth * m.drawScale
    // The waste's box makes room for a full draw-3 fan, so the stock and waste sit in the same
    // place in either draw mode, centred as one group.
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(m.drawGap, Alignment.CenterHorizontally),
    ) {
        Box(modifier = Modifier.pileBox(width).trackPile(input, CardZone.Stock)) {
            StockPile(game, look, input, cards)
            // Gathering, the badge counts the cards home as they arrive; dealing, it counts them away
            // as they leave — rather than jumping to 52, or straight back down to 24.
            val gatherMillis = scaledMillis(GATHER_COUNT_MILLIS.toLong()).toInt()
            val dealMillis = scaledMillis(DEAL_COUNT_MILLIS.toLong()).toInt()
            val count by animateIntAsState(
                game.stock.size,
                when {
                    look.stock.gathering -> tween(gatherMillis, easing = LinearEasing)
                    look.stock.dealing -> tween(dealMillis, easing = LinearEasing)
                    else -> snap()
                },
                label = "stock count",
            )
            if (count > 0) {
                CountBadge(
                    count = count,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = m.stockBadgeOverhang.x, y = m.stockBadgeOverhang.y),
                )
            }
        }
        val fanRoom = m.wasteFanStep * (DRAW_THREE - 1)
        Box(modifier = Modifier.padding(end = fanRoom).pileBox(width).trackPile(input, CardZone.Waste)) {
            WastePile(game, look, input, cards)
        }
    }
}

/** The tableau: seven columns, each a drop target filling the height it's given. */
@Composable
internal fun Tableau(
    game: KlondikeState,
    look: BoardLook,
    input: BoardInput,
    cards: (Card) -> MovableCard,
    modifier: Modifier = Modifier,
) {
    SevenSlots(modifier = modifier) { pile ->
        val column = game.tableau[pile]
        val target = SolitaireDropTarget.Tableau(pile)
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .trackPile(input, CardZone.Tableau(pile))
                    .solitaireDropTarget(input, game, target),
        ) {
            if (column.isEmpty) {
                EmptyPileSlot(
                    glyph = Rank.KING.glyph,
                    modifier =
                        Modifier
                            .targetRing(target in look.targets)
                            .clickable(enabled = input.enabled) { input.onIntent(SolitaireIntent.TapEmptyTableau(pile)) },
                )
            } else {
                TableauColumn(column, pile, target in look.targets, look, input, cards)
            }
        }
    }
}

/**
 * A pile's box: [cardWidth] wide and exactly one card tall, whatever it holds. A card gliding in
 * or out at another size (the waste's 1.5× card on its way to a 1.25× foundation) overhangs the
 * box instead of resizing it — otherwise its row grows mid-flight and the tableau under it jumps.
 */
private fun Modifier.pileBox(cardWidth: Dp): Modifier =
    width(cardWidth).layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, (cardWidth / CARD_ASPECT_RATIO).roundToPx()) { placeable.place(0, 0) }
    }

/** Seven equal slots across the board, 5dp apart — the shared grid of the top row and the tableau. */
@Composable
internal fun SevenSlots(
    modifier: Modifier = Modifier,
    slot: @Composable (Int) -> Unit,
) {
    val m = solitaireMetrics
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = m.boardMargin),
        horizontalArrangement = Arrangement.spacedBy(m.slotGap),
        verticalAlignment = Alignment.Top,
    ) {
        repeat(KlondikeState.TABLEAU_PILE_COUNT) { index ->
            Box(modifier = Modifier.weight(1f)) { slot(index) }
        }
    }
}

internal const val DRAW_THREE = 3

/** About how long a deal takes to leave the stock: the tableau's 28 cards 50ms apart, then a flight. */
private val DEAL_COUNT_MILLIS =
    (DEALT_CARDS - 1) * SolitaireMotionTokens.DEAL_STAGGER + CardGamesMotion.Flight.millis

private const val DEALT_CARDS = 28

/** About how long a gather takes to bring the cards home: 52 cards 12ms apart, then a flight. */
private val GATHER_COUNT_MILLIS =
    (Deck.standard52.size - 1) * SolitaireMotionTokens.GATHER_STAGGER + CardGamesMotion.Flight.millis
