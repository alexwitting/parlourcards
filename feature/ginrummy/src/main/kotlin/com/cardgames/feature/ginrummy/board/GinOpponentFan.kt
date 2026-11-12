package com.cardgames.feature.ginrummy.board

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.Melds
import com.cardgames.core.ui.cards.CARD_ASPECT_RATIO
import com.cardgames.core.ui.table.MovableCard

/**
 * The opponent's hand as a fan of 44dp backs, 17dp apart and tilted 4° per step from the middle,
 * curving down at the edges — face up, grouped into its melds, once the hand is [revealed].
 */
@Composable
internal fun OpponentFan(
    hand: List<Card>,
    revealed: Boolean,
    look: GinLook,
    cards: (Card) -> MovableCard,
    modifier: Modifier = Modifier,
) {
    val m = LocalGinMetrics.current
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (revealed) {
            // Face up at the end of the hand: grouped into the melds it scores with (so the
            // defender's layoffs can be seen joining them), deadwood after.
            val partition = remember(hand) { Melds.bestPartition(hand) }
            val order = compareBy<Card>({ it.suit }, { it.rank })
            MeldsRow(
                melds = partition.melds.map { it.cards.sortedWith(order) },
                loose = partition.deadwood.sortedWith(order),
                look = look,
                input = null,
                cards = cards,
                modifier =
                    Modifier.padding(start = 16.dp, end = 16.dp, top = m.fanTop).height(
                        m.seatHeight - m.fanTop,
                    ),
            )
            return@Column
        }
        Layout(
            modifier = Modifier.fillMaxWidth().height(m.seatHeight),
            content = {
                hand.forEachIndexed { index, card ->
                    cards(card)(revealed, look.glide(card), fanTilt(index, hand.size), Modifier)
                }
            },
        ) { measurables, constraints ->
            val width = m.opponentCard.roundToPx()
            val height = (width / CARD_ASPECT_RATIO).toInt()
            val step = m.opponentStep.roundToPx()
            val placeables = measurables.map { it.measure(Constraints.fixed(width, height)) }
            layout(constraints.maxWidth, m.seatHeight.roundToPx()) {
                val middle = (placeables.size - 1) / 2f
                placeables.forEachIndexed { index, placeable ->
                    val offset = index - middle
                    val edge = if (middle > 0f) offset / middle else 0f
                    val x = constraints.maxWidth / 2 + (offset * step).toInt() - width / 2
                    val y = (m.fanTop.toPx() + m.fanDrop.toPx() * edge * edge).toInt()
                    placeable.place(x, y)
                }
            }
        }
    }
}
