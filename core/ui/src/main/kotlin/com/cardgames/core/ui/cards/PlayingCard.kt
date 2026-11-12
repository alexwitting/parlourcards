package com.cardgames.core.ui.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.LocalReduceMotion
import com.cardgames.core.ui.motion.MotionSpec
import com.cardgames.core.ui.motion.tween
import com.cardgames.core.ui.theme.CardGamesTheme
import kotlin.math.roundToInt

/** Card aspect ratio, width : height — 2.5" x 3.5" (see docs/THEME_SPEC.md). */
const val CARD_ASPECT_RATIO: Float = 5f / 7f

/** Corner rounding as a percentage of card width, applied by the app (see docs/THEME_SPEC.md). */
const val CARD_CORNER_PERCENT: Int = 20

/** Below this width a card shows its small face: no centre pip, a corner suit instead. */
val SMALL_CARD_MAX_WIDTH: Dp = 60.dp

/**
 * Forces every card below to its small (true) or full (false) face whatever its size; null picks
 * by width. Solitaire uses small faces throughout, so its bigger foundation and stock cards read as
 * the same cards as its columns.
 */
val LocalSmallCardFaces = compositionLocalOf<Boolean?> { null }

private const val HALF_TURN_DEGREES = 90f
private const val FULL_TURN_DEGREES = 180f
private const val CAMERA_DISTANCE_DP = 30f

/**
 * Drop shadow so cards read as lying on the felt — app-drawn, never baked into images. Also what
 * separates one card from the next in an overlapping stack: each card is drawn after (on top of)
 * whatever it overlaps, and this shadow reads as the gap between them. A card switches between
 * the two heights, never animating between them.
 */
private val CARD_ELEVATION_REST = 4.dp
private val CARD_ELEVATION_LIFTED = 12.dp

/** Brass (selection) rings are a touch heavier than sage (hint) ones. */
val CARD_RING_SELECT: Dp = 2.5.dp
val CARD_RING_HINT: Dp = 2.dp

private const val MELD_STRIPE_LEFT = 6f / 44f
private const val MELD_STRIPE_TOP = 34f / 62f
private const val MELD_STRIPE_WIDTH = 5f / 44f
private const val MELD_STRIPE_HEIGHT = 20f / 62f
private const val MELD_STRIPE_ROUNDING = 0.6f

/**
 * The one canonical card composable. Renders the active theme pack's images, or the Parlour card
 * art drawn from the theme colors when the pack has none. Turning [faceUp] on or off animates a 3D
 * flip along [flip] (a motion token, so the animation-speed setting applies); first composition
 * renders settled.
 *
 * [lifted] raises the shadow (a held or dragged card). [ring] draws a ring around the card in that
 * color, [ringWidth] wide — brass for selection, sage for a hint, red for an illegal move.
 * [meldEdge] draws the small bar along the bottom of the face that marks the meld a card belongs to.
 * [flipDelayMillis] holds a flip back (at normal speed) — a card turning over partway through its
 * flight. [small] forces the small or full face; null picks by width ([SMALL_CARD_MAX_WIDTH]).
 */
