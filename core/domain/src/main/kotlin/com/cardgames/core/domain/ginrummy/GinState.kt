package com.cardgames.core.domain.ginrummy

import com.cardgames.core.domain.cards.Card
import kotlinx.serialization.Serializable

/** Gin rummy is always exactly the human vs. the AI — no need for a general N-player model. */
enum class GinPlayer {
    PLAYER,
    OPPONENT,
    ;

    val other: GinPlayer
        get() = if (this == PLAYER) OPPONENT else PLAYER
}

/**
 * Where a hand currently stands. Turn flow: [UpcardDecision] (non-dealer, then dealer, may
 * take the discard-pile starter card) → [AwaitingDraw]/[AwaitingDiscard] repeating → either a
 * normal discard loops back to [AwaitingDraw] for the other player, or a knock moves to
 * [AwaitingLayoff] → [HandOver]. [MatchOver] once a player's match score reaches the target.
 */
@Serializable
sealed interface GinPhase {
    @Serializable
    data class UpcardDecision(
        val deciding: GinPlayer,
    ) : GinPhase

    @Serializable
    data class AwaitingDraw(
        val toMove: GinPlayer,
    ) : GinPhase

    /**
     * [toMove] holds 11 cards and must discard or knock. [mustNotDiscard], when set, is the
     * card [toMove] just took from the discard pile — the one card they may not immediately
     * discard back (the standard "no free look" rule).
     */
    @Serializable
    data class AwaitingDiscard(
        val toMove: GinPlayer,
        val mustNotDiscard: Card? = null,
    ) : GinPhase

    /**
     * [knocker] knocked (or went gin) with [knockerMelds]; [defender] may lay off deadwood
     * onto [currentMelds] (starts equal to [knockerMelds], grows as layoffs land) before
     * [GinMove.SettleHand] resolves scoring. No layoffs are legal when [isGin].
     */
    @Serializable
    data class AwaitingLayoff(
        val knocker: GinPlayer,
        val isGin: Boolean,
        /** The card thrown to knock; null for a Big Gin, which goes out with all 11 cards. */
        val knockerDiscard: Card?,
        val knockerMelds: List<Meld>,
        val currentMelds: List<Meld> = knockerMelds,
        val isBigGin: Boolean = false,
    ) : GinPhase

    @Serializable
    data class HandOver(
        val result: HandResult,
    ) : GinPhase

    /**
     * The match is decided. [lastHand] is the hand that ended it; [raceScores] the raw race totals
     * at that point (the settled [GinState.matchScores] add boxes and the game bonus). Both are
     * null when unknown.
     */
    @Serializable
    data class MatchOver(
        val winner: GinPlayer,
        val lastHand: HandResult? = null,
        val raceScores: Map<GinPlayer, Int>? = null,
    ) : GinPhase
}

enum class HandOutcome { KNOCK, GIN, UNDERCUT, BLOCKED }

/**
 * A match's house rules, fixed when it's dealt: the score to play to, whether going gin earns
 * [GinScoring.GIN_BONUS], and whether Big Gin (going out with all 11 cards melded, no discard)
 * is allowed for [GinScoring.BIG_GIN_BONUS].
 */
@Serializable
data class GinMatchRules(
    val target: Int = GinState.MATCH_TARGET,
    val ginBonus: Boolean = true,
    val bigGin: Boolean = false,
) {
    companion object {
        /** The targets a match can be played to. */
        val TARGETS = listOf(50, GinState.MATCH_TARGET, 250)
    }
}

/**
 * How one hand ended. [winner]/[pointsScored] are null/0 for [HandOutcome.BLOCKED] (the stock
 * ran down to its 2-card reserve with nobody knocking — a no-score redeal).
 */
@Serializable
data class HandResult(
    val outcome: HandOutcome,
    val winner: GinPlayer?,
    val pointsScored: Int,
    val knockerDeadwood: Int,
    val defenderDeadwood: Int,
)

/**
 * A whole gin rummy position: the hand in play and the match around it. [matchScores] is the race
 * to the target in hand points only; box bonuses are kept apart in [boxes] (see [GinScoring]).
 *
 * [knownHeld] is table knowledge, not hidden state: the cards each player took from the discard
 * pile (the upcard included) and still holds. [laidOff] keeps the defender's cards laid off onto
 * the knocker's melds this hand, keyed by the knocker; they leave the defender's hand without
 * joining the knocker's, where they'd count as deadwood. Both clear with each new deal.
 */
@Serializable
data class GinState(
    val seed: Long,
    val handNumber: Int,
    val dealer: GinPlayer,
    val playerHand: List<Card>,
    val opponentHand: List<Card>,
    val stock: List<Card>,
    val discardPile: List<Card>,
    val phase: GinPhase,
    val matchScores: Map<GinPlayer, Int>,
    val boxes: Map<GinPlayer, Int>,
    val moveCount: Int = 0,
    val knownHeld: Map<GinPlayer, List<Card>> = emptyMap(),
    val laidOff: Map<GinPlayer, List<Card>> = emptyMap(),
    /** This match's house rules (by default: to 100, gin bonus on, no Big Gin). */
    val rules: GinMatchRules = GinMatchRules(),
) {
    init {
        require(matchScores.keys == GinPlayer.entries.toSet()) { "matchScores must cover both players" }
        require(boxes.keys == GinPlayer.entries.toSet()) { "boxes must cover both players" }
    }

    fun handOf(player: GinPlayer): List<Card> = if (player == GinPlayer.PLAYER) playerHand else opponentHand

    fun withHand(
        player: GinPlayer,
        hand: List<Card>,
    ): GinState = if (player == GinPlayer.PLAYER) copy(playerHand = hand) else copy(opponentHand = hand)

    /** The defender's cards laid off onto [knocker]'s melds this hand — see [laidOff]. */
    fun laidOffOnto(knocker: GinPlayer): List<Card> = laidOff[knocker].orEmpty()

    /** The cards [player] is publicly known to hold — see [knownHeld]. */
    fun knownHeldBy(player: GinPlayer): List<Card> = knownHeld[player].orEmpty()

    internal fun withKnownHeld(
        player: GinPlayer,
        cards: List<Card>,
    ): GinState = copy(knownHeld = knownHeld + (player to cards))

    companion object {
        /** The default target: first to this many raw hand points ends the match (see [GinMatchRules.target]). */
        const val MATCH_TARGET = 100

        /** Deadwood at or under this may knock; exactly 0 is gin. */
        const val KNOCK_THRESHOLD = 10

        /** The last two stock cards are never dealt out — reaching them blocks the hand. */
        const val STOCK_RESERVE = 2

        const val HAND_SIZE = 10
    }
}
