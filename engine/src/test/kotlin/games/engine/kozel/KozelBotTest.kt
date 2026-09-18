package games.engine.kozel

import games.engine.Difficulty
import games.engine.tiles.Tile
import games.engine.tiles.tile
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Уровни бота: чем они отличаются на деле.
 *
 * Различие здесь — в памяти, а не в силе (KOZEL.md, 4.3–4.5), поэтому и
 * проверяется оно на раскладах, где память что-то решает: тяжёлая кость
 * против продолжений, ответный конец против закрытого. Требовать от бота
 * «правильного» хода вообще нельзя — можно лишь того, чтобы он был
 * осмысленным ровно настолько, насколько обещано.
 */
class KozelBotTest {

    private fun view(
        hand: List<Tile>,
        line: Line,
        bazaarSize: Int = 0,
        turn: Int = 0,
        seat: Int = 0,
    ): KozelView = KozelView(
        seat = seat,
        hand = hand,
        line = line,
        opponentHandSize = 7,
        bazaarSize = bazaarSize,
        turn = turn,
    )

    private fun move(view: KozelView, difficulty: Difficulty): KozelMove =
        KozelBot.chooseMove(view, difficulty, Random(1))
            ?: error("хода нет, хотя он должен быть")

    @Test
    fun `ходит не тот, чей черёд — бот молчит`() {
        val view = view(listOf(tile(3, 4)), lineWithEnds(3, 6), turn = 1)

        assertTrue(view.legalMoves().isEmpty())
        assertNull(KozelBot.chooseMove(view, Difficulty.CLEVER, Random(1)))
    }

    @Test
    fun `нет подходящей кости — берёт из базара, а с пустым базаром пропускает`() {
        val stuck = view(listOf(tile(1, 1)), lineWithEnds(3, 6), bazaarSize = 4)
        assertEquals(KozelMove.Draw, move(stuck, Difficulty.CLEVER))

        val empty = view(listOf(tile(1, 1)), lineWithEnds(3, 6), bazaarSize = 0)
        assertEquals(KozelMove.Pass, move(empty, Difficulty.CLEVER))
    }

    @Test
    fun `новичок кладёт что попало`() {
        val view = view(listOf(tile(3, 4), tile(5, 6), tile(0, 1)), lineWithEnds(3, 6))

        val picks = (0 until 40).map { KozelBot.chooseMove(view, Difficulty.NOVICE, Random(it)) }.toSet()
        assertTrue(picks.size > 1, "новичок сорок раз подряд сыграл одно и то же: $picks")
        assertTrue(picks.all { it is KozelMove.Place }, "новичок взялся за базар, хотя было чем ходить")
    }

    @Test
    fun `обычный сбрасывает тяжёлую кость`() {
        // Обе кости подходят к концу «три», но шесть-три весит одиннадцать
        // точек против трёх у пусто-три, а тяжёлое на руке — очки соперника.
        val view = view(listOf(tile(0, 3), tile(3, 6)), lineWithEnds(3, 3))

        val chosen = move(view, Difficulty.NORMAL)
        assertEquals(tile(3, 6), (chosen as KozelMove.Place).tile)
    }

    @Test
    fun `при равном весе обычный оставляет себе продолжения`() {
        // Обе кости весят семь точек, но двойка-пять оставляет на руке две
        // играемые кости, а тройка-четыре — одну.
        val view = view(listOf(tile(2, 5), tile(3, 4), tile(5, 5)), lineWithEnds(2, 4))

        val chosen = move(view, Difficulty.NORMAL)
        assertEquals(tile(2, 5), (chosen as KozelMove.Place).tile)
    }

    @Test
    fun `хитрый смотрит, каких костей ещё не вышло`() {
        // Три хода, и два из них обычному безразличны: пять-четыре и
        // тройка-шесть весят по девять точек и оставляют на руке по два
        // играемых. Обычный берёт первый по порядку — пять-четыре, выставляя
        // наружу четвёрку. Хитрый считает невышедшие: под четвёркой и шестёркой
        // осталось по пять костей, под пятёркой четыре, под тройкой пять, —
        // и он кладёт тройку-шесть, оставив сопернику на один ответ меньше.
        val view = view(
            hand = listOf(
                tile(4, 5), tile(3, 6), tile(3, 5),
                tile(0, 4), tile(0, 1), tile(0, 2), tile(1, 2),
            ),
            line = lineWithEnds(5, 6),
        )

        assertEquals(KozelMove.Place(tile(4, 5), End.LEFT), move(view, Difficulty.NORMAL))
        assertEquals(KozelMove.Place(tile(3, 6), End.RIGHT), move(view, Difficulty.CLEVER))
    }

}
