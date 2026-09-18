package games.engine.kozel

import games.engine.tiles.tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Линия и её концы — сердце правил «Козла»: ошибка здесь означает
 * неправильные ходы у обоих за столом. Проверяем и приставление, и дубли,
 * и то, что концов ровно два.
 */
class KozelRulesTest {

    @Test
    fun `в пустую линию первая кость ложится как угодно`() {
        assertEquals(listOf(End.LEFT), Line.EMPTY.canPlay(tile(3, 6)))

        val line = Line.EMPTY.place(tile(3, 6), End.LEFT)
        assertEquals(listOf(tile(3, 6)), line.tiles)
        assertEquals(3, line.left)
        assertEquals(6, line.right)
    }

    @Test
    fun `кость подходит к концу, если на ней есть его число`() {
        val line = lineWithEnds(3, 6)

        // Тройка-шесть подходит к обоим концам — и влево, и вправо.
        assertEquals(listOf(End.LEFT, End.RIGHT), line.canPlay(tile(3, 6)))
        assertEquals(listOf(End.LEFT), line.canPlay(tile(1, 3)))
        assertEquals(listOf(End.RIGHT), line.canPlay(tile(4, 6)))
        assertEquals(emptyList(), line.canPlay(tile(1, 2)))
    }

    @Test
    fun `наружу смотрит вторая половина кости`() {
        val line = lineWithEnds(3, 6)

        val right = line.place(tile(4, 6), End.RIGHT)
        assertEquals(4, right.right)
        assertEquals(3, right.left)
        assertEquals(listOf(tile(3, 6), tile(4, 6)), right.tiles)

        val left = right.place(tile(1, 3), End.LEFT)
        assertEquals(1, left.left)
        assertEquals(4, left.right)
        assertEquals(listOf(tile(1, 3), tile(3, 6), tile(4, 6)), left.tiles)
    }

    @Test
    fun `дубль занимает конец, но числа на нём не меняет`() {
        val line = lineWithEnds(3, 6)

        val withDouble = line.place(tile(6, 6), End.RIGHT)
        assertEquals(6, withDouble.right)
        assertEquals(3, withDouble.left)
        assertEquals(2, withDouble.tiles.size)

        val otherDouble = line.place(tile(3, 3), End.LEFT)
        assertEquals(3, otherDouble.left)
        assertEquals(6, otherDouble.right)
    }

    @Test
    fun `в линию кладут только одну кость за ход`() {
        val line = lineWithEnds(3, 6).place(tile(4, 6), End.RIGHT)
        assertEquals(2, line.tiles.size)
    }

    @Test
    fun `допустимые ходы перечисляют каждую кость и каждый конец`() {
        val line = lineWithEnds(3, 6)
        val hand = listOf(tile(5, 6), tile(1, 2), tile(3, 3))

        assertEquals(
            setOf(
                KozelMove.Place(tile(5, 6), End.RIGHT),
                KozelMove.Place(tile(3, 3), End.LEFT),
            ),
            line.placements(hand).toSet(),
        )
    }

    @Test
    fun `кость, подходящая к обоим концам, даёт два хода`() {
        val moves = lineWithEnds(3, 6).placements(listOf(tile(3, 6)))
        assertEquals(2, moves.size)
        assertTrue(moves.any { it.end == End.LEFT })
        assertTrue(moves.any { it.end == End.RIGHT })
    }
}
