package com.cardgames.feature.ginrummy.board

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.Melds
import com.cardgames.core.ui.cards.DragDropState
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.tween
import com.cardgames.core.ui.table.CardGlide
import com.cardgames.core.ui.table.MovableCard
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.feature.ginrummy.GinHint
import com.cardgames.feature.ginrummy.GinIntent
import com.cardgames.feature.ginrummy.GinUiState
import com.cardgames.feature.ginrummy.deadwoodAfterDiscarding
import com.cardgames.feature.ginrummy.discardMeldPartners
import com.cardgames.feature.ginrummy.isOpponentsTurn
import com.cardgames.feature.ginrummy.isPlayerTurn
import com.cardgames.feature.ginrummy.motion.ginMotionDistances
import com.cardgames.feature.ginrummy.suggestion

/**
 * The table for one [board] position: the opponent's fan, the piles, the melds and the hand. Drawn
 * once normally, and once more underneath while a finished hand fades out at a new deal (with
 * plain [cards], a throwaway [dragState] and a no-op [onIntent]).
 */
@Composable
internal fun GinTable(
    board: GinUiState,
    cards: (Card) -> MovableCard,
    glide: (Card) -> CardGlide,
    dragState: DragDropState<GinDragSource>,
    onIntent: (GinIntent) -> Unit,
    modifier: Modifier = Modifier,
    // The hand being dealt, as it will be once dealt: the cards land straight in their final places.
    dealt: GinState? = null,
) {
    val game = board.game
    val dealing = dealt != null
    val phase = game.phase
    val canAct = board.isPlayerTurn && !dealing
    val input = GinInput(onIntent, dragState, phase, canAct, board.selectedCard)
    val hand = remember(dealt ?: game, board.drawnCard) { playerHandLayout(dealt ?: game, board.drawnCard) }
    // Mid-deal, only the cards already dealt are drawn; the rest of the slots wait for theirs.
    val present: (Card) -> Boolean = if (dealing) game.playerHand.toSet()::contains else { _ -> true }
    val choosingDraw = canAct && (phase is GinPhase.AwaitingDraw || phase is GinPhase.UpcardDecision)
    val partners = if (choosingDraw && board.meldHints) remember(game) { game.discardMeldPartners() } else emptySet()
    val hint = board.hint
    // A draw that just made a meld (with meld hints on): point at the card worth throwing next.
    val justMelded = board.meldHints && board.feed.lastOrNull()?.let { it.actor == GinPlayer.PLAYER && it.tail != null } == true
    val suggestedDiscard =
        if (justMelded && canAct && phase is GinPhase.AwaitingDiscard) {
            remember(game) { (game.suggestion() as? GinHint.Discard)?.card }
        } else {
            null
        }
    val look =
        GinLook(
            glide = glide,
            selected = board.selectedCard,
            matching = partners + listOfNotNull((hint as? GinHint.Discard)?.card, suggestedDiscard),
            piles =
                PileLook(
                    discardRinged = partners.isNotEmpty() || hint == GinHint.TakeDiscard,
                    discardMakesMeld = partners.isNotEmpty(),
                    stockRinged = hint == GinHint.DrawStock,
                ),
            rejected = board.rejectedCard,
            // Dressed as new (ring, glow, NEW) once it has landed — not as a back still in flight.
            drawn = board.drawnCard.takeIf { board.pending == null },
        )
    val opponentsTurn = board.isOpponentsTurn
    val handAlpha by animateFloatAsState(
        if (opponentsTurn) ginMotionDistances.lockedHandAlpha else 1f,
        CardGamesMotion.StatusFade.tween(),
        label = "hand",
    )
    GinTableColumn(
        board = board,
        hand = hand,
        present = present,
        bubble = bubbleFor(phase, thinking = opponentsTurn, playerToMove = canAct),
        handAlpha = handAlpha,
        dealing = dealing,
        look = look,
        input = input,
        cards = cards,
        modifier = modifier,
    )
}

/**
 * The phone's arrangement, top to bottom: the opponent's seat, the piles, the action feed, then the
 * melds, a divider and the hand sharing the rest of the felt. Mid-deal, [board] holds only the cards
 * dealt so far, and [present] says which of [hand]'s slots are filled.
 */
