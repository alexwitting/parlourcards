package com.cardgames.core.ui.cards

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.core.ui.theme.SpectralFontFamily
import kotlin.math.roundToInt

/**
 * Vertical pile with each covered card peeking out above the next — the Klondike tableau
 * layout. Children are measured to the incoming max width (put cards directly inside);
 * [peekFraction] is how much of covered card [index]'s height stays visible — per-card, so
 * face-down cards can peek less than face-up ones. When the pile would be taller than the space
 * it's given, every peek shrinks by the same factor so the top card still fits.
 */
@Composable
fun OverlappingColumn(
    modifier: Modifier = Modifier,
    peekFraction: (index: Int) -> Float = { DEFAULT_PEEK_FRACTION },
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = constraints.maxWidth, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }
        if (placeables.isEmpty()) {
            return@Layout layout(constraints.maxWidth, 0) {}
        }
        val natural = placeables.mapIndexed { index, p -> p.height * peekFraction(index) }
        val naturalRun = natural.dropLast(1).sum()
        val room = constraints.maxHeight - placeables.last().height
        val squeeze = if (constraints.hasBoundedHeight && naturalRun > room && naturalRun > 0f) maxOf(room, 0) / naturalRun else 1f
        val steps = natural.map { (it * squeeze).roundToInt() }
        val height = placeables.last().height + steps.dropLast(1).sum()
        layout(constraints.maxWidth, height) {
            var y = 0
            placeables.forEachIndexed { index, placeable ->
                placeable.place(0, y)
                y += steps[index]
            }
        }
    }
}

/**
 * Card-sized outline marking an empty slot (tableau, foundation, stock), optionally with a faint
 * [glyph] in the middle — `K` on an empty tableau column, the suit on an empty foundation.
 */
@Composable
fun EmptyPileSlot(
    modifier: Modifier = Modifier,
    glyph: String? = null,
) {
    val colors = CardGamesTheme.colors
    BoxWithConstraints(
        modifier =
            modifier
                .aspectRatio(CARD_ASPECT_RATIO)
                .border(
                    width = 1.5.dp,
                    color = colors.onTable.copy(alpha = EMPTY_SLOT_LINE_ALPHA),
                    shape = RoundedCornerShape(percent = CARD_CORNER_PERCENT),
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph != null) {
            val size = with(LocalDensity.current) { (maxWidth * EMPTY_SLOT_GLYPH).toSp() }
            Text(
                glyph,
                color = colors.onTable.copy(alpha = EMPTY_SLOT_GLYPH_ALPHA),
                fontSize = size,
                lineHeight = size,
                fontFamily = SpectralFontFamily,
            )
        }
    }
}

private const val DEFAULT_PEEK_FRACTION = 0.22f
private const val EMPTY_SLOT_LINE_ALPHA = 0.22f
private const val EMPTY_SLOT_GLYPH_ALPHA = 0.35f
private const val EMPTY_SLOT_GLYPH = 0.46f
