package com.cardgames.feature.solitaire

import app.cash.turbine.test
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.solitaire.KlondikeEngine
import com.cardgames.core.domain.solitaire.KlondikeMove
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
import com.cardgames.feature.solitaire.board.SolitaireDropTarget
import com.cardgames.feature.solitaire.board.Tableau
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime

class SolitaireViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val seedProvider = FakeSeedProvider(seed = 42L)
    private val clockProvider = FakeClockProvider()
    private val savedGames = FakeSavedGameRepository()
    private val stats = FakeStatsRepository()

    // Draw 1: these tests deal with KlondikeEngine.deal's own default.
    private val settings = FakeSettingsRepository().apply { settings = settings.copy(drawCount = 1) }
    private val dealCache = FakeWinnableDealCache()

    // Dispatchers.Unconfined (not a TestDispatcher) so KlondikeDealer/KlondikeSolver's real
    // search runs synchronously on the calling thread — same philosophy as MainDispatcherExtension
    // itself uses for Dispatchers.Main (see its own doc comment): deterministic, no scheduler
    // coordination with runTest needed, at the cost of paying the search's real wall-clock time.
    private fun viewModel() =
        SolitaireViewModel(seedProvider, clockProvider, savedGames, stats, settings, dealCache, Dispatchers.Unconfined)
            .also { it.shuffleMillis = 0 }

    private val emptyPile = TableauPile(emptyList(), 0)

    /**
     * Sends [intent], then lands any motion it started — the screen's part under commit-on-land —
     * and any it leads to (a new deal's gather, then the deal).
     */
    private fun SolitaireViewModel.play(intent: SolitaireIntent) {
        onIntent(intent)
        repeat(MAX_CHAINED_MOTIONS) { state.value.pending?.let { onIntent(SolitaireIntent.MoveLanded(it.id)) } }
    }

    private fun craftedState(
        tableau: List<TableauPile> = List(7) { emptyPile },
        foundations: Map<Suit, Int> = Suit.entries.associateWith { 0 },
        stock: List<Card> = emptyList(),
        waste: List<Card> = emptyList(),
    ) = KlondikeState(
        seed = 0L,
        drawCount = 1,
        tableau = tableau,
        foundations = foundations,
        stock = stock,
        waste = waste,
    )

    @Test
    fun `deals from the injected seed`() {
        val vm = viewModel()

        assertEquals(KlondikeEngine.deal(seed = 42L), vm.state.value.game)
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `tapping the stock deals a card and enables undo`() {
        val vm = viewModel()

        vm.play(SolitaireIntent.TapStock)

        assertEquals(1, vm.state.value.game.waste.size)
        assertEquals(23, vm.state.value.game.stock.size)
        assertTrue(vm.state.value.canUndo)
    }

    @Test
    fun `tapping an empty stock recycles the waste`() {
        val vm = viewModel()
        val card = Card(Suit.CLUBS, Rank.FIVE)
        vm.overrideGameForTest(craftedState(waste = listOf(card)))

        vm.play(SolitaireIntent.TapStock)

        assertEquals(listOf(card), vm.state.value.game.stock)
        assertTrue(
            vm.state.value.game.waste
                .isEmpty(),
        )
    }

    @Test
    fun `undo restores the previous position and empties out`() {
        val vm = viewModel()
        val before = vm.state.value.game

        vm.play(SolitaireIntent.TapStock)
        vm.play(SolitaireIntent.Undo)

        assertEquals(before, vm.state.value.game)
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `redo re-applies an undone move`() {
        val vm = viewModel()

        vm.play(SolitaireIntent.TapStock)
        val afterMove = vm.state.value.game
        vm.play(SolitaireIntent.Undo)

        vm.play(SolitaireIntent.Redo)

        assertEquals(afterMove, vm.state.value.game)
        assertTrue(vm.state.value.canUndo)
        assertFalse(vm.state.value.canRedo)
    }

    @Test
    fun `redo is a no-op with nothing to redo`() {
        val vm = viewModel()
        val before = vm.state.value.game

        vm.play(SolitaireIntent.Redo)

        assertEquals(before, vm.state.value.game)
    }

    @Test
    fun `a new move after undo clears the redo stack`() {
        val vm = viewModel()
        vm.play(SolitaireIntent.TapStock)
        vm.play(SolitaireIntent.Undo)
        assertTrue(vm.state.value.canRedo)

        vm.play(SolitaireIntent.TapStock)

        assertFalse(vm.state.value.canRedo)
    }

    @Test
    fun `undo and redo history survive a save and restore`() {
        val vm = viewModel()
        vm.play(SolitaireIntent.TapStock)
        val afterFirstTap = vm.state.value.game
        vm.play(SolitaireIntent.TapStock)
        val afterSecondTap = vm.state.value.game
        vm.play(SolitaireIntent.Undo)

        assertEquals(afterFirstTap, vm.state.value.game)

        val restored = viewModel()

        assertEquals(afterFirstTap, restored.state.value.game)
        assertTrue(restored.state.value.canUndo)
        assertTrue(restored.state.value.canRedo)

        restored.play(SolitaireIntent.Redo)
        assertEquals(afterSecondTap, restored.state.value.game)
    }

    @Test
    fun `new game deals fresh and clears undo`() {
        val vm = viewModel()
        vm.play(SolitaireIntent.TapStock)

        // seed=8 solves fast (benchmarked) — keeps this test's KlondikeDealer search quick; the
        // deal's identity would be the same for any seed since FakeSeedProvider always returns
        // the one seed it's set to, solvable or not.
        seedProvider.seed = 8L
        vm.play(SolitaireIntent.NewGame)

        assertEquals(KlondikeEngine.deal(seed = 8L), vm.state.value.game)
        assertFalse(vm.state.value.canUndo)
        assertFalse(vm.state.value.isDealing)
    }

    @Test
    fun `a pre-baked deal is served instantly from the cache instead of searching on demand`() {
        val vm = viewModel()
        val cachedDeal = KlondikeEngine.deal(seed = 999L, drawCount = 1)
        dealCache.queued.getOrPut(1) { ArrayDeque() }.addLast(cachedDeal)

        vm.play(SolitaireIntent.NewGame)

        assertEquals(cachedDeal, vm.state.value.game)
        assertFalse(vm.state.value.isDealing)
    }

    @Test
    fun `turning the winnable-deals setting off skips the solver and cache entirely`() =
        runTest {
            settings.setUseSolverForWinnableDeals(false)
            val vm = viewModel()
            // seed=3 never solves quickly (benchmarked) — proves this path never even tries the
            // solver, since a plain unchecked deal is instant regardless.
            seedProvider.seed = 3L

            vm.play(SolitaireIntent.NewGame)

            assertEquals(KlondikeEngine.deal(seed = 3L), vm.state.value.game)
            assertTrue(dealCache.queued.isEmpty())
            assertTrue(dealCache.ensureFillingCalls.isEmpty())
        }

    @Test
    fun `isStuck flags a provably unwinnable position after a no-progress recycle, and clears on the next real move`() {
        val vm = viewModel()
        // An incomplete "deck" (one lone card) can never reach all 52 cards in the foundations —
        // provably unwinnable, not just unsolved-within-budget, so the bounded stuck-check search
        // returns NotFound reliably regardless of its node/deadline budget.
        vm.overrideGameForTest(craftedState(waste = listOf(Card(Suit.CLUBS, Rank.FIVE))))

        vm.play(SolitaireIntent.TapStock)

        assertTrue(vm.state.value.isStuck)

        vm.play(SolitaireIntent.TapStock)
        assertFalse(vm.state.value.isStuck)
    }

    @Test
    fun `tapping a waste ace sends it to the foundation`() {
        val vm = viewModel()
        vm.overrideGameForTest(craftedState(waste = listOf(Card(Suit.HEARTS, Rank.ACE))))

        vm.play(SolitaireIntent.TapWaste)

        assertEquals(
            1,
            vm.state.value.game
                .foundationCount(Suit.HEARTS),
        )
        assertTrue(
            vm.state.value.game.waste
                .isEmpty(),
        )
    }

    @Test
    fun `tapping a tableau card auto-moves it to the best spot`() {
        val vm = viewModel()
        vm.overrideGameForTest(
            craftedState(
                tableau =
                    listOf(
                        TableauPile(listOf(Card(Suit.HEARTS, Rank.NINE)), 0),
                        TableauPile(listOf(Card(Suit.SPADES, Rank.TEN)), 0),
                    ) + List(5) { emptyPile },
            ),
        )

        vm.play(SolitaireIntent.TapTableauCard(pile = 0, cardIndex = 0))

        assertTrue(
            vm.state.value.game.tableau[0]
                .isEmpty,
        )
        assertEquals(
            2,
            vm.state.value.game.tableau[1]
                .cards.size,
        )
    }

    @Test
    fun `a tap with no legal move changes nothing and pushes no undo`() {
        val vm = viewModel()
        val start = craftedState(tableau = listOf(TableauPile(listOf(Card(Suit.HEARTS, Rank.FIVE)), 0)) + List(6) { emptyPile })
        vm.overrideGameForTest(start)

        vm.play(SolitaireIntent.TapTableauCard(pile = 0, cardIndex = 0))

        assertEquals(start, vm.state.value.game)
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `dropping a run onto a matching tableau pile moves the whole run`() {
        val vm = viewModel()
        vm.overrideGameForTest(
            craftedState(
                tableau =
                    listOf(
                        TableauPile(listOf(Card(Suit.HEARTS, Rank.NINE), Card(Suit.SPADES, Rank.EIGHT)), 0),
                        TableauPile(listOf(Card(Suit.SPADES, Rank.TEN)), 0),
                    ) + List(5) { emptyPile },
            ),
        )

        vm.play(
            SolitaireIntent.DropCards(
                SolitaireDragSource.TableauRun(pile = 0, cardIndex = 0),
                SolitaireDropTarget.Tableau(pile = 1),
            ),
        )

        assertTrue(
            vm.state.value.game.tableau[0]
                .isEmpty,
        )
        assertEquals(
            3,
            vm.state.value.game.tableau[1]
                .cards.size,
        )
        assertTrue(vm.state.value.canUndo)
    }

    @Test
    fun `dropping the waste card onto its foundation applies the move`() {
        val vm = viewModel()
        vm.overrideGameForTest(craftedState(waste = listOf(Card(Suit.HEARTS, Rank.ACE))))

        vm.play(
            SolitaireIntent.DropCards(
                SolitaireDragSource.Waste,
                SolitaireDropTarget.Foundation(Suit.HEARTS),
            ),
        )

        assertEquals(
            1,
            vm.state.value.game
                .foundationCount(Suit.HEARTS),
        )
    }

    @Test
    fun `an illegal drop changes nothing and pushes no undo`() {
        val vm = viewModel()
        val start =
            craftedState(
                tableau =
                    listOf(
                        TableauPile(listOf(Card(Suit.HEARTS, Rank.FIVE)), 0),
                        TableauPile(listOf(Card(Suit.SPADES, Rank.SEVEN)), 0),
                    ) + List(5) { emptyPile },
            )
        vm.overrideGameForTest(start)

        vm.play(
            SolitaireIntent.DropCards(
                SolitaireDragSource.TableauRun(pile = 0, cardIndex = 0),
                SolitaireDropTarget.Tableau(pile = 1),
            ),
        )

        assertEquals(start, vm.state.value.game)
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `a foundation card can be dropped back onto the tableau`() {
        val vm = viewModel()
        vm.overrideGameForTest(
            craftedState(
                foundations = Suit.entries.associateWith { if (it == Suit.HEARTS) 2 else 0 },
                tableau = listOf(TableauPile(listOf(Card(Suit.SPADES, Rank.THREE)), 0)) + List(6) { emptyPile },
            ),
        )

        vm.play(
            SolitaireIntent.DropCards(
                SolitaireDragSource.Foundation(Suit.HEARTS),
                SolitaireDropTarget.Tableau(pile = 0),
            ),
        )

        assertEquals(
            1,
            vm.state.value.game
                .foundationCount(Suit.HEARTS),
        )
        assertEquals(
            2,
            vm.state.value.game.tableau[0]
                .cards.size,
        )
    }

    @Test
    fun `auto-complete plays out to the win and undoes in one step`() {
        val vm = viewModel()
        val ready =
            craftedState(
                foundations = Suit.entries.associateWith { 12 },
                tableau =
                    Suit.entries.map { suit -> TableauPile(listOf(Card(suit, Rank.KING)), 0) } +
                        List(3) { emptyPile },
            )
        vm.overrideGameForTest(ready)
        assertTrue(vm.state.value.canAutoComplete)

        vm.play(SolitaireIntent.AutoComplete)

        assertTrue(vm.state.value.isWon)
        assertFalse(vm.state.value.canAutoComplete)

        vm.play(SolitaireIntent.Undo)
        assertEquals(ready, vm.state.value.game)
        assertFalse(vm.state.value.isWon)
    }

    @Test
    fun `hint surfaces an engine suggestion and any move clears it`() {
        val vm = viewModel()
        vm.overrideGameForTest(
            craftedState(
                waste = listOf(Card(Suit.HEARTS, Rank.ACE)),
                stock = listOf(Card(Suit.CLUBS, Rank.NINE)),
            ),
        )

        vm.play(SolitaireIntent.Hint)
        assertEquals(KlondikeMove.WasteToFoundation, vm.state.value.hint)

        vm.play(SolitaireIntent.TapStock)
        assertNull(vm.state.value.hint)
    }

    @Test
    fun `restart replays the same deal from move zero and is undoable`() {
        val vm = viewModel()
        vm.play(SolitaireIntent.TapStock)
        val afterMove = vm.state.value.game

        vm.play(SolitaireIntent.RestartDeal)

        assertEquals(KlondikeEngine.deal(seed = 42L), vm.state.value.game)
        assertEquals(0, vm.state.value.game.moveCount)
        assertTrue(vm.state.value.canUndo)

        vm.play(SolitaireIntent.Undo)
        assertEquals(afterMove, vm.state.value.game)
    }

    @Test
    fun `timer accrues only while the screen is visible`() {
        val start = LocalDateTime.of(2026, 7, 9, 12, 0, 0)
        clockProvider.setNow(start)
        val vm = viewModel()

        vm.play(SolitaireIntent.ScreenVisible)
        clockProvider.setNow(start.plusSeconds(5))
        vm.play(SolitaireIntent.ScreenHidden)

        assertEquals(5, vm.state.value.elapsedSeconds)

        // Hidden time doesn't count.
        clockProvider.setNow(start.plusSeconds(65))
        vm.play(SolitaireIntent.ScreenVisible)
        clockProvider.setNow(start.plusSeconds(68))
        vm.play(SolitaireIntent.ScreenHidden)

        assertEquals(8, vm.state.value.elapsedSeconds)
    }

    @Test
    fun `winning freezes the timer`() {
        val start = LocalDateTime.of(2026, 7, 9, 12, 0, 0)
        clockProvider.setNow(start)
        val vm = viewModel()
        vm.play(SolitaireIntent.ScreenVisible)
        vm.overrideGameForTest(
            craftedState(
                foundations = Suit.entries.associateWith { if (it == Suit.HEARTS) 12 else 13 },
                waste = listOf(Card(Suit.HEARTS, Rank.KING)),
            ),
        )
        clockProvider.setNow(start.plusSeconds(10))

        vm.play(SolitaireIntent.TapWaste)
        assertTrue(vm.state.value.isWon)
        assertEquals(10, vm.state.value.elapsedSeconds)

        // The clock marching on after the win changes nothing.
        clockProvider.setNow(start.plusSeconds(100))
        vm.play(SolitaireIntent.ScreenHidden)
        assertEquals(10, vm.state.value.elapsedSeconds)
    }

    @Test
    fun `undoing a restart brings back the time it set aside`() {
        val start = LocalDateTime.of(2026, 7, 9, 12, 0, 0)
        clockProvider.setNow(start)
        val vm = viewModel()
        vm.play(SolitaireIntent.ScreenVisible)
        vm.play(SolitaireIntent.TapStock)
        val played = vm.state.value.game
        clockProvider.setNow(start.plusSeconds(100))

        vm.play(SolitaireIntent.RestartDeal)
        assertEquals(0, vm.state.value.elapsedSeconds)
        clockProvider.setNow(start.plusSeconds(120))

        // Back to the deal as played: its 100s return, with the 20s spent on the replay.
        vm.play(SolitaireIntent.Undo)
        assertEquals(played, vm.state.value.game)
        assertEquals(120, vm.state.value.elapsedSeconds)
    }

    @Test
    fun `a restart's time comes back on undo after the game is reopened`() {
        val start = LocalDateTime.of(2026, 7, 9, 12, 0, 0)
        clockProvider.setNow(start)
        val first = viewModel()
        first.play(SolitaireIntent.ScreenVisible)
        first.play(SolitaireIntent.TapStock)
        clockProvider.setNow(start.plusSeconds(100))
        first.play(SolitaireIntent.RestartDeal)
        first.play(SolitaireIntent.ScreenHidden)

        val reopened = viewModel()
        reopened.play(SolitaireIntent.ScreenVisible)
        clockProvider.setNow(start.plusSeconds(110))
        reopened.play(SolitaireIntent.Undo)

        assertEquals(110, reopened.state.value.elapsedSeconds)
    }

    @Test
    fun `new game resets the timer`() {
        val start = LocalDateTime.of(2026, 7, 9, 12, 0, 0)
        clockProvider.setNow(start)
        val vm = viewModel()
        vm.play(SolitaireIntent.ScreenVisible)
        clockProvider.setNow(start.plusSeconds(30))

        vm.play(SolitaireIntent.NewGame)

        assertEquals(0, vm.state.value.elapsedSeconds)

        // And it keeps running for the new deal while still visible.
        clockProvider.setNow(start.plusSeconds(37))
        vm.play(SolitaireIntent.ScreenHidden)
        assertEquals(7, vm.state.value.elapsedSeconds)
    }

    @Test
    fun `new deals honor the draw-count setting`() =
        runTest {
            settings.setDrawCount(3)

            val vm = viewModel()

            assertEquals(3, vm.state.value.game.drawCount)
            vm.play(SolitaireIntent.TapStock)
            assertEquals(
                3,
                vm.state.value.game.waste
                    .size,
            )
        }

    @Test
    fun `a dealt game keeps its draw mode when the setting changes`() =
        runTest {
            val vm = viewModel()
            vm.play(SolitaireIntent.TapStock)

            settings.setDrawCount(3)

            assertEquals(1, vm.state.value.game.drawCount)

            vm.play(SolitaireIntent.NewGame)
            assertEquals(3, vm.state.value.game.drawCount)
        }

    @Test
    fun `a restored game keeps its own draw mode over the setting`() =
        runTest {
            settings.setDrawCount(3)
            var savedDeal = KlondikeEngine.deal(seed = 7L, drawCount = 1)
            savedDeal = KlondikeEngine.apply(savedDeal, KlondikeMove.DealFromStock)
            savedGames.save(GameId.KLONDIKE, KlondikeSnapshot(game = savedDeal, elapsedSeconds = 5).toJson())

            val vm = viewModel()

            assertEquals(savedDeal, vm.state.value.game)
            assertEquals(1, vm.state.value.game.drawCount)
        }

    @Test
    fun `every move autosaves a resumable snapshot`() =
        runTest {
            val vm = viewModel()

            vm.play(SolitaireIntent.TapStock)

            val saved = savedGames.load(GameId.KLONDIKE)
            val snapshot = KlondikeSnapshot.fromJson(saved!!.stateJson)
            assertEquals(vm.state.value.game, snapshot!!.game)
        }

    @Test
    fun `a saved game is restored on start`() =
        runTest {
            val savedDeal = KlondikeEngine.deal(seed = 7L)
            savedGames.save(
                GameId.KLONDIKE,
                KlondikeSnapshot(game = savedDeal, elapsedSeconds = 30).toJson(),
            )

            val vm = viewModel()

            assertEquals(savedDeal, vm.state.value.game)
            assertEquals(30, vm.state.value.elapsedSeconds)
            assertFalse(vm.state.value.canUndo)
        }

    @Test
    fun `a corrupt save is discarded and a fresh game dealt`() =
        runTest {
            savedGames.save(GameId.KLONDIKE, "this is not a snapshot")

            val vm = viewModel()

            assertEquals(KlondikeEngine.deal(seed = 42L), vm.state.value.game)
        }

    @Test
    fun `winning deletes the save and records the result once`() =
        runTest {
            val vm = viewModel()
            vm.overrideGameForTest(
                craftedState(
                    foundations = Suit.entries.associateWith { if (it == Suit.HEARTS) 12 else 13 },
                    waste = listOf(Card(Suit.HEARTS, Rank.KING)),
                ),
            )

            vm.play(SolitaireIntent.TapWaste)

            assertTrue(vm.state.value.isWon)
            assertNull(savedGames.load(GameId.KLONDIKE))
            assertEquals(1, stats.recorded.size)
            assertTrue(stats.recorded.single().won)

            // Undoing the win and re-winning must not double-record.
            vm.play(SolitaireIntent.Undo)
            vm.play(SolitaireIntent.TapWaste)
            assertEquals(1, stats.recorded.size)
        }

    @Test
    fun `complete comes on with cards still in the draw pile, and plays them out`() {
        val clubs = Rank.entries.map { Card(Suit.CLUBS, it) }
        val vm = viewModel()
        vm.overrideGameForTest(
            craftedState(
                foundations = Suit.entries.associateWith { 13 } + (Suit.CLUBS to 7),
                stock = clubs.subList(9, 13).reversed(),
                waste = clubs.subList(7, 9),
            ),
        )
        assertTrue(vm.state.value.canAutoComplete)

        vm.play(SolitaireIntent.AutoComplete)

        assertTrue(vm.state.value.isWon)
    }

    @Test
    fun `abandoning a started game via new game records a loss`() {
        val vm = viewModel()
        vm.play(SolitaireIntent.TapStock)

        vm.play(SolitaireIntent.NewGame)

        assertEquals(1, stats.recorded.size)
        assertFalse(stats.recorded.single().won)
    }

    @Test
    fun `a game left during the new deal's animation reopens on the new deal, its loss counted once`() {
        val vm = viewModel()
        vm.play(SolitaireIntent.TapStock)

        // Gone before the cards have gathered and dealt (the screen closed, the process killed).
        vm.onIntent(SolitaireIntent.NewGame)
        val reopened = viewModel()

        assertEquals(0, reopened.state.value.game.moveCount)
        assertFalse(reopened.state.value.abandonCountsAsLoss)
        assertEquals(1, stats.recorded.size)
    }

    @Test
    fun `the screen is told to confirm a new game only once a move has been made`() {
        val vm = viewModel()
        assertFalse(vm.state.value.abandonCountsAsLoss)

        vm.play(SolitaireIntent.TapStock)
        assertTrue(vm.state.value.abandonCountsAsLoss)

        vm.play(SolitaireIntent.NewGame)
        assertFalse(vm.state.value.abandonCountsAsLoss)
    }

    @Test
    fun `new game on an untouched deal records nothing`() {
        val vm = viewModel()

        vm.play(SolitaireIntent.NewGame)

        assertTrue(stats.recorded.isEmpty())
    }

    @Test
    fun `moves emit semantic feedback effects`() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.play(SolitaireIntent.TapStock)
                assertEquals(SolitaireEffect.CardsDealt, awaitItem())

                vm.play(SolitaireIntent.Undo)
                assertEquals(SolitaireEffect.CardMoved, awaitItem())

                vm.play(SolitaireIntent.NewGame)
                assertEquals(SolitaireEffect.Shuffled, awaitItem())
            }
        }

    @Test
    fun `a move that uncovers a face-down card emits CardFlipped`() =
        runTest {
            val vm = viewModel()
            vm.overrideGameForTest(
                craftedState(
                    tableau =
                        listOf(
                            TableauPile(listOf(Card(Suit.CLUBS, Rank.KING), Card(Suit.HEARTS, Rank.NINE)), 1),
                            TableauPile(listOf(Card(Suit.SPADES, Rank.TEN)), 0),
                        ) + List(5) { emptyPile },
                ),
            )
            vm.effects.test {
                vm.play(SolitaireIntent.TapTableauCard(pile = 0, cardIndex = 1))
                assertEquals(SolitaireEffect.CardFlipped, awaitItem())
            }
        }

    @Test
    fun `winning emits GameWon instead of a move effect`() =
        runTest {
            val vm = viewModel()
            vm.overrideGameForTest(
                craftedState(
                    foundations = Suit.entries.associateWith { if (it == Suit.HEARTS) 12 else 13 },
                    waste = listOf(Card(Suit.HEARTS, Rank.KING)),
                ),
            )
            vm.effects.test {
                vm.play(SolitaireIntent.TapWaste)
                assertEquals(SolitaireEffect.GameWon, awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun `won state is flagged`() {
        val vm = viewModel()
        vm.overrideGameForTest(craftedState(foundations = Suit.entries.associateWith { 13 }))

        assertTrue(vm.state.value.isWon)
        assertNotEquals(true, vm.state.value.canAutoComplete)
    }
}

private const val MAX_CHAINED_MOTIONS = 3
