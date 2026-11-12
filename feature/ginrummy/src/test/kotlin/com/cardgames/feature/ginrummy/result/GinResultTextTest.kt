package com.cardgames.feature.ginrummy.result

import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.HandOutcome
import com.cardgames.core.domain.ginrummy.HandResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The result screens' text, as plain functions of a [HandResult] ([knockerOf], [handTitle] and the rest). */
class GinResultTextTest {
    private fun result(
        outcome: HandOutcome,
        winner: GinPlayer?,
    ) = HandResult(outcome = outcome, winner = winner, pointsScored = 0, knockerDeadwood = 0, defenderDeadwood = 0)

    @Test
    fun `knockerOf is the winner for a knock`() {
        assertEquals(GinPlayer.PLAYER, knockerOf(result(HandOutcome.KNOCK, winner = GinPlayer.PLAYER)))
    }

    @Test
    fun `knockerOf is the winner for a gin`() {
        assertEquals(GinPlayer.OPPONENT, knockerOf(result(HandOutcome.GIN, winner = GinPlayer.OPPONENT)))
    }

    @Test
    fun `knockerOf is the other player for an undercut, since the defender wins it`() {
        assertEquals(GinPlayer.OPPONENT, knockerOf(result(HandOutcome.UNDERCUT, winner = GinPlayer.PLAYER)))
    }

    @Test
    fun `knockerOf is null for a blocked hand`() {
        assertNull(knockerOf(result(HandOutcome.BLOCKED, winner = null)))
    }

    @Test
    fun `the overline names who knocked or went gin`() {
        assertEquals("You knocked", handOverline(result(HandOutcome.KNOCK, winner = GinPlayer.PLAYER), "Rosa"))
        assertEquals("Zero deadwood", handOverline(result(HandOutcome.GIN, winner = GinPlayer.OPPONENT), "Rosa"))
        assertEquals("Rosa knocked", handOverline(result(HandOutcome.UNDERCUT, winner = GinPlayer.PLAYER), "Rosa"))
    }

    @Test
    fun `the title names the winner, an undercut or a gin by name`() {
        assertEquals("You win the hand!", handTitle(result(HandOutcome.KNOCK, winner = GinPlayer.PLAYER), "Rosa"))
        assertEquals("Vex wins the hand", handTitle(result(HandOutcome.KNOCK, winner = GinPlayer.OPPONENT), "Vex"))
        val gin = HandResult(HandOutcome.GIN, GinPlayer.OPPONENT, pointsScored = 47, knockerDeadwood = 0, defenderDeadwood = 22)
        assertEquals("Vex went gin!", handTitle(gin, "Vex"))
        assertEquals("Vex went Big Gin!", handTitle(gin.copy(pointsScored = 53), "Vex"))
        assertEquals("Undercut! You win the hand", handTitle(result(HandOutcome.UNDERCUT, winner = GinPlayer.PLAYER), "Rosa"))
    }

    @Test
    fun `the match line says when a gin took the match`() {
        val gin = HandResult(HandOutcome.GIN, GinPlayer.OPPONENT, pointsScored = 47, knockerDeadwood = 0, defenderDeadwood = 22)
        assertEquals("Rosa went gin to take it.", matchLine(won = false, target = 100, short = 60, lastHand = gin, opponentName = "Rosa"))
        val yourGin = gin.copy(winner = GinPlayer.PLAYER)
        assertEquals("You went gin to take it.", matchLine(won = true, target = 100, short = 0, lastHand = yourGin, opponentName = "Rosa"))
        val knock = gin.copy(outcome = HandOutcome.KNOCK, knockerDeadwood = 4, pointsScored = 18)
        assertEquals("So close. You were 60 points from winning.", matchLine(false, 100, 60, knock, "Rosa"))
        assertEquals("First to 100. Well played.", matchLine(true, 100, 0, knock.copy(winner = GinPlayer.PLAYER), "Rosa"))
    }

    @Test
    fun `a knock's points are the deadwood difference`() {
        val knock = HandResult(HandOutcome.KNOCK, GinPlayer.PLAYER, pointsScored = 18, knockerDeadwood = 1, defenderDeadwood = 19)
        assertEquals("19 − 1 = 18", pointsSum(knock))
        assertEquals(0, handBonus(knock))
    }

    @Test
    fun `a gin shows its bonus, whatever the house rules made it`() {
        val gin = HandResult(HandOutcome.GIN, GinPlayer.PLAYER, pointsScored = 47, knockerDeadwood = 0, defenderDeadwood = 22)
        assertEquals("22 + 25 gin bonus = 47", pointsSum(gin))
        val bigGin = gin.copy(pointsScored = 53)
        assertEquals(31, handBonus(bigGin))
    }

    @Test
    fun `an undercut shows its bonus`() {
        val undercut = HandResult(HandOutcome.UNDERCUT, GinPlayer.PLAYER, pointsScored = 31, knockerDeadwood = 6, defenderDeadwood = 0)
        assertEquals("6 − 0 + 25 undercut bonus = 31", pointsSum(undercut))
    }

    @Test
    fun `a blocked hand has no sum`() {
        assertNull(pointsSum(result(HandOutcome.BLOCKED, winner = null)))
    }
}
