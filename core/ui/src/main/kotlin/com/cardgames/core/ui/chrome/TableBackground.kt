package com.cardgames.core.ui.chrome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import com.cardgames.core.ui.cards.CardTableColors
import com.cardgames.core.ui.theme.CardGamesTheme

/** The table under every screen: the pack's table art when it has some, otherwise the felt. */
@Composable
fun TableBackground(modifier: Modifier = Modifier) {
    val theme = CardGamesTheme.table
    val art = theme.images?.tableBackground()
    if (art != null) {
        Image(bitmap = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier = modifier.felt(theme.colors))
    }
}

/**
 * The Parlour felt: an elliptical radial gradient [CardTableColors.tableLight] →
 * [CardTableColors.table] (55%) → [CardTableColors.tableDark], 120% × 60% of the area, centred at
 * 50% x, 42% y — lightest under the cards, darkening into a soft vignette at the edges.
 */
fun Modifier.felt(colors: CardTableColors): Modifier =
    drawBehind {
        val center = Offset(size.width / 2f, size.height * FELT_CENTER_Y)
        val radiusX = size.width * FELT_RADIUS_X
        val squash = size.height * FELT_RADIUS_Y / radiusX
        // A circular gradient squashed vertically about the centre makes the ellipse; the rect is
        // stretched by the inverse so that, once squashed, it still covers the whole area.
        scale(scaleX = 1f, scaleY = squash, pivot = center) {
            val top = center.y - center.y / squash
            val bottom = center.y + (size.height - center.y) / squash
            drawRect(
                brush =
                    Brush.radialGradient(
                        0f to colors.tableLight,
                        FELT_MID_STOP to colors.table,
                        1f to colors.tableDark,
                        center = center,
                        radius = radiusX,
                    ),
                topLeft = Offset(0f, top),
                size = Size(size.width, bottom - top),
            )
        }
    }

private const val FELT_CENTER_Y = 0.42f
private const val FELT_RADIUS_X = 1.2f
private const val FELT_RADIUS_Y = 0.6f
private const val FELT_MID_STOP = 0.55f
