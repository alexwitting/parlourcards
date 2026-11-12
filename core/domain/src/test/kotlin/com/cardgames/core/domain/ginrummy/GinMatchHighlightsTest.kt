package com.cardgames.core.domain.ginrummy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GinMatchHighlightsTest {
    private fun hand(
        outcome: HandOutcome,
        winner: GinPlayer?,
        points: Int = 20,
        knockerDeadwood: Int = 4,
    ) = HandResult(outcome, winner, pointsScored = points, knockerDeadwood = knockerDeadwood, defenderDeadwood = 24)

    @Test
    fun `the player's gin counts as a gin, a go-out and their best hand`() {
        val h = GinMatchHighlights().plus(hand(HandOutcome.GIN, GinPlayer.PLAYER, points = 47, knockerDeadwood = 0))

        assertEquals(GinMatchHighlights(gins = 1, bestHand = 47, handsPlayed = 1, knocks = 1, knockDeadwood = 0), h)
    }

    @Test
    fun `an undercut is the player's win but the opponent's knock`() {
        val h = GinMatchHighlights().plus(hand(HandOutcome.UNDERCUT, GinPlayer.PLAYER, points = 35))

        assertEquals(1, h.undercuts)
        assertEquals(35, h.bestHand)
        assertEquals(0, h.knocks)
    }

    @Test
    fun `being undercut still counts the player's knock and its deadwood`() {
        val h = GinMatchHighlights().plus(hand(HandOutcome.UNDERCUT, GinPlayer.OPPONENT, knockerDeadwood = 7))

        assertEquals(1, h.knocks)
        assertEquals(7, h.knockDeadwood)
        assertEquals(0, h.bestHand)
    }

    @Test
    fun `the opponent's hands and blocked hands only count as played`() {
        val h =
            GinMatchHighlights()
                .plus(hand(HandOutcome.KNOCK, GinPlayer.OPPONENT))
                .plus(hand(HandOutcome.BLOCKED, null, points = 0))

        assertEquals(GinMatchHighlights(handsPlayed = 2), h)
    }

    @Test
    fun `the best hand keeps the biggest across the match`() {
        val h =
            GinMatchHighlights()
                .plus(hand(HandOutcome.KNOCK, GinPlayer.PLAYER, points = 30))
                .plus(hand(HandOutcome.KNOCK, GinPlayer.PLAYER, points = 12))

        assertEquals(30, h.bestHand)
        assertEquals(2, h.knocks)
        assertEquals(8, h.knockDeadwood)
    }
}
