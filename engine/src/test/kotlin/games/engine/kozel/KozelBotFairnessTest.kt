package games.engine.kozel

import games.engine.Difficulty
import games.engine.tiles.Tile
import games.engine.tiles.TileSet
import games.engine.tiles.tile
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Честность бота — то, ради чего заведён [KozelView].
 *
 * Проверяем не обещание «бот не подглядывает», а свойство: два раунда,
 * отличающиеся только чужой рукой и содержимым базара, дают боту одно и то
 * же состояние и один и тот же ход. Подглядывай он — решения бы разошлись.
 */
class KozelBotFairnessTest {

    /** Моя рука и линия одни и те же; у противника и в базаре — другое. */
    private fun pair(
        mine: List<Tile> = listOf(tile(3, 4), tile(5, 6), tile(0, 1)),
        line: Line = lineWithEnds(3, 6),
    ): Pair<KozelRound, KozelRound> = roundOf(
        hands = listOf(mine, listOf(tile(1, 1), tile(2, 2), tile(0, 0))),
        bazaar = listOf(tile(2, 3), tile(4, 4)),
        line = line,
    ) to roundOf(
        hands = listOf(mine, listOf(tile(5, 5), tile(1, 6), tile(2, 4))),
        bazaar = listOf(tile(0, 5), tile(1, 3)),
        line = line,
    )

    @Test
    fun `чужая рука и базар состояния не меняют`() {
        val (a, b) = pair()

        assertEquals(KozelView.of(a, 0), KozelView.of(b, 0))
    }

    @Test
    fun `при одном и том же состоянии бот решает одинаково`() {
        val (a, b) = pair()
        val viewA = KozelView.of(a, 0)
        val viewB = KozelView.of(b, 0)

        for (difficulty in Difficulty.entries) {
            assertEquals(
                KozelBot.chooseMove(viewA, difficulty, Random(5)),
                KozelBot.chooseMove(viewB, difficulty, Random(5)),
                "уровень $difficulty разошёлся на двух раскладах",
            )
        }
    }

    @Test
    fun `в состоянии бота нет ни чужой руки, ни костей базара`() {
        val (round, _) = pair()
        val view = KozelView.of(round, 0)

        assertEquals(3, view.handSizes[1])
        assertEquals(2, view.bazaarSize)
        assertEquals(round.handOf(0), view.hand)
        assertEquals(round.table, view.line)

        // Ровно те кости, которых он не видел: ни свои, ни лежащие на столе.
        val unseen = view.unseen()
        assertEquals(TileSet.full.size - view.hand.size - view.line.tiles.size, unseen.size)
        assertTrue(unseen.none { it in view.hand })
        assertTrue(unseen.none { it in view.line.tiles })
    }

    @Test
    fun `с пустым базаром невиданное — это ровно чужая рука`() {
        // Расклад полный: когда базар разобран, все двадцать восемь костей
        // лежат либо на руках, либо на столе, и «невиданное» перестаёт быть
        // догадкой — это в точности чужая рука.
        val round = KozelRound.deal(KozelRules.BOOK, Random(3))
        while (round.bazaarSize > 0) round.apply(round.turn, KozelMove.Draw)

        val unseen = KozelView.of(round, 0).unseen()
        assertEquals(round.handSize(1), unseen.size)
        assertEquals(round.handOf(1).toSet(), unseen.toSet())
    }

    @Test
    fun `бот никогда не ходит недозволенным ходом`() {
        val random = Random(11)

        for (seed in 0 until 60) {
            val round = KozelRound.deal(KozelRules.BOOK, Random(seed))
            var moves = 0

            while (!round.finished) {
                val seat = round.turn
                val legal = round.legalMoves(seat)
                val move = KozelBot.chooseMove(
                    KozelView.of(round, seat),
                    Difficulty.entries[seed % Difficulty.entries.size],
                    random,
                ) ?: error("бот не нашёл хода, хотя раунд не кончен")

                assertTrue(move in legal, "бот предложил $move, а можно было $legal")
                round.apply(seat, move)
                check(++moves < 1_000) { "раунд не кончается" }
            }
        }
    }
}
