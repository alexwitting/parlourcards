package com.cardgames.feature.ginrummy.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.HandResult
import com.cardgames.core.domain.ginrummy.Meld
import com.cardgames.core.domain.ginrummy.Melds
import com.cardgames.core.ui.cards.CARD_ASPECT_RATIO
import com.cardgames.core.ui.cards.PlayingCard
import com.cardgames.core.ui.cards.label
import com.cardgames.core.ui.chrome.Avatar
import com.cardgames.core.ui.chrome.ChromeShapes
import com.cardgames.core.ui.text.initials
import com.cardgames.core.ui.text.opponentName
import com.cardgames.core.ui.theme.CardGamesTheme

/** A line of the gin maths on its cream card; the total in green. */
@Composable
internal fun SumRow(
    label: String,
    value: String,
    emphasis: Boolean = false,
    note: String? = null,
) {
    val colors = CardGamesTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.onPanel, style = CardGamesTheme.type.rowLabel)
        note?.let { Text(" $it", color = colors.onPanelMuted, style = CardGamesTheme.type.bodySmall) }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            value,
            color = if (emphasis) colors.good else colors.onPanel,
            style = if (emphasis) CardGamesTheme.type.resultTitle else CardGamesTheme.type.tally,
        )
    }
}

/**
 * One player's end-of-hand cards on a cream card: their avatar, deadwood (green for the hand's
 * winner, red otherwise), the cards (melds with their stripes, then deadwood), and a [note] on
 * what was laid off onto them.
 */
@Composable
internal fun HandSummary(
    title: String,
    initials: String,
    game: GinState,
    player: GinPlayer,
    deadwood: Int,
    won: Boolean,
    note: String?,
) {
    val colors = CardGamesTheme.colors
    val mine = player == GinPlayer.PLAYER
    ResultCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(
                initials,
                fill = if (mine) colors.accent else colors.opponent,
                ink = if (mine) colors.onAccent else colors.onTable,
                size = ginResultMetrics.smallAvatar,
            )
            Text(title, color = colors.onPanel, style = CardGamesTheme.type.rowLabel, modifier = Modifier.weight(1f))
            Text("Deadwood", color = colors.onPanelMuted, style = CardGamesTheme.type.actionSub)
            Text("$deadwood", color = if (won) colors.good else colors.bad, style = CardGamesTheme.type.tally)
        }
        MiniHand(handGroups(game, player), cardWidth = ginResultMetrics.summaryCard)
        note?.let {
            Text(
                it,
                color = colors.onPanel,
                style = CardGamesTheme.type.actionSub,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.toolTile, ChromeShapes.Segment)
                        .padding(horizontal = 10.dp, vertical = CardGamesTheme.spacing.sm),
            )
        }
    }
}

/** A hand in small cards: each group overlapped, groups apart, from the left; melds wear their stripes. */
@Composable
private fun MiniHand(
    groups: HandGroups,
    cardWidth: Dp,
) {
    val colors = CardGamesTheme.colors
    val all = groups.melds + listOf(groups.deadwood)
    val groupGap = ginResultMetrics.summaryGroupGap
    Layout(
        content = {
            all.forEachIndexed { index, group ->
                val edge = if (index < groups.melds.size) colors.meld[index % colors.meld.size] else null
                group.forEach { card -> PlayingCard(card = card, faceUp = true, meldEdge = edge) }
            }
        },
    ) { measurables, constraints ->
        val width = cardWidth.roundToPx()
        val height = (width / CARD_ASPECT_RATIO).toInt()
        val placeables = measurables.map { it.measure(Constraints.fixed(width, height)) }
        val sizes = all.map { it.size }.filter { it > 0 }
        val gap = groupGap.roundToPx()
        val cards = sizes.sum()
        val natural = (width * MINI_STEP_FRACTION).toInt()
        val room = constraints.maxWidth - sizes.size * width - gap * (sizes.size - 1)
        val step = if (cards - sizes.size > 0) minOf(natural, room / (cards - sizes.size)).coerceAtLeast(0) else natural
        layout(constraints.maxWidth, height) {
            var x = 0
            var index = 0
            sizes.forEach { size ->
                repeat(size) { i -> placeables[index++].place(x + i * step, 0) }
                x += width + step * (size - 1) + gap
            }
        }
    }
}

internal class HandGroups(
    val melds: List<List<Card>>,
    val deadwood: List<Card>,
    /** For each meld, whether it's a run (else a set). */
    val kinds: List<Boolean>,
)

/** [player]'s end-of-hand cards grouped as the engine scores them, with anything laid off on their melds. */
internal fun handGroups(
    game: GinState,
    player: GinPlayer,
): HandGroups {
    val partition = Melds.bestPartition(game.handOf(player) + game.laidOffOnto(player))
    val order = compareBy<Card>({ it.suit }, { it.rank })
    return HandGroups(
        partition.melds.map { it.cards.sortedWith(order) },
        partition.deadwood.sortedWith(order),
        partition.melds.map { it is Meld.Run },
    )
}

internal fun deadwoodOf(
    result: HandResult,
    player: GinPlayer,
    knocker: GinPlayer,
): Int = if (player == knocker) result.knockerDeadwood else result.defenderDeadwood

/** "Rosa laid off 10♣ on your melds" — or null when the defender had nothing that fit. */
internal fun layoffLine(
    game: GinState,
    knocker: GinPlayer,
    opponentName: String,
): String? {
    val cards = game.laidOffOnto(knocker).takeIf { it.isNotEmpty() } ?: return null
    val list = cards.joinToString(" ") { it.label() }
    return if (knocker == GinPlayer.PLAYER) "$opponentName laid off $list on your melds" else "You laid off $list on $opponentName's melds"
}

private const val MINI_STEP_FRACTION = 0.45f
