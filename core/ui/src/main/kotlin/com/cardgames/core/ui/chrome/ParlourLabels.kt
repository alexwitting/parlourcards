package com.cardgames.core.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardgames.core.ui.theme.CardGamesTheme

/** The top bar's centre pill: an 8dp sage dot and "Hand 5 · First to 100". */
@Composable
fun TablePill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = CardGamesTheme.colors
    Row(
        modifier =
            modifier
                .background(colors.chipOnTableSoft, ChromeShapes.Pill)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm),
    ) {
        Box(modifier = Modifier.size(8.dp).background(colors.hint, CircleShape))
        Text(text, color = colors.onTable, style = CardGamesTheme.type.pill, maxLines = 1)
    }
}

/** A small chip on the felt ("Medium bot", "10 cards"); [deep] for the darker one. */
@Composable
fun TableChip(
    text: String,
    modifier: Modifier = Modifier,
    deep: Boolean = false,
    fill: Color? = null,
    ink: Color? = null,
) {
    val colors = CardGamesTheme.colors
    Text(
        text,
        color = ink ?: colors.onTable,
        style = CardGamesTheme.type.chip,
        maxLines = 1,
        modifier =
            modifier
                .background(fill ?: if (deep) colors.deepChip else colors.chipOnTableSoft, ChromeShapes.Pill)
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** A round count badge: brass for a pile's cards left, burgundy for hints left. */
@Composable
fun CountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    fill: Color = CardGamesTheme.colors.accent,
    ink: Color = CardGamesTheme.colors.onAccent,
    size: Dp = PILE_BADGE,
) {
    Box(
        modifier =
            modifier
                .defaultMinSize(
                    minWidth = size,
                    minHeight = size,
                ).background(fill, ChromeShapes.Pill)
                .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$count",
            color = ink,
            style =
                CardGamesTheme.type.badge.copy(
                    fontSize =
                        CardGamesTheme.type.badge.fontSize * (size / PILE_BADGE),
                ),
        )
    }
}

/** A round avatar with initials ("You", "Ro"). */
@Composable
fun Avatar(
    initials: String,
    fill: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    size: Dp = AVATAR,
    style: TextStyle? = null,
) {
    Box(
        modifier = modifier.size(size).background(fill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // 14sp in a full avatar, 10sp in the small one beside an action message; a [style] for a big one.
        val fontSize = if (size >= AVATAR) AVATAR_TEXT else AVATAR_SMALL_TEXT
        Text(initials, color = ink, style = style ?: CardGamesTheme.type.chip.copy(fontSize = fontSize))
    }
}

/**
 * The action message under the piles: who acted (a small avatar) and what they did, with card
 * names picked out — "Rosa discarded **K♣** · passed on 6♥". [text] carries its own bold spans.
 */
@Composable
fun FeedPill(
    initials: String,
    avatarFill: Color,
    avatarInk: Color,
    text: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    val colors = CardGamesTheme.colors
    Row(
        modifier =
            modifier
                .background(colors.feedPill, ChromeShapes.Pill)
                .padding(start = 8.dp, top = 7.dp, end = 14.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm),
    ) {
        Avatar(initials, avatarFill, avatarInk, size = AVATAR_SMALL)
        Text(text, color = colors.onTable, style = CardGamesTheme.type.bodySmall, maxLines = 1)
    }
}

/** A soft surface on the felt — the score card, stat tiles, grouped lists — with no border. */
fun Modifier.softSurface(color: Color): Modifier = background(color, ChromeShapes.Soft)

/** A label over a region of the table ("YOUR MELDS") with an optional right-hand [hint]. No rule. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    hintIcon: ImageVector? = null,
) {
    val colors = CardGamesTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            color = colors.onTable.copy(alpha = LABEL_ALPHA),
            style = CardGamesTheme.type.overline,
            modifier = Modifier.weight(1f),
        )
        if (hint != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                hintIcon?.let {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = colors.onTable.copy(alpha = HINT_ALPHA),
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(hint, color = colors.onTable.copy(alpha = HINT_ALPHA), style = CardGamesTheme.type.actionSub)
            }
        }
    }
}

private const val HINT_ALPHA = 0.8f
private const val LABEL_ALPHA = 0.85f
private val PILE_BADGE: Dp = 28.dp
private val AVATAR: Dp = 38.dp
private val AVATAR_SMALL: Dp = 22.dp
private val AVATAR_TEXT = 14.sp
private val AVATAR_SMALL_TEXT = 10.sp
