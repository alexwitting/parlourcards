package com.cardgames.debug

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Deck
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinMatchRules
import com.cardgames.core.domain.ginrummy.GinMove
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinSnapshot
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.solitaire.KlondikeSnapshot
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.domain.solitaire.TableauPile

/**
 * A game set up just before a moment worth checking — Complete, a win, a gin, a match result —
 * saved as that game's save slot, so Continue opens on it. Debug builds only (see
 * [ScenarioActivity]); [id] is what `adb shell am start … --es scenario <id>` takes.
 */
internal class Scenario(
    val id: String,
    val title: String,
    val description: String,
    val game: GameId,
    val snapshotJson: () -> String,
)

internal val scenarios: List<Scenario> =
    listOf(
        Scenario(
            id = "solitaire-complete",
            title = "Solitaire: ready to Complete",
            description =
                "Draw 1. Ace to 6 of each suit home, the rest face up in four columns, one card each " +
                    "in the stock and the waste. Tap Complete.",
            game = GameId.KLONDIKE,
        ) {
            klondike(
                drawCount = 1,
                foundationsUpTo = 6,
                columns =
                    listOf(
                        "K♠ Q♥ J♠ 10♥ 9♠ 8♥ 7♠",
                        "K♥ Q♠ J♥ 10♠ 9♥ 8♠ 7♥",
                        "K♣ Q♦ J♣ 10♦ 9♣ 8♦",
                        "K♦ Q♣ J♦ 10♣ 9♦ 8♣",
                    ),
                stock = "7♣",
                waste = "7♦",
            )
        },
        Scenario(
            id = "solitaire-complete-draw3",
            title = "Solitaire: ready to Complete, draw 3",
            description = "Draw 3, with eight cards still in the draw pile. Tap Complete.",
            game = GameId.KLONDIKE,
        ) {
            klondike(
                drawCount = 3,
                foundationsUpTo = 7,
                columns = listOf("K♠ Q♥ J♣ 10♦", "K♥ Q♣ J♦ 10♠", "K♣ Q♦ J♠ 10♥", "K♦ Q♠ J♥ 10♣"),
                stock = "9♣ 8♥ 9♦ 8♠ 9♥ 8♣",
                waste = "8♦ 9♠",
            )
        },
        Scenario(
            id = "solitaire-win",
            title = "Solitaire: one move from winning",
            description = "Everything home but the K♠. Tap it for the Solved screen.",
            game = GameId.KLONDIKE,
        ) {
            klondike(
                drawCount = 3,
                foundationsUpTo = 13,
                spadesUpTo = 12,
                columns = listOf("K♠"),
            )
        },
        Scenario(
            id = "gin-gin",
            title = "Gin: gin on your next discard",
            description = "Throw the K♠ (or tap Go Gin) for the GIN! screen.",
            game = GameId.GIN_RUMMY,
        ) {
            gin(player = "A♠ 2♠ 3♠ 4♠ 7♥ 7♦ 7♣ 9♥ 9♦ 9♣ K♠")
        },
        Scenario(
            id = "gin-knock",
            title = "Gin: knock to win the hand",
            description = "Knock throwing the K♥ (2 left in deadwood); the opponent lays off three cards and still loses.",
            game = GameId.GIN_RUMMY,
        ) {
            gin(player = "A♣ 2♣ 3♣ 5♥ 5♦ 5♠ 9♠ 10♠ J♠ 2♦ K♥", opponent = "K♦ Q♣ J♥ 8♦ 7♠ 6♥ 4♣ 5♣ 10♦ Q♠")
        },
        Scenario(
            id = "gin-undercut",
            title = "Gin: you undercut Rosa",
            description = "Rosa has just knocked with 8 deadwood; you hold 2. Watch the undercut and its result.",
            game = GameId.GIN_RUMMY,
        ) {
            gin(
                player = "5♣ 6♣ 7♣ 4♦ 4♥ 4♠ 10♥ J♥ Q♥ 2♣",
                opponent = ROSA_KNOCKING,
                opponentKnocks = "K♦",
            )
        },
        Scenario(
            id = "gin-rosa-knocks",
            title = "Gin: Rosa knocks and wins",
            description = "Rosa has just knocked with 8 deadwood. You lay off 4♥, 10♠ and 9♥ on her melds and still lose the hand.",
            game = GameId.GIN_RUMMY,
        ) {
            gin(
                player = "5♣ 6♣ 7♣ 4♥ 10♠ 9♥ K♣ Q♦ J♣ 2♦",
                opponent = ROSA_KNOCKING,
                opponentKnocks = "K♦",
            )
        },
        Scenario(
            id = "gin-rosa-gin",
            title = "Gin: Rosa goes gin",
            description = "20–0, Rosa holding gin. Throw any card and watch her go out mid-match.",
            game = GameId.GIN_RUMMY,
        ) {
            gin(
                player = "K♠ Q♦ J♣ 9♠ 8♥ 7♣ 7♦ 5♥ 3♠ 2♦ K♣",
                opponent = "A♥ 2♥ 3♥ 4♥ 6♣ 6♦ 6♠ 10♣ 10♦ 10♥",
                playerScore = 20,
            )
        },
        Scenario(
            id = "gin-match-win",
            title = "Gin: gin to win the match",
            description = "90–40 in a match to 100: throw the K♠ for gin and the match result.",
            game = GameId.GIN_RUMMY,
        ) {
            gin(player = "A♠ 2♠ 3♠ 4♠ 7♥ 7♦ 7♣ 9♥ 9♦ 9♣ K♠", playerScore = 90, opponentScore = 40)
        },
        Scenario(
            id = "gin-match-loss",
            title = "Gin: the opponent wins the match",
            description = "40–95 in a match to 100, the opponent holding gin. Throw any card and watch them go out.",
            game = GameId.GIN_RUMMY,
        ) {
            gin(
                player = "K♠ Q♦ J♣ 9♠ 8♥ 7♣ 7♦ 5♥ 3♠ 2♦ K♣",
                opponent = "A♥ 2♥ 3♥ 4♥ 6♣ 6♦ 6♠ 10♣ 10♦ 10♥",
                playerScore = 40,
                opponentScore = 95,
            )
        },
        Scenario(
            id = "gin-blocked",
            title = "Gin: a blocked hand",
            description = "Three cards left in the stock. Draw and discard: the hand is blocked, with a redeal.",
            game = GameId.GIN_RUMMY,
        ) {
            gin(player = "K♠ Q♦ J♣ 9♠ 8♥ 7♣ 7♦ 5♥ 3♠ 2♦", stockLeft = 3)
        },
    )

