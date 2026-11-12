package com.cardgames.core.domain.ginrummy

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * [GinPhase.AwaitingLayoff] carries `List<Meld>` and is autosaved on every knock (not just a
 * match win), so `Meld` and its subclasses must stay `@Serializable` or [GinSnapshot.toJson]
 * throws for the entire lifetime of every hand from the knock onward.
 */
class GinSnapshotSerializationTest {
    @Test
    fun `an AwaitingLayoff state round-trips through JSON`() {
        val hand =
            listOf(
                Card(Suit.SPADES, Rank.ACE),
                Card(Suit.SPADES, Rank.TWO),
                Card(Suit.SPADES, Rank.THREE),
                Card(Suit.HEARTS, Rank.SEVEN),
                Card(Suit.DIAMONDS, Rank.SEVEN),
                Card(Suit.CLUBS, Rank.SEVEN),
                Card(Suit.HEARTS, Rank.TWO),
                Card(Suit.DIAMONDS, Rank.THREE),
                Card(Suit.CLUBS, Rank.FOUR),
                Card(Suit.CLUBS, Rank.ACE),
                Card(Suit.HEARTS, Rank.KING),
            )
        val discardCard = Card(Suit.HEARTS, Rank.KING)
        val custom =
            GinEngine.newMatch(seed = 1L).copy(
                playerHand = hand,
                phase = GinPhase.AwaitingDiscard(GinPlayer.PLAYER),
            )
        val afterKnock = GinEngine.apply(custom, GinMove.Knock(discardCard))

        val json = GinSnapshot(game = afterKnock).toJson()
        val restored = GinSnapshot.fromJson(json)

        assertEquals(afterKnock, restored?.game)
    }
}
