package com.cardgames.feature.ginrummy.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.LocalReduceMotion
import com.cardgames.core.ui.motion.scaledMillis
import kotlinx.coroutines.delay

/**
 * The on-screen deal: a freshly dealt hand's cards start out stacked on the stock and are dealt
 * one at a time — alternating seats, non-dealer first, then the upcard turned onto the discard
 * pile — instead of every card fading in at its final spot at once.
 *
 * Purely presentational. The engine deals the whole hand in one step; this only decides how much
 * of that already-final deal the table shows yet ([midDeal]), so the movable-card glide cache in
 * `GinScreen.kt` animates each card from the stock to its seat for free. The deal is the ViewModel's
 * pending motion: the screen reports it landed once this finishes, and only then does anyone act.
 */
internal const val DEAL_CARD_COUNT = GinState.HAND_SIZE * 2 + 1

/** One card's flight from the stock to its seat. */
private val FLIGHT_MILLIS = CardGamesMotion.Flight.millis.toLong()

/** Gap between consecutive cards leaving the stock. */
internal const val DEAL_STEP_MILLIS = GinMotionTokens.DEAL_STAGGER

/** The last card's departure plus its flight — when the table settles. */
internal val DEAL_TOTAL_MILLIS = DEAL_STEP_MILLIS * DEAL_CARD_COUNT + FLIGHT_MILLIS - DEAL_STEP_MILLIS

/**
 * True for a hand nobody has touched yet — the only position the deal animation applies to. A
 * resumed mid-hand game, or a hand already under way, renders as-is.
 */
internal fun GinState.isFreshDeal(): Boolean =
    moveCount == 0 &&
        phase is GinPhase.UpcardDecision &&
        playerHand.size == GinState.HAND_SIZE &&
        opponentHand.size == GinState.HAND_SIZE &&
        discardPile.size == 1

/** Every card that leaves the stock during the deal, in the order it leaves. */
internal fun GinState.dealOrder(): List<Card> {
    val first = handOf(dealer.other)
    val second = handOf(dealer)
    return (0 until GinState.HAND_SIZE).flatMap { listOf(first[it], second[it]) } + discardPile.first()
}

/**
 * The table as it looks once the first [dealt] cards of [dealOrder] have left the stock: the rest
 * are still stacked on it, the next one to deal on top. The phase is swapped for one where nobody
 * at the screen has a decision, so every control stays inert until the deal finishes.
 */
internal fun GinState.midDeal(dealt: Int): GinState {
    val pending = dealOrder().drop(dealt)
    if (pending.isEmpty()) return this
    val pendingSet = pending.toSet()
    return copy(
        playerHand = playerHand.filter { it !in pendingSet },
        opponentHand = opponentHand.filter { it !in pendingSet },
        stock = stock + pending.asReversed(),
        discardPile = discardPile.filter { it !in pendingSet },
        phase = GinPhase.AwaitingDraw(toMove = GinPlayer.OPPONENT),
    )
}

/**
 * How many cards of [game]'s deal have been dealt so far, or null once the deal is finished (or
 * [game] isn't a fresh deal at all). Restarts whenever a new hand is dealt. "Finished" means the
 * last card has landed, not just left the stock: after the final departure this keeps reporting
 * [DEAL_CARD_COUNT] for one more glide, so the hand doesn't sort itself and the Deadwood count
 * doesn't appear while cards are still in the air. That makes the whole deal [DEAL_TOTAL_MILLIS],
 * the same pause the ViewModel gives the AI before the hand's opening decision.
 */
@Composable
internal fun rememberDealProgress(
    game: GinState,
    // False while the table isn't drawn yet (the screen still arriving): the deal waits for it.
    onTable: Boolean = true,
    // Which deal this is. The seed alone isn't enough: restarting a hand deals the same seed again.
    dealKey: Any = game.seed,
): Int? {
    val fresh = game.isFreshDeal()
    var dealt by remember(dealKey) { mutableIntStateOf(if (fresh) 0 else DEAL_CARD_COUNT) }
    var landed by remember(dealKey) { mutableStateOf(!fresh) }
    // A restarted hand deals the same cards again: they're out on the table, so they fly home to
    // the stock first and the deal waits for them. A new hand's cards start in the stock.
    val lastSeed = remember { arrayOfNulls<Long>(1) }
    val regather = remember(dealKey) { fresh && lastSeed[0] == game.seed }
    SideEffect { lastSeed[0] = game.seed }
    val gatherMillis = scaledMillis(FLIGHT_MILLIS)
    val stepMillis = scaledMillis(DEAL_STEP_MILLIS)
    val settleMillis = scaledMillis(FLIGHT_MILLIS - DEAL_STEP_MILLIS)
    val still = LocalReduceMotion.current
    val reducedMillis = scaledMillis(CardGamesMotion.ReducedFade.millis.toLong())
    LaunchedEffect(dealKey, onTable) {
        if (!fresh || !onTable || landed) return@LaunchedEffect
        // Reduce motion: every card goes out at once on the short reduced slide.
        if (still) {
            dealt = DEAL_CARD_COUNT
            delay(reducedMillis)
            landed = true
            return@LaunchedEffect
        }
        if (regather) delay(gatherMillis)
        while (dealt < DEAL_CARD_COUNT) {
            delay(stepMillis)
            dealt++
        }
        delay(settleMillis)
        landed = true
    }
    return dealt.takeIf { fresh && !landed }
}
