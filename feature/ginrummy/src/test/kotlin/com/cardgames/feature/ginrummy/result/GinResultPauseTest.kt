package com.cardgames.feature.ginrummy.result

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.HandOutcome
import com.cardgames.core.domain.ginrummy.HandResult
import com.cardgames.core.testing.cards
import com.cardgames.core.testing.ginPosition
import com.cardgames.core.testing.ui.TestDevices
import com.cardgames.core.ui.theme.AppTheme
import com.cardgames.feature.ginrummy.GinContent
import com.cardgames.feature.ginrummy.GinUiState
import com.cardgames.feature.ginrummy.motion.GinMotionTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A gin's result waits a beat on the table, so the loser's hand is seen turning face up; a knock's doesn't. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = TestDevices.PHONE)
class GinResultPauseTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun handOver(outcome: HandOutcome): GinUiState {
        val game = ginPosition(cards("A♠ 2♠ 3♠ 4♠ 7♥ 7♦ 7♣ 9♥ 9♦ 9♣")).copy(moveCount = 12)
        val result = HandResult(outcome, GinPlayer.PLAYER, pointsScored = 40, knockerDeadwood = 0, defenderDeadwood = 15)
        return GinUiState(game = game.copy(phase = GinPhase.HandOver(result)))
    }

    private fun showAt(
        state: GinUiState,
        millis: Long,
    ) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            AppTheme {
                GinContent(state = state, onIntent = {}, onNavigateBack = {}, onOpenSettings = {}, snackbarHostState = SnackbarHostState())
            }
        }
        composeTestRule.mainClock.advanceTimeBy(millis)
    }

    @Test
    fun aGinWaitsOnTheTableBeforeItsResult() {
        showAt(handOver(HandOutcome.GIN), millis = GinMotionTokens.GIN_REVEAL_PAUSE / 2)

        composeTestRule.onNodeWithText("Next hand").assertDoesNotExist()
    }

    @Test
    fun aGinsResultShowsOnceThePauseHasRun() {
        showAt(handOver(HandOutcome.GIN), millis = GinMotionTokens.GIN_REVEAL_PAUSE + SETTLE_MILLIS)

        composeTestRule.onNodeWithText("Next hand").assertExists()
    }

    @Test
    fun aKnocksResultShowsWithoutThePause() {
        showAt(handOver(HandOutcome.KNOCK), millis = GinMotionTokens.GIN_REVEAL_PAUSE / 2)

        composeTestRule.onNodeWithText("Next hand").assertExists()
    }

    private companion object {
        /** The result's fade-in and its parts' entrances. */
        const val SETTLE_MILLIS = 1_000L
    }
}
