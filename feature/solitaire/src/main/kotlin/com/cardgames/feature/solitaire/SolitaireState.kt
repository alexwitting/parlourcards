package com.cardgames.feature.solitaire

import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.solitaire.KlondikeMove
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.feature.solitaire.board.SolitaireDragSource
import com.cardgames.feature.solitaire.board.SolitaireDropTarget

data class SolitaireState(
    /**
     * The position the board shows. While [pending] is set this is where the cards are flying to,
     * not yet committed: the move counts (saves, scores, wins) only once it lands.
     */
    val game: KlondikeState,
    /**
     * False while the saved game is still loading: until then [game] is the constructor's stand-in
     * deal, and the screen draws no table rather than show it and swap it out.
     */
    val loaded: Boolean = true,
    /** Cards in motion towards [game]; the screen animates it and reports [SolitaireIntent.MoveLanded]. */
    val pending: PendingMotion? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isWon: Boolean = false,
    val canAutoComplete: Boolean = false,
    /** Play time so far; only accrues while the screen is visible and the game isn't won. */
    val elapsedSeconds: Int = 0,
    /** The move the hint button suggested for this exact position; cleared by any move. */
    val hint: KlondikeMove? = null,
    /** Hints still available this deal; null when unlimited. */
    val hintsLeft: Int? = null,
    /** A card (or run) held up by a long press, waiting for a tap on where it should go. */
    val selection: SolitaireDragSource? = null,
    /** A winnable-deal search is in flight (New Game, or the very first deal) — New disables, the board stays as-is. */
    val isDealing: Boolean = false,
    /** The stock is shuffling before a new deal (it shakes). */
    val shuffling: Boolean = false,
    /** A bounded solver search couldn't find further progress from here — a non-blocking, ignorable hint, not a proof. */
    val isStuck: Boolean = false,
    /** Starting a new game now would record this one as a loss — the screen confirms first. */
    val abandonCountsAsLoss: Boolean = false,
    /** The figures for the Solved screen, once the winning move has landed. */
    val solved: SolvedSummary? = null,
)

/** A position change on its way: the screen animates [from] → [SolitaireState.game] as [kind]. */
data class PendingMotion(
    val id: Long,
    val kind: MotionKind,
    val from: KlondikeState,
)

enum class MotionKind {
    /** A tapped card (or a selected one) flying to its destination. */
    MOVE,

    /** Cards dropped by a drag — already where they belong. */
    DROP,

    /** Cards turned from the stock onto the waste. */
    DRAW,

    /** The waste turned back over into the stock. */
    RECYCLE,

    /** A fresh deal, or the same deal again. */
    DEAL,

    /** Restart: every card flies back to the stock face down, foundations first, before the deal replays. */
    GATHER,

    /** Every remaining card sent home, lowest first. */
    AUTO_COMPLETE,

    /** Undo or redo: the path back, a little quicker. */
    UNDO,
}

/** What the Solved screen shows. [isNewBest] when this win beat (or set) the best time. */
data class SolvedSummary(
    val seconds: Int,
    val moves: Int,
    val drawCount: Int,
    val streak: Int,
    val bestSeconds: Int,
    val isNewBest: Boolean,
    val gamesWon: Int,
    val gamesPlayed: Int,
)

/**
 * One-shot events. The feedback ones are semantic — the screen maps them to sounds/haptics via
 * GameFeedback; the ViewModel never knows what a move sounds like.
 */
sealed interface SolitaireEffect {
    data class ShowSnackbar(
        val message: String,
    ) : SolitaireEffect

    /** A card (or run) landed somewhere: move, drop, or undo. */
    data object CardMoved : SolitaireEffect

    /** A move that also turned a face-down card over. */
    data object CardFlipped : SolitaireEffect

    /** Cards dealt from the stock. */
    data object CardsDealt : SolitaireEffect

    /** The waste turned back into the stock, or a fresh deal hit the table. */
    data object Shuffled : SolitaireEffect

    data object GameWon : SolitaireEffect
}

/** What a successful non-winning move should feel like — pure mapping, kept out of the ViewModel. */
internal fun feedbackFor(
    move: KlondikeMove,
    before: KlondikeState,
    after: KlondikeState,
): SolitaireEffect =
    when {
        move is KlondikeMove.DealFromStock -> SolitaireEffect.CardsDealt
        move is KlondikeMove.RecycleWaste -> SolitaireEffect.Shuffled
        after.tableau.sumOf { it.faceDownCount } < before.tableau.sumOf { it.faceDownCount } ->
            SolitaireEffect.CardFlipped
        else -> SolitaireEffect.CardMoved
    }

sealed interface SolitaireIntent {
    /** Deal from the stock, or recycle the waste when the stock is empty. Cancels a selection. */
    data object TapStock : SolitaireIntent

    /** Sends the waste's top card to its best place — or, with a selection up, cancels it. */
    data object TapWaste : SolitaireIntent

    /** Sends this card (and the run on it) to its best place — or, with a selection up, moves the selection onto this pile. */
    data class TapTableauCard(
        val pile: Int,
        val cardIndex: Int,
    ) : SolitaireIntent

    /** An empty tableau column: the selection's destination when one is up. */
    data class TapEmptyTableau(
        val pile: Int,
    ) : SolitaireIntent

    /** A foundation: the selection's destination when one is up. */
    data class TapFoundation(
        val suit: Suit,
    ) : SolitaireIntent

    /** The bare table: cancels a selection. */
    data object TapTable : SolitaireIntent

    /** A long press: holds [source] up until a tap says where it goes. */
    data class Hold(
        val source: SolitaireDragSource,
    ) : SolitaireIntent

    /** Cards dragged from [source] were released over [target]. */
    data class DropCards(
        val source: SolitaireDragSource,
        val target: SolitaireDropTarget,
    ) : SolitaireIntent

    /** The cards of [PendingMotion] [id] have landed: the move is committed. */
    data class MoveLanded(
        val id: Long,
    ) : SolitaireIntent

    data object Undo : SolitaireIntent

    /** Replays the most recently undone move — cleared by any new move, restart, or auto-complete. */
    data object Redo : SolitaireIntent

    /** The screen came on screen (lifecycle START) — the play timer runs. */
    data object ScreenVisible : SolitaireIntent

    /** The screen went to the background (lifecycle STOP) — the play timer pauses. */
    data object ScreenHidden : SolitaireIntent

    /** The pause menu opened ([paused]) or closed — the play timer stops while it's up. */
    data class Paused(
        val paused: Boolean,
    ) : SolitaireIntent

    data object NewGame : SolitaireIntent

    /** Replay the current shuffle from move zero (same seed). Undoable like a move. */
    data object RestartDeal : SolitaireIntent

    data object Hint : SolitaireIntent

    data object AutoComplete : SolitaireIntent
}
