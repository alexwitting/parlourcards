package com.cardgames.core.domain.ginrummy

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Deck
import kotlin.random.Random

/**
 * Pure gin rummy rules facade: no coroutines, no time, no I/O. Dealing a hand needs a fresh seed
 * each time (from the ViewModel's injected `SeedProvider`, same pattern as `KlondikeEngine.deal`)
 * — that's why it's a distinct call rather than a [GinMove], which is why a hand-ending phase has
 * no legal moves rather than an implicit "next hand" one.
 */
object GinEngine {
    /** The first hand of a fresh match under [rules]: 0-0 score, no boxes yet, [GinPlayer.PLAYER] deals. */
    fun newMatch(
        seed: Long,
        rules: GinMatchRules = GinMatchRules(),
    ): GinState =
        dealHand(
            seed = seed,
            handNumber = 1,
            dealer = GinPlayer.PLAYER,
            matchScores = GinPlayer.entries.associateWith { 0 },
            boxes = GinPlayer.entries.associateWith { 0 },
            rules = rules,
        )

    /** The hand in play dealt again from its own seed — the same cards, dealer, scores and rules. */
    fun redealHand(game: GinState): GinState =
        dealHand(
            seed = game.seed,
            handNumber = game.handNumber,
            dealer = game.dealer,
            matchScores = game.matchScores,
            boxes = game.boxes,
            rules = game.rules,
        )

    /**
     * The next hand of an in-progress match, carrying its score/box totals and rules forward. The
     * dealer alternates every hand — except after a blocked hand, which the same dealer redeals.
     */
    fun dealNextHand(
        previous: GinState,
        seed: Long,
    ): GinState {
        val phase = previous.phase
        require(phase is GinPhase.HandOver) { "Can only deal the next hand once the current one is over" }
        return dealHand(
            seed = seed,
            handNumber = previous.handNumber + 1,
            dealer = if (phase.result.outcome == HandOutcome.BLOCKED) previous.dealer else previous.dealer.other,
            matchScores = previous.matchScores,
            boxes = previous.boxes,
            rules = previous.rules,
        )
    }

    fun isLegal(
        state: GinState,
        move: GinMove,
    ): Boolean = GinRules.isLegal(state, move)

    fun apply(
        state: GinState,
        move: GinMove,
    ): GinState {
        require(isLegal(state, move)) { "Illegal move $move in phase ${state.phase}" }
        return GinMutations.apply(state, move).copy(moveCount = state.moveCount + 1)
    }

    /**
     * The single best discard-and-knock for whichever seat is [GinPhase.AwaitingDiscard.toMove]:
     * the discard that leaves the least deadwood, so long as that's still at or under
     * [GinState.KNOCK_THRESHOLD]. Null outside that phase, or when no discard would qualify.
     * `apply`ing [GinMove.Knock] with this card is always legal whenever this returns non-null —
     * drives the UI's knock affordance so the player doesn't have to guess-and-check which card
     * to select before Knock lights up.
     */
    fun bestKnockCard(state: GinState): Card? {
        val phase = state.phase as? GinPhase.AwaitingDiscard ?: return null
        val hand = state.handOf(phase.toMove)
        return hand
            .filterNot { it == phase.mustNotDiscard }
            .associateWith { card -> Melds.bestPartition(hand - card).deadwoodPips }
            .minByOrNull { it.value }
            ?.takeIf { it.value <= GinState.KNOCK_THRESHOLD }
            ?.key
    }

    fun isHandOver(state: GinState): Boolean = state.phase is GinPhase.HandOver || state.phase is GinPhase.MatchOver

    fun isMatchOver(state: GinState): Boolean = state.phase is GinPhase.MatchOver

    private fun dealHand(
        seed: Long,
        handNumber: Int,
        dealer: GinPlayer,
        matchScores: Map<GinPlayer, Int>,
        boxes: Map<GinPlayer, Int>,
        rules: GinMatchRules,
    ): GinState {
        val deck = Deck.shuffled(Random(seed))
        val nonDealer = dealer.other
        val nonDealerHand = deck.subList(0, GinState.HAND_SIZE)
        val dealerHand = deck.subList(GinState.HAND_SIZE, GinState.HAND_SIZE * 2)
        val upcard = deck[GinState.HAND_SIZE * 2]
        val stock = deck.subList(GinState.HAND_SIZE * 2 + 1, deck.size)

        return GinState(
            seed = seed,
            handNumber = handNumber,
            dealer = dealer,
            playerHand = if (nonDealer == GinPlayer.PLAYER) nonDealerHand else dealerHand,
            opponentHand = if (nonDealer == GinPlayer.OPPONENT) nonDealerHand else dealerHand,
            stock = stock,
            discardPile = listOf(upcard),
            phase = GinPhase.UpcardDecision(nonDealer),
            matchScores = matchScores,
            boxes = boxes,
            rules = rules,
        )
    }
}
