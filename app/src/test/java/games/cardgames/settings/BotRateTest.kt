package games.cardgames.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Скорость речи соперника: ступени, их слова и перебор по кругу.
 *
 * Числа этой настройки игрок не видит — он слышит только слово в строке
 * настройки и то, как звучит бот за столом. Поэтому ошибка тут не видна ни на
 * экране, ни в отладчике: две ступени под одним словом выглядят как кнопка,
 * которая не работает, а ступень мимо круга — как пропавшая (Катерина, 19.09).
 */
class BotRateTest {

    /** Кнопка перебирает ступени по кругу: с последней — на первую. */
    @Test
    fun `ступени идут по кругу`() {
        assertEquals(0.9f, nextBotRate(BOT_RATES[0]), 0f)
        assertEquals(1.0f, nextBotRate(0.9f), 0f)
        assertEquals(1.15f, nextBotRate(1.0f), 0f)
        assertEquals(1.4f, nextBotRate(1.15f), 0f)
        assertEquals(BOT_RATES.first(), nextBotRate(BOT_RATES.last()), 0f)
    }

    /** Обычная скорость — «как приложение»: обычная она у обоих. */
    @Test
    fun `обычная скорость зовётся как у приложения`() {
        assertEquals("как приложение", botRateTitle(1.0f))
    }

    /**
     * У каждой ступени своё слово: строка настройки — это оно и есть, и две
     * ступени под одним словом не отличить одну от другой.
     */
    @Test
    fun `у каждой ступени своё слово`() {
        val titles = BOT_RATES.map { botRateTitle(it) }
        assertEquals(titles.size, titles.toSet().size)
    }

    /**
     * Скорость — примета, по которой бота узнают, а не разборчивость, которую
     * подгоняют под себя: ступени стоят вокруг обычной и не уходят в пределы,
     * где речь перестаёт быть речью.
     */
    @Test
    fun `ступени стоят вокруг обычной`() {
        assertTrue(BOT_RATES.all { it in 0.5f..1.5f })
        assertTrue(1.0f in BOT_RATES)
    }

    /**
     * Чужая правка в хранилище — не повод молчать: с любого числа кнопка ведёт
     * на ступень, а не остаётся на месте. Число сперва подтягивают к ближней
     * ступени, а уж с неё шагают на следующую — иначе у края круга кнопка
     * прыгала бы через ступень.
     */
    @Test
    fun `с чужого числа кнопка ведёт на ступень`() {
        assertEquals(1.0f, nextBotRate(0.85f), 0f)
        assertEquals(0.75f, nextBotRate(3f), 0f)
        assertEquals(0.9f, nextBotRate(0f), 0f)
    }
}
