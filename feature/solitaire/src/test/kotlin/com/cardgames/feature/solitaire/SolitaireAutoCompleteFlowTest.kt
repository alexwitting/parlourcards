package com.cardgames.feature.solitaire

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.domain.solitaire.TableauPile
import com.cardgames.core.testing.FakeClockProvider
import com.cardgames.core.testing.FakeSavedGameRepository
import com.cardgames.core.testing.FakeSeedProvider
import com.cardgames.core.testing.FakeSettingsRepository
import com.cardgames.core.testing.FakeStatsRepository
import com.cardgames.core.testing.FakeWinnableDealCache
import com.cardgames.core.testing.cards
import com.cardgames.core.testing.ui.TestDevices
import com.cardgames.core.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Complete, end to end through the real ViewModel and screen: the cards fly home and the game is
 * won — the move lands, nothing is left pending.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = TestDevices.PHONE)
class SolitaireAutoCompleteFlowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** Everything to the 9s home; 10s to kings face up in the columns, but one card each in the stock and the waste. */
    private val endgame =
        KlondikeState(
            seed = 1L,
            drawCount = 1,
            tableau =
                listOf(
                    cards("K♠ Q♥ J♣ 10♦"),
                    cards("K♥ Q♣ J♦ 10♠"),
                    cards("K♣ Q♦"),
                    cards("K♦ Q♠ J♥ 10♣"),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                ).map { TableauPile(it, faceDownCount = 0) },
            foundations = Suit.entries.associateWith { 9 },
            stock = cards("J♠"),
            waste = cards("10♥"),
        )

    @Test
    fun completePlaysEveryCardHomeAndWins() {
        val vm =
            SolitaireViewModel(
                FakeSeedProvider(seed = 42L),
                FakeClockProvider(),
                FakeSavedGameRepository(),
                FakeStatsRepository(),
                FakeSettingsRepository(),
                FakeWinnableDealCache(),
                Dispatchers.Unconfined,
            ).also { it.shuffleMillis = 0 }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            AppTheme {
                SolitaireContent(
                    state = state,
                    onIntent = vm::onIntent,
                    onNavigateBack = {},
                    onOpenSettings = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeTestRule.runOnIdle { vm.overrideGameForTest(endgame) }
        composeTestRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertTrue("Complete is offered", vm.state.value.canAutoComplete)

        composeTestRule.onNodeWithText("Complete").performClick()
        repeat(STEPS) { composeTestRule.mainClock.advanceTimeBy(STEP_MILLIS) }

        assertNull("the motion landed", vm.state.value.pending)
        assertTrue("the game is won", vm.state.value.isWon)
    }

    private companion object {
        const val SETTLE_MILLIS = 3_000L
        const val STEPS = 40
        const val STEP_MILLIS = 250L
    }
}
