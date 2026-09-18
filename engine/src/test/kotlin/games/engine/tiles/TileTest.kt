package games.engine.tiles

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Кость и набор костей: то, на чём стоит вся игра. Ошибка здесь — это
 * неправильные концы линии, а значит и неправильные ходы, поэтому проверяем
 * и арифметику, и речь, и сам набор.
 */
class TileTest {

    @Test
    fun `половины пишутся по возрастанию`() {
        assertEquals(Tile(3, 6), tile(6, 3))
        assertFailsWith<IllegalArgumentException> { Tile(6, 3) }
        assertFailsWith<IllegalArgumentException> { Tile(0, 7) }
    }

    @Test
    fun `кость называется старшей половиной первой`() {
        // За столом говорят «шесть-три», а не «три-шесть».
        assertEquals("шесть-три", Tile(3, 6).spoken())
        assertEquals("пусто-пусто", Tile(0, 0).spoken())
        assertEquals("пять-пусто", Tile(0, 5).spoken())
    }

    @Test
    fun `названия чисел — как их произносят`() {
        assertEquals("пусто", pipsName(0))
        assertEquals("один", pipsName(1))
        assertEquals("шесть", pipsName(6))
    }

    @Test
    fun `очки кости — сумма половин`() {
        assertEquals(0, Tile(0, 0).pips)
        assertEquals(9, Tile(3, 6).pips)
        assertEquals(12, Tile(6, 6).pips)
    }

    @Test
    fun `вторая половина — та, что остаётся наружу`() {
        assertEquals(6, Tile(3, 6).other(3))
        assertEquals(3, Tile(3, 6).other(6))
        // У дубля наружу смотрит то же число: дубль занимает конец и не меняет его.
        assertEquals(4, Tile(4, 4).other(4))
    }

    @Test
    fun `в наборе дубль-шесть двадцать восемь костей, и все разные`() {
        assertEquals(TILE_COUNT, TileSet.full.size)
        assertEquals(TILE_COUNT, TileSet.full.toSet().size)
        // Каждая пара чисел ровно один раз, вместе с дублями.
        assertEquals(7, TileSet.full.count { it.isDouble })
        assertEquals(Tile(0, 0), TileSet.full.first())
        assertEquals(Tile(6, 6), TileSet.full.last())
    }

    @Test
    fun `тасовка не теряет и не добавляет костей`() {
        val set = TileSet.shuffled(Random(1))
        assertEquals(TILE_COUNT, set.size)
        assertEquals(TileSet.full.toSet(), set.toSet())
    }

    @Test
    fun `тасовка ставит наверх разные кости`() {
        // Грубая, но честная проверка: если бы набор не тасовался, сверху
        // всегда лежало бы пусто-пусто, и базар был бы предсказуем.
        val firsts = (0 until 200).map { TileSet.shuffled(Random(it)).first() }.toSet()
        assertTrue(firsts.size > 20, "сверху набора побывало всего ${firsts.size} костей")
    }
}
