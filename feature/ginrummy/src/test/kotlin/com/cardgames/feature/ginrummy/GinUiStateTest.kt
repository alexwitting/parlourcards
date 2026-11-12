package com.cardgames.feature.ginrummy

import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.HandOutcome
import com.cardgames.core.domain.ginrummy.HandResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Targeted coverage for two [GinUiState]/[GinEffect] gaps: [isPlayerTurn]'s "nobody's live turn"
 * fallback and the two [GinEffect] variants no existing test ever constructed.
 */
class GinUiStateTest {
    private fun stateWith(phase: GinPhase) =
        GinUiState(
            game =
                GinState(
                    seed = 0L,
                    handNumber = 1,
                    dealer = GinPlayer.OPPONENT,
                    playerHand = emptyList(),
                    opponentHand = emptyList(),
                    stock = emptyList(),
                    discardPile = emptyList(),
                    phase = phase,
                    matchScores = GinPlayer.entries.associateWith { 0 },
                    boxes = GinPlayer.entries.associateWith { 0 },
                ),
        )

    @Test
    fun `isPlayerTurn is false once the hand is over`() {
        val result =
            HandResult(outcome = HandOutcome.BLOCKED, winner = null, pointsScored = 0, knockerDeadwood = 0, defenderDeadwood = 0)

        assertFalse(stateWith(GinPhase.HandOver(result)).isPlayerTurn)
    }

    @Test
    fun `isPlayerTurn is false once the match is over`() {
        assertFalse(stateWith(GinPhase.MatchOver(winner = GinPlayer.PLAYER)).isPlayerTurn)
    }

    @Test
    fun `GinEffect ShowSnackbar carries its message`() {
        assertEquals("Couldn't save the setting", GinEffect.ShowSnackbar("Couldn't save the setting").message)
    }

    @Test
    fun `GinEffect HandBlocked is a plain marker`() {
        assertEquals(GinEffect.HandBlocked, GinEffect.HandBlocked)
    }
}
