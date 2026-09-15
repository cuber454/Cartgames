package games.engine

import games.engine.Rank.ACE
import games.engine.Rank.KING
import games.engine.Rank.SEVEN
import games.engine.Rank.SIX
import games.engine.Suit.CLUBS
import games.engine.Suit.DIAMONDS
import games.engine.Suit.HEARTS
import games.engine.Suit.SPADES
import kotlin.test.Test
import kotlin.test.assertEquals

class HandOrderTest {

    /** Козырь — черви. В руке нарочно перемешано. */
    private val hand = listOf(
        Card(ACE, HEARTS),
        Card(SIX, SPADES),
        Card(KING, CLUBS),
        Card(SEVEN, SPADES),
        Card(SIX, HEARTS),
    )

    @Test
    fun `по масти козыри уходят в конец`() {
        val sorted = HandOrder.BY_SUIT.sort(hand, HEARTS)
        assertEquals(
            listOf(
                Card(SIX, SPADES), Card(SEVEN, SPADES),
                Card(KING, CLUBS),
                Card(SIX, HEARTS), Card(ACE, HEARTS),
            ),
            sorted,
        )
    }

    @Test
    fun `по масти внутри масти возрастание`() {
        val sorted = HandOrder.BY_SUIT.sort(hand, HEARTS)
        val spades = sorted.filter { it.suit == SPADES }
        assertEquals(listOf(SIX, SEVEN), spades.map { it.rank })
    }

    @Test
    fun `по достоинству масти не важны`() {
        val sorted = HandOrder.BY_RANK.sort(hand, HEARTS)
        assertEquals(
            listOf(SIX, SIX, SEVEN, KING, ACE),
            sorted.map { it.rank },
        )
    }

    @Test
    fun `козыри вперёд ставят козыри в начало`() {
        val sorted = HandOrder.TRUMPS_FIRST.sort(hand, HEARTS)
        assertEquals(listOf(SIX, ACE), sorted.take(2).map { it.rank })
        assertEquals(HEARTS, sorted.first().suit)
    }

    @Test
    fun `порядок переключается по кругу`() {
        assertEquals(HandOrder.BY_RANK, HandOrder.BY_SUIT.next())
        assertEquals(HandOrder.TRUMPS_FIRST, HandOrder.BY_RANK.next())
        assertEquals(HandOrder.BY_SUIT, HandOrder.TRUMPS_FIRST.next())
    }

    @Test
    fun `сортировка не теряет и не добавляет карт`() {
        for (order in HandOrder.entries) {
            val sorted = order.sort(hand, DIAMONDS)
            assertEquals(hand.size, sorted.size)
            assertEquals(hand.toSet(), sorted.toSet())
        }
    }
}
