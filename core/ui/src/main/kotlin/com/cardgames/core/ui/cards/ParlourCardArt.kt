package com.cardgames.core.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.core.ui.theme.SpectralFontFamily

/*
 * The Parlour card design, drawn in code from the theme colors. It is both the fallback when a pack
 * ships no card art and the source the theme-pack render task turns into a pack's face and back
 * images (docs/THEME_SPEC.md), so the two always match. Every measure is a fraction of the card
 * width, taken from the design mocks.
 */

/**
 * A card face filling its bounds (which should be [CARD_ASPECT_RATIO]). [small] is the variant for
 * cards under 60dp wide: an inline index (rank then suit) and a corner suit instead of the centre pip.
 */
@Composable
fun CardFaceArt(
    card: Card,
    small: Boolean,
    modifier: Modifier = Modifier,
    colors: CardTableColors = CardGamesTheme.colors,
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier) { drawCardFace(card, small, colors, textMeasurer) }
}

/** The lattice card back filling its bounds. */
@Composable
fun CardBackArt(
    modifier: Modifier = Modifier,
    colors: CardTableColors = CardGamesTheme.colors,
) {
    Canvas(modifier = modifier) { drawCardBack(colors) }
}

internal fun DrawScope.drawCardFace(
    card: Card,
    small: Boolean,
    colors: CardTableColors,
    textMeasurer: TextMeasurer,
) {
    val w = size.width
    val ink = if (card.isRed) colors.suitRed else colors.suitBlack
    // No edge: the face stands off the felt by its shadow alone.
    drawRect(colors.cardFace)

    // Index in the top-left corner: on a full face the rank over its suit; on a small face (a
    // tableau column shows only a strip of each card) the suit beside the rank, on its baseline.
    val rankSize = w * (if (small) SMALL_INDEX_RANK else INDEX_RANK)
    val rank =
        textMeasurer.measure(
            text = card.rank.glyph,
            style =
                TextStyle(
                    color = ink,
                    fontFamily = SpectralFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = rankSize.toSp(),
                    lineHeight = rankSize.toSp(),
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ),
        )
    if (small) {
        val left = w * SMALL_INDEX_LEFT
        val top = w * SMALL_INDEX_TOP
        drawText(rank, topLeft = Offset(left, top))
        val suitInk = w * SMALL_INDEX_SUIT * GLYPH_INK
        val baseline = top + rank.firstBaseline
        drawSuit(card.suit, ink, Offset(left + rank.size.width + w * SMALL_INDEX_GAP + suitInk / 2f, baseline - suitInk / 2f), suitInk)
    } else {
        val indexSuitBox = w * INDEX_SUIT
        val columnWidth = maxOf(rank.size.width.toFloat(), indexSuitBox * GLYPH_ADVANCE)
        val columnCentreX = w * INDEX_LEFT + columnWidth / 2f
        val top = w * INDEX_TOP
        drawText(rank, topLeft = Offset(columnCentreX - rank.size.width / 2f, top))
        drawSuit(card.suit, ink, Offset(columnCentreX, top + rankSize + indexSuitBox / 2f), indexSuitBox * GLYPH_INK)
    }

    if (small) {
        val box = w * CORNER_SUIT
        val centre = Offset(w - w * CORNER_SUIT_RIGHT - box * GLYPH_ADVANCE / 2f, size.height - w * CORNER_SUIT_BOTTOM - box / 2f)
        drawSuit(card.suit, ink, centre, box * GLYPH_INK)
    } else {
        drawSuit(card.suit, ink, Offset(w / 2f, size.height / 2f + w * PIP_DROP), w * PIP * GLYPH_INK)
    }
}

internal fun DrawScope.drawCardBack(colors: CardTableColors) {
    val w = size.width
    drawRect(colors.cardBack)
    // Two 45° line patterns crossing into a lattice.
    val period = w * LATTICE_PERIOD
    val stroke = w * LATTICE_LINE
    val reach = size.width + size.height
    clipRect {
        for (angle in floatArrayOf(LATTICE_ANGLE, -LATTICE_ANGLE)) {
            rotate(angle, pivot = center) {
                var x = center.x - reach
                while (x <= center.x + reach) {
                    drawLine(colors.cardBackLattice, Offset(x, center.y - reach), Offset(x, center.y + reach), stroke)
                    x += period
                }
            }
        }
    }
    drawCardEdge(colors.cardBackBorder, strokeWidth = w * BACK_BORDER)
}

/** The card's outline, on the same 10% radius the app clips cards to so the clip never cuts it. */
private fun DrawScope.drawCardEdge(
    color: Color,
    strokeWidth: Float,
) {
    val inset = strokeWidth / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = CornerRadius(size.width * CARD_CORNER_PERCENT / PERCENT - inset),
        style = Stroke(width = strokeWidth),
    )
}

