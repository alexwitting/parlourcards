package com.cardgames.feature.solitaire

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.repository.GameResult
import com.cardgames.core.domain.repository.HintLimit
import com.cardgames.core.domain.solitaire.KlondikeSnapshot
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.domain.solitaire.TableauPile
import com.cardgames.core.testing.FakeClockProvider
import com.cardgames.core.testing.FakeSavedGameRepository
import com.cardgames.core.testing.FakeSeedProvider
import com.cardgames.core.testing.FakeSettingsRepository
import com.cardgames.core.testing.FakeStatsRepository
import com.cardgames.core.testing.FakeWinnableDealCache
import com.cardgames.core.testing.MainDispatcherExtension
import com.cardgames.feature.solitaire.board.SolitaireDragSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** The Parlour play loop: commit-on-land, hold to select, per-deal hints and the Solved figures. */
class SolitairePlayLoopTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val savedGames = FakeSavedGameRepository()
    private val stats = FakeStatsRepository()

    // Draw 1: these tests deal with KlondikeEngine.deal's own default.
    private val settings = FakeSettingsRepository().apply { settings = settings.copy(drawCount = 1) }

    private fun viewModel() =
        SolitaireViewModel(
            FakeSeedProvider(seed = 42L),
            FakeClockProvider(),
            savedGames,
            stats,
            settings,
            FakeWinnableDealCache(),
            Dispatchers.Unconfined,
        ).also { it.shuffleMillis = 0 }

    private val emptyPile = TableauPile(emptyList(), 0)

    private fun craftedState(
        tableau: List<TableauPile> = List(7) { emptyPile },
        foundations: Map<Suit, Int> = Suit.entries.associateWith { 0 },
        stock: List<Card> = emptyList(),
        waste: List<Card> = emptyList(),
    ) = KlondikeState(seed = 0L, drawCount = 1, tableau = tableau, foundations = foundations, stock = stock, waste = waste)

    /**
     * Sends [intent], then lands any motion it started — the screen's part under commit-on-land —
     * and any it leads to (a new deal's gather, then the deal).
     */
    private fun SolitaireViewModel.play(intent: SolitaireIntent) {
        onIntent(intent)
        repeat(MAX_CHAINED_MOTIONS) { state.value.pending?.let { onIntent(SolitaireIntent.MoveLanded(it.id)) } }
    }

    // --- Commit on land -------------------------------------------------------------------------

    @Test
    fun `a move shows at once but is only saved once it lands`() {
        val vm = viewModel()
        vm.play(SolitaireIntent.TapStock)
        val savedBefore = savedGames.saved[GameId.KLONDIKE]

        vm.onIntent(SolitaireIntent.TapStock)
        val pending = vm.state.value.pending

        assertEquals(MotionKind.DRAW, pending?.kind)
        assertEquals(2, vm.state.value.game.waste.size)
        assertEquals(savedBefore, savedGames.saved[GameId.KLONDIKE])

        vm.onIntent(SolitaireIntent.MoveLanded(pending!!.id))

        assertNull(vm.state.value.pending)
        val saved = KlondikeSnapshot.fromJson(savedGames.saved.getValue(GameId.KLONDIKE).stateJson)
        assertEquals(vm.state.value.game, saved?.game)
    }

    @Test
    fun `a landing report for an older motion is ignored`() {
        val vm = viewModel()
        vm.onIntent(SolitaireIntent.TapStock)
        val first =
            vm.state.value.pending!!
                .id

        vm.onIntent(SolitaireIntent.TapStock)
        vm.onIntent(SolitaireIntent.MoveLanded(first))

        assertTrue(vm.state.value.pending != null)
        assertNotEquals(
            first,
            vm.state.value.pending
                ?.id,
        )
    }

    @Test
    fun `new input mid-flight lands the move in flight first`() {
        val vm = viewModel()
        vm.onIntent(SolitaireIntent.TapStock)

        vm.onIntent(SolitaireIntent.TapStock)

        assertEquals(2, vm.state.value.game.waste.size)
        assertEquals(
            1,
            vm.state.value.pending
                ?.from
                ?.moveCount,
        )
        assertTrue(vm.state.value.canUndo)
    }

    @Test
    fun `the win counts only when the last card lands`() {
        val vm = viewModel()
        vm.overrideGameForTest(
            craftedState(
                foundations = Suit.entries.associateWith { if (it == Suit.CLUBS) 12 else 13 },
                waste = listOf(Card(Suit.CLUBS, Rank.KING)),
            ),
        )

        vm.onIntent(SolitaireIntent.TapWaste)

        assertFalse(vm.state.value.isWon)
        assertTrue(stats.recorded.isEmpty())

        vm.onIntent(
            SolitaireIntent.MoveLanded(
                vm.state.value.pending!!
                    .id,
            ),
        )

        assertTrue(vm.state.value.isWon)
        assertEquals(1, stats.recorded.size)
        assertEquals(1, stats.recorded.single().moves)
        assertEquals(1, stats.recorded.single().drawCount)
    }

    @Test
    fun `a new game deals from the gathered stock`() {
        val vm = viewModel()
        vm.play(SolitaireIntent.TapStock)

        vm.onIntent(SolitaireIntent.NewGame)
        // The old cards fly home first; once they land, the deal goes out from the full stock.
        assertEquals(
            MotionKind.GATHER,
            vm.state.value.pending!!
                .kind,
        )
        vm.onIntent(
            SolitaireIntent.MoveLanded(
                vm.state.value.pending!!
                    .id,
            ),
        )
        val pending = vm.state.value.pending!!

        assertEquals(MotionKind.DEAL, pending.kind)
        assertEquals(52, pending.from.stock.size)
        assertTrue(pending.from.tableau.all { it.isEmpty })
    }

    // --- Hold to select -------------------------------------------------------------------------

    private val selectionTable =
        craftedState(
            tableau =
                listOf(
                    TableauPile(listOf(Card(Suit.HEARTS, Rank.NINE)), 0),
                    TableauPile(listOf(Card(Suit.SPADES, Rank.TEN)), 0),
                    TableauPile(listOf(Card(Suit.CLUBS, Rank.TEN)), 0),
                ) + List(4) { emptyPile },
        )

    @Test
    fun `holding a card selects it and a tap on a legal pile moves it there`() {
        val vm = viewModel()
        vm.overrideGameForTest(selectionTable)

        vm.play(SolitaireIntent.Hold(SolitaireDragSource.TableauRun(0, 0)))
        assertEquals(SolitaireDragSource.TableauRun(0, 0), vm.state.value.selection)

        // Auto-move would pick pile 1; the selection goes where the player taps.
        vm.play(SolitaireIntent.TapTableauCard(pile = 2, cardIndex = 0))

        assertNull(vm.state.value.selection)
        assertEquals(
            2,
            vm.state.value.game.tableau[2]
                .cards.size,
        )
        assertTrue(
            vm.state.value.game.tableau[0]
                .isEmpty,
        )
    }

    @Test
    fun `a tap somewhere the selection can't go just puts it down`() {
        val vm = viewModel()
        vm.overrideGameForTest(selectionTable)
        vm.play(SolitaireIntent.Hold(SolitaireDragSource.TableauRun(0, 0)))

        vm.play(SolitaireIntent.TapEmptyTableau(pile = 5))

        assertNull(vm.state.value.selection)
        assertEquals(selectionTable, vm.state.value.game)
    }

    @Test
    fun `tapping the table cancels a selection`() {
        val vm = viewModel()
        vm.overrideGameForTest(selectionTable)
        vm.play(SolitaireIntent.Hold(SolitaireDragSource.TableauRun(0, 0)))

        vm.play(SolitaireIntent.TapTable)

        assertNull(vm.state.value.selection)
    }

    // --- Hint limits ----------------------------------------------------------------------------

    @Test
    fun `hints run out at the per-deal limit and a new deal refills them`() {
        val vm = viewModel()
        vm.overrideGameForTest(craftedState(stock = listOf(Card(Suit.CLUBS, Rank.NINE), Card(Suit.CLUBS, Rank.FIVE))))
        assertEquals(3, vm.state.value.hintsLeft)

        repeat(3) { vm.play(SolitaireIntent.Hint) }
        assertEquals(0, vm.state.value.hintsLeft)

        vm.play(SolitaireIntent.TapStock)
        vm.play(SolitaireIntent.Hint)
        assertNull(vm.state.value.hint)

        vm.play(SolitaireIntent.NewGame)
        assertEquals(3, vm.state.value.hintsLeft)
    }

    @Test
    fun `unlimited hints report no count`() {
        settings.settings = settings.settings.copy(solitaireHints = HintLimit.UNLIMITED)
        val vm = viewModel()

        assertNull(vm.state.value.hintsLeft)
    }

    // --- Solved ---------------------------------------------------------------------------------

    @Test
    fun `the solved summary adds this win to the stats so far`() =
        runTest {
            stats.record(GameResult(GameId.KLONDIKE, won = true, durationSeconds = 100, moves = 90))
            stats.record(GameResult(GameId.KLONDIKE, won = false, durationSeconds = 50))
            val vm = viewModel()
            vm.overrideGameForTest(
                craftedState(
                    foundations = Suit.entries.associateWith { if (it == Suit.CLUBS) 12 else 13 },
                    waste = listOf(Card(Suit.CLUBS, Rank.KING)),
                ),
            )

            vm.play(SolitaireIntent.TapWaste)

            val solved = vm.state.value.solved!!
            assertEquals(1, solved.streak)
            assertEquals(2, solved.gamesWon)
            assertEquals(3, solved.gamesPlayed)
            assertEquals(1, solved.moves)
        }
}

private const val MAX_CHAINED_MOTIONS = 3
