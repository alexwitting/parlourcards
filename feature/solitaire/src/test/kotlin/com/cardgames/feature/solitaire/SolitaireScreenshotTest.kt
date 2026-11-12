package com.cardgames.feature.solitaire

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.solitaire.KlondikeEngine
import com.cardgames.core.domain.solitaire.KlondikeMove
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.domain.solitaire.TableauPile
import com.cardgames.core.testing.ui.ScreenSize
import com.cardgames.core.testing.ui.snapshot
import com.cardgames.core.ui.cards.SampleCardTableThemes
import com.cardgames.feature.solitaire.board.SolitaireDragSource
import com.cardgames.feature.solitaire.board.legalTargets
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines (Roborazzi): `verifyRoborazziDebug` checks them, `recordRoborazziDebug` re-records.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class SolitaireScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Composable
    private fun Board(state: SolitaireState) {
        SolitaireContent(
            state = state,
            onIntent = {},
            onNavigateBack = {},
            onOpenSettings = {},
            snackbarHostState = SnackbarHostState(),
        )
    }

    /** A fresh deal with one stock draw played, so the waste isn't empty. */
    private fun midGame(drawCount: Int): SolitaireState {
        val dealt = KlondikeEngine.deal(seed = 1L, drawCount = drawCount)
        return SolitaireState(game = KlondikeEngine.apply(dealt, KlondikeMove.DealFromStock), canUndo = true, elapsedSeconds = 205)
    }

    @Test
    fun midGame_draw1_parlour() = composeTestRule.snapshot { Board(midGame(drawCount = 1)) }

    @Test
    fun midGame_draw1_midnight() = composeTestRule.snapshot(SampleCardTableThemes.Midnight) { Board(midGame(drawCount = 1)) }

    @Test
    fun midGame_tablet() = composeTestRule.snapshot(size = ScreenSize.TABLET) { Board(midGame(drawCount = 3)) }

    @Test
    fun stuck_tablet() = composeTestRule.snapshot(size = ScreenSize.TABLET) { Board(midGame(drawCount = 1).copy(isStuck = true)) }

    @Test
    fun midGame_draw3() = composeTestRule.snapshot { Board(midGame(drawCount = 3)) }

    @Test
    fun hintShown() = hint(size = null)

    @Test
    fun hintShown_tablet() = hint(size = ScreenSize.TABLET)

    private fun hint(size: ScreenSize?) =
        composeTestRule.snapshot(size = size) {
            val state = midGame(drawCount = 1)
            Board(state.copy(hint = KlondikeEngine.legalMoves(state.game).first()))
        }

    @Test
    fun cardHeld() =
        composeTestRule.snapshot {
            val state = midGame(drawCount = 1)
            val source =
                state.game.tableau.indices
                    .map {
                        SolitaireDragSource.TableauRun(
                            it,
                            state.game.tableau[it]
                                .cards.lastIndex,
                        )
                    }.first { state.game.legalTargets(it).isNotEmpty() }
            Board(state.copy(selection = source))
        }

    @Test
    fun readyToAutoComplete() = ready(size = null)

    @Test
    fun readyToAutoComplete_tablet() = ready(size = ScreenSize.TABLET)

    private fun ready(size: ScreenSize?) =
        composeTestRule.snapshot(size = size) {
            Board(
                SolitaireState(
                    game =
                        KlondikeState(
                            seed = 1L,
                            drawCount = 1,
                            tableau =
                                Suit.entries.map { TableauPile(listOf(Card(it, Rank.KING)), 0) } + List(3) { TableauPile(emptyList(), 0) },
                            foundations = Suit.entries.associateWith { 12 },
                            stock = emptyList(),
                            waste = emptyList(),
                            moveCount = 117,
                        ),
                    canAutoComplete = true,
                    canUndo = true,
                    elapsedSeconds = 240,
                    hintsLeft = 2,
                ),
            )
        }

    @Test
    fun stuck() = composeTestRule.snapshot { Board(midGame(drawCount = 1).copy(isStuck = true)) }

    @Test
    fun won() = solved(size = null)

    @Test
    fun won_tablet() = solved(size = ScreenSize.TABLET)

    private fun solved(size: ScreenSize?) =
        composeTestRule.snapshot(size = size) {
            Board(
                SolitaireState(
                    game =
                        KlondikeState(
                            seed = 1L,
                            drawCount = 1,
                            tableau = List(7) { TableauPile(emptyList(), 0) },
                            foundations = Suit.entries.associateWith { KlondikeState.FOUNDATION_MAX },
                            stock = emptyList(),
                            waste = emptyList(),
                            moveCount = 118,
                        ),
                    isWon = true,
                    elapsedSeconds = 245,
                    solved =
                        SolvedSummary(
                            seconds = 245,
                            moves = 118,
                            drawCount = 1,
                            streak = 4,
                            bestSeconds = 198,
                            isNewBest = false,
                            gamesWon = 41,
                            gamesPlayed = 63,
                        ),
                ),
            )
        }

    // The tablet in each of the other theme packs.

    @Test
    fun midGame_tablet_violetPop() =
        composeTestRule.snapshot(SampleCardTableThemes.VioletPop, size = ScreenSize.TABLET) { Board(midGame(drawCount = 3)) }

    @Test
    fun midGame_tablet_lagoon() =
        composeTestRule.snapshot(SampleCardTableThemes.Lagoon, size = ScreenSize.TABLET) { Board(midGame(drawCount = 3)) }

    @Test
    fun midGame_tablet_midnight() =
        composeTestRule.snapshot(SampleCardTableThemes.Midnight, size = ScreenSize.TABLET) { Board(midGame(drawCount = 3)) }
}
