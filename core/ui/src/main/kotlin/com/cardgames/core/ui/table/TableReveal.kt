package com.cardgames.core.ui.table

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.scaledMillis
import com.cardgames.core.ui.motion.tween
import kotlinx.coroutines.delay

/** Whether a game screen's table is drawn yet, and how opaque it is (see [rememberTableReveal]). */
class TableReveal(
    val shown: Boolean,
    val alpha: () -> Float,
)

/**
 * A game screen's table as the screen opens. With [afterTransition] the screen arrives with just
 * its felt and chrome — composing a whole table is heavy, and doing it mid-slide freezes the slide —
 * and once the screen's transition ([CardGamesMotion.Screen]) is over and the game is [ready], the
 * table is composed while still invisible and then, a frame later, fades in over
 * [CardGamesMotion.Base]. Without it (previews, tests) the table is drawn as soon as it's ready.
 */
@Composable
fun rememberTableReveal(
    ready: Boolean,
    afterTransition: Boolean,
): TableReveal {
    val alpha = remember { Animatable(if (afterTransition) 0f else 1f) }
    var arrived by remember { mutableStateOf(!afterTransition) }
    val hold = scaledMillis(CardGamesMotion.Screen.millis.toLong())
    val fade = CardGamesMotion.Base.tween<Float>()
    LaunchedEffect(Unit) {
        if (arrived) return@LaunchedEffect
        delay(hold)
        arrived = true
    }
    val shown = ready && arrived
    LaunchedEffect(shown) {
        if (!shown || alpha.value == 1f) return@LaunchedEffect
        // Past the frame that composes the table, so the fade starts from a table that's all there.
        withFrameNanos {}
        withFrameNanos {}
        alpha.animateTo(1f, fade)
    }
    return TableReveal(shown = shown, alpha = { alpha.value })
}
