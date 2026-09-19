package games.engine.kozel

import games.engine.Difficulty
import games.engine.tiles.TileSet
import games.engine.tiles.tile
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Запись партии на диск: партия, поднятая с диска, обязана продолжиться
 * ровно с того же места — иначе сохранение не сохраняет.
 */
class KozelSaveTest {

    @Test
    fun `партия переживает запись и чтение`() {
        for (seed in 0 until 6) {
            val random = Random(seed)
            val match = KozelMatch(KozelRules.BOOK, random)
            // Партия на паузе, а не на раздаче: несколько ходов уже сыграно.
            repeat(seed + 4) {
                if (match.round.finished) return@repeat
                val move = KozelBot.chooseMove(
                    KozelView.of(match.round, match.round.turn),
                    Difficulty.NORMAL,
                    random,
                ) ?: return@repeat
                match.round.apply(match.round.turn, move)
            }
            if (match.round.finished) continue

            val restored = assertNotNull(KozelSave.read(KozelSave.write(match)), "запись не прочиталась")

            assertEquals(match.table, restored.table)
            assertEquals(match.roundNumber, restored.roundNumber)
            assertEquals(match.rules, restored.rules)
            val before = match.round
            val after = restored.round
            assertEquals(before.turn, after.turn)
            assertEquals(before.passes, after.passes)
            assertEquals(before.bazaarSize, after.bazaarSize)
            assertEquals(before.bazaarTiles, after.bazaarTiles)
            assertEquals(before.table, after.table)
            for (seat in 0 until before.seats) {
                assertEquals(before.handOf(seat), after.handOf(seat), "рука места $seat разошлась")
            }
        }
    }

    @Test
    fun `счёт и договорённости едут вместе с партией`() {
        val rules = KozelRules(target = 200, emptyDoubleBonus = KozelRules.EMPTY_DOUBLE_AS_BONUS)
        val match = KozelMatch(rules, Random(7))
        // Раунд доигран и записан — на диске лежит уже следующий, со счётом.
        playRound(match.round, listOf(Difficulty.NORMAL, Difficulty.NORMAL), Random(7))
        match.finishRound()

        val restored = assertNotNull(KozelSave.read(KozelSave.write(match)))

        assertEquals(rules, restored.rules)
        assertEquals(match.table, restored.table)
        assertEquals(2, restored.roundNumber)
    }

    @Test
    fun `пропущенный ход не теряется - второй закроет раунд рыбой`() {
        // Раунд, где ходить нечем никому: у обоих кости с шестёркой, а на
        // столе пусто-пусто. Такой раунд закрывается «рыбой» на втором
        // пропуске, и поднятая с диска партия обязана помнить первый.
        val round = roundOf(
            hands = listOf(listOf(tile(6, 6)), listOf(tile(6, 5))),
            bazaar = TileSet.full.filterNot { it == tile(6, 6) || it == tile(6, 5) || it == tile(0, 0) },
            line = Line(listOf(tile(0, 0)), 0, 0),
            turn = 0,
        )
        round.apply(0, KozelMove.Pass)
        assertEquals(1, round.passes)

        val match = KozelMatch.restore(KozelRules.BOOK, round, listOf(0, 0), 1)
        val restored = assertNotNull(KozelSave.read(KozelSave.write(match)))

        assertEquals(1, restored.round.passes)
        // Второй пропуск и закрывает раунд: счёт пропусков доехал целым.
        restored.round.apply(restored.round.turn, KozelMove.Pass)
        assertTrue(restored.round.fish, "второй пропуск не закрыл раунд «рыбой»")
    }

    @Test
    fun `чужая или побитая запись отвергается`() {
        val match = KozelMatch(KozelRules.BOOK, Random(3))
        val text = KozelSave.write(match)

        assertNull(KozelSave.read(""), "пустая запись прочиталась")
        assertNull(KozelSave.read("game durak\nformat 1"), "чужая игра прочиталась")
        assertNull(KozelSave.read(text.replace("format 1", "format 99")), "чужая версия прочиталась")
        assertNull(KozelSave.read(text + "\nhand0 4-4"), "повтор кости на руке прочитался")
        assertNull(KozelSave.read(text.replace("hand0 ", "hand0 6-6 6-6 ")), "залипшая кость прочиталась")
        // Кость выкинута из записи: набор перестал сходиться.
        val short = text.lineSequence()
            .map { if (it.startsWith("hand1 ")) "hand1" else it }
            .joinToString("\n")
        assertNull(KozelSave.read(short), "запись без костей на руке прочиталась")
    }

    @Test
    fun `доигранный матч не поднимается с диска`() {
        val match = KozelMatch(KozelRules(target = 5), Random(11))
        val random = Random(11)
        while (!match.over) {
            playRound(match.round, listOf(Difficulty.NORMAL, Difficulty.NORMAL), random)
            match.finishRound()
            check(match.roundNumber < 500)
        }

        // Матч кончен, и на столе лежит его последний раунд. Записать его
        // можно, но прочитать — нет: это итог, а не пауза.
        assertNull(KozelSave.read(KozelSave.write(match)))
    }

    @Test
    fun `линия с перепутанным порядком костей отвергается`() {
        val onTable = listOf(tile(5, 5), tile(5, 2))
        val onHand = listOf(tile(6, 6), tile(5, 4), tile(3, 2), tile(1, 0))
        val round = roundOf(
            hands = listOf(onHand.take(2), onHand.drop(2)),
            bazaar = TileSet.full.filterNot { it in onTable || it in onHand },
            line = Line(onTable, 5, 2),
            turn = 0,
        )
        val text = KozelSave.write(KozelMatch.restore(KozelRules.BOOK, round, listOf(0, 0), 1))
        assertNotNull(KozelSave.read(text), "ровная линия не прочиталась")

        // Кости на столе переставлены: крайняя кость больше не держит наружу
        // тот конец, что записан. Пишутся кости младшей половиной вперёд,
        // поэтому 5-2 в записи выглядит как «2-5».
        val broken = text.replace("line 5-5 2-5", "line 2-5 5-5")
        assertNull(KozelSave.read(broken), "линия с перепутанным порядком прочиталась")

        // Конец, которого у крайней кости нет, — тоже поломка.
        assertNull(KozelSave.read(text.replace("ends 5 2", "ends 6 2")))
    }
}
