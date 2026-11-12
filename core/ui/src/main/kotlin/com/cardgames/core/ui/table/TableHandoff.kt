package com.cardgames.core.ui.table

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.tween

/**
 * A table leaving at a new deal: [outgoing] is its last state, still to be drawn underneath the
 * new deal at [alpha] until it has faded out; null when nothing is leaving.
 */
class TableHandoff<S : Any>(
    val outgoing: S?,
    val alpha: () -> Float,
)

/**
 * When [dealKey] changes from a deal that was played ([wasPlayed] on its last state),
 * keeps that last state around to be drawn under the new deal and fades it out over
 * [CardGamesMotion.PopupExit] — a crossfade with the new deal's own cards fading in, so the table
 * never empties, even briefly. A deal replacing an untouched one (e.g. a fresh table shown for a
 * moment while a saved game loads) has nothing to fade.
 */
@Composable
fun <S : Any> rememberTableHandoff(
    state: S,
    dealKey: Any,
    wasPlayed: (S) -> Boolean,
): TableHandoff<S> {
    // Plain holders, not state: the last state and deal composed, read only when the deal changes.
    val lastSeen = remember { arrayOfNulls<Any>(2) }
    val outgoing =
        remember(dealKey) {
            @Suppress("UNCHECKED_CAST")
            val previous = lastSeen[0] as S?
            previous?.takeIf { lastSeen[1] != dealKey && wasPlayed(it) }
        }
    SideEffect {
        lastSeen[0] = state
        lastSeen[1] = dealKey
    }
    val alpha = remember(dealKey) { Animatable(1f) }
    var faded by remember(dealKey) { mutableStateOf(outgoing == null) }
    val fade = CardGamesMotion.PopupExit.tween<Float>()
    LaunchedEffect(dealKey) {
        if (outgoing != null) {
            alpha.animateTo(0f, fade)
            faded = true
        }
    }
    return TableHandoff(outgoing.takeIf { !faded }) { alpha.value }
}
