package com.cardgames.feature.ginrummy

import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinMove
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.testing.FakeSavedGameRepository
import com.cardgames.core.testing.MainDispatcherExtension
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.feature.ginrummy.motion.GinMotionTokens
import com.cardgames.feature.ginrummy.motion.ginChoreography
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** The Parlour play loop: commit-on-land for both seats, the opponent's turn locking the table, hints per hand. */
class GinPlayLoopTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val savedGames = FakeSavedGameRepository()

    private fun rawViewModel() = testGinViewModel(savedGames = savedGames)

    private fun drawPhase(toMove: GinPlayer): GinState =
        GinEngine.newMatch(seed = 3L).copy(moveCount = 1, phase = GinPhase.AwaitingDraw(toMove))

    @Test
    fun `the opening deal is a motion, and nothing is saved until it lands`() {
        val vm = rawViewModel()
        val deal = vm.state.value.pending

        assertEquals(GinMotionKind.DEAL, deal?.kind)
        assertNull(savedGames.saved[GameId.GIN_RUMMY])

        vm.onIntent(GinIntent.MoveLanded(deal!!.id))

        assertNotNull(savedGames.saved[GameId.GIN_RUMMY])
    }

    @Test
    fun `the opponent's move in flight locks the table until it lands`() {
        val vm = rawViewModel()
        vm.settle()
        vm.overrideGameForTest(drawPhase(GinPlayer.OPPONENT))
        val theirs = vm.state.value.pending

        assertTrue(theirs?.byOpponent == true)
        assertTrue(vm.state.value.isOpponentsTurn)

        vm.onIntent(GinIntent.DrawFromStock)
        assertEquals(theirs, vm.state.value.pending)

        vm.settle()
        assertTrue(vm.state.value.isPlayerTurn || vm.state.value.game.phase is GinPhase.HandOver)
    }

    @Test
    fun `the player's draw shows at once and counts once it lands`() {
        val vm = rawViewModel()
        vm.settle()
        vm.overrideGameForTest(drawPhase(GinPlayer.PLAYER))

        vm.onIntent(GinIntent.DrawFromStock)

        assertEquals(
            GinMotionKind.DRAW,
            vm.state.value.pending
                ?.kind,
        )
        assertEquals(GinState.HAND_SIZE + 1, vm.state.value.game.playerHand.size)
        // Still the player's turn while their own card flies in, so the panel doesn't blink.
        assertTrue(vm.state.value.isPlayerTurn)

        vm.settle()
        assertTrue(vm.state.value.isPlayerTurn)
        assertTrue(vm.state.value.canUndo)
    }

    @Test
    fun `hints run out at three a hand`() {
        val vm = rawViewModel()
        vm.settle()
        vm.overrideGameForTest(drawPhase(GinPlayer.PLAYER))
        assertEquals(3, vm.state.value.hintsLeft)

        vm.onIntent(GinIntent.Hint)
        assertNotNull(vm.state.value.hint)
        repeat(2) { vm.onIntent(GinIntent.Hint) }

        assertEquals(0, vm.state.value.hintsLeft)
    }

    @Test
    fun `a card drawn from the stock turns over partway through its flight`() {
        val from = drawPhase(GinPlayer.PLAYER)
        val to = GinEngine.apply(from, GinMove.DrawFromStock)
        val drawn = to.playerHand.last()

        val result = ginChoreography(GinMotionKind.DRAW, from, to)

        assertEquals(GinMotionTokens.FLIP_IN_FLIGHT, result.timings.getValue(drawn).flipDelayMillis)
        // Face up before it lands: the motion lasts as long as the longer of the flight and the flip.
        assertTrue(GinMotionTokens.FLIP_IN_FLIGHT + CardGamesMotion.Flip.millis <= GinMotionTokens.Flight.millis)
        assertEquals(maxOf(GinMotionTokens.Flight.millis, GinMotionTokens.FLIP_IN_FLIGHT + CardGamesMotion.Flip.millis), result.totalMillis)
    }

    @Test
    fun `the opponent's discard turns face up halfway`() {
        val drawn = GinEngine.apply(drawPhase(GinPlayer.OPPONENT), GinMove.DrawFromStock)
        val thrown = drawn.opponentHand.first()
        val to = GinEngine.apply(drawn, GinMove.Discard(thrown))

        val result = ginChoreography(GinMotionKind.DISCARD, drawn, to)

        assertEquals(GinMotionTokens.Flight.millis / 2, result.timings.getValue(thrown).flipDelayMillis)
    }

    @Test
    fun `a dragged card is already in place, only turning over where it lands`() {
        val from = drawPhase(GinPlayer.PLAYER)
        val to = GinEngine.apply(from, GinMove.DrawFromStock)

        val result = ginChoreography(GinMotionKind.DROP, from, to)

        assertEquals(0, result.timings.getValue(to.playerHand.last()).glideMillis)
        assertEquals(CardGamesMotion.Flip.millis, result.totalMillis)
    }
}
