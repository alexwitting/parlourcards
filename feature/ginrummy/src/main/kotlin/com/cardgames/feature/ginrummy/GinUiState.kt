package com.cardgames.feature.ginrummy

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinMatchHighlights
import com.cardgames.core.domain.ginrummy.GinMove
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.Melds
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.stats.Record
import com.cardgames.core.ui.text.opponentName
import com.cardgames.feature.ginrummy.board.GinDragSource
import com.cardgames.feature.ginrummy.board.GinDropTarget
import com.cardgames.feature.ginrummy.board.waitingOn

data class GinUiState(
    /**
     * The position the table shows. While [pending] is set this is where the cards are flying to,
     * not yet committed: the move only counts (saves, scores, hands play on) once it lands.
     */
    val game: GinState,
    /**
     * False until the saved match has been restored (or a new one dealt): until then [game] is a
     * stand-in, and the screen draws no table rather than flash it.
     */
    val loaded: Boolean = true,
    /** Cards in motion towards [game]; the screen animates it and reports [GinIntent.MoveLanded]. */
    val pending: GinMotion? = null,
    /** The player's currently tapped hand card, pending Discard or Knock. */
    val selectedCard: Card? = null,
    /** True while an AI decision's pacing delay is running — drives a "thinking" indicator. */
    val isOpponentThinking: Boolean = false,
    /** Whether the undo stack has anything to pop — enables Undo. */
    val canUndo: Boolean = false,
    /** Whether the redo stack has anything to replay — enables Redo. */
    val canRedo: Boolean = false,
    /** Starting a new match now would record this one as a loss — the screen confirms first. */
    val abandonCountsAsLoss: Boolean = false,
    /** Hints still available this hand; null when unlimited. */
    val hintsLeft: Int? = null,
    /** What the Hint button suggested for this exact position; cleared by any move. */
    val hint: GinHint? = null,
    /** This match's opponent (Milo, Rosa or Vex). */
    val opponent: GinDifficulty = GinDifficulty.MEDIUM,
    /** The player's highlights so far this match. */
    val highlights: GinMatchHighlights = GinMatchHighlights(),
    /** A card the player just tried to throw but can't (they just took it from the pile). */
    val rejectedCard: Card? = null,
    /** The player passed on going gin this turn — the panel stops offering it. */
    val ginOfferDismissed: Boolean = false,
    /** Knock was pressed with "confirm before knocking" on — the panel asks first. */
    val confirmingKnock: Boolean = false,
    /** The card the player just drew, shown last in IN PLAY until they discard. */
    val drawnCard: Card? = null,
    /** At a match's end, the player's record against this opponent, this match included. */
    val overallRecord: Record? = null,
    /** Outline the pile card (and the cards it would join) when it makes a meld — the Meld hints setting. */
    val meldHints: Boolean = true,
    /** The action message under the piles: the last [FEED_LENGTH] moves this hand, oldest first. */
    val feed: List<GinFeedLine> = emptyList(),
)

val GinUiState.opponentName: String
    get() = opponent.opponentName

/**
 * What the panel offers on the player's discard: going out with every card melded — Big Gin
 * (all 11, when the match allows it) or gin (10, throwing [GinUiState.knockCard]) — unless the
 * player has said "Keep playing" this turn.
 */
val GinUiState.ginOffer: GinOffer?
    get() =
        when {
            !isPlayerTurn || ginOfferDismissed -> null
            GinEngine.isLegal(game, GinMove.BigGin) -> GinOffer.BigGin
            canKnock && knockIsGin -> knockCard?.let(GinOffer::Gin)
            else -> null
        }

sealed interface GinOffer {
    data class Gin(
        val throwing: Card,
    ) : GinOffer

    data object BigGin : GinOffer
}

/** A position change on its way: the screen animates [from] → [GinUiState.game] as [kind]. */
data class GinMotion(
    val id: Long,
    val kind: GinMotionKind,
    val from: GinState,
    /** The opponent's own move — the player's hand stays locked until it lands. */
    val byOpponent: Boolean = false,
)

enum class GinMotionKind {
    /** A fresh hand dealt out from the stock. */
    DEAL,

    /** A card taken from the stock or the discard pile (or the upcard). */
    DRAW,

    /** A card thrown onto the discard pile (including the knocker's). */
    DISCARD,

    /** A card dragged into place — it's already there. */
    DROP,

    /** Undo or redo: the path back, a little quicker. */
    UNDO,

    /** Anything else the table settles into: a pass, layoffs, the hand ending. */
    SETTLE,
}

/** The Hint button's suggestion. */
sealed interface GinHint {
    /** Take the discard pile's top card. */
    data object TakeDiscard : GinHint

    /** Draw blind from the stock. */
    data object DrawStock : GinHint

    /** Throw this card away. */
    data class Discard(
        val card: Card,
    ) : GinHint
}

/**
 * The player has a decision to make. Their own card still in flight doesn't count against it — a
 * tap lands that card at once and goes ahead — so the panel and hand don't blink off and back on
 * with every draw; the opponent's motions and the deal do.
 */
