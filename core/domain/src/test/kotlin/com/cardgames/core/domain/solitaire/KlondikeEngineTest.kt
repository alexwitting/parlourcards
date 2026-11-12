package com.cardgames.core.domain.solitaire

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Deck
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KlondikeEngineTest {
    private fun card(
        rank: Rank,
        suit: Suit,
    ) = Card(suit, rank)

    private fun pile(
        vararg cards: Card,
        faceDownCount: Int = 0,
    ) = TableauPile(cards.toList(), faceDownCount)

    private val emptyPile = TableauPile(emptyList(), 0)

    private fun state(
        tableau: List<TableauPile> = List(7) { emptyPile },
        foundations: Map<Suit, Int> = Suit.entries.associateWith { 0 },
        stock: List<Card> = emptyList(),
        waste: List<Card> = emptyList(),
        drawCount: Int = 1,
    ) = KlondikeState(
        seed = 0L,
        drawCount = drawCount,
        tableau = tableau,
        foundations = foundations,
        stock = stock,
        waste = waste,
    )

    @Nested
    inner class Deal {
        @Test
        fun `deals piles of 1 to 7 with all but the top card face down`() {
            val dealt = KlondikeEngine.deal(seed = 42L)

            dealt.tableau.forEachIndexed { index, pile ->
                assertEquals(index + 1, pile.cards.size)
                assertEquals(index, pile.faceDownCount)
            }
        }

        @Test
        fun `leaves 24 cards in stock, nothing in waste or foundations`() {
            val dealt = KlondikeEngine.deal(seed = 42L)

            assertEquals(24, dealt.stock.size)
            assertTrue(dealt.waste.isEmpty())
            assertTrue(dealt.foundations.values.all { it == 0 })
            assertEquals(0, dealt.moveCount)
        }

        @Test
        fun `uses all 52 unique cards`() {
            val dealt = KlondikeEngine.deal(seed = 42L)
            val all = dealt.tableau.flatMap { it.cards } + dealt.stock

            assertEquals(Deck.standard52.toSet(), all.toSet())
        }

        @Test
        fun `is deterministic per seed`() {
            assertEquals(KlondikeEngine.deal(seed = 7L), KlondikeEngine.deal(seed = 7L))
            assertNotEquals(KlondikeEngine.deal(seed = 7L), KlondikeEngine.deal(seed = 8L))
        }

        @Test
        fun `records seed and draw count in the state`() {
            val dealt = KlondikeEngine.deal(seed = 9L, drawCount = 3)

            assertEquals(9L, dealt.seed)
            assertEquals(3, dealt.drawCount)
        }

        @Test
        fun `rejects draw counts other than 1 and 3`() {
            assertThrows<IllegalArgumentException> { KlondikeEngine.deal(seed = 1L, drawCount = 2) }
        }
    }

    @Nested
    inner class StockAndWaste {
        private val a = card(Rank.TWO, Suit.CLUBS)
        private val b = card(Rank.FIVE, Suit.HEARTS)
        private val c = card(Rank.NINE, Suit.SPADES)

        @Test
        fun `draw-1 flips the top stock card onto the waste`() {
            val start = state(stock = listOf(a, b, c))

            val next = KlondikeEngine.apply(start, KlondikeMove.DealFromStock)

            assertEquals(listOf(a, b), next.stock)
            assertEquals(listOf(c), next.waste)
        }

        @Test
        fun `draw-3 flips three cards reversing their order`() {
            val start = state(stock = listOf(a, b, c), drawCount = 3)

            val next = KlondikeEngine.apply(start, KlondikeMove.DealFromStock)

            assertTrue(next.stock.isEmpty())
            assertEquals(listOf(c, b, a), next.waste)
        }

        @Test
        fun `draw-3 with fewer cards left flips what remains`() {
            val start = state(stock = listOf(a, b), drawCount = 3)

            val next = KlondikeEngine.apply(start, KlondikeMove.DealFromStock)

            assertTrue(next.stock.isEmpty())
            assertEquals(listOf(b, a), next.waste)
        }

        @Test
        fun `dealBatchRemaining tracks the just-dealt count, decremented by waste removals`() {
            // ace ends up on top of waste: dealFromStock reverses the taken group, so the *first*
            // card of the 3 taken (stock index size-3) lands last (topmost).
            val ace = card(Rank.ACE, Suit.CLUBS)
            val start = state(stock = listOf(ace, b, c), drawCount = 3)

            val dealt = KlondikeEngine.apply(start, KlondikeMove.DealFromStock)
            assertEquals(3, dealt.dealBatchRemaining)
            assertEquals(ace, dealt.waste.last())

            val played = KlondikeEngine.apply(dealt, KlondikeMove.WasteToFoundation)
            assertEquals(2, played.dealBatchRemaining)
        }

        @Test
        fun `a second deal resets dealBatchRemaining rather than accumulating`() {
            val ace = card(Rank.ACE, Suit.CLUBS)
            val x1 = card(Rank.THREE, Suit.DIAMONDS)
            val x2 = card(Rank.FOUR, Suit.DIAMONDS)
            val x3 = card(Rank.FIVE, Suit.DIAMONDS)
            val y2 = card(Rank.SIX, Suit.DIAMONDS)
            val y3 = card(Rank.SEVEN, Suit.DIAMONDS)
            val start = state(stock = listOf(x1, x2, x3, ace, y2, y3), drawCount = 3)

            val firstDeal = KlondikeEngine.apply(start, KlondikeMove.DealFromStock)
            val played = KlondikeEngine.apply(firstDeal, KlondikeMove.WasteToFoundation)
            assertEquals(2, played.dealBatchRemaining)

            val secondDeal = KlondikeEngine.apply(played, KlondikeMove.DealFromStock)
            assertEquals(3, secondDeal.dealBatchRemaining)
        }

        @Test
        fun `recycling resets dealBatchRemaining`() {
            val ace = card(Rank.ACE, Suit.CLUBS)
            var s = state(stock = listOf(ace), drawCount = 1)
            s = KlondikeEngine.apply(s, KlondikeMove.DealFromStock)
            assertEquals(1, s.dealBatchRemaining)

            s = KlondikeEngine.apply(s, KlondikeMove.RecycleWaste)

            assertEquals(0, s.dealBatchRemaining)
            assertTrue(s.waste.isEmpty())
        }

        @Test
        fun `dealing from an empty stock is illegal`() {
            assertFalse(KlondikeEngine.isLegal(state(), KlondikeMove.DealFromStock))
        }

        @Test
        fun `recycle is legal only when the stock is empty and the waste is not`() {
            assertTrue(KlondikeEngine.isLegal(state(waste = listOf(a)), KlondikeMove.RecycleWaste))
            assertFalse(KlondikeEngine.isLegal(state(stock = listOf(b), waste = listOf(a)), KlondikeMove.RecycleWaste))
            assertFalse(KlondikeEngine.isLegal(state(), KlondikeMove.RecycleWaste))
        }

        @Test
        fun `recycling then dealing repeats the same card order`() {
            var s = state(stock = listOf(a, b, c))
            val firstCycle = mutableListOf<Card>()
            repeat(3) {
                s = KlondikeEngine.apply(s, KlondikeMove.DealFromStock)
                firstCycle += s.waste.last()
            }

            s = KlondikeEngine.apply(s, KlondikeMove.RecycleWaste)
            assertTrue(s.waste.isEmpty())

            val secondCycle = mutableListOf<Card>()
            repeat(3) {
                s = KlondikeEngine.apply(s, KlondikeMove.DealFromStock)
                secondCycle += s.waste.last()
            }

            assertEquals(firstCycle, secondCycle)
        }
    }

    @Nested
    inner class TableauMoves {
        private val blackTen = card(Rank.TEN, Suit.SPADES)
        private val redNine = card(Rank.NINE, Suit.HEARTS)
        private val blackEight = card(Rank.EIGHT, Suit.CLUBS)
        private val redKing = card(Rank.KING, Suit.DIAMONDS)

        @Test
        fun `a red card goes on a black card one rank higher`() {
            val start =
                state(
                    tableau =
                        listOf(pile(blackTen), pile(redNine)) + List(5) { emptyPile },
                )
            val move = KlondikeMove.TableauToTableau(fromPile = 1, cardIndex = 0, toPile = 0)

            assertTrue(KlondikeEngine.isLegal(start, move))
            val next = KlondikeEngine.apply(start, move)
            assertEquals(listOf(blackTen, redNine), next.tableau[0].cards)
            assertTrue(next.tableau[1].isEmpty)
        }

        @Test
        fun `same-color and wrong-rank placements are illegal`() {
            val start =
                state(
                    tableau =
                        listOf(
                            pile(blackTen),
                            pile(blackEight),
                            pile(card(Rank.NINE, Suit.CLUBS)),
                        ) + List(4) { emptyPile },
                )

            // 8 on 10: wrong rank
            assertFalse(KlondikeEngine.isLegal(start, KlondikeMove.TableauToTableau(1, 0, 0)))
            // black 9 on black 10: same color
            assertFalse(KlondikeEngine.isLegal(start, KlondikeMove.TableauToTableau(2, 0, 0)))
        }

        @Test
        fun `only a king may go to an empty pile`() {
            val start = state(tableau = listOf(pile(redKing), pile(redNine)) + List(5) { emptyPile })

            assertTrue(KlondikeEngine.isLegal(start, KlondikeMove.TableauToTableau(0, 0, 2)))
            assertFalse(KlondikeEngine.isLegal(start, KlondikeMove.TableauToTableau(1, 0, 2)))
        }

        @Test
        fun `a run moves as one unit preserving order`() {
            val start =
                state(
                    tableau =
                        listOf(
                            pile(blackTen, redNine, blackEight),
                            pile(card(Rank.JACK, Suit.HEARTS)),
                        ) + List(5) { emptyPile },
                )
            val move = KlondikeMove.TableauToTableau(fromPile = 0, cardIndex = 0, toPile = 1)

            val next = KlondikeEngine.apply(start, move)

            assertEquals(
                listOf(card(Rank.JACK, Suit.HEARTS), blackTen, redNine, blackEight),
                next.tableau[1].cards,
            )
        }

        @Test
        fun `a partial run may split off, leaving the rest behind`() {
            // Pile 0 holds 10♠ 9♥ 8♣; pile 1 is a lone 9♦. Only the 8♣ moves (black on red 9).
            val redNineDiamonds = card(Rank.NINE, Suit.DIAMONDS)
            val start =
                state(
                    tableau =
                        listOf(
                            pile(blackTen, redNine, blackEight),
                            pile(redNineDiamonds),
                        ) + List(5) { emptyPile },
                )
            val move = KlondikeMove.TableauToTableau(fromPile = 0, cardIndex = 2, toPile = 1)

            assertTrue(KlondikeEngine.isLegal(start, move))
            val next = KlondikeEngine.apply(start, move)
            assertEquals(listOf(blackTen, redNine), next.tableau[0].cards)
            assertEquals(listOf(redNineDiamonds, blackEight), next.tableau[1].cards)
        }

        @Test
        fun `face-down cards cannot be moved`() {
            val start =
                state(
                    tableau =
                        listOf(
                            TableauPile(listOf(redNine, blackEight), faceDownCount = 1),
                            pile(blackTen),
                        ) + List(5) { emptyPile },
                )

            assertFalse(KlondikeEngine.isLegal(start, KlondikeMove.TableauToTableau(0, 0, 1)))
        }

        @Test
        fun `moving away the face-up cards flips the exposed card`() {
            val start =
                state(
                    tableau =
                        listOf(
                            TableauPile(listOf(blackEight, redNine), faceDownCount = 1),
                            pile(blackTen),
                        ) + List(5) { emptyPile },
                )

            val next = KlondikeEngine.apply(start, KlondikeMove.TableauToTableau(0, 1, 1))

            assertEquals(listOf(blackEight), next.tableau[0].cards)
            assertEquals(0, next.tableau[0].faceDownCount)
        }

        @Test
        fun `moving a pile onto itself is illegal`() {
            val start = state(tableau = listOf(pile(redKing)) + List(6) { emptyPile })

            assertFalse(KlondikeEngine.isLegal(start, KlondikeMove.TableauToTableau(0, 0, 0)))
        }

        @Test
        fun `waste card can be placed on a matching tableau pile`() {
            val start = state(tableau = listOf(pile(blackTen)) + List(6) { emptyPile }, waste = listOf(redNine))

            val next = KlondikeEngine.apply(start, KlondikeMove.WasteToTableau(toPile = 0))

            assertEquals(listOf(blackTen, redNine), next.tableau[0].cards)
            assertTrue(next.waste.isEmpty())
        }
    }

    @Nested
    inner class FoundationMoves {
        private val aceHearts = card(Rank.ACE, Suit.HEARTS)
        private val twoHearts = card(Rank.TWO, Suit.HEARTS)

        @Test
        fun `an ace starts its foundation`() {
            val start = state(tableau = listOf(pile(aceHearts)) + List(6) { emptyPile })

            val next = KlondikeEngine.apply(start, KlondikeMove.TableauToFoundation(fromPile = 0))

            assertEquals(1, next.foundationCount(Suit.HEARTS))
            assertTrue(next.tableau[0].isEmpty)
        }

        @Test
        fun `foundations build up within a suit only`() {
            val base = state(foundations = foundationsWith(Suit.HEARTS to 1), waste = listOf(twoHearts))
            assertTrue(KlondikeEngine.isLegal(base, KlondikeMove.WasteToFoundation))

            val wrongSuit = state(foundations = foundationsWith(Suit.HEARTS to 1), waste = listOf(card(Rank.TWO, Suit.CLUBS)))
            assertFalse(KlondikeEngine.isLegal(wrongSuit, KlondikeMove.WasteToFoundation))

            val skippedRank = state(foundations = foundationsWith(Suit.HEARTS to 1), waste = listOf(card(Rank.THREE, Suit.HEARTS)))
            assertFalse(KlondikeEngine.isLegal(skippedRank, KlondikeMove.WasteToFoundation))
        }

        @Test
        fun `waste to foundation removes the waste top`() {
            val start = state(waste = listOf(twoHearts, aceHearts))

            val next = KlondikeEngine.apply(start, KlondikeMove.WasteToFoundation)

            assertEquals(1, next.foundationCount(Suit.HEARTS))
            assertEquals(listOf(twoHearts), next.waste)
        }

        @Test
        fun `a foundation card can come back to the tableau`() {
            val blackFour = card(Rank.FOUR, Suit.SPADES)
            val start =
                state(
                    foundations = foundationsWith(Suit.HEARTS to 3),
                    tableau = listOf(pile(blackFour)) + List(6) { emptyPile },
                )
            val move = KlondikeMove.FoundationToTableau(suit = Suit.HEARTS, toPile = 0)

            assertTrue(KlondikeEngine.isLegal(start, move))
            val next = KlondikeEngine.apply(start, move)
            assertEquals(2, next.foundationCount(Suit.HEARTS))
            assertEquals(listOf(blackFour, card(Rank.THREE, Suit.HEARTS)), next.tableau[0].cards)
        }

        @Test
        fun `every applied move increments the move counter`() {
            val start = state(waste = listOf(aceHearts))

            val next = KlondikeEngine.apply(start, KlondikeMove.WasteToFoundation)

            assertEquals(1, next.moveCount)
        }

        private fun foundationsWith(vararg counts: Pair<Suit, Int>): Map<Suit, Int> = Suit.entries.associateWith { 0 } + counts
    }

    @Nested
    inner class Win {
        @Test
        fun `the game is won when all 52 cards are on foundations`() {
            val won = state(foundations = Suit.entries.associateWith { 13 })

            assertTrue(KlondikeEngine.isWon(won))
            assertFalse(KlondikeEngine.isWon(state()))
        }
    }

    @Nested
    inner class LegalMoveEnumeration {
        @Test
        fun `enumerates exactly the legal moves in a crafted position`() {
            val start =
                state(
                    tableau =
                        listOf(
                            pile(card(Rank.TEN, Suit.SPADES)),
                            pile(card(Rank.NINE, Suit.HEARTS)),
                        ) + List(5) { emptyPile },
                    stock = listOf(card(Rank.THREE, Suit.CLUBS)),
                    waste = listOf(card(Rank.ACE, Suit.DIAMONDS)),
                )

            val moves = KlondikeEngine.legalMoves(start)

            assertTrue(KlondikeMove.DealFromStock in moves)
            assertTrue(KlondikeMove.WasteToFoundation in moves)
            assertTrue(KlondikeMove.TableauToTableau(1, 0, 0) in moves)
            assertFalse(KlondikeMove.RecycleWaste in moves)
            assertFalse(moves.any { it is KlondikeMove.WasteToTableau })
            assertFalse(moves.any { it is KlondikeMove.FoundationToTableau })
        }

        @Test
        fun `every enumerated move is applicable`() {
            var s = KlondikeEngine.deal(seed = 123L, drawCount = 3)
            repeat(20) {
                val moves = KlondikeEngine.legalMoves(s)
                moves.forEach { assertTrue(KlondikeEngine.isLegal(s, it)) }
                s = KlondikeEngine.apply(s, moves.first())
            }
            assertEquals(20, s.moveCount)
        }

        @Test
        fun `applying an illegal move throws`() {
            assertThrows<IllegalArgumentException> {
                KlondikeEngine.apply(state(), KlondikeMove.DealFromStock)
            }
        }
    }
}
