package games.cardgames.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Пауза между репликами: ступени, их слова и перебор по кругу.
 *
 * Проверять это надо тестом, а не на слух: строка настройки звучит одним
 * словом, и две ступени под одним словом слышны как кнопка, которая не
 * работает, — а ступень мимо круга как пропавшая. Ровно на этом уже спотыкались
 * со скоростью соперника (см. [BotRateTest]).
 */
class PhrasePauseTest {

    /** Кнопка перебирает ступени по кругу: с последней — на выключенную. */
    @Test
    fun `ступени идут по кругу`() {
        assertEquals(1000, nextPhrasePause(PHRASE_PAUSES[0]))
        assertEquals(2000, nextPhrasePause(1000))
        assertEquals(3000, nextPhrasePause(2000))
        assertEquals(PHRASE_PAUSES.first(), nextPhrasePause(PHRASE_PAUSES.last()))
    }

    /** Выключенная пауза так и называется: за ней стоит «как было». */
    @Test
    fun `выключенная пауза зовётся выключенной`() {
        assertEquals("выключена", phrasePauseTitle(0))
    }

    /**
     * У каждой ступени своё слово: строка настройки — это оно и есть, и две
     * ступени под одним словом одну от другой не отличить.
     */
    @Test
    fun `у каждой ступени своё слово`() {
        val titles = PHRASE_PAUSES.map { phrasePauseTitle(it) }
        assertEquals(titles.size, titles.toSet().size)
    }

    /**
     * Пауза — не разборчивость речи, а дыхание между фразами: короткая ступень
     * ничего не меняет, длинная превращает партию в допрос. Держим её в
     * пределах нескольких секунд, и одна ступень — «выключено».
     */
    @Test
    fun `ступени стоят в разумных пределах`() {
        assertTrue(PHRASE_PAUSES.all { it in 0..3000 })
        assertTrue(0 in PHRASE_PAUSES)
    }

    /**
     * Чужая правка в хранилище — не повод молчать: с любого числа кнопка ведёт
     * на ступень, а не остаётся на месте. Число сперва подтягивают к ближней
     * ступени, а уж с неё шагают на следующую — ближняя ищется первой из равных,
     * поэтому у середины между ступенями берётся младшая.
     */
    @Test
    fun `с чужого числа кнопка ведёт на ступень`() {
        assertEquals(2000, nextPhrasePause(1500))
        assertEquals(0, nextPhrasePause(2600))
        assertEquals(1000, nextPhrasePause(-500))
    }
}
