package com.cardgames.feature.ginrummy

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Deck
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinMatchHighlights
import com.cardgames.core.domain.ginrummy.GinMove
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.HandOutcome
import com.cardgames.core.domain.ginrummy.HandResult
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.stats.Record
import com.cardgames.core.testing.ui.ScreenSize
import com.cardgames.core.testing.ui.snapshot
import com.cardgames.core.ui.cards.SampleCardTableThemes
import com.cardgames.feature.ginrummy.setup.GinSetupContent
import com.cardgames.feature.ginrummy.setup.GinSetupState
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
class GinScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Composable
    private fun Board(state: GinUiState) {
        GinContent(
            state = state,
            onIntent = {},
            onNavigateBack = {},
            onOpenSettings = {},
            snackbarHostState = SnackbarHostState(),
        )
    }

    /** Both passed the upcard and the opponent played one turn: the player is to draw. */
    private fun playerToDraw(): GinState {
        var game = GinEngine.newMatch(seed = 1L)
        game = GinEngine.apply(game, GinMove.PassUpcard)
        game = GinEngine.apply(game, GinMove.PassUpcard)
        game = GinEngine.apply(game, GinMove.DrawFromStock)
        return GinEngine.apply(game, GinMove.Discard(game.handOf(GinPlayer.OPPONENT).first()))
    }

    @Test
    fun playerToDraw_parlour() = composeTestRule.snapshot { Board(GinUiState(game = playerToDraw(), canUndo = true)) }

    @Test
    fun playerToDraw_tablet() =
        composeTestRule.snapshot(size = ScreenSize.TABLET) { Board(GinUiState(game = playerToDraw(), canUndo = true, hintsLeft = 3)) }

    @Test
    fun ginAvailable_tablet() =
        composeTestRule.snapshot(size = ScreenSize.TABLET) {
            Board(GinUiState(game = ginAvailableGame(), selectedCard = Card(Suit.SPADES, Rank.KING)))
        }

    @Test
    fun playerToDraw_midnight() =
        composeTestRule.snapshot(SampleCardTableThemes.Midnight) { Board(GinUiState(game = playerToDraw(), canUndo = true)) }

    /** A fresh hand part-way through its deal: some cards dealt, the rest still stacked on the stock. */
    @Test
    fun dealInProgress() =
        composeTestRule.snapshot(settleMillis = MID_DEAL_MILLIS) { Board(GinUiState(game = GinEngine.newMatch(seed = 1L))) }

    @Test
    fun discardWithSelection() = selectedDiscard(size = null)

    @Test
    fun discardWithSelection_tablet() = selectedDiscard(size = ScreenSize.TABLET)

    private fun selectedDiscard(size: ScreenSize?) =
        composeTestRule.snapshot(size = size) {
            val game = GinEngine.apply(playerToDraw(), GinMove.DrawFromStock)
            Board(GinUiState(game = game, selectedCard = game.playerHand.first(), canUndo = true))
        }

    @Test
    fun opponentThinking() = thinking(size = null)

    @Test
    fun opponentThinking_tablet() = thinking(size = ScreenSize.TABLET)

    private fun thinking(size: ScreenSize?) =
        composeTestRule.snapshot(size = size) {
            val game = GinEngine.apply(GinEngine.apply(playerToDraw(), GinMove.DrawFromStock), GinMove.Discard(playerToDrawDiscard()))
            Board(GinUiState(game = game, isOpponentThinking = true, canUndo = true, hintsLeft = 3))
        }

    @Test
    fun upcardChoice() = upcard(size = null)

    @Test
    fun upcardChoice_tablet() = upcard(size = ScreenSize.TABLET)

    private fun upcard(size: ScreenSize?) =
        composeTestRule.snapshot(size = size) {
            Board(GinUiState(game = GinEngine.newMatch(seed = 1L).copy(moveCount = 1, phase = GinPhase.UpcardDecision(GinPlayer.PLAYER))))
        }

    @Test
    fun ginAvailable() =
        composeTestRule.snapshot { Board(GinUiState(game = ginAvailableGame(), selectedCard = Card(Suit.SPADES, Rank.KING))) }

    @Test
    fun handOver_withLayoff_parlour() = composeTestRule.snapshot { Board(GinUiState(game = handOverWithLayoff())) }

    @Test
    fun handOver_withLayoff_tablet() = composeTestRule.snapshot(size = ScreenSize.TABLET) { Board(GinUiState(game = handOverWithLayoff())) }

    @Test
    fun handOver_withLayoff_midnight() =
        composeTestRule.snapshot(SampleCardTableThemes.Midnight) { Board(GinUiState(game = handOverWithLayoff())) }

    @Test
    fun matchWon() = wonMatch(size = null)

    @Test
    fun matchWon_tablet() = wonMatch(size = ScreenSize.TABLET)

    private fun wonMatch(size: ScreenSize?) =
        composeTestRule.snapshot(size = size) {
            val game =
                handOverWithLayoff().copy(
                    phase = GinPhase.MatchOver(GinPlayer.PLAYER),
                    matchScores = mapOf(GinPlayer.PLAYER to 212, GinPlayer.OPPONENT to 64),
                    boxes = mapOf(GinPlayer.PLAYER to 5, GinPlayer.OPPONENT to 2),
                )
            Board(GinUiState(game = game))
        }

    @Test
    fun matchLost() = lostMatch(size = null)

    @Test
    fun matchLost_tablet() = lostMatch(size = ScreenSize.TABLET)

    private fun lostMatch(size: ScreenSize?) =
        composeTestRule.snapshot(size = size) {
            val game =
                handOverWithLayoff().copy(
                    phase =
                        GinPhase.MatchOver(
                            GinPlayer.OPPONENT,
                            raceScores = mapOf(GinPlayer.PLAYER to 87, GinPlayer.OPPONENT to 104),
                        ),
                    boxes = mapOf(GinPlayer.PLAYER to 3, GinPlayer.OPPONENT to 5),
                )
            Board(
                GinUiState(
                    game = game,
                    highlights = GinMatchHighlights(gins = 2, undercuts = 1, bestHand = 47, handsPlayed = 8),
                    overallRecord = Record(wins = 12, losses = 10),
                ),
            )
        }

    @Test
    fun playerGin() = gin(size = null)

    @Test
    fun playerGin_tablet() = gin(size = ScreenSize.TABLET)

    private fun gin(size: ScreenSize?) =
        composeTestRule.snapshot(size = size) {
            val gin = ginAvailableGame()
            val game =
                gin.copy(
                    playerHand = gin.playerHand - Card(Suit.SPADES, Rank.KING),
                    phase =
                        GinPhase.HandOver(
                            HandResult(HandOutcome.GIN, GinPlayer.PLAYER, pointsScored = 47, knockerDeadwood = 0, defenderDeadwood = 22),
                        ),
                    matchScores = mapOf(GinPlayer.PLAYER to 80, GinPlayer.OPPONENT to 48),
                )
            Board(GinUiState(game = game))
        }

    @Test
    fun matchSetup() = setup(size = null)

    @Test
    fun matchSetup_tablet() = setup(size = ScreenSize.TABLET)

    private fun setup(size: ScreenSize?) =
        composeTestRule.snapshot(size = size) {
            val records =
                mapOf(
                    GinDifficulty.EASY to Record(8, 2),
                    GinDifficulty.MEDIUM to Record(12, 10),
                    GinDifficulty.HARD to Record(0, 0),
                )
            GinSetupContent(GinSetupState(matchInProgress = true, records = records), onIntent = {}, onNavigateBack = {})
        }

    @Test
    fun handBlocked() =
        composeTestRule.snapshot {
            val game =
                playerToDraw().copy(
                    phase =
                        GinPhase.HandOver(
                            HandResult(HandOutcome.BLOCKED, winner = null, pointsScored = 0, knockerDeadwood = 0, defenderDeadwood = 0),
                        ),
                    matchScores = mapOf(GinPlayer.PLAYER to 80, GinPlayer.OPPONENT to 48),
                )
            Board(GinUiState(game = game))
        }

    /** The card the player discards in [opponentThinking] — whatever it drew, so the hand is unchanged. */
    private fun playerToDrawDiscard(): Card = GinEngine.apply(playerToDraw(), GinMove.DrawFromStock).playerHand.last()

    /** The player holds A-4♠, three 7s, three 9s and the K♠: discarding the king is gin. */
    private fun ginAvailableGame(): GinState {
        val hand =
            listOf(
                Card(Suit.SPADES, Rank.ACE),
                Card(Suit.SPADES, Rank.TWO),
                Card(Suit.SPADES, Rank.THREE),
                Card(Suit.SPADES, Rank.FOUR),
                Card(Suit.HEARTS, Rank.SEVEN),
                Card(Suit.DIAMONDS, Rank.SEVEN),
                Card(Suit.CLUBS, Rank.SEVEN),
                Card(Suit.HEARTS, Rank.NINE),
                Card(Suit.DIAMONDS, Rank.NINE),
                Card(Suit.CLUBS, Rank.NINE),
                Card(Suit.SPADES, Rank.KING),
            )
        val rest = Deck.standard52 - hand.toSet()
        return GinEngine.newMatch(seed = 1L).copy(
            playerHand = hand,
            opponentHand = rest.take(10),
            discardPile = rest.drop(10).take(1),
            stock = rest.drop(11),
            phase = GinPhase.AwaitingDiscard(GinPlayer.PLAYER),
            moveCount = 12,
        )
    }

    /** The opponent knocked; the player laid the 7♠ off onto its melds. */
    private fun handOverWithLayoff(): GinState {
        val laidOff = Card(Suit.SPADES, Rank.SEVEN)
        val rest = Deck.standard52 - laidOff
        return GinEngine.newMatch(seed = 1L).copy(
            playerHand = rest.take(9),
            opponentHand = rest.drop(9).take(10),
            discardPile = rest.drop(19).take(1),
            stock = rest.drop(20),
            phase =
                GinPhase.HandOver(
                    HandResult(HandOutcome.KNOCK, GinPlayer.OPPONENT, pointsScored = 12, knockerDeadwood = 4, defenderDeadwood = 16),
                ),
            matchScores = mapOf(GinPlayer.PLAYER to 30, GinPlayer.OPPONENT to 42),
            moveCount = 20,
            laidOff = mapOf(GinPlayer.OPPONENT to listOf(laidOff)),
        )
    }

    private companion object {
        /** About nine cards into the 21-card deal at normal speed. */
        const val MID_DEAL_MILLIS = 500L
    }

    // The tablet in each of the other theme packs.

    @Test
    fun playerToDraw_tablet_violetPop() =
        composeTestRule.snapshot(SampleCardTableThemes.VioletPop, size = ScreenSize.TABLET) {
            Board(GinUiState(game = playerToDraw(), canUndo = true, hintsLeft = 3))
        }

    @Test
    fun playerToDraw_tablet_lagoon() =
        composeTestRule.snapshot(SampleCardTableThemes.Lagoon, size = ScreenSize.TABLET) {
            Board(GinUiState(game = playerToDraw(), canUndo = true, hintsLeft = 3))
        }

    @Test
    fun playerToDraw_tablet_midnight() =
        composeTestRule.snapshot(SampleCardTableThemes.Midnight, size = ScreenSize.TABLET) {
            Board(GinUiState(game = playerToDraw(), canUndo = true, hintsLeft = 3))
        }
}
