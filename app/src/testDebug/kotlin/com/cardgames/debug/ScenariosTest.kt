package com.cardgames.debug

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Deck
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinSnapshot
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.Melds
import com.cardgames.core.domain.solitaire.KlondikeAutoComplete
import com.cardgames.core.domain.solitaire.KlondikeEngine
import com.cardgames.core.domain.solitaire.KlondikeSnapshot
import com.cardgames.core.domain.solitaire.KlondikeState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The hand-built scenarios are real positions: whole decks, readable saves, the moments they promise. */
class ScenariosTest {
    private fun solitaire(id: String): KlondikeState = checkNotNull(KlondikeSnapshot.fromJson(byId(id).snapshotJson())).game

    private fun gin(id: String): GinState = checkNotNull(GinSnapshot.fromJson(byId(id).snapshotJson())).game

    private fun byId(id: String) = scenarios.single { it.id == id }

    @Test
    fun `every scenario holds the whole deck, once`() {
        scenarios.forEach { scenario ->
            val cards: List<Card> =
                when (scenario.game) {
                    GameId.KLONDIKE ->
                        solitaire(scenario.id).let { game ->
                            game.tableau.flatMap { it.cards } + game.stock + game.waste +
                                game.foundations.flatMap { (suit, count) ->
                                    Deck.standard52
                                        .filter { it.suit == suit }
                                        .sortedBy { it.rank }
                                        .take(count)
                                }
                        }
                    GameId.GIN_RUMMY -> gin(scenario.id).let { it.playerHand + it.opponentHand + it.stock + it.discardPile }
                }
            assertEquals(Deck.standard52.toSet(), cards.toSet(), scenario.id)
            assertEquals(Deck.standard52.size, cards.size, "${scenario.id} has a card twice")
        }
    }

    @Test
    fun `the Complete scenarios can be completed`() {
        listOf("solitaire-complete", "solitaire-complete-draw3").forEach { id ->
            val game = solitaire(id)
            val moves = checkNotNull(KlondikeAutoComplete.playOut(game)) { "$id can't be completed" }
            assertTrue(KlondikeEngine.isWon(moves.fold(game, KlondikeEngine::apply)), id)
        }
    }

    @Test
    fun `the winning scenario wins in one move`() {
        val game = solitaire("solitaire-win")
        assertTrue(KlondikeEngine.legalMoves(game).any { KlondikeEngine.isWon(KlondikeEngine.apply(game, it)) })
    }

    @Test
    fun `the gin scenarios are one discard from gin, and the knock scenario can knock`() {
        listOf("gin-gin", "gin-match-win").forEach { id ->
            val hand = gin(id).playerHand
            assertTrue(hand.any { Melds.bestPartition(hand - it).deadwoodPips == 0 }, "$id isn't one discard from gin")
        }
        val knock = gin("gin-knock").playerHand
        assertTrue(knock.any { Melds.bestPartition(knock - it).deadwoodPips <= KNOCK_LIMIT }, "gin-knock can't knock")
        listOf("gin-match-loss", "gin-rosa-gin").forEach { id ->
            assertEquals(0, Melds.bestPartition(gin(id).opponentHand).deadwoodPips, "$id: the opponent should hold gin")
        }
    }

    @Test
    fun `the knock scenarios start with Rosa just knocked, and end the way they say`() {
        listOf("gin-undercut", "gin-rosa-knocks").forEach { id ->
            val phase = gin(id).phase
            assertTrue(phase is GinPhase.AwaitingLayoff && phase.knocker == GinPlayer.OPPONENT && !phase.isGin, id)
        }
        val rosa = Melds.bestPartition(gin("gin-undercut").opponentHand).deadwoodPips
        // Layoffs only lower the defender's deadwood, so holding no more than Rosa before them is an undercut.
        assertTrue(Melds.bestPartition(gin("gin-undercut").playerHand).deadwoodPips <= rosa, "gin-undercut isn't an undercut")
        // You can lay off 4♥ (on A–3♥), 10♠ (on J–K♠) and 9♥ (on the 9s); even without them you hold more than Rosa.
        val layoffs = setOf(Card(Suit.HEARTS, Rank.FOUR), Card(Suit.SPADES, Rank.TEN), Card(Suit.HEARTS, Rank.NINE))
        val laidOff = gin("gin-rosa-knocks").playerHand - layoffs
        assertTrue(Melds.bestPartition(laidOff).deadwoodPips > rosa, "gin-rosa-knocks: you'd undercut Rosa")
    }

    private companion object {
        const val KNOCK_LIMIT = 10
    }
}
