package games.cardgames.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Синтезатор и голос соперника: какой движок бот возьмёт и что он от этого
 * скажет.
 *
 * Проверять это на слух — единственный настоящий способ, но за столом ошибка
 * выглядит как «бот говорит моим же голосом»: не отказ, а ровно то, на что
 * Катерина и жаловалась (19.09: «меня всё равно разговаривает всё одним
 * голосом, нет разделения»). Поэтому правила разбора — здесь.
 *
 * Случай, ради которого всё затевалось: у движка бывает один-единственный
 * голос, и тогда бот без своего движка говорит ровно тем же, что и
 * приложение. Другой движок — различие, которое ни с чем не спутать.
 */
class BotEngineTest {

    /** Своего синтезатора боту не выбрали — он говорит движком приложения. */
    @Test
    fun `без своего выбора бот берёт синтезатор приложения`() {
        assertEquals("com.google.android.tts", botEngine(null, "com.google.android.tts"))
        assertNull(botEngine(null, null))
    }

    /**
     * «Системный» у бота — не то же, что «как у приложения»: пустая строка
     * значит «движок по умолчанию», а он не обязан совпадать с тем, который
     * выбрал себе игрок.
     */
    @Test
    fun `системный синтезатор — это пустая строка, а не движок приложения`() {
        assertNull(botEngine("", "com.google.android.tts"))
        assertEquals("com.samsung.tts", botEngine("com.samsung.tts", "com.google.android.tts"))
    }

    /**
     * Голос приложения подставляется только тогда, когда бот и правда говорит
     * его движком: имя голоса живёт внутри движка, и в чужом оно значит не
     * «тот же голос», а «первый подходящий».
     */
    @Test
    fun `чужой синтезатор не получает голос приложения`() {
        assertEquals(
            "ru-ru-x-ruf-local",
            botVoice(null, "ru-ru-x-ruf-local", null, "com.google.android.tts"),
        )
        assertEquals(
            "ru-ru-x-ruf-local",
            botVoice(null, "ru-ru-x-ruf-local", "com.google.android.tts", "com.google.android.tts"),
        )
        assertNull(botVoice(null, "ru-ru-x-ruf-local", "com.samsung.tts", "com.google.android.tts"))
    }

    /** Выбранный боту голос главнее движка: его и берём, каким бы тот ни был. */
    @Test
    fun `свой голос бота не подменяется`() {
        assertEquals(
            "ru-ru-x-ruf-network",
            botVoice("ru-ru-x-ruf-network", "ru-ru-x-ruf-local", "com.samsung.tts", "com.google.tts"),
        )
    }

    /**
     * «Системный» бот и «системный» у приложения — один и тот же движок,
     * хотя записаны они по-разному: пустой строкой и пустым значением.
     * Считать их разными значит объявить бота чужим самому себе и оставить
     * его без голоса приложения.
     */
    @Test
    fun `системный у обоих — это один и тот же синтезатор`() {
        assertEquals("ru-ru-x-ruf-local", botVoice(null, "ru-ru-x-ruf-local", "", null))
    }
}