/** Every suit home up to [foundationsUpTo] (spades to [spadesUpTo]); the rest in [columns], face up, and the draw pile. */
private fun klondike(
    drawCount: Int,
    foundationsUpTo: Int,
    columns: List<String>,
    spadesUpTo: Int = foundationsUpTo,
    stock: String = "",
    waste: String = "",
): String {
    val tableau = columns.map { TableauPile(cards(it), faceDownCount = 0) }
    val game =
        KlondikeState(
            seed = SEED,
            drawCount = drawCount,
            tableau = tableau + List(KlondikeState.TABLEAU_PILE_COUNT - tableau.size) { TableauPile(emptyList(), 0) },
            foundations = Suit.entries.associateWith { if (it == Suit.SPADES) spadesUpTo else foundationsUpTo },
            stock = cards(stock),
            waste = cards(waste),
            moveCount = MOVES,
        )
    return KlondikeSnapshot(game = game, elapsedSeconds = ELAPSED_SECONDS).toJson()
}

/**
 * The player to discard from [player] (11 cards) — or to draw, holding 10; the opponent holds
 * [opponent] (default: the first ten cards left); one upcard on the pile, the rest the stock — or
 * only [stockLeft] of it, the others under the upcard. With [opponentKnocks], the opponent holds 11
 * and has just knocked throwing that card (the engine makes the knock): loading it plays out the
 * layoffs and the result.
 */
private fun gin(
    player: String,
    opponent: String? = null,
    playerScore: Int = 0,
    opponentScore: Int = 0,
    stockLeft: Int? = null,
    opponentKnocks: String? = null,
): String {
    val playerHand = cards(player)
    var left = Deck.standard52 - playerHand.toSet()
    val opponentHand = opponent?.let(::cards) ?: left.take(GinState.HAND_SIZE)
    left = left - opponentHand.toSet()
    val stock = stockLeft?.let { left.take(it) } ?: left.drop(1)
    val pile = left - stock.toSet()
    val game =
        GinState(
            seed = SEED,
            handNumber = 2,
            dealer = GinPlayer.OPPONENT,
            playerHand = playerHand,
            opponentHand = opponentHand,
            stock = stock,
            discardPile = pile,
            phase =
                when {
                    opponentKnocks != null -> GinPhase.AwaitingDiscard(GinPlayer.OPPONENT)
                    playerHand.size > GinState.HAND_SIZE -> GinPhase.AwaitingDiscard(GinPlayer.PLAYER)
                    else -> GinPhase.AwaitingDraw(GinPlayer.PLAYER)
                },
            matchScores = mapOf(GinPlayer.PLAYER to playerScore, GinPlayer.OPPONENT to opponentScore),
            boxes = GinPlayer.entries.associateWith { 0 },
            moveCount = MOVES,
            rules = GinMatchRules(target = MATCH_TARGET),
        )
    val played = opponentKnocks?.let { GinEngine.apply(game, GinMove.Knock(card(it))) } ?: game
    return GinSnapshot(game = played, opponent = GinDifficulty.MEDIUM).toJson()
}

private fun cards(text: String): List<Card> = text.split(' ').filter { it.isNotBlank() }.map(::card)

/** "10♥", "K♠": a rank, then a suit symbol. */
private fun card(text: String): Card {
    val suit = SUITS.getValue(text.last())
    val rankText = text.dropLast(1)
    val rank =
        when (rankText) {
            "A" -> Rank.ACE
            "J" -> Rank.JACK
            "Q" -> Rank.QUEEN
            "K" -> Rank.KING
            else -> Rank.entries.first { it.value == rankText.toInt() }
        }
    return Card(suit, rank)
}

/** Rosa's 11 for the knock scenarios: A–3♥, the 9s, J–K♠, then 8♦ (deadwood) and K♦ (the throw). */
private const val ROSA_KNOCKING = "A♥ 2♥ 3♥ 9♣ 9♦ 9♠ J♠ Q♠ K♠ 8♦ K♦"

private val SUITS = mapOf('♣' to Suit.CLUBS, '♦' to Suit.DIAMONDS, '♥' to Suit.HEARTS, '♠' to Suit.SPADES)
private const val SEED = 7L
private const val MOVES = 40
private const val ELAPSED_SECONDS = 300
private const val MATCH_TARGET = 100
