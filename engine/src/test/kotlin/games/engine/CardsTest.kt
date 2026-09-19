package games.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Как карта называется вслух.
 *
 * Именительный — название карты, винительный — она же дополнением:
 * «получаешь семёрку крести». Обе формы перечислены руками, и опечатка в
 * одной из девяти не видна на глаз — а за столом её слышно сразу.
 */
class CardsTest {

    @Test
    fun `именительный — достоинство и масть`() {
        assertEquals("семёрка крести", Card(Rank.SEVEN, Suit.CLUBS).spoken())
        assertEquals("туз пик", Card(Rank.ACE, Suit.SPADES).spoken())
    }

    @Test
    fun `винительный меняет одно достоинство`() {
        assertEquals("семёрку крести", Card(Rank.SEVEN, Suit.CLUBS).spokenAccusative())
        assertEquals("туза пик", Card(Rank.ACE, Suit.SPADES).spokenAccusative())
    }

    /** Все девять форм разом: у каждой карты колоды винительный свой. */
    @Test
    fun `винительный есть у каждого достоинства`() {
        val forms = Rank.entries.map { Card(it, Suit.HEARTS).spokenAccusative() }
        assertEquals(
            listOf(
                "шестёрку червей",
                "семёрку червей",
                "восьмёрку червей",
                "девятку червей",
                "десятку червей",
                "валета червей",
                "даму червей",
                "короля червей",
                "туза червей",
            ),
            forms,
        )
    }
}
