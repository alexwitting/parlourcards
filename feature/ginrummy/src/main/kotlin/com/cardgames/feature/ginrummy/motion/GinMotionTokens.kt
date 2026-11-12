package com.cardgames.feature.ginrummy.motion

import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.MotionSpec
import com.cardgames.core.ui.motion.StandardEasing

/**
 * Gin's own motion numbers — every tunable timing and lift the game doesn't share with Solitaire,
 * in one place. Millisecond values are at normal speed (apply the Card speed setting with
 * `scaledMillis` / `tween`). Shared tokens (flip, springs, fades) are in [CardGamesMotion]; *when*
 * each applies within a move is in `GinChoreography.kt` and the deal's pacing in `GinDeal.kt`.
 */
internal object GinMotionTokens {
    /** Draws and discards: a card carried across the table, a little slower than the shared flight. */
    val Flight = MotionSpec(460, StandardEasing)

    /** The gap between cards leaving the stock in a deal. */
    const val DEAL_STAGGER = 60L

    /** A card drawn from the stock turns over this far into its flight, so it arrives face up. */
    const val FLIP_IN_FLIGHT = 100

    /** Undo replays the path at this fraction of the duration. */
    const val UNDO_SPEED = 0.75f

    /** The opponent thinks for [THINK_MIN]–[THINK_MAX] before a draw, [STEP] before a discard. */
    const val THINK_MIN = 700L
    const val THINK_MAX = 1100L
    const val STEP = 450L

    /** The beat before a layoff resolves at the end of a hand. */
    const val LAYOFF_PAUSE = 450L

    /** The "Thinking…" dots: each pulse, and how far each dot lags the one before. */
    const val THINKING_DOT_PULSE = 500
    const val THINKING_DOT_LAG = 160

    /** After a gin lands, how long the table shows the loser's hand turned face up before the result. */
    const val GIN_REVEAL_PAUSE = 550L

    /** The beat before a result's title pops in. */
    const val RESULT_TITLE_BEAT = 240L
}
