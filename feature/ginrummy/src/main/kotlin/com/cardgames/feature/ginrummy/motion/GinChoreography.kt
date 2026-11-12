package com.cardgames.feature.ginrummy.motion

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.feature.ginrummy.GinMotionKind
import kotlin.math.roundToInt

/** Where a card is on the gin table. Laid-off cards count as part of the hand they joined. */
internal enum class GinZone { STOCK, DISCARD, PLAYER, OPPONENT }

internal fun GinState.zones(): Map<Card, GinZone> =
    buildMap {
        stock.forEach { put(it, GinZone.STOCK) }
        discardPile.forEach { put(it, GinZone.DISCARD) }
        (playerHand + laidOffOnto(GinPlayer.PLAYER)).forEach { put(it, GinZone.PLAYER) }
        (opponentHand + laidOffOnto(GinPlayer.OPPONENT)).forEach { put(it, GinZone.OPPONENT) }
    }

/** One card's part in a motion, in normal-speed milliseconds (see Solitaire's `CardTiming`). */
internal data class GinTiming(
    val delayMillis: Int = 0,
    val glideMillis: Int = GinMotionTokens.Flight.millis,
    val flipDelayMillis: Int = 0,
    /** A discard: it flies to the pile along an arc and settles to its tilt on the land spring. */
    val arcs: Boolean = false,
)

internal data class GinChoreography(
    val timings: Map<Card, GinTiming>,
    val totalMillis: Int,
)

/**
 * The gin motion spec as timings for [kind] taking the table from [from] to [to]: a card drawn
 * from the stock turns over partway through its flight into the player's hand; one the opponent
 * takes from the discard pile flies face up and turns down once it lands; the opponent's discard
 * turns face up halfway; undo replays the path a little quicker. The deal's own pacing lives in
 * `GinDeal.kt`.
 */
internal fun ginChoreography(
    kind: GinMotionKind,
    from: GinState,
    to: GinState,
): GinChoreography {
    if (kind == GinMotionKind.DEAL) return GinChoreography(emptyMap(), DEAL_TOTAL_MILLIS.toInt())
    val before = from.zones()
    val after = to.zones()
    val moved = after.keys.filter { before[it] != null && before[it] != after.getValue(it) }
    val undo = kind == GinMotionKind.UNDO
    val glide =
        when (kind) {
            GinMotionKind.UNDO -> undoMillis(GinMotionTokens.Flight.millis)
            GinMotionKind.DROP -> 0
            else -> GinMotionTokens.Flight.millis
        }
    val timings =
        moved.associateWith { card ->
            val src = before.getValue(card)
            val dst = after.getValue(card)
            val flipDelay = if (undo || kind == GinMotionKind.DROP) 0 else flipDelayFor(src, dst)
            GinTiming(
                glideMillis = glide,
                flipDelayMillis = flipDelay,
                arcs = dst == GinZone.DISCARD && !undo && kind != GinMotionKind.DROP,
            )
        }
    // A knock turns the opponent's hand face up where it lies, a card at a time.
    val revealing = to.showsOpponent() && !from.showsOpponent()
    val revealed =
        if (revealing && !undo) {
            to.opponentHand.filter { it !in moved }.withIndex().associate { (i, card) ->
                card to GinTiming(glideMillis = 0, flipDelayMillis = i * REVEAL_STAGGER)
            }
        } else {
            emptyMap()
        }
    val all = timings + revealed
    val flipMillis = if (undo) undoMillis(FLIP) else FLIP
    val total =
        all.maxOfOrNull { (card, t) ->
            val turns = card in revealed || faceUp(before.getValue(card), from) != faceUp(after.getValue(card), to)
            maxOf(t.delayMillis + t.glideMillis, if (turns) t.flipDelayMillis + flipMillis else 0)
        } ?: 0
    return GinChoreography(all, total)
}

/**
 * When a card turns over on its way from [src] to [dst]: partway into a stock draw to the player,
 * halfway through the opponent's discard, and only once it has landed when it goes into the
 * opponent's hidden hand.
 */
private fun flipDelayFor(
    src: GinZone,
    dst: GinZone,
): Int =
    when {
        src == GinZone.STOCK && dst == GinZone.PLAYER -> GinMotionTokens.FLIP_IN_FLIGHT
        src == GinZone.OPPONENT && dst == GinZone.DISCARD -> GinMotionTokens.Flight.millis / 2
        dst == GinZone.OPPONENT -> GinMotionTokens.Flight.millis
        else -> 0
    }

/** The opponent's hand is shown once a knock is being settled, and after. */
private fun GinState.showsOpponent(): Boolean =
    phase is GinPhase.AwaitingLayoff || phase is GinPhase.HandOver || phase is GinPhase.MatchOver

/** Faces show in the player's hand, on the discard pile, and in the opponent's hand once it's shown. */
private fun faceUp(
    zone: GinZone,
    state: GinState,
): Boolean = zone == GinZone.PLAYER || zone == GinZone.DISCARD || (zone == GinZone.OPPONENT && state.showsOpponent())

private fun undoMillis(millis: Int): Int = (millis * GinMotionTokens.UNDO_SPEED).roundToInt()

private const val REVEAL_STAGGER = CardGamesMotion.Stagger.REVEAL.toInt()
private val FLIP = CardGamesMotion.Flip.millis
