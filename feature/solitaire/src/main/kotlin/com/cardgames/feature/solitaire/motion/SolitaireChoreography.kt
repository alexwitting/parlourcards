package com.cardgames.feature.solitaire.motion

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.domain.solitaire.TableauPile
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.feature.solitaire.MotionKind
import com.cardgames.feature.solitaire.board.Tableau
import kotlin.math.roundToInt

/** Where a card is on the table. */
internal sealed interface CardZone {
    data object Stock : CardZone

    data object Waste : CardZone

    data class Foundation(
        val suit: Suit,
    ) : CardZone

    data class Tableau(
        val pile: Int,
    ) : CardZone
}

internal data class CardSpot(
    val zone: CardZone,
    val index: Int,
    val faceUp: Boolean,
)

/** Every card's zone, position in it, and face. */
internal fun KlondikeState.spots(): Map<Card, CardSpot> =
    buildMap {
        stock.forEachIndexed { i, card -> put(card, CardSpot(CardZone.Stock, i, faceUp = false)) }
        waste.forEachIndexed { i, card -> put(card, CardSpot(CardZone.Waste, i, faceUp = true)) }
        Suit.entries.forEach { suit ->
            repeat(foundationCount(suit)) { i ->
                put(Card(suit, Rank.entries[i]), CardSpot(CardZone.Foundation(suit), i, faceUp = true))
            }
        }
        tableau.forEachIndexed { pile, column ->
            column.cards.forEachIndexed { i, card ->
                put(card, CardSpot(CardZone.Tableau(pile), i, faceUp = i >= column.faceDownCount))
            }
        }
    }

/** The tableau's cards in the order they're dealt: row by row, each row starting one column further in. */
internal fun KlondikeState.dealOrder(): List<Card> =
    buildList {
        for (row in 0 until KlondikeState.TABLEAU_PILE_COUNT) {
            for (pile in row until KlondikeState.TABLEAU_PILE_COUNT) {
                tableau[pile].cards.getOrNull(row)?.let(::add)
            }
        }
    }

/** Every card back in the stock, face down — where a new deal's gather sends the old one. */
internal fun KlondikeState.allInStock(): KlondikeState =
    copy(
        tableau = List(KlondikeState.TABLEAU_PILE_COUNT) { TableauPile(emptyList(), faceDownCount = 0) },
        foundations = Suit.entries.associateWith { 0 },
        stock =
            stock + waste + tableau.flatMap { it.cards } +
                Suit.entries.flatMap { suit -> (0 until foundationCount(suit)).map { Card(suit, Rank.entries[it]) } },
        waste = emptyList(),
        dealBatchRemaining = 0,
    )

/** This fresh deal before it's dealt: every card in the stock, the first to be dealt on top. */
internal fun KlondikeState.gathered(): KlondikeState =
    copy(
        tableau = List(KlondikeState.TABLEAU_PILE_COUNT) { TableauPile(emptyList(), faceDownCount = 0) },
        stock = stock + dealOrder().reversed(),
        waste = emptyList(),
        dealBatchRemaining = 0,
    )

/**
 * One card's part in a motion, in normal-speed milliseconds: it sets off [delayMillis] in, takes
 * [glideMillis] to arrive (0 = it's already there), and turns over [flipDelayMillis] in.
 */
internal data class CardTiming(
    val delayMillis: Int = 0,
    val glideMillis: Int = CardGamesMotion.Flight.millis,
    val flipDelayMillis: Int = 0,
)

/** A motion's per-card [timings] (cards not listed glide plainly) and when the last card is done. */
internal data class Choreography(
    val timings: Map<Card, CardTiming>,
    val totalMillis: Int,
)

