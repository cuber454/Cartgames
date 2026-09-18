package games.engine.kozel

import games.engine.tiles.Tile
import games.engine.tiles.TileSet
import games.engine.tiles.tile
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Раунд: раздача, обязательный ход, закрытый базар, «рыба» и очки.
 *
 * Отдельно проверяется то, ради чего всё и затевалось: базар никому не
 * показывает своего содержимого, и ход из него берётся вслепую.
 */
class KozelRoundTest {

    @Test
    fun `раздача — по семь костей, четырнадцать в базар, все двадцать восемь`() {
        val round = KozelRound.deal(KozelRules.BOOK, Random(7))

        assertEquals(7, round.handSize(0))
        assertEquals(7, round.handSize(1))
        assertEquals(14, round.bazaarSize)

        val dealt = round.handOf(0) + round.handOf(1) + round.bazaarTiles
        assertEquals(TileSet.full.size, dealt.size)
        assertEquals(TileSet.full.toSet(), dealt.toSet())
    }

    @Test
    fun `первым ходит тот, у кого старший дубль`() {
        val hands = listOf(
            listOf(tile(0, 1), tile(2, 2)),
            listOf(tile(3, 3), tile(4, 4)),
        )
        assertEquals(1, KozelRound.openerSeat(hands))
    }

    @Test
    fun `дублей нет — ходит старшая кость по сумме точек`() {
        val hands = listOf(
            listOf(tile(0, 1), tile(3, 4)),
            listOf(tile(2, 3), tile(5, 6)),
        )
        // Пять-шесть — одиннадцать точек против семи у тройки-четвёрки.
        assertEquals(1, KozelRound.openerSeat(hands))
    }

    @Test
    fun `есть чем ходить — ходить обязательно, базар не берут`() {
        val round = roundOf(
            hands = listOf(listOf(tile(3, 4)), listOf(tile(1, 1))),
            bazaar = listOf(tile(6, 6)),
            line = lineWithEnds(3, 6),
        )

        assertEquals(listOf(KozelMove.Place(tile(3, 4), End.LEFT)), round.legalMoves(0))
    }

    @Test
    fun `нет подходящих — берут из базара, и ход остаётся у того же места`() {
        val round = roundOf(
            hands = listOf(listOf(tile(1, 1)), listOf(tile(2, 2))),
            bazaar = listOf(tile(1, 2), tile(3, 6)),
            line = lineWithEnds(3, 6),
        )

        assertEquals(listOf(KozelMove.Draw), round.legalMoves(0))
        round.apply(0, KozelMove.Draw)

        // Взятая кость осталась на руке, ход не перешёл: не подошла — берём ещё.
        assertEquals(2, round.handSize(0))
        assertEquals(0, round.turn)
        assertEquals(listOf(KozelMove.Draw), round.legalMoves(0))

        round.apply(0, KozelMove.Draw)

        // Вторая кость подошла — теперь её обязаны положить, а не брать ещё.
        assertEquals(0, round.turn)
        assertTrue(round.legalMoves(0).all { it is KozelMove.Place })
        assertEquals(3, round.handSize(0))
    }

    @Test
    fun `базар пуст и ходить нечем — ход переходит сопернику`() {
        val round = roundOf(
            hands = listOf(listOf(tile(1, 1)), listOf(tile(2, 2))),
            line = lineWithEnds(3, 6),
        )

        assertEquals(listOf(KozelMove.Pass), round.legalMoves(0))
        round.apply(0, KozelMove.Pass)

        assertEquals(1, round.turn)
        assertFalse(round.finished)
    }

    @Test
    fun `два пропуска подряд закрывают линию «рыбой»`() {
        val round = roundOf(
            // Четыре точки против десяти: раунд за тем, у кого рука легче.
            hands = listOf(listOf(tile(1, 1), tile(1, 1)), listOf(tile(2, 2), tile(3, 3))),
            line = lineWithEnds(3, 6),
        )

        round.apply(0, KozelMove.Pass)
        round.apply(1, KozelMove.Pass)

        assertTrue(round.finished)
        assertTrue(round.fish)
        assertEquals(null, round.out)

        // Рука места 0 легче — раунд за ним, и записывает он чужую руку.
        val score = round.score()
        assertEquals(0, score.winner)
        assertEquals(10, score.points)
        assertEquals(round.handPoints(1), score.points)
        assertTrue(score.fish)
    }

    @Test
    fun `равные руки на «рыбе» — ничья, очков не пишет никто`() {
        val round = roundOf(
            hands = listOf(listOf(tile(1, 2)), listOf(tile(0, 3))),
            line = lineWithEnds(3, 6),
            turn = 0,
        )
        round.apply(0, KozelMove.Pass)
        round.apply(1, KozelMove.Pass)

        val score = round.score()
        assertEquals(null, score.winner)
        assertEquals(0, score.points)
        assertFalse(round.finished && round.out != null)
    }

    @Test
    fun `вышел первым — записал себе чужую руку`() {
        val round = roundOf(
            hands = listOf(listOf(tile(3, 4)), listOf(tile(1, 2), tile(0, 0))),
            line = lineWithEnds(3, 6),
        )

        round.apply(0, KozelMove.Place(tile(3, 4), End.LEFT))

        assertTrue(round.finished)
        assertEquals(0, round.out)
        val score = round.score()
        assertEquals(0, score.winner)
        assertEquals(3, score.points)
        assertFalse(score.fish)
    }

    @Test
    fun `пусто-пусто по договорённости стоит двадцать пять`() {
        val rules = KozelRules(emptyDoubleBonus = KozelRules.EMPTY_DOUBLE_AS_BONUS)
        val round = roundOf(
            hands = listOf(listOf(tile(3, 4)), listOf(tile(0, 0))),
            line = lineWithEnds(3, 6),
            rules = rules,
        )

        assertEquals(25, round.handPoints(1))
        round.apply(0, KozelMove.Place(tile(3, 4), End.LEFT))
        assertEquals(25, round.score().points)
    }

    @Test
    fun `по книге пусто-пусто стоит как есть, то есть ноль`() {
        val round = roundOf(
            hands = listOf(listOf(tile(3, 4)), listOf(tile(0, 0))),
            line = lineWithEnds(3, 6),
        )

        assertEquals(0, round.handPoints(1))
    }

    @Test
    fun `концы объявляются вслух`() {
        val empty = listOf(emptyList<Tile>(), emptyList())

        assertEquals("Концы: три и шесть", roundOf(empty, line = lineWithEnds(3, 6)).spokenEnds())

        // Одинаковые концы — один конец, а не «три и три».
        assertEquals("Конец: три", roundOf(empty, line = lineWithEnds(3, 3)).spokenEnds())

        // Пустая линия — концов нет вовсе: первая кость ложится в пустоту.
        assertEquals("Концов нет", roundOf(empty).spokenEnds())
    }

    @Test
    fun `ход не того места не принимается`() {
        val round = roundOf(
            hands = listOf(listOf(tile(3, 4)), listOf(tile(3, 3))),
            line = lineWithEnds(3, 6),
            turn = 0,
        )

        assertTrue(round.legalMoves(1).isEmpty())
        assertTrue(
            runCatching { round.apply(1, KozelMove.Pass) }.isFailure,
            "ход чужого места должен быть отвергнут",
        )
    }
}
