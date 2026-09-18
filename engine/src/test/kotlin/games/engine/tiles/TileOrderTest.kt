package games.engine.tiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Порядок костей на руке: рука листается свайпами, поэтому порядок — часть
 * игры, а не оформление, и проверяется как правило.
 */
class TileOrderTest {

    @Test
    fun `по числам одинаковые кости идут подряд`() {
        val hand = listOf(tile(6, 1), tile(4, 4), tile(6, 6), tile(2, 0), tile(6, 3))

        val sorted = TileOrder.BY_NUMBERS.sort(hand)

        assertEquals(
            listOf(tile(6, 6), tile(6, 3), tile(6, 1), tile(4, 4), tile(2, 0)),
            sorted,
        )
    }

    @Test
    fun `по весу тяжёлое сверху, а при равном весе вперёд идёт старшая половина`() {
        val hand = listOf(tile(2, 0), tile(5, 5), tile(6, 6), tile(6, 4), tile(6, 5))

        val sorted = TileOrder.BY_PIPS.sort(hand)

        // 6-6 — это 12, 6-5 — 11, а 6-4 и 5-5 обе по десять: вперёд идёт
        // та, у которой старшая половина больше.
        assertEquals(
            listOf(tile(6, 6), tile(6, 5), tile(6, 4), tile(5, 5), tile(2, 0)),
            sorted,
        )
    }

    @Test
    fun `порядки действительно разные`() {
        // Кость, у которой вес и старшее число спорят: 6-0 весит шесть, а
        // 4-3 — семь. По весу вперёд идёт 4-3, по числам — 6-0.
        val hand = listOf(tile(4, 3), tile(6, 0), tile(5, 1))

        assertEquals(listOf(tile(6, 0), tile(5, 1), tile(4, 3)), TileOrder.BY_NUMBERS.sort(hand))
        assertEquals(listOf(tile(4, 3), tile(6, 0), tile(5, 1)), TileOrder.BY_PIPS.sort(hand))
    }

    @Test
    fun `порядок не теряет и не добавляет костей`() {
        val hand = TileSet.full

        for (order in TileOrder.entries) {
            val sorted = order.sort(hand)
            assertEquals(hand.size, sorted.size, "порядок ${order.title} изменил число костей")
            assertEquals(hand.toSet(), sorted.toSet(), "порядок ${order.title} подменил кости")
        }
    }

    @Test
    fun `порядок однозначен на всей руке из набора`() {
        // Две кости с одинаковым весом в наборе есть (6-4 и 5-5), поэтому
        // проверяем не только вес, но и что сортировка вообще не оставляет
        // выбора: повторный проход по уже уложенной руке её не двигает.
        for (order in TileOrder.entries) {
            val sorted = order.sort(TileSet.full)
            assertEquals(sorted, order.sort(sorted), "порядок ${order.title} не устойчив")
        }
    }

    @Test
    fun `переключение по кругу возвращается к началу`() {
        val first = TileOrder.entries.first()

        var order = first
        repeat(TileOrder.entries.size - 1) { order = order.next() }
        assertNotEquals(first, order)
        assertEquals(first, order.next(), "круг не вернулся к первому порядку")
    }
}
