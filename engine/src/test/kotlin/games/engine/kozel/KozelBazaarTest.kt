package games.engine.kozel

import games.engine.tiles.Tile
import games.engine.tiles.TileSet
import games.engine.tiles.tile
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Базар: закрытый и честный.
 *
 * Обе половины этого утверждения проверяемы. Закрытость — тем, что кость
 * берётся строго в том порядке, в каком легла при раздаче, и никто, включая
 * бота, этого порядка не знает заранее. Честность — тем, что наверх с
 * одинаковой частотой попадает любая кость: перекос здесь означал бы, что
 * игроку и боту сдают не поровну (KOZEL.md, 4.2).
 */
class KozelBazaarTest {

    @Test
    fun `кость из базара берётся строго по порядку раздачи`() {
        val order = listOf(tile(0, 1), tile(0, 2), tile(1, 2))
        val round = roundOf(
            hands = listOf(listOf(tile(1, 1)), listOf(tile(2, 2))),
            bazaar = order,
            // Ни одна из костей базара к этим концам не подходит, поэтому
            // ход за ходом можно брать и смотреть, какая пришла.
            line = lineWithEnds(3, 6),
        )

        order.forEachIndexed { step, expected ->
            assertEquals(expected, round.bazaarTiles.first(), "шаг ${step + 1}")
            round.apply(0, KozelMove.Draw)
            assertEquals(expected, round.handOf(0).last())
            assertEquals(order.size - step - 1, round.bazaarSize)
        }
    }

    @Test
    fun `взятая кость уходит с верха базара, а не откуда-то ещё`() {
        val round = roundOf(
            hands = listOf(listOf(tile(1, 1)), listOf(tile(2, 2))),
            bazaar = listOf(tile(0, 1), tile(0, 2)),
            line = lineWithEnds(3, 6),
        )

        round.apply(0, KozelMove.Draw)
        assertEquals(listOf(tile(0, 2)), round.bazaarTiles)
    }

    @Test
    fun `наверх тасовки любая кость попадает одинаково часто`() {
        val shuffles = 28_000
        val random = Random(2024)
        val tops = mutableMapOf<Tile, Int>()

        repeat(shuffles) {
            val top = TileSet.shuffled(random).first()
            tops[top] = (tops[top] ?: 0) + 1
        }

        assertEquals(TileSet.full.size, tops.size, "какая-то кость ни разу не легла наверх")

        // Ожидание — тысяча на кость; разброс при таком числе тасовок
        // порядка тридцати, так что полторы сотни — заведомо не случайность.
        val expected = shuffles / TileSet.full.size
        tops.forEach { (tile, count) ->
            assertTrue(
                count in (expected - 150)..(expected + 150),
                "кость ${tile.spoken()} легла наверх $count раз из $shuffles",
            )
        }
    }

    @Test
    fun `раздача не повторяется от сдачи к сдаче`() {
        val deals = (0 until 10).map { seed ->
            val round = KozelRound.deal(KozelRules.BOOK, Random(seed))
            round.handOf(0) to round.handOf(1)
        }
        assertEquals(deals.size, deals.toSet().size, "одна и та же рука сдана дважды")
    }
}
