package com.cardgames.feature.ginrummy

import androidx.lifecycle.SavedStateHandle
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinMove
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinScoring
import com.cardgames.core.domain.ginrummy.GinSnapshot
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.Melds
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.ginrummy.ai.GinOpponentFactory
import com.cardgames.core.domain.ginrummy.ai.HeuristicGinOpponent
import com.cardgames.core.testing.FakeSavedGameRepository
import com.cardgames.core.testing.FakeSettingsRepository
import com.cardgames.core.testing.FakeStatsRepository
import com.cardgames.core.testing.MainDispatcherExtension
import com.cardgames.core.testing.card
import com.cardgames.core.testing.cards
import com.cardgames.core.testing.ginPosition
import com.cardgames.feature.ginrummy.board.GinDragSource
import com.cardgames.feature.ginrummy.board.GinDropTarget
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class GinViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val savedGames = FakeSavedGameRepository()
    private val stats = FakeStatsRepository()
    private val settings = FakeSettingsRepository()

    /** Every difficulty the ViewModel asked for an opponent at, in order. */
    private val opponentsCreated = mutableListOf<GinDifficulty>()
    private val opponentFactory =
        GinOpponentFactory { difficulty ->
            opponentsCreated += difficulty
            HeuristicGinOpponent()
        }

    /** A ViewModel with its opening deal (and any opponent turn after it) already landed. */
    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        testGinViewModel(opponentFactory, savedGames, stats, settings, savedState).also { it.settle() }

    /** Sends [intent], then lets everything it set off (the opponent's reply included) land. */
    private fun GinViewModel.play(intent: GinIntent) {
        onIntent(intent)
        settle()
    }

    /** Pins an exact position, then lets any opponent turn it starts land. */
    private fun GinViewModel.pin(game: GinState) {
        overrideGameForTest(game)
        settle()
    }

    @Test
    fun `a difficulty change applies from the next match, not the one in progress`() {
        settings.settings = settings.settings.let { it.copy(gin = it.gin.copy(difficulty = GinDifficulty.MEDIUM)) }
        val vm = viewModel()
        vm.pin(handAwaitingDiscardFor(GinPlayer.OPPONENT, hand = tenPlusOneHand()))
        assertEquals(listOf(GinDifficulty.MEDIUM), opponentsCreated)

        // Changed mid-match (e.g. Settings opened from the drawer): the current match keeps its opponent.
        settings.settings = settings.settings.let { it.copy(gin = it.gin.copy(difficulty = GinDifficulty.HARD)) }
        vm.pin(handAwaitingDiscardFor(GinPlayer.OPPONENT, hand = tenPlusOneHand()))
        assertEquals(listOf(GinDifficulty.MEDIUM), opponentsCreated)

        // New match on the same screen (same ViewModel) picks the new difficulty up.
        vm.play(GinIntent.NewMatch)
        vm.pin(handAwaitingDiscardFor(GinPlayer.OPPONENT, hand = tenPlusOneHand()))
        assertEquals(GinDifficulty.HARD, opponentsCreated.last())
    }

    @Test
    fun `a new match resolves every opponent-only decision and lands on the player's turn`() {
        val vm = viewModel()

        assertTrue(vm.state.value.isPlayerTurn)
        assertFalse(vm.state.value.isOpponentThinking)
    }

    @Test
    fun `tapping a hand card selects it, tapping it again deselects it`() {
        val vm = viewModel()
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = tenPlusOneHand())
        vm.pin(state)
        val card = state.playerHand.first()

        vm.play(GinIntent.SelectHandCard(card))
        assertEquals(card, vm.state.value.selectedCard)

        vm.play(GinIntent.SelectHandCard(card))
        assertNull(vm.state.value.selectedCard)
    }

    @Test
    fun `selecting a different card replaces the selection`() {
        val vm = viewModel()
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = tenPlusOneHand())
        vm.pin(state)
        val (first, second) = state.playerHand.take(2)

        vm.play(GinIntent.SelectHandCard(first))
        vm.play(GinIntent.SelectHandCard(second))

        assertEquals(second, vm.state.value.selectedCard)
    }

    @Test
    fun `discarding a card applies the move and clears any selection`() {
        val vm = viewModel()
        val hand = tenPlusOneHand()
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand)
        vm.pin(state)
        val discard = hand.last()
        vm.play(GinIntent.SelectHandCard(hand.first()))

        vm.play(GinIntent.DropCard(GinDragSource.HandCard(discard), GinDropTarget.Discard))

        assertNull(vm.state.value.selectedCard)
        assertFalse(discard in vm.state.value.game.playerHand)
    }

    @Test
    fun `discarding the just-drawn-from-discard card is a no-op`() {
        val vm = viewModel()
        val hand = tenPlusOneHand()
        val state =
            handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand)
                .copy(phase = GinPhase.AwaitingDiscard(GinPlayer.PLAYER, mustNotDiscard = hand.first()))
        vm.pin(state)

        vm.play(GinIntent.DropCard(GinDragSource.HandCard(hand.first()), GinDropTarget.Discard))

        assertEquals(state, vm.state.value.game)
    }

    @Test
    fun `knocking with a legal discard ends the hand`() {
        val vm = viewModel()
        val hand = ginHand()
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand)
        vm.pin(state)
        val discard = card("K♠")

        vm.play(GinIntent.SelectHandCard(discard))
        assertTrue(vm.state.value.canKnock)

        vm.play(GinIntent.Knock)

        assertTrue(vm.state.value.game.phase is GinPhase.HandOver)
    }

    @Test
    fun `knock is available without first selecting a card — the engine suggests the discard itself`() {
        val vm = viewModel()
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = ginHand())
        vm.pin(state)

        assertNull(vm.state.value.selectedCard)
        assertEquals(card("K♠"), vm.state.value.knockCard)
        assertTrue(vm.state.value.canKnock)

        // The first tap lifts the suggested throw and asks; nothing has gone yet.
        vm.play(GinIntent.Knock)
        assertEquals(card("K♠"), vm.state.value.selectedCard)
        assertTrue(vm.state.value.confirmingKnock)
        assertTrue(vm.state.value.game.phase is GinPhase.AwaitingDiscard)

        vm.play(GinIntent.ConfirmKnock)

        assertTrue(vm.state.value.game.phase is GinPhase.HandOver)
    }

    @Test
    fun `in knock mode a card dragged to the pile is the one thrown`() {
        val vm = viewModel()
        val hand = twoKnocksHand()
        vm.pin(handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand))
        vm.play(GinIntent.Knock)
        val suggested = requireNotNull(vm.state.value.selectedCard)
        val game = vm.state.value.game
        // 2♦ can also go out: the player throws that instead of the suggested K♠.
        val other = card("2♦")
        assertEquals(card("K♠"), suggested)
        assertTrue(GinEngine.isLegal(game, GinMove.Knock(other)))

        vm.play(GinIntent.DropCard(GinDragSource.HandCard(other), GinDropTarget.Discard))

        val after = vm.state.value.game
        assertTrue(other !in after.playerHand)
        assertTrue(suggested in after.playerHand)
        assertTrue(after.phase !is GinPhase.AwaitingDiscard)
    }

    @Test
    fun `in knock mode a card that can't go out is refused, not discarded`() {
        val vm = viewModel()
        val hand = twoKnocksHand()
        vm.pin(handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand))
        vm.play(GinIntent.Knock)
        val before = vm.state.value.game
        val cannot = card("5♥")
        assertFalse(GinEngine.isLegal(before, GinMove.Knock(cannot)))

        vm.play(GinIntent.DropCard(GinDragSource.HandCard(cannot), GinDropTarget.Discard))

        assertEquals(before, vm.state.value.game)
        assertEquals(cannot, vm.state.value.rejectedCard)
    }

    @Test
    fun `knock stays disabled when no discard would reach the deadwood threshold`() {
        val vm = viewModel()
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = tenPlusOneHand())
        vm.pin(state)

        assertNull(vm.state.value.knockCard)
        assertFalse(vm.state.value.canKnock)
    }

    @Test
    fun `undo rewinds past the AI's reply turn to the player's previous decision point`() {
        val vm = viewModel()
        val hand = tenPlusOneHand()
        val before = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand)
        vm.pin(before)
        assertFalse(vm.state.value.canUndo)

        vm.play(GinIntent.DropCard(GinDragSource.HandCard(hand.last()), GinDropTarget.Discard))
        // The test ViewModel has no pacing (GinPacing.Instant), so the AI's whole reply
        // (and any further auto-driven phases) has already resolved synchronously by here.
        assertNotEquals(before, vm.state.value.game)
        assertTrue(vm.state.value.canUndo)

        vm.play(GinIntent.Undo)

        assertEquals(before, vm.state.value.game)
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `undo is unlimited within a hand — repeated undos walk back through multiple player actions`() {
        val vm = viewModel()
        val hand = tenPlusOneHand()
        val stateA = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand)
        vm.pin(stateA)

        vm.play(GinIntent.DropCard(GinDragSource.HandCard(hand.last()), GinDropTarget.Discard))
        val stateB = vm.state.value.game
        assertNotEquals(stateA, stateB)

        vm.play(GinIntent.DropCard(GinDragSource.StockTop, GinDropTarget.Hand))
        assertNotEquals(stateB, vm.state.value.game)

        vm.play(GinIntent.Undo)
        assertEquals(stateB, vm.state.value.game)

        vm.play(GinIntent.Undo)
        assertEquals(stateA, vm.state.value.game)
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `undo with nothing to undo is a no-op`() {
        val vm = viewModel()
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = tenPlusOneHand())
        vm.pin(state)

        vm.play(GinIntent.Undo)

        assertEquals(state, vm.state.value.game)
    }

    @Test
    fun `redo replays the undone action forward, including the AI's reply`() {
        val vm = viewModel()
        val hand = tenPlusOneHand()
        val before = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand)
        vm.pin(before)

        vm.play(GinIntent.DropCard(GinDragSource.HandCard(hand.last()), GinDropTarget.Discard))
        val afterMove = vm.state.value.game
        vm.play(GinIntent.Undo)

        vm.play(GinIntent.Redo)

        assertEquals(afterMove, vm.state.value.game)
        assertTrue(vm.state.value.canUndo)
        assertFalse(vm.state.value.canRedo)
    }

    @Test
    fun `redo with nothing to redo is a no-op`() {
        val vm = viewModel()
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = tenPlusOneHand())
        vm.pin(state)

        vm.play(GinIntent.Redo)

        assertEquals(state, vm.state.value.game)
    }

    @Test
    fun `a new move after undo clears the redo stack`() {
        val vm = viewModel()
        val hand = tenPlusOneHand()
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand)
        vm.pin(state)
        vm.play(GinIntent.DropCard(GinDragSource.HandCard(hand.last()), GinDropTarget.Discard))
        vm.play(GinIntent.Undo)
        assertTrue(vm.state.value.canRedo)

        vm.play(GinIntent.DropCard(GinDragSource.HandCard(hand[hand.size - 2]), GinDropTarget.Discard))

        assertFalse(vm.state.value.canRedo)
    }

    @Test
    fun `redo stack clears on new match`() {
        val vm = viewModel()
        val hand = tenPlusOneHand()
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand)
        vm.pin(state)
        vm.play(GinIntent.DropCard(GinDragSource.HandCard(hand.last()), GinDropTarget.Discard))
        vm.play(GinIntent.Undo)
        assertTrue(vm.state.value.canRedo)

        vm.play(GinIntent.NewMatch)

        assertFalse(vm.state.value.canRedo)
    }

    @Test
    fun `undo and redo history survive a save and restore`() {
        val vm = viewModel()
        val hand = tenPlusOneHand()
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand)
        vm.pin(state)
        vm.play(GinIntent.DropCard(GinDragSource.HandCard(hand.last()), GinDropTarget.Discard))
        val afterMove = vm.state.value.game
        vm.play(GinIntent.Undo)

        val restored = viewModel()

        assertEquals(state, restored.state.value.game)
        assertFalse(restored.state.value.canUndo)
        assertTrue(restored.state.value.canRedo)

        restored.play(GinIntent.Redo)
        assertEquals(afterMove, restored.state.value.game)
        assertTrue(restored.state.value.canUndo)
        assertFalse(restored.state.value.canRedo)
    }

    @Test
    fun `the action message is told again after a restore`() {
        val vm = viewModel()
        val hand = tenPlusOneHand()
        vm.pin(handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand))
        vm.play(GinIntent.DropCard(GinDragSource.HandCard(hand.last()), GinDropTarget.Discard))
        val feed = vm.state.value.feed
        assertTrue(feed.isNotEmpty())

        val restored = viewModel()

        assertEquals(feed, restored.state.value.feed)
    }

    @Test
    fun `undo stack clears on next hand`() {
        val vm = viewModel()
        val hand = ginHand()
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand)
        vm.pin(state)
        vm.play(GinIntent.SelectHandCard(card("K♠")))
        vm.play(GinIntent.Knock)

        vm.play(GinIntent.NextHand)

        assertFalse(vm.state.value.canUndo)
        val settled = vm.state.value.game
        vm.play(GinIntent.Undo)
        assertEquals(settled, vm.state.value.game)
    }

    @Test
    fun `undo stack clears on new match`() {
        val vm = viewModel()
        val hand = tenPlusOneHand()
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand)
        vm.pin(state)
        vm.play(GinIntent.DropCard(GinDragSource.HandCard(hand.last()), GinDropTarget.Discard))
        assertTrue(vm.state.value.canUndo)

        vm.play(GinIntent.NewMatch)

        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `next hand deals a fresh hand once the current one is over`() {
        val vm = viewModel()
        val handOverState = blockedHandOverState()
        vm.pin(handOverState)

        vm.play(GinIntent.NextHand)

        assertEquals(2, vm.state.value.game.handNumber)
        assertNull(vm.state.value.selectedCard)
    }

    @Test
    fun `new match resets scores even mid-hand`() {
        val vm = viewModel()
        val midHand =
            handAwaitingDiscardFor(GinPlayer.PLAYER, hand = tenPlusOneHand())
                .copy(matchScores = mapOf(GinPlayer.PLAYER to 60, GinPlayer.OPPONENT to 40))
        vm.pin(midHand)

        vm.play(GinIntent.NewMatch)

        assertEquals(mapOf(GinPlayer.PLAYER to 0, GinPlayer.OPPONENT to 0), vm.state.value.game.matchScores)
        assertEquals(1, vm.state.value.game.handNumber)
    }

    @Test
    fun `every position change autosaves the game`() {
        val vm = viewModel()
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = tenPlusOneHand())
        vm.pin(state)

        val saved = savedGames.saved.getValue(GameId.GIN_RUMMY)
        assertEquals(state, GinSnapshot.fromJson(saved.stateJson)?.game)
    }

    @Test
    fun `a saved game is resumed instead of dealing a fresh one`() {
        val saved = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = tenPlusOneHand())
        savedGames.saved = mapOf(GameId.GIN_RUMMY to savedGameRow(saved))

        val vm = viewModel()

        assertEquals(saved, vm.state.value.game)
    }

    @Test
    fun `reaching match-over clears the save and records the player's result`() {
        val vm = viewModel()
        val hand = ginHand()
        val state =
            handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand)
                .copy(matchScores = mapOf(GinPlayer.PLAYER to 95, GinPlayer.OPPONENT to 10))
        vm.pin(state)
        // Read the expected total independently (never hand-calculate deadwood/scoring — see
        // GinEngineTest's own lesson on this) rather than just checking self-consistency.
        val defenderDeadwood = Melds.bestPartition(state.opponentHand).deadwoodPips
        val rawFinalScore = 95 + defenderDeadwood + GinScoring.GIN_BONUS
        val expectedScore = GinScoring.settleMatch(winnerRawScore = rawFinalScore, winnerBoxes = 1, loserRawScore = 10)

        vm.play(GinIntent.SelectHandCard(card("K♠")))
        vm.play(GinIntent.Knock)

        assertEquals(GinPlayer.PLAYER, (vm.state.value.game.phase as GinPhase.MatchOver).winner)
        assertFalse(GameId.GIN_RUMMY in savedGames.saved)
        val result = stats.recorded.single()
        assertTrue(result.won)
        assertEquals(GameId.GIN_RUMMY, result.gameId)
        assertEquals(expectedScore, result.score)
        assertEquals(
            expectedScore,
            vm.state.value.game.matchScores
                .getValue(GinPlayer.PLAYER),
        )
    }

    @Test
    fun `abandoning a match mid-hand via New Match records a loss`() {
        val vm = viewModel()
        val hand = tenPlusOneHand()
        val midHand =
            handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand)
                .copy(matchScores = mapOf(GinPlayer.PLAYER to 42, GinPlayer.OPPONENT to 10))
        vm.pin(midHand)
        // A real player move — not just the AI's own auto-driven turns — is what "in progress"
        // means here (see GinViewModel.onNewMatch's KDoc).
        vm.play(GinIntent.DropCard(GinDragSource.HandCard(hand.last()), GinDropTarget.Discard))

        vm.play(GinIntent.NewMatch)

        val result = stats.recorded.single()
        assertFalse(result.won)
        assertEquals(42, result.score)
    }

    @Test
    fun `a match left during the new match's deal reopens on the new match, its loss counted once`() {
        val vm = viewModel()
        val hand = tenPlusOneHand()
        vm.pin(
            handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand)
                .copy(matchScores = mapOf(GinPlayer.PLAYER to 42, GinPlayer.OPPONENT to 10)),
        )
        vm.play(GinIntent.DropCard(GinDragSource.HandCard(hand.last()), GinDropTarget.Discard))

        // Gone before the new match's deal lands (the screen closed, the process killed).
        vm.onIntent(GinIntent.NewMatch)
        val reopened = viewModel()

        assertEquals(
            0,
            reopened.state.value.game.matchScores
                .getValue(GinPlayer.PLAYER),
        )
        assertFalse(reopened.state.value.abandonCountsAsLoss)
        assertEquals(1, stats.recorded.size)
    }

    @Test
    fun `a fresh match reopened untouched is left without recording a loss`() {
        runBlocking { savedGames.save(GameId.GIN_RUMMY, GinSnapshot(game = GinEngine.newMatch(seed = 5L)).toJson()) }
        val vm = viewModel()

        assertFalse(vm.state.value.abandonCountsAsLoss)
        vm.play(GinIntent.NewMatch)

        assertTrue(stats.recorded.isEmpty())
    }

    @Test
    fun `arriving from match setup deals a new match in place of the saved one, recording it lost`() {
        val saved = GinEngine.newMatch(seed = 5L).copy(matchScores = mapOf(GinPlayer.PLAYER to 42, GinPlayer.OPPONENT to 10))
        runBlocking { savedGames.save(GameId.GIN_RUMMY, GinSnapshot(game = saved).toJson()) }

        val vm = viewModel(SavedStateHandle(mapOf(GinViewModel.NEW_MATCH_ARG to true)))

        assertEquals(42, stats.recorded.single().score)
        assertEquals(
            0,
            vm.state.value.game.matchScores
                .getValue(GinPlayer.PLAYER),
        )
    }

    @Test
    fun `restart this hand deals the same hand again`() {
        val vm = viewModel()
        val dealt = vm.state.value.game
        vm.play(GinIntent.RestartHand)

        val again = vm.state.value.game
        assertEquals(dealt.handNumber, again.handNumber)
        // The opponent's upcard decision never touches the player's ten.
        assertEquals(GinEngine.redealHand(dealt).playerHand.toSet(), again.playerHand.toSet())
    }

    @Test
    fun `the screen is told to confirm a new match only once the player has acted`() {
        val vm = viewModel()
        assertFalse(vm.state.value.abandonCountsAsLoss)

        val hand = tenPlusOneHand()
        vm.pin(handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand))
        vm.play(GinIntent.DropCard(GinDragSource.HandCard(hand.last()), GinDropTarget.Discard))
        assertTrue(vm.state.value.abandonCountsAsLoss)

        vm.play(GinIntent.NewMatch)
        assertFalse(vm.state.value.abandonCountsAsLoss)
    }

    @Test
    fun `starting a fresh match with nothing in progress records nothing`() {
        // Construction alone already drives the AI's opening upcard decision — that mustn't
        // count as "in progress" on its own (see GinViewModel.onNewMatch's KDoc).
        val vm = viewModel()

        vm.play(GinIntent.NewMatch)

        assertTrue(stats.recorded.isEmpty())
    }

    // --- Test state builders (mirrors GinEngineTest's helpers) ------------------------------

    private fun savedGameRow(game: GinState) =
        com.cardgames.core.domain.repository.SavedGame(
            gameId = GameId.GIN_RUMMY,
            stateJson = GinSnapshot(game = game).toJson(),
            updatedAtEpochMillis = 0L,
        )

    private fun handAwaitingDiscardFor(
        player: GinPlayer,
        hand: List<Card>,
    ): GinState = ginPosition(hand, seat = player)

    private fun blockedHandOverState(): GinState {
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = tenPlusOneHand())
        val result =
            com.cardgames.core.domain.ginrummy.HandResult(
                outcome = com.cardgames.core.domain.ginrummy.HandOutcome.BLOCKED,
                winner = null,
                pointsScored = 0,
                knockerDeadwood = 0,
                defenderDeadwood = 0,
            )
        return state.copy(phase = GinPhase.HandOver(result))
    }

    /** An 11-card hand with no special meld structure — just needs to be dealable and legal. */
    private fun tenPlusOneHand(): List<Card> = cards("K♥ Q♣ J♦ 2♥ 3♣ 4♦ 5♥ 6♣ 8♦ 9♠ K♠")

    /** Discarding the spade king leaves a gin (zero-deadwood) hand. */
    private fun ginHand(): List<Card> = cards("A♠ 2♠ 3♠ 4♠ 7♥ 7♦ 7♣ 9♥ 9♦ 9♣ K♠")

    /** Three melds plus 2♦ and K♠: throwing K♠ leaves 2 (the suggestion), 2♦ leaves 10 (still a knock), a meld card can't. */
    private fun twoKnocksHand(): List<Card> = cards("A♣ 2♣ 3♣ 5♦ 5♥ 5♠ 9♥ 10♥ J♥ 2♦ K♠")
}
