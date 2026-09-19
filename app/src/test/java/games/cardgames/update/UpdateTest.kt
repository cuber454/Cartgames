package games.cardgames.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор файла обновления и сравнение сборок.
 *
 * Проверяется здесь ровно то, что нельзя проверить глазами: файл пишет CI, а
 * читает приложение, и разойдись они в формате — игрок просто перестал бы
 * получать новые сборки. Молча: проверка не удалась, а игра работает, и
 * заметить это неоткуда. Сеть и установка тут не проверяются — это не логика,
 * а окружение.
 */
class UpdateTest {

    private val sum = "a".repeat(64)

    private fun manifest(vararg lines: String) = lines.joinToString("\n")

    @Test
    fun `читает все поля файла обновления`() {
        val release = Update.parse(
            manifest(
                "# Описание последней сборки.",
                "versionCode=42",
                "versionName=0.9",
                "build=0.9.42",
                "apkUrl=https://github.com/cuber454/Cartgames/releases/download/v0.9.42/cartgames-0.9.42.apk",
                "sha256=$sum",
                "notes=Тысяча: починена похвальба",
            ),
        )

        assertEquals(42, release?.versionCode)
        assertEquals("0.9.42", release?.build)
        assertEquals("Тысяча: починена похвальба", release?.notes)
        assertEquals(sum, release?.sha256)
        assertTrue(release?.apkUrl?.endsWith("cartgames-0.9.42.apk") == true)
    }

    /** Заголовок коммита вполне может содержать «=»: режем по первому. */
    @Test
    fun `значение с равно внутри остаётся целым`() {
        val release = Update.parse(
            manifest(
                "versionCode=1",
                "apkUrl=https://example.org/a.apk",
                "sha256=$sum",
                "notes=Правка: жирный шрифт = крупнее",
            ),
        )

        assertEquals("Правка: жирный шрифт = крупнее", release?.notes)
    }

    @Test
    fun `без суммы сборку не берём`() {
        val release = Update.parse(
            manifest(
                "versionCode=1",
                "apkUrl=https://example.org/a.apk",
                "sha256=слишкомкороткая",
            ),
        )

        assertNull(release)
    }

    @Test
    fun `без ссылки на сборку файл не годится`() {
        assertNull(Update.parse(manifest("versionCode=1", "sha256=$sum")))
    }

    @Test
    fun `без номера сборки сравнивать нечего`() {
        assertNull(Update.parse(manifest("apkUrl=https://example.org/a.apk", "sha256=$sum")))
    }

    /** Пустой ответ сервера — тоже не разбор: сборки в нём нет. */
    @Test
    fun `пустой файл не разбирается`() {
        assertNull(Update.parse(""))
    }

    /** Имя сборки вслух: пустое поле не должно оставить фразу без числа. */
    @Test
    fun `сборка без имени называется по номеру`() {
        val release = Update.parse(
            manifest("versionCode=7", "apkUrl=https://example.org/a.apk", "sha256=$sum"),
        )

        assertEquals("сборка 7", release?.title)
    }

    /**
     * Дороги к сборке. Прямая обязана идти первой и остаться нетронутой: на
     * незаблокированной сети посредник не нужен вовсе. Зеркала же не
     * разбирают ссылку, а приставляют себя спереди — потеряй они её, и
     * посредник не поймёт, что у него просят.
     */
    @Test
    fun `сборку берут сперва напрямую, потом через зеркала`() {
        val url =
            "https://github.com/cuber454/Cartgames/releases/download/v0.9.42/cartgames-0.9.42.apk"

        val roads = Update.apkRoads(url)

        assertEquals(url, roads.first().url)
        assertTrue("зеркал не осталось", roads.size > 1)
        roads.drop(1).forEach { road ->
            assertTrue("дорога «${road.name}» потеряла ссылку", road.url.endsWith(url))
        }
    }

    @Test
    fun `свежая сборка — та, чей номер больше`() {
        val release = Update.parse(
            manifest("versionCode=42", "apkUrl=https://example.org/a.apk", "sha256=$sum"),
        )!!

        assertTrue(Update.isNewer(release, localVersionCode = 41))
        assertFalse(Update.isNewer(release, localVersionCode = 42))
        assertFalse(Update.isNewer(release, localVersionCode = 43))
    }
}
