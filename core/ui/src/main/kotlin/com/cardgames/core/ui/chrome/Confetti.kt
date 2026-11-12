package com.cardgames.core.ui.chrome

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.LocalReduceMotion
import com.cardgames.core.ui.motion.scaledMillis
import com.cardgames.core.ui.theme.CardGamesTheme
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Confetti over a win — brass, burgundy, sage, cream and deep brass flecks that burst in from
 * just above the top edge, fanning down and out across the screen, slow as they spread, then
 * drift down, swaying and turning, and loop round from the top. Under Reduce motion it's still: the
 * flecks lie along the top edge and down both sides ([sideDepth] deep). The burst waits
 * [startAfterMillis] — by default the win screens' fade-in ([CardGamesMotion.Base], at the Card
 * speed) — so it's seen whole; nothing is drawn before it. The clock is read only in the draw
 * phase, so a frame redraws the canvas without recomposing anything.
 */
@Composable
fun Confetti(
    modifier: Modifier = Modifier,
    pieces: Int = CONFETTI_PIECES,
    sideDepth: Dp = CONFETTI_SIDE_DEPTH,
    startAfterMillis: Int = scaledMillis(CardGamesMotion.Base.millis),
) {
    val colors = CardGamesTheme.colors
    val palette = listOf(colors.accent, colors.opponent, colors.hint, colors.panel, colors.accentDeep)
    val still = LocalReduceMotion.current
    val flecks = remember(pieces) { List(pieces) { Fleck(it) } }
    var seconds by remember { mutableFloatStateOf(NOT_STARTED) }
    if (!still) {
        LaunchedEffect(Unit) {
            delay(startAfterMillis.toLong())
            val start = withFrameNanos { it }
            while (true) withFrameNanos { seconds = (it - start) / NANOS_PER_SECOND }
        }
    }
    Canvas(modifier = modifier) {
        val unit = size.width / DESIGN_WIDTH
        val loop = size.height / unit + CONFETTI_WRAP * 2
        val t = seconds
        if (!still && t < 0f) return@Canvas
        flecks.forEachIndexed { i, fleck ->
            val (x, y, turn) =
                if (still) {
                    stillPlace(i, sideDepth.value)
                } else {
                    // Thrown out against drag (it slows to a stop over ~1s), while falling at its own pace.
                    val thrown = (1 - exp(-DRAG * t)) / DRAG
                    val bx = DESIGN_WIDTH / 2 + fleck.vx * thrown + sin(t * fleck.swayRate + i) * SWAY * (1 - exp(-t))
                    val by = BURST_TOP + fleck.vy * thrown + fleck.fall * t
                    // Back round from the top once it's fallen past the bottom (never while still rising).
                    val wrapped = if (by > loop - CONFETTI_WRAP) (by + CONFETTI_WRAP) % loop - CONFETTI_WRAP else by
                    Triple(bx, wrapped, fleck.turn + fleck.spin * t + fleck.burstSpin * thrown)
                }
            val long = i % SIDE_EVERY != 0
            val w = (if (long) PIECE_THIN else PIECE_WIDE) * unit
            val h = (if (long) PIECE_LONG else PIECE_SHORT) * unit
            rotate(turn % FULL_TURN, pivot = Offset(x * unit + w / 2, y * unit + h / 2)) {
                drawRoundRect(
                    palette[i % palette.size],
                    topLeft = Offset(x * unit, y * unit),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

/** One fleck's throw, fall, sway and spin — from its index, so every run (and screenshot) is the same. */
private class Fleck(
    index: Int,
) {
    private val random = Random(index)
    private val angle = BURST_ANGLE_MIN + random.nextFloat() * BURST_ANGLE_SPREAD
    private val speed = BURST_SPEED_MIN + random.nextFloat() * BURST_SPEED_SPREAD
    val vx = cos(angle) * speed
    val vy = sin(angle) * speed
    val fall = FALL_MIN + random.nextFloat() * FALL_SPREAD
    val swayRate = SWAY_MIN + random.nextFloat() * SWAY_SPREAD
    val turn = index * GOLDEN_ANGLE
    val spin = (if (index % 2 == 0) 1 else -1) * (SPIN_MIN + random.nextFloat() * SPIN_SPREAD)
    val burstSpin = spin * BURST_SPIN
}

/** Where a fleck lies when nothing moves: along the top edge, every third down one side or the other. */
private fun stillPlace(
    i: Int,
    sideDepth: Float,
): Triple<Float, Float, Float> {
    val side = i % SIDE_EVERY == 0
    val x =
        when {
            !side -> ((i * X_STRIDE) % CONFETTI_SPAN + CONFETTI_INSET).toFloat()
            i % 2 == 1 -> (SIDE_EDGE + (i * LEFT_STRIDE) % SIDE_BAND).toFloat()
            else -> DESIGN_WIDTH - SIDE_BAND + (i * RIGHT_STRIDE) % SIDE_BAND
        }
    val y = if (side) SIDE_TOP + (i * Y_STRIDE) % sideDepth else TOP_ROW + (i * TOP_STRIDE) % TOP_BAND
    return Triple(x, y, i * GOLDEN_ANGLE)
}

private const val CONFETTI_PIECES = 180
private val CONFETTI_SIDE_DEPTH = 150.dp
private const val BURST_TOP = -12f
private const val DESIGN_WIDTH = 380f
private const val SIDE_EVERY = 3
private const val X_STRIDE = 53
private const val CONFETTI_SPAN = 370
private const val CONFETTI_INSET = 10
private const val SIDE_BAND = 12
private const val SIDE_EDGE = 2
private const val LEFT_STRIDE = 7
private const val RIGHT_STRIDE = 5
private const val SIDE_TOP = 60f
private const val Y_STRIDE = 41
private const val TOP_ROW = 34f
private const val TOP_STRIDE = 17
private const val TOP_BAND = 10
private const val PIECE_THIN = 7f
private const val PIECE_WIDE = 10f
private const val PIECE_LONG = 12f
private const val PIECE_SHORT = 6f
private const val GOLDEN_ANGLE = 137.5f
private const val FULL_TURN = 360f
private const val NANOS_PER_SECOND = 1e9f
private const val NOT_STARTED = -1f

// Motion, in design units (1/380 of the width) and seconds. The burst throws each fleck from
// BURST_TOP (just out of sight above the middle of the top edge) down and out — a fan from nearly
// straight right, through down, to nearly straight left — and drag slows it; it then falls at its
// own pace and comes back in above the top, CONFETTI_WRAP units out of sight each way.
private const val DRAG = 2.4f
private const val BURST_ANGLE_MIN = 0.15f
private const val BURST_ANGLE_SPREAD = 2.84f
private const val BURST_SPEED_MIN = 160f
private const val BURST_SPEED_SPREAD = 640f
private const val BURST_SPIN = 4f
private const val CONFETTI_WRAP = 20f
private const val FALL_MIN = 28f
private const val FALL_SPREAD = 40f
private const val SWAY = 6f
private const val SWAY_MIN = 0.9f
private const val SWAY_SPREAD = 1.3f
private const val SPIN_MIN = 30f
private const val SPIN_SPREAD = 120f
