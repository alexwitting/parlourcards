package com.cardgames.core.domain.ginrummy

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.ginrummy.ai.GinOpponentDriver
import com.cardgames.core.domain.ginrummy.ai.HeuristicGinOpponent
import com.cardgames.core.testing.card
import com.cardgames.core.testing.cards
import com.cardgames.core.testing.ginPosition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Per-match house rules: the target, the gin bonus, and Big Gin. */
class GinHouseRulesTest {
    /** Eleven cards, all melded: A–4 of hearts, 4–7 of clubs, three kings. */
    private val elevenMelded =
        cards("A♥ 2♥ 3♥ 4♥ 4♣ 5♣ 6♣ 7♣ K♠ K♥ K♦")

    /** Throwing the four of hearts still leaves every card melded — a gin. */
    private val ginKnock = card("4♥")

    private val opponentHand =
        cards("2♠ 4♠ 6♠ 8♠ 10♠ 2♦ 4♦ 6♦ 9♣ J♣")

    private fun discarding(
        hand: List<Card>,
        rules: GinMatchRules,
        scores: Int = 0,
    ): GinState =
        ginPosition(hand, otherHand = opponentHand, rules = rules)
            .copy(matchScores = mapOf(GinPlayer.PLAYER to scores, GinPlayer.OPPONENT to 0))

    @Test
    fun `big gin is only legal when the match allows it`() {
        assertFalse(GinEngine.isLegal(discarding(elevenMelded, GinMatchRules()), GinMove.BigGin))
        assertTrue(GinEngine.isLegal(discarding(elevenMelded, GinMatchRules(bigGin = true)), GinMove.BigGin))
    }

    @Test
    fun `big gin scores the opponent's deadwood plus 31`() {
        var state = GinEngine.apply(discarding(elevenMelded, GinMatchRules(bigGin = true)), GinMove.BigGin)
        state = GinEngine.apply(state, GinMove.SettleHand)

        val result = (state.phase as GinPhase.HandOver).result
        assertEquals(HandOutcome.GIN, result.outcome)
        assertEquals(Melds.bestPartition(opponentHand).deadwoodPips + GinScoring.BIG_GIN_BONUS, result.pointsScored)
    }

    @Test
    fun `without the gin bonus a gin scores only the deadwood difference`() {
        val withBonus = settledGin(GinMatchRules())
        val without = settledGin(GinMatchRules(ginBonus = false))

        assertEquals(GinScoring.GIN_BONUS, withBonus - without)
    }

    @Test
    fun `the match ends at its own target`() {
        val state = GinEngine.apply(discarding(elevenMelded, GinMatchRules(target = 50), scores = 40), GinMove.Knock(ginKnock))
        val settled = GinEngine.apply(state, GinMove.SettleHand)

        val over = settled.phase as GinPhase.MatchOver
        assertEquals(GinPlayer.PLAYER, over.winner)
        assertTrue(over.raceScores!!.getValue(GinPlayer.PLAYER) >= 50)
        assertEquals(HandOutcome.GIN, over.lastHand?.outcome)
    }

    @Test
    fun `the computer opponent takes a big gin when it can`() {
        val state =
            discarding(elevenMelded, GinMatchRules(bigGin = true)).let {
                it.copy(playerHand = opponentHand, opponentHand = it.playerHand, phase = GinPhase.AwaitingDiscard(GinPlayer.OPPONENT))
            }

        val next = GinOpponentDriver.driveOneDecision(state, GinPlayer.OPPONENT, HeuristicGinOpponent())

        assertTrue((next.phase as GinPhase.AwaitingLayoff).isBigGin)
    }

    private fun settledGin(rules: GinMatchRules): Int {
        var state = GinEngine.apply(discarding(elevenMelded, rules), GinMove.Knock(ginKnock))
        state = GinEngine.apply(state, GinMove.SettleHand)
        return (state.phase as GinPhase.HandOver).result.pointsScored
    }
}