@Composable
private fun GinTableColumn(
    board: GinUiState,
    hand: HandLayout,
    present: (Card) -> Boolean,
    bubble: Bubble?,
    handAlpha: Float,
    dealing: Boolean,
    look: GinLook,
    input: GinInput,
    cards: (Card) -> MovableCard,
    modifier: Modifier = Modifier,
) {
    val game = board.game
    val phase = game.phase
    val handFinished = phase is GinPhase.HandOver || phase is GinPhase.MatchOver
    // A short screen gets the compact table, so the hand never runs under the panel.
    val formFactor = CardGamesTheme.formFactor
    BoxWithConstraints(modifier = modifier) {
        val m = GinMetrics.forTable(formFactor, maxWidth, maxHeight)
        CompositionLocalProvider(LocalGinMetrics provides m) {
            Column(modifier = Modifier.fillMaxSize()) {
                OpponentSeat(
                    opponent = board.opponent,
                    cardCount = game.opponentHand.size,
                    bubble = bubble,
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .widthIn(max = CardGamesTheme.spacing.maxBoardWidth)
                            .testTag(GinTableTags.OPPONENT),
                ) {
                    OpponentFan(
                        hand = game.opponentHand + game.laidOffOnto(GinPlayer.OPPONENT),
                        revealed = handFinished || phase is GinPhase.AwaitingLayoff,
                        look = look,
                        cards = cards,
                    )
                }
                Spacer(modifier = Modifier.height(m.fanToPiles))
                // Above the hand, so a discard flies up over the cards it leaves rather than under them.
                Piles(game, look, input, cards, modifier = Modifier.zIndex(PILES_Z).testTag(GinTableTags.PILES))
                ActionFeed(
                    board.feed,
                    board.opponent,
                    modifier =
                        Modifier
                            .align(
                                Alignment.CenterHorizontally,
                            ).padding(top = m.feedGap, start = m.handMargin, end = m.handMargin),
                )
                // The player's melds, a divider and their hand share the rest of the felt evenly — no
                // labels, the meld stripes say which group each card is in. Dimmed and still while
                // the opponent plays.
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .fade(handAlpha)
                            .ginDropTarget(input.dragState, game, GinDropTarget.Hand, input.onIntent)
                            .padding(top = m.handAreaTop, bottom = m.handAreaBottom)
                            .testTag(GinTableTags.HAND_AREA),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Box(modifier = Modifier.testTag(GinTableTags.MELDS), contentAlignment = Alignment.Center) {
                        MeldsRow(hand.melds, look, input, cards, modifier = Modifier.padding(horizontal = m.handMargin), present = present)
                        // With no melds yet, a faint dashed meld marks where they'll gather — and fades
                        // as the first real one arrives, so nothing on the table moves.
                        val noMelds by animateFloatAsState(
                            if (hand.melds.isEmpty() && !dealing) 1f else 0f,
                            CardGamesMotion.StatusFade.tween(),
                            label = "no melds",
                        )
                        if (noMelds > 0f) MeldOutline(modifier = Modifier.graphicsLayer { alpha = noMelds })
                    }
                    Box(
                        modifier =
                            Modifier
                                .padding(horizontal = m.dividerInset)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(CardGamesTheme.colors.lineOnTable)
                                .testTag(GinTableTags.DIVIDER),
                    )
                    InPlay(
                        present = present,
                        loose = hand.loose,
                        deadwoodChange = { card -> game.deadwoodAfterDiscarding(card) - hand.deadwood },
                        look = look,
                        input = input,
                        cards = cards,
                        modifier = Modifier.padding(horizontal = m.handMargin).testTag(GinTableTags.HAND),
                    )
                }
            }
        }
    }
}

/** What the opponent says: "Thinking…" on its turn, a nudge on the player's. */
internal fun bubbleFor(
    phase: GinPhase,
    thinking: Boolean,
    playerToMove: Boolean,
): Bubble? =
    when {
        thinking -> Bubble.Thinking
        !playerToMove -> null
        phase is GinPhase.UpcardDecision -> Bubble.Says("Your pick!")
        phase is GinPhase.AwaitingDraw -> Bubble.Says("Your draw!")
        phase is GinPhase.AwaitingDiscard -> Bubble.Says("Your move!")
        else -> null
    }

/** The player's hand as the table shows it: the engine's melds, and the rest in play. */
internal class HandLayout(
    val melds: List<List<Card>>,
    val loose: List<Card>,
    val deadwood: Int,
)

/**
 * Melds as the engine groups them (each in suit-then-rank order, melds ordered by their first
 * card so they don't swap places for no visible reason), loose cards sorted the same way except
 * the card just [drawn], which waits at the end.
 */
internal fun playerHandLayout(
    game: GinState,
    drawn: Card?,
): HandLayout {
    val shown = game.playerHand + game.laidOffOnto(GinPlayer.PLAYER)
    val partition = Melds.bestPartition(shown)
    val order = compareBy<Card>({ it.suit }, { it.rank })
    val melds = partition.melds.map { it.cards.sortedWith(order) }.sortedWith(compareBy(order) { it.first() })
    // The card just drawn waits at the right end, apart from the sorted rest, for the discard decision.
    val loose = partition.deadwood.sortedWith(order).sortedBy { it == drawn }
    return HandLayout(melds, loose, partition.deadwoodPips)
}

/** Test tags on the table's parts — for tests that check the table holds still while cards move. */
internal object GinTableTags {
    const val OPPONENT = "gin:opponent"
    const val PILES = "gin:piles"
    const val STOCK = "gin:stock"
    const val DISCARD = "gin:discard"
    const val HAND_AREA = "gin:handArea"
    const val MELDS = "gin:melds"
    const val DIVIDER = "gin:divider"
    const val HAND = "gin:hand"
}

private const val PILES_Z = 1f
