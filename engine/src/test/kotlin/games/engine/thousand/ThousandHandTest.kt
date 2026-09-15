package games.engine.thousand

import games.engine.Card
import games.engine.HandOrder
import games.engine.Rank
import games.engine.Suit
import kotlin.test.Test
import kotlin.test.assertEquals

class ThousandHandTest {

    private fun c(rank: Rank, suit: Suit) = Card(rank, suit)

    @Test
    fun `десятка идёт после короля — старшинство здесь своё`() {
        val hand = listOf(c(Rank.TEN, Suit.SPADES), c(Rank.KING, Suit.SPADES))
        assertEquals(
            listOf(c(Rank.KING, Suit.SPADES), c(Rank.TEN, Suit.SPADES)),
            orderHand(hand, HandOrder.BY_RANK, trump = null),
        )
    }

    @Test
    fun `козырь в конце при сортировке по масти`() {
        val hand = listOf(
            c(Rank.ACE, Suit.HEARTS), c(Rank.NINE, Suit.SPADES), c(Rank.TEN, Suit.HEARTS),
        )
        assertEquals(
            listOf(c(Rank.NINE, Suit.SPADES), c(Rank.TEN, Suit.HEARTS), c(Rank.ACE, Suit.HEARTS)),
            orderHand(hand, HandOrder.BY_SUIT, trump = Suit.HEARTS),
        )
    }

    @Test
    fun `козырь вперёд ставит козыри первыми`() {
        val hand = listOf(c(Rank.ACE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS))
        assertEquals(
            listOf(c(Rank.NINE, Suit.HEARTS), c(Rank.ACE, Suit.SPADES)),
            orderHand(hand, HandOrder.TRUMPS_FIRST, trump = Suit.HEARTS),
        )
    }

    @Test
    fun `пока козырь не объявлен, рука всё равно упорядочена`() {
        val hand = listOf(c(Rank.ACE, Suit.CLUBS), c(Rank.NINE, Suit.SPADES))
        assertEquals(
            listOf(c(Rank.NINE, Suit.SPADES), c(Rank.ACE, Suit.CLUBS)),
            orderHand(hand, HandOrder.BY_SUIT, trump = null),
        )
    }
}
