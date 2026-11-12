package com.cardgames.core.domain.ginrummy.ai

import com.cardgames.core.domain.cards.Deck
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import kotlin.random.Random

/**
 * Builds an engine-legal [GinState] consistent with an [OpponentView] — a determinization for
 * Monte Carlo simulation. The cards neither side has seen (52 minus [OpponentView.ownHand] and
 * [OpponentView.discardPile]) are dealt at random between the hidden hand and the stock, at their
 * known sizes; cards the hidden seat is known to hold ([OpponentView.opponentKnownCards], taken
 * from the pile) always go into its hand. It's built from the view alone, never from a real
 * [GinState].
 *
 * In the built state the deciding seat is always [GinPlayer.PLAYER] and the hidden hand
 * [GinPlayer.OPPONENT], with fresh match bookkeeping (0–0, hand 1), whichever seat is deciding in
 * the real match.
 */
object HiddenWorldSampler {
    /**
     * [phase] is the caller's job to supply correctly for whatever decision is being evaluated
     * (e.g. `AwaitingDiscard(PLAYER, mustNotDiscard = view.mustNotDiscard)`) — [OpponentView]
     * alone doesn't encode which of [GinOpponent]'s three methods is in play.
     */
    fun sample(
        view: OpponentView,
        phase: GinPhase,
        random: Random,
    ): GinState {
        val known = view.ownHand.toSet() + view.discardPile.toSet()
        val unseen = Deck.standard52.filterNot { it in known }.shuffled(random)
        check(unseen.size == view.opponentHandSize + view.stockRemaining) {
            "OpponentView declares ${view.opponentHandSize} + ${view.stockRemaining} unseen cards " +
                "but 52 - ownHand - discardPile leaves ${unseen.size}"
        }
        // Only ever cards that are unseen, and never more than the hand holds — so a
        // stale or inconsistent view degrades to plain random sampling instead of a broken deal.
        val certain =
            view.opponentKnownCards
                .filter { it in unseen }
                .distinct()
                .take(view.opponentHandSize)
        val rest = unseen - certain.toSet()
        val randomCount = view.opponentHandSize - certain.size
        return GinState(
            seed = random.nextLong(),
            handNumber = 1,
            dealer = GinPlayer.PLAYER,
            playerHand = view.ownHand,
            opponentHand = certain + rest.take(randomCount),
            stock = rest.drop(randomCount),
            discardPile = view.discardPile,
            phase = phase,
            matchScores = GinPlayer.entries.associateWith { 0 },
            boxes = GinPlayer.entries.associateWith { 0 },
            knownHeld = mapOf(GinPlayer.OPPONENT to certain),
        )
    }
}
