package com.cardgames.feature.ginrummy

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.testing.ui.TestDevices
import com.cardgames.core.ui.theme.AppTheme
import com.cardgames.feature.ginrummy.board.GinDragSource
import com.cardgames.feature.ginrummy.board.GinDropTarget
import com.cardgames.feature.ginrummy.board.GinTableTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The table holds still while its cards move ("Keeping the table still" in
 * docs/DESIGN_SYSTEM.md): over whole hands played through the real ViewModel — deals, draws from
 * both piles, melds forming, discards, the opponent's turns, the hand's end — every part of the
 * table sits in exactly one place on every frame. Cards glide; the seat, the piles, the melds row,
 * the divider and the hand's row never budge.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = TestDevices.PHONE)
class GinTableStillTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val tags =
        listOf(
            GinTableTags.OPPONENT,
            GinTableTags.PILES,
            GinTableTags.STOCK,
            GinTableTags.DISCARD,
            GinTableTags.HAND_AREA,
            GinTableTags.MELDS,
            GinTableTags.DIVIDER,
            GinTableTags.HAND,
        )

    /** Each part's bounds as seen, with the first moment each was seen. */
    private val seen = tags.associateWith { linkedMapOf<DpRect, String>() }

    @Test
    fun `nothing on the table moves but the cards, over whole hands`() {
        val vm = testGinViewModel()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            AppTheme {
                GinContent(
                    state = state,
                    onIntent = vm::onIntent,
                    onNavigateBack = {},
                    onOpenSettings = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        repeat(HANDS) { hand -> playHand(vm, hand) }

        assertTrue("every hand was played out", vm.state.value.game.handNumber > HANDS || vm.state.value.game.phase is GinPhase.MatchOver)

        val moved = seen.filterValues { it.size > 1 }
        assertTrue(
            moved.entries.joinToString("\n") { (tag, bounds) -> "$tag: " + bounds.entries.joinToString { (b, at) -> "$b at $at" } },
            moved.isEmpty(),
        )
    }

    /** Plays one hand to its end: the deal, then the player's simple strategy against the opponent's. */
    private fun playHand(
        vm: GinViewModel,
        hand: Int,
    ) {
        settleFrames(vm, "hand $hand deal")
        repeat(MAX_TURNS) { turn ->
            val state = vm.state.value
            val phase = state.game.phase
            if (phase is GinPhase.HandOver || phase is GinPhase.MatchOver) {
                vm.onIntent(GinIntent.NextHand)
                return
            }
            playerIntent(state)?.let(vm::onIntent)
            settleFrames(vm, "hand $hand turn $turn (${phase.javaClass.simpleName})")
        }
    }

    /** Takes the upcard or the pile when it makes a meld, else draws blind; throws the suggested card. */
    private fun playerIntent(state: GinUiState): GinIntent? {
        val game = state.game
        val phase = game.phase
        val makesMeld = game.discardMeldPartners().isNotEmpty()
        return when {
            state.pending != null || !state.isPlayerTurn -> null
            state.ginOffer != null -> GinIntent.DismissGinOffer
            phase is GinPhase.UpcardDecision -> if (makesMeld) GinIntent.TakeUpcard else GinIntent.PassUpcard
            phase is GinPhase.AwaitingDraw -> if (makesMeld) GinIntent.DrawFromDiscard else GinIntent.DrawFromStock
            phase is GinPhase.AwaitingDiscard -> {
                val card = (game.suggestion() as? GinHint.Discard)?.card ?: game.playerHand.last()
                GinIntent.DropCard(GinDragSource.HandCard(card), GinDropTarget.Discard, carried = false)
            }
            else -> null
        }
    }

    /** Steps frame by frame until every motion has landed and the fades after it are done, noting each part's bounds. */
    private fun settleFrames(
        vm: GinViewModel,
        moment: String,
    ) {
        var still = 0
        for (frame in 0 until MAX_FRAMES) {
            composeTestRule.mainClock.advanceTimeByFrame()
            tags.forEach { tag -> bounds(tag)?.let { seen.getValue(tag).putIfAbsent(it, "$moment, frame $frame") } }
            still = if (vm.state.value.pending == null) still + 1 else 0
            if (still > SETTLED_FRAMES) return
        }
    }

    /** Null while the part isn't on screen (the felt waits for the match before drawing). */
    private fun bounds(tag: String): DpRect? =
        runCatching { composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot() }.getOrNull()

    private companion object {
        const val HANDS = 3
        const val MAX_TURNS = 40
        const val MAX_FRAMES = 600

        /** A motion's fades (the no-melds outline, the hand's dim) finish within this many frames of it landing. */
        const val SETTLED_FRAMES = 30
    }
}