val GinUiState.isPlayerTurn: Boolean
    get() =
        (pending == null || (!pending.byOpponent && pending.kind != GinMotionKind.DEAL)) &&
            game.phase.waitingOn() == GinPlayer.PLAYER

/**
 * The opponent is taking its turn — from the moment the player's discard leaves their hand, through
 * its thinking, until its own card lands — so the hand dims once, not around each step.
 */
val GinUiState.isOpponentsTurn: Boolean
    get() =
        isOpponentThinking ||
            pending?.byOpponent == true ||
            (pending?.kind != GinMotionKind.DEAL && game.phase.waitingOn() == GinPlayer.OPPONENT)

/**
 * The card a Knock would discard. With a card lifted, that card, or none if throwing it leaves too
 * much deadwood. With nothing lifted, the throw that leaves the least deadwood, so Knock lights up
 * as soon as any discard would end the hand; tapping it lifts that card first, so the player sees
 * (and can change) what goes.
 *
 * Null outside the player's turn: the opponent's `AwaitingDiscard` is visible for a moment while it
 * decides, and the engine would apply a knock then to the opponent's hand.
 */
val GinUiState.knockCard: Card?
    get() =
        if (!isPlayerTurn) {
            null
        } else {
            when (val lifted = selectedCard) {
                null -> GinEngine.bestKnockCard(game)
                else -> lifted.takeIf { GinEngine.isLegal(game, GinMove.Knock(it)) }
            }
        }

val GinUiState.canKnock: Boolean
    get() = knockCard != null

/** True when [knockCard] would leave zero deadwood: a gin, offered as one in the panel. */
val GinUiState.knockIsGin: Boolean
    get() = knockCard?.let { Melds.bestPartition(game.playerHand - it).deadwoodPips == 0 } == true

/**
 * One-shot events. Semantic — the screen maps these to sounds/haptics via GameFeedback; the
 * ViewModel never knows what a move sounds like.
 */
sealed interface GinEffect {
    data class ShowSnackbar(
        val message: String,
    ) : GinEffect

    /** A draw or a discard, by either seat. */
    data object CardMoved : GinEffect

    /** A hand just ended in a knock, gin, or undercut — the opponent's win, or a match lost. */
    data object Knocked : GinEffect

    /** The player just won a hand (by knock, gin or undercut) without it ending the match. */
    data object HandWon : GinEffect

    /** The stock ran down with nobody knocking — a scoreless redeal. */
    data object HandBlocked : GinEffect

    /** A fresh hand (or match) was just dealt. */
    data object NewHandDealt : GinEffect

    data object MatchWon : GinEffect

    /** The player tried to throw the card they just took from the pile. */
    data object CantDiscard : GinEffect
}

sealed interface GinIntent {
    data object TakeUpcard : GinIntent

    data object PassUpcard : GinIntent

    data object DrawFromStock : GinIntent

    data object DrawFromDiscard : GinIntent

    /** Tapping a hand card selects it (or deselects it, if already selected). */
    data class SelectHandCard(
        val card: Card,
    ) : GinIntent

    /**
     * A drag-and-drop move: [source] dropped on [target] — drag the stock or discard top onto
     * the hand to draw, or a hand card onto the discard pile to discard. See `GinDrag.kt`'s
     * `moveFor` for the source/target → engine-move mapping. [carried] is false for the same
     * move made by tapping (a selected card, then it or the pile): the card then flies there,
     * where a dragged card is already in the player's finger and just settles.
     */
    data class DropCard(
        val source: GinDragSource,
        val target: GinDropTarget,
        val carried: Boolean = true,
    ) : GinIntent

    /**
     * Knock. With nothing lifted, lifts [GinUiState.knockCard] (the suggestion) and asks — "Knock,
     * throw 2♠?" — so the throw is the player's to see and change. With a card lifted, throws it
     * and knocks, asking first only when "confirm before knocking" is on.
     */
    data object Knock : GinIntent

    /** Yes, knock (after the confirmation). */
    data object ConfirmKnock : GinIntent

    /** Not yet — back to the turn. */
    data object CancelKnock : GinIntent

    /** Go out as offered: gin (throwing the offered card) or Big Gin. */
    data object GoGin : GinIntent

    /** Keep playing instead of going gin this turn. */
    data object DismissGinOffer : GinIntent

    /** The cards of [GinMotion] [id] have landed: the move is committed. */
    data class MoveLanded(
        val id: Long,
    ) : GinIntent

    /** Asks for a suggestion: which pile to draw from, or which card to throw. */
    data object Hint : GinIntent

    /** Rewinds to the start of the player's previous action, including the AI's reply in between. */
    data object Undo : GinIntent

    /** Replays forward the most recently undone action, including the AI's reply in between. */
    data object Redo : GinIntent

    data object NextHand : GinIntent

    /** Deal the hand in play again from the start (the pause menu's Restart this hand). */
    data object RestartHand : GinIntent

    /** Abandons any hand in progress and starts a fresh match — no mid-hand resume. */
    data object NewMatch : GinIntent

    /** A fresh match against [opponent] (a rematch, or an easier rival), which becomes the default. */
    data class NewMatchAgainst(
        val opponent: GinDifficulty,
    ) : GinIntent
}