/** [suit] drawn [inkSize] tall (and at most that wide), centred on [centre]. */
private fun DrawScope.drawSuit(
    suit: Suit,
    color: Color,
    centre: Offset,
    inkSize: Float,
) {
    translate(centre.x - inkSize / 2f, centre.y - inkSize / 2f) {
        scale(inkSize, inkSize, pivot = Offset.Zero) { drawPath(suitPaths.getValue(suit), color) }
    }
}

/** Suit shapes in a unit square, so they render identically everywhere (no font fallback). */
private val suitPaths: Map<Suit, Path> =
    mapOf(
        Suit.HEARTS to
            Path().apply {
                moveTo(0.5f, 0.96f)
                cubicTo(0.2f, 0.73f, 0f, 0.53f, 0f, 0.31f)
                cubicTo(0f, 0.13f, 0.13f, 0.02f, 0.28f, 0.02f)
                cubicTo(0.39f, 0.02f, 0.47f, 0.09f, 0.5f, 0.19f)
                cubicTo(0.53f, 0.09f, 0.61f, 0.02f, 0.72f, 0.02f)
                cubicTo(0.87f, 0.02f, 1f, 0.13f, 1f, 0.31f)
                cubicTo(1f, 0.53f, 0.8f, 0.73f, 0.5f, 0.96f)
                close()
            },
        Suit.DIAMONDS to
            Path().apply {
                moveTo(0.5f, 0f)
                quadraticTo(0.68f, 0.28f, 0.9f, 0.5f)
                quadraticTo(0.68f, 0.72f, 0.5f, 1f)
                quadraticTo(0.32f, 0.72f, 0.1f, 0.5f)
                quadraticTo(0.32f, 0.28f, 0.5f, 0f)
                close()
            },
        Suit.SPADES to
            Path().apply {
                moveTo(0.5f, 0f)
                cubicTo(0.8f, 0.25f, 1f, 0.42f, 1f, 0.61f)
                cubicTo(1f, 0.77f, 0.88f, 0.87f, 0.74f, 0.87f)
                cubicTo(0.64f, 0.87f, 0.57f, 0.82f, 0.53f, 0.76f)
                cubicTo(0.55f, 0.88f, 0.59f, 0.95f, 0.67f, 1f)
                lineTo(0.33f, 1f)
                cubicTo(0.41f, 0.95f, 0.45f, 0.88f, 0.47f, 0.76f)
                cubicTo(0.43f, 0.82f, 0.36f, 0.87f, 0.26f, 0.87f)
                cubicTo(0.12f, 0.87f, 0f, 0.77f, 0f, 0.61f)
                cubicTo(0f, 0.42f, 0.2f, 0.25f, 0.5f, 0f)
                close()
            },
        Suit.CLUBS to
            Path().apply {
                addOval(Rect(center = Offset(0.5f, 0.25f), radius = 0.22f))
                addOval(Rect(center = Offset(0.25f, 0.58f), radius = 0.22f))
                addOval(Rect(center = Offset(0.75f, 0.58f), radius = 0.22f))
                addOval(Rect(center = Offset(0.5f, 0.52f), radius = 0.12f))
                moveTo(0.47f, 0.6f)
                cubicTo(0.47f, 0.8f, 0.43f, 0.93f, 0.33f, 1f)
                lineTo(0.67f, 1f)
                cubicTo(0.57f, 0.93f, 0.53f, 0.8f, 0.53f, 0.6f)
                close()
            },
    )

private const val PERCENT = 100f
private const val INDEX_LEFT = 0.1f
private const val INDEX_TOP = 0.065f
private const val INDEX_RANK = 0.32f
private const val INDEX_SUIT = 0.24f
private const val PIP = 0.48f

/** The centre pip sits this far below the card's middle. */
private const val PIP_DROP = 0.096f
private const val SMALL_INDEX_LEFT = 0.083f
private const val SMALL_INDEX_TOP = 0.0625f
private const val SMALL_INDEX_RANK = 0.333f
private const val SMALL_INDEX_SUIT = 0.25f
private const val SMALL_INDEX_GAP = 0.02f
private const val CORNER_SUIT = 0.46f
private const val CORNER_SUIT_RIGHT = 0.083f
private const val CORNER_SUIT_BOTTOM = 0.0625f

/** How much of its font size a suit glyph's ink fills, and how wide its advance is. */
private const val GLYPH_INK = 0.72f
private const val GLYPH_ADVANCE = 0.8f
private const val BACK_BORDER = 0.04f
private const val LATTICE_PERIOD = 0.136f
private const val LATTICE_LINE = 0.023f
private const val LATTICE_ANGLE = 45f
