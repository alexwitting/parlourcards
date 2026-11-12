package com.cardgames.feature.solitaire.motion

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardgames.core.ui.motion.CardGamesMotion

/**
 * Solitaire's own motion numbers — every tunable timing the game doesn't share with Gin, in one
 * place. Millisecond values are at normal speed (apply the Card speed setting with `scaledMillis` /
 * `tween`). Shared tokens (flight, flip, springs) are in [CardGamesMotion]; when each applies
 * within a move is in `SolitaireChoreography.kt`.
 */
internal object SolitaireMotionTokens {
    /** Gaps between cards in a sequence. */
    const val DEAL_STAGGER = 50
    const val DRAW_THREE_STAGGER = 60
    const val RUN_STAGGER = 30
    const val AUTO_COMPLETE_STAGGER = 120
    const val RECYCLE_STAGGER = 40

    /** Restart and New: every card heading back to the stock. */
    const val GATHER_STAGGER = 12

    /** How long after a moving run's last card leaves the card it uncovers starts turning over. */
    const val REVEAL_LEAD = 60

    /** A dragged run is already in place: the uncovered card turns over after this short beat. */
    const val DROP_REVEAL = 40

    /** A card being dealt or drawn turns over this far into its flight. */
    const val FLIP_IN_FLIGHT = 160

    /** Undo replays the path at this fraction of the duration. */
    const val UNDO_SPEED = 0.75f

    /** The stock's shuffle before a new deal: three shakes of [SHUFFLE_SHAKE] in this long. */
    const val SHUFFLE_MILLIS = 360L
    const val SHUFFLE_SHAKES = 3
    val SHUFFLE_SHAKE: Dp = 4.dp

    /** Complete's one pulse when it comes on (1 → this → 1, on the pop spring). */
    const val COMPLETE_PULSE = 1.04f
}
