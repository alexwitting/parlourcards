package com.cardgames.core.ui.cards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.game.GameId

/** Each game's picture: two cards, overlapped (Gin's pair of eights, Solitaire's ace and king). */
val GameId.artCards: List<Card>
    get() =
        when (this) {
            GameId.GIN_RUMMY -> listOf(Card(Suit.HEARTS, Rank.EIGHT), Card(Suit.SPADES, Rank.EIGHT))
            GameId.KLONDIKE -> listOf(Card(Suit.HEARTS, Rank.ACE), Card(Suit.SPADES, Rank.KING))
        }

/**
 * A game's two cards overlapping, [cardWidth] wide, the first tilted left and the second right —
 * on Home's game list and Continue card, and the game rows in Settings and Stats.
 */
@Composable
fun GameArt(
    game: GameId,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val cards = game.artCards
    val step = cardWidth * ART_STEP
    Box(modifier = modifier.width(cardWidth + step * (cards.size - 1))) {
        cards.forEachIndexed { i, card ->
            val tilt = if (i == 0) -ART_TILT else ART_TILT
            PlayingCard(
                card = card,
                faceUp = true,
                modifier =
                    Modifier
                        .padding(start = step * i)
                        .width(cardWidth)
                        .graphicsLayer { rotationZ = tilt },
            )
        }
    }
}

private const val ART_STEP = 0.45f
private const val ART_TILT = 8f

/** Each game's three cards for its fan (Home's tablet cards): Gin's three eights, Solitaire's ace, king and queen. */
val GameId.fanCards: List<Card>
    get() =
        when (this) {
            GameId.GIN_RUMMY -> artCards + Card(Suit.DIAMONDS, Rank.EIGHT)
            GameId.KLONDIKE -> artCards + Card(Suit.HEARTS, Rank.QUEEN)
        }

/**
 * A game's three cards fanned, each [cardWidth] wide: the middle one upright and a little higher,
 * the outer two tilted out about a point below the cards, as if held in a hand.
 */
@Composable
fun GameFan(
    game: GameId,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val cards = game.fanCards
    Box(
        modifier = modifier.size(cardWidth * FAN_WIDTH, cardWidth / CARD_ASPECT_RATIO * FAN_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        cards.forEachIndexed { i, card ->
            val side = i - 1
            PlayingCard(
                card = card,
                faceUp = true,
                modifier =
                    Modifier
                        .offset(x = cardWidth * FAN_SPREAD * side, y = -cardWidth * if (side == 0) FAN_RAISE_MIDDLE else FAN_RAISE_SIDE)
                        .width(cardWidth)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(FAN_PIVOT_X, FAN_PIVOT_Y)
                            rotationZ = FAN_TILT * side
                        },
            )
        }
    }
}

// The fan's measures in card widths, from the design's 84dp cards.
private const val FAN_WIDTH = 2.7f
private const val FAN_HEIGHT = 1.25f
private const val FAN_SPREAD = 0.74f
private const val FAN_RAISE_SIDE = 0.04f
private const val FAN_RAISE_MIDDLE = 0.11f
private const val FAN_TILT = 12f
private const val FAN_PIVOT_X = 0.5f
private const val FAN_PIVOT_Y = 1.3f
