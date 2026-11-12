package com.cardgames.core.ui.rules

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.game.GameId

/** One piece of a game's rules page, in reading order. */
sealed interface RulesBlock {
    /** A section title ("The goal"). */
    data class Heading(
        val text: String,
    ) : RulesBlock

    data class Paragraph(
        val text: String,
    ) : RulesBlock

    /** Example cards, in groups side by side (a run and a set). */
    data class Cards(
        val groups: List<List<Card>>,
    ) : RulesBlock

    /** A two-column table: what, and what it's worth. */
    data class Table(
        val rows: List<Pair<String, String>>,
    ) : RulesBlock
}

/** Each game's rules page — what How to play shows on that game's tab. */
val GameId.rules: List<RulesBlock>
    get() =
        when (this) {
            GameId.GIN_RUMMY -> GinRules
            GameId.KLONDIKE -> SolitaireRules
        }

private val GinRules =
    listOf(
        RulesBlock.Heading("The goal"),
        RulesBlock.Paragraph(
            "Form melds with your ten cards, then knock to end the hand with as little leftover deadwood as possible. " +
                "First to the match's target — 100 points unless you chose another — wins.",
        ),
        RulesBlock.Heading("Melds"),
        RulesBlock.Cards(
            listOf(
                listOf(Card(Suit.DIAMONDS, Rank.FOUR), Card(Suit.DIAMONDS, Rank.FIVE), Card(Suit.DIAMONDS, Rank.SIX)),
                listOf(Card(Suit.SPADES, Rank.NINE), Card(Suit.DIAMONDS, Rank.NINE), Card(Suit.CLUBS, Rank.NINE)),
            ),
        ),
        RulesBlock.Paragraph(
            "Three or more cards in a row of one suit, or three or four of the same rank. Each card belongs to one meld at a time.",
        ),
        RulesBlock.Heading("Your turn"),
        RulesBlock.Paragraph(
            "Draw one card from the stock or the discard pile, then discard one. You can’t throw back a card you just took from the pile.",
        ),
        RulesBlock.Heading("Scoring"),
        RulesBlock.Table(
            listOf(
                "Card values" to "A = 1 · 2–10 · J Q K = 10",
                "Knock (deadwood 10 or less)" to "Difference in deadwood",
                "Gin (no deadwood)" to "+25 plus their deadwood",
                "Undercut" to "+25 plus the difference",
            ),
        ),
    )

private val SolitaireRules =
    listOf(
        RulesBlock.Heading("The goal"),
        RulesBlock.Paragraph("Move all fifty-two cards to the four foundations, building each suit up from ace to king."),
        RulesBlock.Heading("The columns"),
        RulesBlock.Cards(
            listOf(listOf(Card(Suit.SPADES, Rank.NINE), Card(Suit.HEARTS, Rank.EIGHT), Card(Suit.CLUBS, Rank.SEVEN))),
        ),
        RulesBlock.Paragraph(
            "Build down in alternating colours. Move a face-up run together, and turn over a face-down card once it’s uncovered. " +
                "Only a king, or a run headed by one, fills an empty column.",
        ),
        RulesBlock.Heading("The stock"),
        RulesBlock.Paragraph(
            "Tap the stock to turn over cards onto the waste. When it runs out, tap it again to turn the waste back over.",
        ),
        RulesBlock.Heading("Playing a card"),
        RulesBlock.Paragraph(
            "Tap a card to send it to its best place, or hold it to pick it up and tap where it goes. Dragging works too.",
        ),
        RulesBlock.Heading("Draw modes"),
        RulesBlock.Table(
            listOf(
                "Draw 1" to "One card at a time",
                "Draw 3" to "Three at a time; only the top one plays",
            ),
        ),
    )