/**
 * The Solitaire motion spec as timings for [kind] taking the table from [from] to [to]: the
 * row-by-row deal, draw-3's stagger with the flip partway through each flight, a run's trailing
 * cards, the reveal flip after a run lands, the waste turning over before it slides back,
 * auto-complete lowest first, and undo replaying the path a little quicker. [flight] is the
 * flight time between two zones (by default [CardGamesMotion.Flight]; the screen stretches it for
 * long paths, see [com.cardgames.core.ui.motion.TableMotion.flightMillisFor]).
 */
internal fun choreograph(
    kind: MotionKind,
    from: KlondikeState,
    to: KlondikeState,
    flight: (from: CardZone, to: CardZone) -> Int = { _, _ -> FLIGHT },
): Choreography {
    val change = TableChange(from.spots(), to.spots(), flight)
    val timings =
        when (kind) {
            MotionKind.MOVE -> runTimings(change, dropped = false)
            MotionKind.DROP -> runTimings(change, dropped = true)
            MotionKind.DRAW -> drawTimings(change)
            MotionKind.RECYCLE -> recycleTimings(change)
            MotionKind.DEAL -> dealTimings(change, to.dealOrder())
            MotionKind.GATHER -> gatherTimings(change)
            MotionKind.AUTO_COMPLETE -> autoCompleteTimings(change)
            MotionKind.UNDO -> undoTimings(change)
        }
    val flipMillis = if (kind == MotionKind.UNDO) undo(FLIP) else FLIP
    val total =
        timings.maxOfOrNull { (card, t) ->
            val flipEnd = if (card in change.turning) t.flipDelayMillis + flipMillis else 0
            maxOf(t.delayMillis + t.glideMillis, flipEnd)
        } ?: 0
    return Choreography(timings, total)
}

/** Which cards change place ([moved]) and which turn over where they lie ([flipped]) between two positions. */
private class TableChange(
    val before: Map<Card, CardSpot>,
    val after: Map<Card, CardSpot>,
    private val flight: (CardZone, CardZone) -> Int,
) {
    /** How long [card]'s flight takes, from where it was to where it goes. */
    fun flightOf(card: Card): Int {
        val to = after.getValue(card).zone
        return flight(before[card]?.zone ?: to, to)
    }

    val moved: List<Card> = after.keys.filter { before[it]?.zone != after.getValue(it).zone }
    val flipped: List<Card> = after.keys.filter { it !in moved && before[it]?.faceUp != after.getValue(it).faceUp }

    /** Every card whose face changes, moving or not. */
    val turning: Set<Card> = flipped.toSet() + moved.filter { before[it]?.faceUp != after.getValue(it).faceUp }
}

/**
 * A run follows its leader 30ms apart (in place at once when [dropped]). The card it uncovers turns
 * over almost at once — [SolitaireMotionTokens.REVEAL_LEAD] after the run's last card has left, while the run is still in
 * the air (waiting for it to land read as a stall). A dropped run is already in place, so its reveal
 * waits just [SolitaireMotionTokens.DROP_REVEAL].
 */
private fun runTimings(
    change: TableChange,
    dropped: Boolean,
): Map<Card, CardTiming> {
    val stagger = if (dropped) 0 else SolitaireMotionTokens.RUN_STAGGER
    val run = change.moved.sortedBy { change.before.getValue(it).index }
    val glide = run.firstOrNull()?.takeUnless { dropped }?.let(change::flightOf) ?: 0
    val reveal =
        if (glide ==
            0
        ) {
            SolitaireMotionTokens.DROP_REVEAL
        } else {
            (run.size - 1).coerceAtLeast(0) * stagger + SolitaireMotionTokens.REVEAL_LEAD
        }
    return run.withIndex().associate { (i, card) -> card to CardTiming(delayMillis = i * stagger, glideMillis = glide) } +
        change.flipped.associateWith { CardTiming(flipDelayMillis = reveal) }
}

