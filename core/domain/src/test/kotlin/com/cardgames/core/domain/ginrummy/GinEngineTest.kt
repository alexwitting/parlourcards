package com.cardgames.core.domain.ginrummy

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.testing.card
import com.cardgames.core.testing.cards
import com.cardgames.core.testing.ginPosition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GinEngineTest {
    private val lowDeadwoodDiscard = card("K♠")

    // --- Dealing -----------------------------------------------------------------------

    @Test
    fun `a new match deals 10 cards each, an upcard, a 31-card stock, and offers the non-dealer first`() {
        val state = GinEngine.newMatch(seed = 1L)

        assertEquals(10, state.playerHand.size)
        assertEquals(10, state.opponentHand.size)
        assertEquals(1, state.discardPile.size)
        assertEquals(31, state.stock.size)
        assertEquals(GinPlayer.PLAYER, state.dealer)
        assertEquals(GinPhase.UpcardDecision(GinPlayer.OPPONENT), state.phase)
        assertEquals(mapOf(GinPlayer.PLAYER to 0, GinPlayer.OPPONENT to 0), state.matchScores)
        assertEquals(1, state.handNumber)
    }

    @Test
    fun `no card appears twice across hands, stock, and the discard pile`() {
        val state = GinEngine.newMatch(seed = 7L)

        val allCards = state.playerHand + state.opponentHand + state.stock + state.discardPile
        assertEquals(52, allCards.size)
        assertEquals(52, allCards.toSet().size)
    }

    // --- Upcard phase --------------------------------------------------------------------

    @Test
    fun `the non-dealer taking the upcard moves straight to discard, no free look`() {
        val state = GinEngine.newMatch(seed = 1L)
        val upcard = state.discardPile.single()

        val afterTake = GinEngine.apply(state, GinMove.TakeUpcard)

        assertEquals(11, afterTake.handOf(GinPlayer.OPPONENT).size)
        assertTrue(upcard in afterTake.handOf(GinPlayer.OPPONENT))
        assertTrue(afterTake.discardPile.isEmpty())
        val phase = afterTake.phase as GinPhase.AwaitingDiscard
        assertEquals(GinPlayer.OPPONENT, phase.toMove)
        assertEquals(upcard, phase.mustNotDiscard)
        assertFalse(GinEngine.isLegal(afterTake, GinMove.Discard(upcard)))
    }

    @Test
    fun `both players passing the upcard hands the stock draw to the non-dealer`() {
        val state = GinEngine.newMatch(seed = 1L)

        val afterNonDealerPass = GinEngine.apply(state, GinMove.PassUpcard)
        assertEquals(GinPhase.UpcardDecision(GinPlayer.PLAYER), afterNonDealerPass.phase)

        val afterBothPass = GinEngine.apply(afterNonDealerPass, GinMove.PassUpcard)
        assertEquals(GinPhase.AwaitingDraw(GinPlayer.OPPONENT), afterBothPass.phase)
        // The upcard is still sitting there, untouched, for whoever draws from the discard next.
        assertEquals(1, afterBothPass.discardPile.size)
    }

    @Test
    fun `only the deciding player's upcard moves are legal`() {
        val state = GinEngine.newMatch(seed = 1L)

        assertFalse(GinEngine.isLegal(state, GinMove.DrawFromStock))
        assertFalse(GinEngine.isLegal(state, GinMove.DrawFromDiscard))
    }

    // --- Draw / discard rotation -----------------------------------------------------------

    @Test
    fun `drawing from stock does not restrict the following discard`() {
        var state = GinEngine.newMatch(seed = 1L)
        state = GinEngine.apply(state, GinMove.PassUpcard)
        state = GinEngine.apply(state, GinMove.PassUpcard)

        state = GinEngine.apply(state, GinMove.DrawFromStock)

        val phase = state.phase as GinPhase.AwaitingDiscard
        assertNull(phase.mustNotDiscard)
        assertEquals(11, state.handOf(GinPlayer.OPPONENT).size)
    }

    @Test
    fun `drawing from the discard pile forbids immediately discarding that exact card`() {
        var state = GinEngine.newMatch(seed = 1L)
        state = GinEngine.apply(state, GinMove.PassUpcard)
        state = GinEngine.apply(state, GinMove.PassUpcard)
        val taken = state.discardPile.last()

        state = GinEngine.apply(state, GinMove.DrawFromDiscard)

        assertFalse(GinEngine.isLegal(state, GinMove.Discard(taken)))
        val anyOtherCard = state.handOf(GinPlayer.OPPONENT).first { it != taken }
        assertTrue(GinEngine.isLegal(state, GinMove.Discard(anyOtherCard)))
    }

    @Test
    fun `a normal discard passes the turn to the other player`() {
        var state = GinEngine.newMatch(seed = 1L)
        state = GinEngine.apply(state, GinMove.PassUpcard)
        state = GinEngine.apply(state, GinMove.PassUpcard)
        state = GinEngine.apply(state, GinMove.DrawFromStock)
        val discarded = state.handOf(GinPlayer.OPPONENT).first()

        state = GinEngine.apply(state, GinMove.Discard(discarded))

        assertEquals(GinPhase.AwaitingDraw(GinPlayer.PLAYER), state.phase)
        assertEquals(10, state.handOf(GinPlayer.OPPONENT).size)
        assertEquals(discarded, state.discardPile.last())
    }

    @Test
    fun `the stock reserve blocks the hand with no score instead of offering a draw`() {
        var state = GinEngine.newMatch(seed = 1L)
        state = GinEngine.apply(state, GinMove.PassUpcard)
        state = GinEngine.apply(state, GinMove.PassUpcard)
        // Draw the stock down to exactly the 2-card reserve.
        while (state.stock.size > GinState.STOCK_RESERVE) {
            val toMove = (state.phase as GinPhase.AwaitingDraw).toMove
            state = GinEngine.apply(state, GinMove.DrawFromStock)
            state = GinEngine.apply(state, GinMove.Discard(state.handOf(toMove).first()))
        }

        assertTrue(GinEngine.isHandOver(state))
        val phase = state.phase as GinPhase.HandOver
        assertEquals(HandOutcome.BLOCKED, phase.result.outcome)
        assertNull(phase.result.winner)
        assertEquals(0, phase.result.pointsScored)
        assertEquals(state.matchScores, GinEngine.newMatch(seed = 99L).matchScores) // untouched, still 0-0
    }

    // --- Knock / gin legality ---------------------------------------------------------------

    @Test
    fun `knocking is illegal above the deadwood threshold`() {
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = highDeadwoodHand())

        val move = GinMove.Knock(state.playerHand.last())
        assertFalse(GinEngine.isLegal(state, move))
    }

    @Test
    fun `knocking is legal at exactly the deadwood threshold and sets up layoff`() {
        // Melds: A-2-3 spades (0 pips), 7-7-7 hearts/diamonds/clubs (0 pips) = 6 cards.
        // Deadwood-to-keep: 2h,3d,4c,Ac = 2+3+4+1 = exactly 10 pips (4 cards, no accidental melds).
        // Discard: Kh — irrelevant value, just needs to be the 11th card.
        val hand =
            cards("A♠ 2♠ 3♠ 7♥ 7♦ 7♣ 2♥ 3♦ 4♣ A♣ K♥")
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = hand)
        val discardCard = card("K♥")

        assertTrue(GinEngine.isLegal(state, GinMove.Knock(discardCard)))

        val afterKnock = GinEngine.apply(state, GinMove.Knock(discardCard))
        val layoffPhase = afterKnock.phase as GinPhase.AwaitingLayoff
        assertEquals(GinPlayer.PLAYER, layoffPhase.knocker)
        assertFalse(layoffPhase.isGin)
        val meldedCards = layoffPhase.knockerMelds.flatMap { it.cards }.toSet()
        val deadwoodPips = (afterKnock.handOf(GinPlayer.PLAYER) - meldedCards).sumOf { it.rank.pipValue }
        assertEquals(10, deadwoodPips)
    }

    @Test
    fun `a zero-deadwood knock is gin and forbids layoffs`() {
        val ginHand =
            listOf(
                card("A♠"),
                card("2♠"),
                card("3♠"),
                card("4♠"),
                card("7♥"),
                card("7♦"),
                card("7♣"),
                card("9♥"),
                card("9♦"),
                card("9♣"),
                card("K♠"), // the discard
            )
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = ginHand)
        val discardCard = card("K♠")

        assertTrue(GinEngine.isLegal(state, GinMove.Knock(discardCard)))
        val afterKnock = GinEngine.apply(state, GinMove.Knock(discardCard))

        val phase = afterKnock.phase as GinPhase.AwaitingLayoff
        assertTrue(phase.isGin)
        assertFalse(GinEngine.isLegal(afterKnock, GinMove.LayOff(afterKnock.opponentHand.first(), 0)))
        assertTrue(GinEngine.isLegal(afterKnock, GinMove.SettleHand))
    }

    // --- Best-knock-card suggestion ----------------------------------------------------------

    @Test
    fun `bestKnockCard finds the discard that unlocks a knock without the player guessing`() {
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = lowDeadwoodKnockerHand())

        val suggestion = GinEngine.bestKnockCard(state)

        assertEquals(lowDeadwoodDiscard, suggestion)
        assertTrue(GinEngine.isLegal(state, GinMove.Knock(requireNotNull(suggestion))))
    }

    @Test
    fun `bestKnockCard is null when no discard reaches the deadwood threshold`() {
        val state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = highDeadwoodHand())

        assertNull(GinEngine.bestKnockCard(state))
    }

    @Test
    fun `bestKnockCard is null outside the discard phase`() {
        val state = GinEngine.newMatch(seed = 1L)

        assertNull(GinEngine.bestKnockCard(state))
    }

    @Test
    fun `bestKnockCard never suggests the card that was just taken from the discard pile`() {
        var state = handAwaitingDiscardFor(GinPlayer.PLAYER, hand = lowDeadwoodKnockerHand())
        state = state.copy(phase = GinPhase.AwaitingDiscard(GinPlayer.PLAYER, mustNotDiscard = lowDeadwoodDiscard))

        val suggestion = GinEngine.bestKnockCard(state)

        assertTrue(suggestion == null || suggestion != lowDeadwoodDiscard)
        suggestion?.let { assertTrue(GinEngine.isLegal(state, GinMove.Knock(it))) }
    }

    // --- Layoff + scoring worked examples ---------------------------------------------------

    @Test
    fun `a plain knock scores the deadwood difference to the knocker`() {
        val knockerHand = lowDeadwoodKnockerHand()
        val defenderHand =
            cards("K♥ Q♣ 8♦ 4♥ 5♣ 6♦ 9♠ 8♣ 2♦ 4♠")
        var state = customState(dealer = GinPlayer.OPPONENT, playerHand = knockerHand, opponentHand = defenderHand)
        state = state.copy(phase = GinPhase.AwaitingDiscard(GinPlayer.PLAYER))
        val knockerDeadwood = Melds.bestPartition(knockerHand - lowDeadwoodDiscard).deadwoodPips
        val defenderDeadwood = Melds.bestPartition(defenderHand).deadwoodPips

        state = GinEngine.apply(state, GinMove.Knock(lowDeadwoodDiscard))
        state = GinEngine.apply(state, GinMove.SettleHand)

        val result = (state.phase as GinPhase.HandOver).result
        assertEquals(HandOutcome.KNOCK, result.outcome)
        assertEquals(GinPlayer.PLAYER, result.winner)
        assertEquals(knockerDeadwood, result.knockerDeadwood)
        assertEquals(defenderDeadwood, result.defenderDeadwood)
        assertEquals(defenderDeadwood - knockerDeadwood, result.pointsScored)
        assertEquals(defenderDeadwood - knockerDeadwood, state.matchScores.getValue(GinPlayer.PLAYER))
        assertEquals(1, state.boxes.getValue(GinPlayer.PLAYER))
    }

    @Test
    fun `an undercut scores the difference plus 25 to the defender`() {
        val knockerHand = lowDeadwoodKnockerHand()
        // Set of kings (0 pips) + a 4-card heart run (0 pips) + 3 low deadwood cards — well
        // under the knocker's true optimal deadwood (their hand also hides a second set —
        // 3s can join 3d/3c instead of the A-2-3 spade run — so it's lower than it first looks;
        // that's exactly the kind of overlap Melds.bestPartition exists to find), so this undercuts.
        val defenderHand =
            cards("K♥ K♦ K♣ 3♥ 4♥ 5♥ 6♥ A♦ 2♦ 2♣")
        var state = customState(dealer = GinPlayer.OPPONENT, playerHand = knockerHand, opponentHand = defenderHand)
        state = state.copy(phase = GinPhase.AwaitingDiscard(GinPlayer.PLAYER))
        val knockerDeadwood = Melds.bestPartition(knockerHand - lowDeadwoodDiscard).deadwoodPips
        val defenderDeadwoodBefore = Melds.bestPartition(defenderHand).deadwoodPips
        assertTrue(defenderDeadwoodBefore <= knockerDeadwood) // sanity: this hand really is an undercut

        state = GinEngine.apply(state, GinMove.Knock(lowDeadwoodDiscard))
        state = GinEngine.apply(state, GinMove.SettleHand)

        val result = (state.phase as GinPhase.HandOver).result
        assertEquals(HandOutcome.UNDERCUT, result.outcome)
        assertEquals(GinPlayer.OPPONENT, result.winner)
        assertEquals(knockerDeadwood - defenderDeadwoodBefore + 25, result.pointsScored)
        assertEquals(1, state.boxes.getValue(GinPlayer.OPPONENT))
    }

    @Test
    fun `gin scores the defender's full deadwood plus 25, with no layoff reduction`() {
        val ginHand =
            cards("A♠ 2♠ 3♠ 4♠ 7♥ 7♦ 7♣ 9♥ 9♦ 9♣ K♠")
        val defenderHand =
            listOf(
                card("K♥"), // 10
                card("Q♣"), // 10
                card("8♦"), // 8 -> could lay off onto the 7s set, but gin forbids it
                card("2♥"),
                card("3♣"),
                card("4♦"),
                card("5♥"),
                card("8♣"),
                card("2♦"),
                card("6♠"),
            )
        var state = customState(dealer = GinPlayer.OPPONENT, playerHand = ginHand, opponentHand = defenderHand)
        state = state.copy(phase = GinPhase.AwaitingDiscard(GinPlayer.PLAYER))
        val defenderRawDeadwood = Melds.bestPartition(defenderHand).deadwoodPips

        state = GinEngine.apply(state, GinMove.Knock(card("K♠")))
        state = GinEngine.apply(state, GinMove.SettleHand)

        val result = (state.phase as GinPhase.HandOver).result
        assertEquals(HandOutcome.GIN, result.outcome)
        assertEquals(GinPlayer.PLAYER, result.winner)
        assertEquals(0, result.knockerDeadwood)
        assertEquals(defenderRawDeadwood, result.defenderDeadwood)
        assertEquals(defenderRawDeadwood + 25, result.pointsScored)
    }

    @Test
    fun `a legal layoff extends the knocker's meld and reduces the defender's deadwood`() {
        val knockerHand = lowDeadwoodKnockerHand()
        // Defender holds the 4th seven — lays it off onto the knocker's set of sevens.
        val defenderHand =
            cards("7♠ K♥ Q♣ 8♦ 4♥ 5♣ 6♦ 9♠ 8♣ 2♦")
        var state = customState(dealer = GinPlayer.OPPONENT, playerHand = knockerHand, opponentHand = defenderHand)
        state = state.copy(phase = GinPhase.AwaitingDiscard(GinPlayer.PLAYER))
        state = GinEngine.apply(state, GinMove.Knock(lowDeadwoodDiscard))

        val setIndex =
            (state.phase as GinPhase.AwaitingLayoff).knockerMelds.indexOfFirst { it is Meld.Set }
        val sevenOfSpades = card("7♠")
        assertTrue(GinEngine.isLegal(state, GinMove.LayOff(sevenOfSpades, setIndex)))

        state = GinEngine.apply(state, GinMove.LayOff(sevenOfSpades, setIndex))
        val phase = state.phase as GinPhase.AwaitingLayoff
        assertEquals(4, phase.currentMelds[setIndex].cards.size)
        assertFalse(sevenOfSpades in state.handOf(GinPlayer.OPPONENT))

        val before = Melds.bestPartition(defenderHand).deadwoodPips
        state = GinEngine.apply(state, GinMove.SettleHand)
        val result = (state.phase as GinPhase.HandOver).result
        assertEquals(before - 7, result.defenderDeadwood)
        // Still on record after settling (for the table to show with the knocker's melds), without
        // joining the knocker's hand — which would count it as the knocker's deadwood.
        assertEquals(listOf(sevenOfSpades), state.laidOffOnto(GinPlayer.PLAYER))
        assertFalse(sevenOfSpades in state.handOf(GinPlayer.PLAYER))
    }

    // --- Match progression -------------------------------------------------------------------

    @Test
    fun `reaching the match target ends the match instead of the hand`() {
        val bigWinHand =
            cards("A♠ 2♠ 3♠ 4♠ 7♥ 7♦ 7♣ 9♥ 9♦ 9♣ K♠")
        val weakHand =
            cards("K♥ Q♣ K♦ Q♥ K♣ Q♦ J♥ 8♣ 2♦ 6♠")
        var state =
            customState(
                dealer = GinPlayer.OPPONENT,
                playerHand = bigWinHand,
                opponentHand = weakHand,
                matchScores = mapOf(GinPlayer.PLAYER to 80, GinPlayer.OPPONENT to 10),
            )
        state = state.copy(phase = GinPhase.AwaitingDiscard(GinPlayer.PLAYER))
        val defenderDeadwood = Melds.bestPartition(weakHand).deadwoodPips
        val rawFinalScore = 80 + defenderDeadwood + GinScoring.GIN_BONUS

        state = GinEngine.apply(state, GinMove.Knock(card("K♠")))
        state = GinEngine.apply(state, GinMove.SettleHand)

        assertTrue(GinEngine.isMatchOver(state))
        assertEquals(GinPlayer.PLAYER, (state.phase as GinPhase.MatchOver).winner)
        assertFalse(GinEngine.isLegal(state, GinMove.SettleHand))
        // The final score is the settled total (box bonus + game bonus), not the bare
        // race-to-100 number — GinScoring.settleMatch(rawFinalScore, winnerBoxes = 1, loserRawScore = 10).
        assertEquals(
            GinScoring.settleMatch(winnerRawScore = rawFinalScore, winnerBoxes = 1, loserRawScore = 10),
            state.matchScores.getValue(GinPlayer.PLAYER),
        )
        assertEquals(10, state.matchScores.getValue(GinPlayer.OPPONENT))
    }

    @Test
    fun `a shutout match doubles the winner's final settled score`() {
        val bigWinHand =
            cards("A♠ 2♠ 3♠ 4♠ 7♥ 7♦ 7♣ 9♥ 9♦ 9♣ K♠")
        val weakHand =
            cards("K♥ Q♣ K♦ Q♥ K♣ Q♦ J♥ 8♣ 2♦ 6♠")
        // Opponent's raw score stays exactly 0 — the shutout case.
        var state =
            customState(
                dealer = GinPlayer.OPPONENT,
                playerHand = bigWinHand,
                opponentHand = weakHand,
                matchScores = mapOf(GinPlayer.PLAYER to 80, GinPlayer.OPPONENT to 0),
            )
        state = state.copy(phase = GinPhase.AwaitingDiscard(GinPlayer.PLAYER))
        val defenderDeadwood = Melds.bestPartition(weakHand).deadwoodPips
        val rawFinalScore = 80 + defenderDeadwood + GinScoring.GIN_BONUS
        val unDoubled = rawFinalScore + 1 * GinScoring.BOX_BONUS + GinScoring.GAME_BONUS

        state = GinEngine.apply(state, GinMove.Knock(card("K♠")))
        state = GinEngine.apply(state, GinMove.SettleHand)

        assertEquals(GinPlayer.PLAYER, (state.phase as GinPhase.MatchOver).winner)
        assertEquals(unDoubled * 2, state.matchScores.getValue(GinPlayer.PLAYER))
    }

    @Test
    fun `a blocked hand is redealt by the same dealer, scores carried forward`() {
        var state = GinEngine.newMatch(seed = 1L)
        // Force a quick blocked hand by draining stock, to reach HandOver cheaply.
        state = GinEngine.apply(state, GinMove.PassUpcard)
        state = GinEngine.apply(state, GinMove.PassUpcard)
        while (state.stock.size > GinState.STOCK_RESERVE) {
            val toMove = (state.phase as GinPhase.AwaitingDraw).toMove
            state = GinEngine.apply(state, GinMove.DrawFromStock)
            state = GinEngine.apply(state, GinMove.Discard(state.handOf(toMove).first()))
        }

        val nextHand = GinEngine.dealNextHand(state, seed = 2L)

        assertEquals(2, nextHand.handNumber)
        assertEquals(GinPlayer.PLAYER, nextHand.dealer)
        assertEquals(state.matchScores, nextHand.matchScores)
        assertEquals(state.boxes, nextHand.boxes)
    }

    // --- Publicly known cards ----------------------------------------------------------------

    @Test
    fun `taking the upcard makes it publicly known to be in that hand`() {
        val state = GinEngine.newMatch(seed = 1L)
        val upcard = state.discardPile.single()

        val afterTake = GinEngine.apply(state, GinMove.TakeUpcard)

        assertEquals(listOf(upcard), afterTake.knownHeldBy(GinPlayer.OPPONENT))
        assertTrue(afterTake.knownHeldBy(GinPlayer.PLAYER).isEmpty())
    }

    @Test
    fun `a discard-pile draw is known until discarded again, a stock draw never is`() {
        var state = GinEngine.newMatch(seed = 1L)
        state = GinEngine.apply(state, GinMove.PassUpcard)
        state = GinEngine.apply(state, GinMove.PassUpcard)
        state = GinEngine.apply(state, GinMove.DrawFromStock)
        assertTrue(state.knownHeldBy(GinPlayer.OPPONENT).isEmpty())
        val fed = state.handOf(GinPlayer.OPPONENT).first()
        state = GinEngine.apply(state, GinMove.Discard(fed))

        state = GinEngine.apply(state, GinMove.DrawFromDiscard)
        assertEquals(listOf(fed), state.knownHeldBy(GinPlayer.PLAYER))
        state = GinEngine.apply(state, GinMove.Discard(state.handOf(GinPlayer.PLAYER).first { it != fed }))
        assertEquals(listOf(fed), state.knownHeldBy(GinPlayer.PLAYER), "discarding a different card keeps it known")

        state = GinEngine.apply(state, GinMove.DrawFromStock)
        state = GinEngine.apply(state, GinMove.Discard(state.handOf(GinPlayer.OPPONENT).first()))
        state = GinEngine.apply(state, GinMove.DrawFromStock)
        state = GinEngine.apply(state, GinMove.Discard(fed))
        assertTrue(state.knownHeldBy(GinPlayer.PLAYER).isEmpty(), "discarding it again makes it public in the pile instead")
    }

    @Test
    fun `a new deal starts with nothing known`() {
        val taken = GinEngine.apply(GinEngine.newMatch(seed = 1L), GinMove.TakeUpcard)
        val blocked = HandResult(HandOutcome.BLOCKED, winner = null, pointsScored = 0, knockerDeadwood = 0, defenderDeadwood = 0)
        val handOver = taken.copy(phase = GinPhase.HandOver(blocked))

        assertTrue(
            GinEngine
                .dealNextHand(handOver, seed = 2L)
                .knownHeld.values
                .all { it.isEmpty() },
        )
    }

    // --- Test helpers --------------------------------------------------------------------

    private fun customState(
        dealer: GinPlayer,
        playerHand: List<Card>,
        opponentHand: List<Card>,
        matchScores: Map<GinPlayer, Int> = GinPlayer.entries.associateWith { 0 },
    ): GinState {
        val used = (playerHand + opponentHand).toSet()
        val remaining =
            com.cardgames.core.domain.cards.Deck.standard52
                .filterNot { it in used }
        return GinState(
            seed = 0L,
            handNumber = 1,
            dealer = dealer,
            playerHand = playerHand,
            opponentHand = opponentHand,
            stock = remaining.drop(1),
            discardPile = listOf(remaining.first()),
            phase = GinPhase.AwaitingDraw(GinPlayer.PLAYER),
            matchScores = matchScores,
            boxes = GinPlayer.entries.associateWith { 0 },
        )
    }

    private fun handAwaitingDiscardFor(
        player: GinPlayer,
        hand: List<Card>,
    ): GinState = ginPosition(hand, seat = player)

    /**
     * After discarding [lowDeadwoodDiscard], the obvious reading is A-2-3s run + 7-7-7 set
     * (deadwood 3d,3c,Ac,2h = 9 pips) — but the true optimum is Set(7h7d7c) +
     * Set(3s3d3c) + deadwood(As,2s,Ac,2h) = 6 pips, since 3s can join 3d/3c as a second set
     * instead of finishing the spade run. Either way it's well under the knock threshold;
     * tests read the real value via [Melds.bestPartition] rather than hardcoding it, since
     * this hand exists specifically to exercise that overlap.
     */
    private fun lowDeadwoodKnockerHand(): List<Card> =
        listOf(
            card("A♠"),
            card("2♠"),
            card("3♠"),
            card("7♥"),
            card("7♦"),
            card("7♣"),
            card("A♣"),
            card("2♥"),
            card("3♦"),
            card("3♣"),
            lowDeadwoodDiscard,
        )

    private fun highDeadwoodHand(): List<Card> = cards("K♥ Q♣ J♦ 2♥ 3♣ 4♦ 5♥ 6♣ 8♦ 9♠ K♠")

    @Test
    fun `restarting a hand deals the same cards to the same seats again`() {
        val dealt = GinEngine.newMatch(seed = 7L)
        val played = GinEngine.apply(dealt, GinMove.PassUpcard)

        val again = GinEngine.redealHand(played)

        assertEquals(dealt.playerHand, again.playerHand)
        assertEquals(dealt.opponentHand, again.opponentHand)
        assertEquals(dealt.discardPile, again.discardPile)
        assertEquals(dealt.dealer, again.dealer)
        assertEquals(dealt.handNumber, again.handNumber)
        assertEquals(0, again.moveCount)
    }
}
