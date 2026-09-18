package games.engine.kozel

import games.engine.Difficulty
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Прогон уровней бота (KOZEL.md, 4.6).
 *
 * Проверяем две вещи. Первая: уровень сильнее слабого — иначе деление на
 * уровни ничего не значит, и игроку всё равно, кого он выбрал соперником.
 * Вторая: сильный не выигрывает слишком уж много — за столом так не бывает,
 * базар закрыт и для него.
 *
 * Полосы здесь широкие нарочно. Числа замера (73.6% и 75.3%) — это замер от
 * 18.09, а не пожелание: тест сторожит, чтобы уровень не перестал обыгрывать
 * слабого, а не чтобы сошлась красивая цифра. Серия идёт от одного зерна,
 * поэтому падение теста означает изменение в правилах или в счёте бота, а не
 * другую тасовку.
 *
 * Матчей по умолчанию столько, сколько записано в 4.6. Меньше — для быстрой
 * прикидки при подборе весов: `-Dkozel.sim.matches=50`.
 */
class KozelSimulatorTest {

    @Test
    fun `обычный обыгрывает лёгкого`() {
        val result = KozelSimulator.series(Difficulty.NORMAL, Difficulty.NOVICE, matches)
        println("обычный против лёгкого: ${result.spoken()}")

        assertTrue(
            result.winRate(0) in 0.60..0.85,
            "обычный должен заметно обыгрывать лёгкого: ${result.spoken()}",
        )
    }

    @Test
    fun `сложный обыгрывает обычного`() {
        val result = KozelSimulator.series(Difficulty.CLEVER, Difficulty.NORMAL, matches)
        println("сложный против обычного: ${result.spoken()}")

        assertTrue(
            result.winRate(0) in 0.60..0.85,
            "сложный должен заметно обыгрывать обычного: ${result.spoken()}",
        )
    }

    @Test
    fun `сильный уровень оставляет на руке меньше`() {
        // Штраф за раунд записывают тому, у кого осталась рука. Если счёт
        // работает, рука сильного в момент чужого выхода должна быть легче.
        val result = KozelSimulator.series(Difficulty.CLEVER, Difficulty.NOVICE, matches)
        println("сложный против лёгкого: ${result.spoken()}")

        assertTrue(
            result.averagePenalty[0] < result.averagePenalty[1],
            "сложный должен оставлять меньше: ${result.spoken()}",
        )
    }

    @Test
    fun `уровень сам с собой выигрывает половину`() {
        // Проверка самого прогона, а не бота: одинаковые уровни обязаны
        // разойтись поровну. Заметный перекос значит, что исход решает место
        // за столом или что победы считаются не по тому, за кем их записали, —
        // и тогда любые числа выше не верны.
        val result = KozelSimulator.series(Difficulty.NORMAL, Difficulty.NORMAL, matches)
        println("обычный сам с собой: ${result.spoken()}")

        assertTrue(
            result.winRate(0) in 0.45..0.55,
            "одинаковые уровни должны делить матчи поровну: ${result.spoken()}",
        )
    }

    private val matches: Int
        get() = System.getProperty("kozel.sim.matches")?.toIntOrNull() ?: 1000
}
