package games.engine.kozel

import games.engine.Difficulty
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Матч: очки, цель и «козёл».
 *
 * Очки в «Козле» штрафные: за раунд их записывают тому, кто его проиграл, —
 * у кого остались на руке кости. Значит счёт — это счёт отыгранных раздач, и
 * «козлом» становится тот, кто набрал больше всех.
 */
class KozelMatchTest {

    @Test
    fun `новый матч начинается с раздачи и нулей`() {
        val match = KozelMatch(KozelRules.BOOK, Random(1))

        assertEquals(listOf(0, 0), match.table)
        assertEquals(1, match.roundNumber)
        assertFalse(match.over)
        assertEquals(null, match.goat)
        assertEquals(7, match.round.handSize(0))
        assertEquals(7, match.round.handSize(1))
        assertEquals(14, match.round.bazaarSize)
    }

    @Test
    fun `очки раунда записываются тому, кто его проиграл`() {
        // Цель заведомо недостижимая: матч не должен кончиться на первом раунде.
        val match = KozelMatch(KozelRules(target = 10_000), Random(2))
        val random = Random(2)

        repeat(30) { round ->
            playRound(match.round, listOf(Difficulty.NORMAL, Difficulty.NORMAL), random)

            val played = match.round
            val before = match.table
            val won = played.score()
            val summary = match.finishRound()

            assertEquals(won.winner, summary.winner)
            assertEquals(won.written, summary.written)
            assertEquals(won.fish, summary.fish)

            if (summary.winner == null) {
                // Ничейная «рыба»: не записано никому.
                assertEquals(listOf(0, 0), summary.written)
                assertEquals(before, match.table)
            } else {
                // Записали проигравшим раунд — тем, у кого осталась рука.
                // Ноль у места не ошибка: у вышедшего его и не бывает, а у
                // оставшегося могла остаться одна пусто-пусто, она стоит
                // ровно ничего.
                summary.written.forEachIndexed { seat, points ->
                    assertEquals(before[seat] + points, match.table[seat], "счёт места $seat")
                }
                assertEquals(before[summary.winner], match.table[summary.winner])
            }

            assertFalse(match.over)
            assertEquals(round + 2, match.roundNumber)
            assertEquals(7, match.round.handSize(0), "следующий раунд сдан не по семь костей")
        }
    }

    @Test
    fun `кто первым набрал до цели — тот козёл, матч за остальными`() {
        val target = 25

        for (seed in 0 until 8) {
            val rules = KozelRules(target = target)
            val match = KozelMatch(rules, Random(seed))
            val random = Random(seed)
            var rounds = 0

            while (!match.over) {
                playRound(match.round, listOf(Difficulty.NORMAL, Difficulty.CLEVER), random)
                match.finishRound()
                check(++rounds < 500) { "матч не кончается" }
            }

            val goat = match.goat ?: error("матч кончился, а козла нет")

            assertTrue(match.table[goat] >= target, "козёл не дошёл до цели")
            // Козёл ровно один: остальные до цели не дошли.
            match.table.indices.filter { it != goat }.forEach { seat ->
                assertTrue(match.table[seat] < target, "до цели дошёл не только козёл")
            }
            assertEquals(rounds, match.roundNumber, "номер раунда разошёлся с числом сыгранных")
        }
    }

    @Test
    fun `последний раунд матча остаётся на столе`() {
        val rules = KozelRules(target = 5)
        val match = KozelMatch(rules, Random(4))
        val random = Random(4)

        while (!match.over) {
            playRound(match.round, listOf(Difficulty.NORMAL, Difficulty.NORMAL), random)
            match.finishRound()
            check(match.roundNumber < 500)
        }

        assertTrue(match.round.finished, "доигранный раунд подменили новым")
    }

    @Test
    fun `бот против бота всегда доигрывает матч до конца`() {
        for (seed in 0 until 6) {
            val goat = playMatch(KozelRules.BOOK, listOf(Difficulty.CLEVER, Difficulty.NORMAL), seed)
            assertTrue(goat == 0 || goat == 1)
        }
    }
}
