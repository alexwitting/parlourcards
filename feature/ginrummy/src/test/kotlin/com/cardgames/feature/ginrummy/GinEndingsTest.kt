package com.cardgames.feature.ginrummy

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinMatchRules
import com.cardgames.core.domain.ginrummy.GinMove
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.HandOutcome
import com.cardgames.core.domain.ginrummy.HandResult
import com.cardgames.core.testing.FakeSettingsRepository
import com.cardgames.core.testing.MainDispatcherExtension
import com.cardgames.core.testing.cards
import com.cardgames.feature.ginrummy.board.GinDragSource
import com.cardgames.feature.ginrummy.board.GinDropTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** The edges of a hand and its endings: the refused discard, going out, confirming a knock, a blocked hand, highlights. */
class GinEndingsTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val settings = FakeSettingsRepository()

    private fun viewModel() = testGinViewModel(settings = settings).also { it.settle() }

    /** A–4♥, 4–7♣, three kings: every card melded, and throwing 4♥ still leaves a gin. */
    private val elevenMelded =
        cards("A♥ 2♥ 3♥ 4♥ 4♣ 5♣ 6♣ 7♣ K♠ K♥ K♦")

    private fun discarding(
        hand: List<Card>,
        rules: GinMatchRules = GinMatchRules(),
    ): GinState {
        val base = GinEngine.newMatch(seed = 5L, rules = rules)
        val opponent = (base.stock + base.opponentHand + base.playerHand + base.discardPile).filter { it !in hand }
        return base.copy(
            moveCount = 3,
            playerHand = hand,
            opponentHand = opponent.take(GinState.HAND_SIZE),
            discardPile = opponent.subList(GinState.HAND_SIZE, GinState.HAND_SIZE + 1),
            stock = opponent.drop(GinState.HAND_SIZE + 1),
            phase = GinPhase.AwaitingDiscard(GinPlayer.PLAYER),
        )
    }

    @Test
    fun `throwing back the card just taken is refused with a reason`() {
        val vm = viewModel()
        val drawn =
            GinEngine.apply(
                GinEngine.newMatch(seed = 3L).copy(moveCount = 1, phase = GinPhase.AwaitingDraw(GinPlayer.PLAYER)),
                GinMove.DrawFromDiscard,
            )
        vm.overrideGameForTest(drawn)
        val taken = (drawn.phase as GinPhase.AwaitingDiscard).mustNotDiscard!!

        vm.onIntent(GinIntent.DropCard(GinDragSource.HandCard(taken), GinDropTarget.Discard))

        assertEquals(taken, vm.state.value.rejectedCard)
        assertNull(vm.state.value.pending)
        assertEquals(GinPanel.CantDiscard(taken), ginPanel(vm.state.value))
    }

    @Test
    fun `zero deadwood offers gin in the panel, and keep playing puts it away`() {
        val vm = viewModel()
        vm.overrideGameForTest(discarding(elevenMelded))

        assertTrue(vm.state.value.ginOffer is GinOffer.Gin)

        vm.onIntent(GinIntent.DismissGinOffer)

        assertNull(vm.state.value.ginOffer)
        assertEquals(GinPanel.Play, ginPanel(vm.state.value))
    }

    @Test
    fun `going gin knocks with the offered card`() {
        val vm = viewModel()
        vm.overrideGameForTest(discarding(elevenMelded))

        vm.onIntent(GinIntent.GoGin)
        vm.settle()

        val phase = vm.state.value.game.phase
        val result = (phase as? GinPhase.HandOver)?.result ?: (phase as GinPhase.MatchOver).lastHand
        assertEquals(HandOutcome.GIN, result?.outcome)
        assertEquals(1, vm.state.value.highlights.gins)
    }

    @Test
    fun `big gin is offered when the match allows it`() {
        val vm = viewModel()
        vm.overrideGameForTest(discarding(elevenMelded, GinMatchRules(bigGin = true)))

        assertEquals(GinOffer.BigGin, vm.state.value.ginOffer)
    }

    @Test
    fun `with confirm-before-knock on, knock asks first`() {
        settings.settings = settings.settings.let { it.copy(gin = it.gin.copy(confirmKnock = true)) }
        val vm = viewModel()
        vm.overrideGameForTest(discarding(elevenMelded))
        vm.onIntent(GinIntent.DismissGinOffer)

        vm.onIntent(GinIntent.Knock)

        assertTrue(vm.state.value.confirmingKnock)
        assertNull(vm.state.value.pending)

        vm.onIntent(GinIntent.CancelKnock)
        assertFalse(vm.state.value.confirmingKnock)

        vm.onIntent(GinIntent.Knock)
        vm.onIntent(GinIntent.ConfirmKnock)
        assertEquals(
            GinMotionKind.DISCARD,
            vm.state.value.pending
                ?.kind,
        )
    }

    @Test
    fun `a blocked hand is settled in the panel, with a redeal`() {
        val blocked =
            GinEngine.newMatch(seed = 3L).copy(
                phase =
                    GinPhase.HandOver(
                        HandResult(HandOutcome.BLOCKED, winner = null, pointsScored = 0, knockerDeadwood = 0, defenderDeadwood = 0),
                    ),
            )

        assertEquals(GinPanel.Blocked, ginPanel(GinUiState(game = blocked)))
    }
}