/** Drawn cards fly to the waste 60ms apart, each turning over partway through its flight. */
private fun drawTimings(change: TableChange): Map<Card, CardTiming> =
    change.moved.sortedBy { change.after.getValue(it).index }.withIndex().associate { (i, card) ->
        val delay = i * SolitaireMotionTokens.DRAW_THREE_STAGGER
        card to
            CardTiming(
                delayMillis = delay,
                glideMillis = change.flightOf(card),
                flipDelayMillis =
                    delay + SolitaireMotionTokens.FLIP_IN_FLIGHT,
            )
    }

/** The waste turns face down in place, top card first, then the stack slides home. */
private fun recycleTimings(change: TableChange): Map<Card, CardTiming> {
    val slide = FLIP + (RECYCLE_VISIBLE - 1) * SolitaireMotionTokens.RECYCLE_STAGGER
    return change.moved.sortedByDescending { change.before.getValue(it).index }.withIndex().associate { (i, card) ->
        card to
            CardTiming(
                delayMillis = slide,
                glideMillis = change.flightOf(card),
                flipDelayMillis = minOf(i, RECYCLE_VISIBLE - 1) * SolitaireMotionTokens.RECYCLE_STAGGER,
            )
    }
}

/** Row by row, 50ms apart, each column's last card turning over in flight; anything else heads to the stock at once. */
private fun dealTimings(
    change: TableChange,
    order: List<Card>,
): Map<Card, CardTiming> =
    change.moved.filter { it !in order }.associateWith { CardTiming(glideMillis = change.flightOf(it)) } +
        order.withIndex().associate { (i, card) ->
            val delay = i * SolitaireMotionTokens.DEAL_STAGGER
            card to
                CardTiming(
                    delayMillis = delay,
                    glideMillis = change.flightOf(card),
                    flipDelayMillis =
                        delay + SolitaireMotionTokens.FLIP_IN_FLIGHT,
                )
        }

/**
 * Back to the stock 12ms apart — the foundations first, then the waste, then the columns from the
 * top down — each card turning face down as it leaves.
 */
private fun gatherTimings(change: TableChange): Map<Card, CardTiming> {
    val order =
        change.moved.sortedWith(
            compareBy<Card>(
                {
                    change.before
                        .getValue(it)
                        .zone
                        .gatherRank()
                },
                { -change.before.getValue(it).index },
                { (change.before.getValue(it).zone as? CardZone.Tableau)?.pile ?: 0 },
            ),
        )
    return order.withIndex().associate { (i, card) ->
        val delay = i * SolitaireMotionTokens.GATHER_STAGGER
        card to CardTiming(delayMillis = delay, glideMillis = change.flightOf(card), flipDelayMillis = delay)
    }
}

private fun CardZone.gatherRank(): Int =
    when (this) {
        is CardZone.Foundation -> 0
        CardZone.Waste -> 1
        is CardZone.Tableau -> 2
        CardZone.Stock -> 3
    }

/** Lowest rank first, 120ms apart. */
private fun autoCompleteTimings(change: TableChange): Map<Card, CardTiming> =
    change.moved.sortedWith(compareBy({ it.rank.ordinal }, { it.suit.ordinal })).withIndex().associate { (i, card) ->
        card to CardTiming(delayMillis = i * SolitaireMotionTokens.AUTO_COMPLETE_STAGGER, glideMillis = change.flightOf(card))
    }

/** Anything that turns back over does so first; then the cards return, a little quicker. */
private fun undoTimings(change: TableChange): Map<Card, CardTiming> {
    val delay = if (change.turning.isNotEmpty()) undo(FLIP) else 0
    return change.moved.associateWith { CardTiming(delayMillis = delay, glideMillis = undo(change.flightOf(it))) } +
        change.flipped.associateWith { CardTiming() }
}

private fun undo(millis: Int): Int = (millis * SolitaireMotionTokens.UNDO_SPEED).roundToInt()

private val FLIGHT = CardGamesMotion.Flight.millis
private val FLIP = CardGamesMotion.Flip.millis

/** Only the top few waste cards show, so only they stagger as the waste turns over. */
private const val RECYCLE_VISIBLE = 3
