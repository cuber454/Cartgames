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
 * В «Козле» очки записывает себе выигравший раздачу, поэтому счёт — это
 * счёт выигранных раздач, а не отыгранных. Отсюда и развязка: до ста одного
 * доходит тот, кто выиграл больше, а «козлом» остаётся второй.
 */
class KozelMatchTest {

    @Test
    fun `новый матч начинается с раздачи и нулей`() {
        val match = KozelMatch(KozelRules.BOOK, Random(1))

        assertEquals(listOf(0, 0), match.table)
        assertEquals(1, match.roundNumber)
        assertFalse(match.over)
        assertEquals(null, match.matchWinner)
        assertEquals(null, match.goat)
        assertEquals(7, match.round.handSize(0))
        assertEquals(7, match.round.handSize(1))
        assertEquals(14, match.round.bazaarSize)
    }

    @Test
    fun `очки раунда достаются тому, кто его выиграл`() {
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
            assertEquals(won.points, summary.points)
            assertEquals(won.fish, summary.fish)

            val winner = summary.winner
            if (winner == null) {
                // Ничейная «рыба»: не записано никому.
                assertEquals(0, summary.points)
                assertEquals(before, match.table)
            } else {
                // Ноль тут не ошибка: у проигравшего могла остаться одна
                // пусто-пусто, а она стоит ровно ничего.
                assertEquals(before[winner] + summary.points, match.table[winner])
                assertEquals(before[KozelRound.other(winner)], match.table[KozelRound.other(winner)])
            }

            assertFalse(match.over)
            assertEquals(round + 2, match.roundNumber)
            assertEquals(7, match.round.handSize(0), "следующий раунд сдан не по семь костей")
        }
    }

    @Test
    fun `кто первым дошёл до цели — тот выиграл, второй стал козлом`() {
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

            val winner = match.matchWinner ?: error("матч кончился, а победителя нет")
            assertTrue(match.table[winner] >= target, "победитель не дошёл до цели")
            assertTrue(match.table[KozelRound.other(winner)] < target, "проигравший тоже дошёл до цели")
            assertEquals(KozelRound.other(winner), match.goat, "козлом должен быть второй")
            assertNotEquals(winner, match.goat)
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
            val winner = playMatch(KozelRules.BOOK, listOf(Difficulty.CLEVER, Difficulty.NORMAL), seed)
            assertTrue(winner == 0 || winner == 1)
        }
    }
}