@Composable
fun PlayingCard(
    card: Card,
    faceUp: Boolean,
    modifier: Modifier = Modifier,
    flip: MotionSpec = CardGamesMotion.Flip,
    lifted: Boolean = false,
    ring: Color? = null,
    ringWidth: Dp = CARD_RING_SELECT,
    meldEdge: Color? = null,
    flipDelayMillis: Int = 0,
    small: Boolean? = null,
) {
    val theme = CardGamesTheme.table
    val shape = RoundedCornerShape(percent = CARD_CORNER_PERCENT)
    // Reduce motion turns cards over in place, with no visible flip.
    val flipSpec = if (LocalReduceMotion.current) MotionSpec(0) else flip
    val rotation by animateFloatAsState(
        targetValue = if (faceUp) 0f else FULL_TURN_DEGREES,
        animationSpec = flipSpec.tween(delay = MotionSpec(flipDelayMillis)),
        label = "cardFlip",
    )
    val card3d =
        modifier
            .aspectRatio(CARD_ASPECT_RATIO)
            // The 3D flip lives in this graphicsLayer; the shadow and ring must be inside it
            // (not before it) so they foreshorten along with the rotating card instead of
            // staying full size underneath a shrinking card mid-flip.
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = CAMERA_DISTANCE_DP * density
            }.cardRing(ring, ringWidth)
            .cardShadow(elevation = if (lifted) CARD_ELEVATION_LIFTED else CARD_ELEVATION_REST, cornerPercent = CARD_CORNER_PERCENT)
            .clip(shape)
            // Only a face-up card says what it is: a face-down one adds nothing a screen reader can
            // use (its pile or seat is labelled), and a table's dozens of stacked backs make every
            // accessibility pass sort them all by position — enough to stall a screen opening.
            .then(if (faceUp) Modifier.semantics { contentDescription = card.toString() } else Modifier)
    val images = theme.images
    if (images != null) {
        // A pack's images: one node that picks and draws its bitmap at draw time — no measuring
        // layout per card (a table composes 52 of these at once) and no recomposition per flip frame.
        val forcedSmall = small ?: LocalSmallCardFaces.current
        val smallMaxPx = with(LocalDensity.current) { SMALL_CARD_MAX_WIDTH.toPx() }
        Box(
            modifier =
                card3d.drawBehind {
                    val showFace = rotation < HALF_TURN_DEGREES
                    val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    if (showFace) {
                        drawImage(images.face(card, forcedSmall ?: (size.width < smallMaxPx)), dstSize = dst)
                        meldEdge?.let { drawMeldEdge(it) }
                    } else {
                        // The back shows while the card is past 90°, so mirror it to read correctly.
                        scale(scaleX = -1f, scaleY = 1f) { drawImage(images.back(), dstSize = dst) }
                    }
                },
        )
        return
    }
    BoxWithConstraints(modifier = card3d) {
        val small = small ?: LocalSmallCardFaces.current ?: (maxWidth < SMALL_CARD_MAX_WIDTH)
        val showFace = rotation < HALF_TURN_DEGREES
        // The back side renders while the card is past 90°, so mirror it to read correctly.
        val sideModifier =
            if (showFace) Modifier else Modifier.graphicsLayer { rotationY = FULL_TURN_DEGREES }
        Box(modifier = sideModifier.fillMaxSize()) {
            if (showFace) {
                CardFaceArt(card = card, small = small, modifier = Modifier.fillMaxSize())
            } else {
                CardBackArt(modifier = Modifier.fillMaxSize())
            }
            if (showFace && meldEdge != null) Box(modifier = Modifier.matchParentSize().meldEdge(meldEdge))
        }
    }
}

/**
 * The meld stripe on a face: a short vertical bar under the index (5×20 on a 44dp card, scaled
 * with the card) — the same stripe as [PlayingCard]'s `meldEdge`, for call sites that only hold a
 * card's modifier (a movable card in a meld fan).
 */
fun Modifier.meldEdge(color: Color?): Modifier =
    if (color == null) {
        this
    } else {
        drawWithContent {
            drawContent()
            drawMeldEdge(color)
        }
    }

private fun DrawScope.drawMeldEdge(color: Color) {
    val width = size.width * MELD_STRIPE_WIDTH
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * MELD_STRIPE_LEFT, size.height * MELD_STRIPE_TOP),
        size = Size(width, size.height * MELD_STRIPE_HEIGHT),
        cornerRadius = CornerRadius(width * MELD_STRIPE_ROUNDING),
    )
}

/**
 * Darkens a card by [amount] (0–1) without touching what's around it — the under-cards of a
 * stacked pile, so the stack reads as depth.
 */
fun Modifier.cardShade(amount: Float): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(Color.Black.copy(alpha = amount), blendMode = BlendMode.SrcAtop)
        }

/**
 * A ring just outside a card's edge, on the card's own corner radius — the same ring as
 * [PlayingCard]'s `ring`, for call sites that only hold a card's modifier (a movable card).
 */
fun Modifier.cardRing(
    color: Color?,
    width: Dp,
): Modifier =
    if (color == null) {
        this
    } else {
        drawWithContent {
            drawContent()
            val stroke = width.toPx()
            val half = stroke / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(-half, -half),
                size = Size(size.width + stroke, size.height + stroke),
                cornerRadius = CornerRadius(size.width * CARD_CORNER_PERCENT / PERCENT + half),
                style = Stroke(width = stroke),
            )
        }
    }

private const val PERCENT = 100f
