package games.engine.kozel

import games.engine.tiles.tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun `линия раскладывается поворотами костей, а не вразнобой`() {
        // Линия собирается ходами, и каждая кость ложится той половиной,
        // которой подошла: наружу у неё остаётся вторая.
        val line = Line.EMPTY
            .place(tile(3, 6), End.LEFT)
            .place(tile(4, 6), End.RIGHT)
            .place(tile(1, 3), End.LEFT)

        // Слева направо: 1-3 (наружу единицей), 3-6, 6-4 (наружу четвёркой).
        assertEquals(
            listOf(LaidTile(1, 3), LaidTile(3, 6), LaidTile(6, 4)),
            line.laid(),
        )
        // Соседние половины сходятся: нарисованная линия не разъедется.
        val laid = line.laid()
        for (i in 0 until laid.size - 1) {
            assertEquals(laid[i].right, laid[i + 1].left, "кости $i и ${i + 1} не сходятся")
        }
        assertEquals(line.left, laid.first().left)
        assertEquals(line.right, laid.last().right)
    }

    @Test
    fun `дубль в линии смотрит наружу тем же числом`() {
        val line = Line.EMPTY.place(tile(3, 6), End.LEFT).place(tile(6, 6), End.RIGHT)

        assertEquals(listOf(LaidTile(3, 6), LaidTile(6, 6)), line.laid())
    }

    @Test
    fun `пустая линия раскладывается в пустое`() {
        assertEquals(emptyList(), Line.EMPTY.laid())
    }

    @Test
    fun `кость, подходящая к обоим концам, даёт два хода`() {
        val moves = lineWithEnds(3, 6).placements(listOf(tile(3, 6)))
        assertEquals(2, moves.size)
        assertTrue(moves.any { it.end == End.LEFT })
        assertTrue(moves.any { it.end == End.RIGHT })
    }

    @Test
    fun `два дубля по концам — можно выложить оба за один ход`() {
        // Кость на руке ищется по числу, а не по порядку в руке: четвёрка
        // идёт к четвёрке, единица к единице, и порядок тут ни при чём.
        val both = lineWithEnds(4, 1).bothDoubles(listOf(tile(1, 1), tile(5, 6), tile(4, 4)))
        assertEquals(KozelMove.PlaceBoth(tile(4, 4), tile(1, 1)), both)
    }

    @Test
    fun `дубль только к одному концу — двумя не ходят`() {
        assertNull(lineWithEnds(4, 1).bothDoubles(listOf(tile(4, 4), tile(5, 6))))
    }

    @Test
    fun `концы сошлись — двумя не ходят, дубль к этому числу в наборе один`() {
        // Концы линии оба четвёрки, а дубль 4-4 на руке один: приставить его
        // к двум концам сразу нечем.
        val line = Line(listOf(tile(4, 6), tile(6, 4)), 4, 4)
        assertNull(line.bothDoubles(listOf(tile(4, 4), tile(5, 6))))
    }

    @Test
    fun `пустая линия — двумя не ходят, концов, к которым класть, нет`() {
        assertNull(Line.EMPTY.bothDoubles(listOf(tile(4, 4), tile(1, 1))))
    }

    @Test
    fun `оба дубля — это сверх обычных ходов, а не вместо них`() {
        val line = lineWithEnds(4, 1)
        val hand = listOf(tile(4, 4), tile(1, 1))

        val moves = line.movesFor(hand, bazaarSize = 0)

        assertTrue(moves.contains(KozelMove.Place(tile(4, 4), End.LEFT)))
        assertTrue(moves.contains(KozelMove.Place(tile(1, 1), End.RIGHT)))
        assertTrue(moves.contains(KozelMove.PlaceBoth(tile(4, 4), tile(1, 1))))
    }

    @Test
    fun `пустую линию открывают младшим дублем — и только им`() {
        val hand = listOf(tile(4, 4), tile(5, 6), tile(1, 2))

        val moves = Line.EMPTY.movesFor(hand, bazaarSize = 14)

        // Ход один: ни взять из базара, ни положить что-то другое нельзя.
        assertEquals(listOf(KozelMove.Place(tile(4, 4), End.LEFT)), moves)
    }

    @Test
    fun `младший дубль из двух — тот, что меньше числом`() {
        val hand = listOf(tile(5, 5), tile(1, 1), tile(6, 3))

        assertEquals(listOf(KozelMove.Place(tile(1, 1), End.LEFT)), Line.EMPTY.movesFor(hand, 0))
    }

    @Test
    fun `дублей нет ни у кого — первый ход свободен`() {
        val hand = listOf(tile(5, 6), tile(3, 2))

        val moves = Line.EMPTY.movesFor(hand, bazaarSize = 0)

        assertEquals(2, moves.size)
        assertTrue(moves.contains(KozelMove.Place(tile(5, 6), End.LEFT)))
    }

    @Test
    fun `правило первого хода кончается вместе с пустой линией`() {
        // Дубль 4-4 на руке, но линия уже начата: ходят тем, что подходит,
        // и дубль здесь — не обязанность, а один из ходов.
        val hand = listOf(tile(4, 4), tile(5, 6))

        val moves = lineWithEnds(4, 6).movesFor(hand, bazaarSize = 0)

        assertTrue(moves.contains(KozelMove.Place(tile(5, 6), End.RIGHT)))
    }
}
