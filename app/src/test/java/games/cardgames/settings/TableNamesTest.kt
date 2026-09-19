package games.cardgames.settings

import games.cardgames.GAME_DURAK
import games.cardgames.GAME_KOZEL
import games.cardgames.GAME_THOUSAND
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

    /**
     * За столом на двоих соперник один, и третьего имени никто не спрашивает:
     * имя второго бота к этому столу не относится. Имя первого при этом
     * остаётся — им зовут человека фразы приложения, а не сам бот себя.
     */
    @Test
    fun `на двоих за столом только одно имя соперника`() {
        assertEquals(
            listOf("ты", "Петя"),
            seatTitles(Settings(botNameThousand = "Петя", botNameThousandSecond = "Вася"), 2),
        )
    }

    /** Без имени на двоих место всё равно названо: «ты» и «Бот». */
    @Test
    fun `на двоих без имени соперник зовётся Ботом`() {
        assertEquals(listOf("ты", "Бот"), seatTitles(Settings(), 2))
    }

    /** На троих каждое место зовётся своим именем. */
    @Test
    fun `третье место зовётся вторым именем`() {
        assertEquals(
            listOf("ты", "Петя", "Вася"),
            seatTitles(Settings(botNameThousand = "Петя", botNameThousandSecond = "Вася"), 3),
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
            seatTitles(Settings(botNameThousand = "   ", botNameThousandSecond = "  "), 3),
        )
    }

    /**
     * Имя у каждой игры своё. Иначе имя, набранное за столом дурака, звало бы
     * Петю и за тысячей, и за козлом — а там сидит кто-то другой или никто
     * (Катерина, 19.09: «чтобы боты были в настройках с игрой»).
     */
    @Test
    fun `у каждой игры своё имя соперника`() {
        val settings = withBotName(withBotName(Settings(), GAME_DURAK, "Петя"), GAME_KOZEL, "Вася")
        assertEquals("Петя", botTitle(settings, GAME_DURAK))
        assertEquals("Вася", botTitle(settings, GAME_KOZEL))
        assertEquals("Бот", botTitle(settings, GAME_THOUSAND))
    }

    /** Пустое имя — «Бот»: строка настройки не остаётся без ответа. */
    @Test
    fun `без имени соперник зовётся Ботом в любой игре`() {
        assertEquals("Бот", botTitle(Settings(botNameKozel = "  "), GAME_KOZEL))
        assertEquals("Бот", botTitle(Settings(), GAME_THOUSAND))
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
