package games.cardgames.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Как звать сидящих за столом «Тысячи».
 *
 * Имена мест — то, на чём стоит вся речь за столом на троих: по ним
 * называются ход, взятка, снос, болт и счёт. Ошибка здесь не видна на
 * экране, а слышна: два места под одним именем — это «ходит Бот, ходит Бот»,
 * и кто из них кто, не различить (Катерина, 19.09).
 */
class TableNamesTest {

    /** За столом на двоих соперник один, и третьего имени никто не спрашивает. */
    @Test
    fun `на двоих за столом только одно имя соперника`() {
        assertEquals(
            listOf("ты", "Петя"),
            seatTitles(Settings(botName = "Петя", botNameSecond = "Вася"), 2),
        )
    }

    /** На троих каждое место зовётся своим именем. */
    @Test
    fun `третье место зовётся вторым именем`() {
        assertEquals(
            listOf("ты", "Петя", "Вася"),
            seatTitles(Settings(botName = "Петя", botNameSecond = "Вася"), 3),
        )
    }

    /** Без имён — имена по умолчанию, и они разные: одинаковые слились бы. */
    @Test
    fun `без имени места зовутся Бот и Второй бот`() {
        assertEquals(
            listOf("ты", "Бот", "Второй бот"),
            seatTitles(Settings(), 3),
        )
    }

    /** Пробелы — это пустое поле, а не имя из пробелов. */
    @Test
    fun `одни пробелы вместо имени считаются пустым полем`() {
        assertEquals(
            listOf("ты", "Бот", "Второй бот"),
            seatTitles(Settings(botName = "   ", botNameSecond = "  "), 3),
        )
    }

    /**
     * В хранилище могло попасть что угодно, а стол держит только двое или
     * трое: чужая правка — не повод упасть за столом.
     */
    @Test
    fun `число мест за столом зажато двумя и тремя`() {
        assertEquals(2, seatsAtTable(Settings(thousandSeats = 0)))
        assertEquals(2, seatsAtTable(Settings(thousandSeats = 1)))
        assertEquals(3, seatsAtTable(Settings(thousandSeats = 4)))
        assertEquals(3, seatsAtTable(Settings(thousandSeats = 7)))
    }
}
