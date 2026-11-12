package com.cardgames.feature.ginrummy

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.Melds
import com.cardgames.feature.ginrummy.board.waitingOn

/**
 * One line of the action message under the piles: "Rosa discarded **K♣**", "You took **K♣** · and
 * made a new meld". [card], when set, is the card named after [verb] (shown in bold).
 */
data class GinFeedLine(
    val actor: GinPlayer,
    val verb: String,
    val card: Card? = null,
    val tail: String? = null,
)

/** How many lines the feed keeps: the last move, and the one before it. */
internal const val FEED_LENGTH = 2

/**
 * What the move from [before] to [after] was, as the feed tells it — or null for one it doesn't
 * narrate (a deal, layoffs, the hand being settled).
 */
internal fun feedLine(
    before: GinState,
    after: GinState,
): GinFeedLine? {
    val phase = before.phase
    val actor = phase.waitingOn() ?: return null
    val topBefore = before.discardPile.lastOrNull()
    return when (phase) {
        is GinPhase.UpcardDecision ->
            topBefore?.let { upcard ->
                if (after.discardPile.size <
                    before.discardPile.size
                ) {
                    took(actor, upcard, before, after)
                } else {
                    GinFeedLine(actor, "passed on", upcard)
                }
            }
        is GinPhase.AwaitingDraw ->
            when {
                after.stock.size < before.stock.size -> GinFeedLine(actor, "drew from the stock")
                topBefore != null && after.discardPile.size < before.discardPile.size -> took(actor, topBefore, before, after)
                else -> null
            }
        is GinPhase.AwaitingDiscard ->
            when (val next = after.phase) {
                is GinPhase.AwaitingLayoff -> GinFeedLine(actor, if (next.isGin) "went gin" else "knocked")
                else ->
                    after.discardPile
                        .lastOrNull()
                        ?.takeIf {
                            after.discardPile.size > before.discardPile.size
                        }?.let { GinFeedLine(actor, "discarded", it) }
            }
        else -> null
    }
}

/** A card taken from the pile — the player's own line also says when it made a new meld. */
private fun took(
    actor: GinPlayer,
    card: Card,
    before: GinState,
    after: GinState,
): GinFeedLine {
    val madeMeld =
        actor == GinPlayer.PLAYER &&
            Melds.bestPartition(after.playerHand).melds.size > Melds.bestPartition(before.playerHand).melds.size
    return GinFeedLine(actor, "took", card, tail = "and made a new meld".takeIf { madeMeld })
}
