package com.cardgames.core.ui.cards

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import com.cardgames.core.ui.motion.CardGamesMotion
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A card's two touch actions: a quick tap runs [onTap]; holding still for
 * [CardGamesMotion.LONG_PRESS_MILLIS] (shorter than the system long press) runs [onHold] at once,
 * without waiting for the finger to lift. Moving off before then is a drag — a [dragSource] on the
 * same node claims it, and neither action fires. No double-tap: a tap never waits to see whether
 * a second one follows.
 */
fun Modifier.tapOrHold(
    enabled: Boolean = true,
    onHold: (() -> Unit)? = null,
    onTap: () -> Unit,
): Modifier =
    composed {
        val tap by rememberUpdatedState(onTap)
        val hold by rememberUpdatedState(onHold)
        pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var released = false
                val lifted =
                    withTimeoutOrNull(CardGamesMotion.LONG_PRESS_MILLIS) {
                        val up = waitForUpOrCancellation()
                        released = true
                        up
                    }
                when {
                    // Lifted in time without anything else claiming the pointer: a tap.
                    released && lifted != null -> {
                        lifted.consume()
                        tap()
                    }
                    // Still down after the hold time: a hold (when this card has one).
                    !released -> hold?.invoke()
                    // A drag (or another gesture) claimed the pointer first: nothing here.
                    else -> Unit
                }
            }
        }
    }
