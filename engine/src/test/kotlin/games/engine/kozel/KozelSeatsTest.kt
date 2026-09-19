package games.engine.kozel

import games.engine.Difficulty
import games.engine.tiles.TileSet
import games.engine.tiles.tile
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Стол на троих в «Козле».
 *
 * Набор «дубль-шесть» — 28 костей, и он один решает, сколько бывает мест:
 * по семь на руке двоим и троим, а базар сжимается с четырнадцати костей
 * до семи. Дальше всё идёт по кругу, и правила остаются те же (Катерина,
 * 19.09): вышедший не пишет ничего, каждый из оставшихся считает своё.
 */
class KozelSeatsTest {

    /** Втроём базар короче вдвое: семь костей вместо четырнадцати. */
    @Test
    fun `на троих по семь костей и семь в базаре`() {
        val round = KozelRound.deal(KozelRules.BOOK, Random(1), seats = 3)

        assertEquals(3, round.seats)
        for (seat in 0 until 3) {
            assertEquals(HAND_SIZE, round.handSize(seat), "рука места $seat")
        }
        assertEquals(TileSet.full.size - HAND_SIZE * 3, round.bazaarSize)

        // И ни одна кость не потерялась и не задвоилась.
        val all = round.bazaarTiles + (0 until 3).flatMap { round.handOf(it) }
        assertEquals(TileSet.full.size, all.size)
        assertEquals(TileSet.full.size, all.toSet().size)
    }

    /**
     * Ход идёт по кругу: сходил — и ходит сосед, а не ты снова. На троих это
     * первое, что ломается, если места по-прежнему «двое»: третий за столом
     * просто не получил бы хода.
     */
    @Test
    fun `ход идёт по кругу через все три места`() {
        val round = roundOf(
            hands = listOf(
                listOf(tile(3, 3), tile(0, 0)),
                listOf(tile(3, 4), tile(0, 0)),
                listOf(tile(4, 5), tile(0, 0)),
            ),
            line = lineWithEnds(3, 6),
            turn = 0,
        )

        assertEquals(0, round.turn)
        round.apply(0, KozelMove.Place(tile(3, 3), End.LEFT))
        assertEquals(1, round.turn, "после первого места ходит второе")
        round.apply(1, KozelMove.Place(tile(3, 4), End.LEFT))
        assertEquals(2, round.turn, "после второго ходит третье")
        round.apply(2, KozelMove.Place(tile(4, 5), End.LEFT))
        assertEquals(0, round.turn, "круг замкнулся на первом месте")
    }

    /**
     * «Рыба» закрывает раунд, когда пропустили все за столом. На троих это
     * три пропуска, а не два: пока хоть кто-то не пропустил, ходить есть кому.
     */
    @Test
    fun `рыба закрывает раунд после трёх пропусков`() {
        val round = roundOf(
            hands = listOf(listOf(tile(1, 1)), listOf(tile(2, 2)), listOf(tile(5, 5))),
            line = lineWithEnds(3, 6),
            turn = 0,
        )

        round.apply(0, KozelMove.Pass)
        round.apply(1, KozelMove.Pass)
        assertTrue(!round.finished, "двое пропустили, а третий ещё не ходил")
        round.apply(2, KozelMove.Pass)

        assertTrue(round.finished)
        assertTrue(round.fish)
        assertNull(round.out)
    }

    /**
     * Вышел первым — себе не пишет ничего, а считают своё оба оставшихся.
     * Это и есть решение Катерины 19.09: «если один вышел, то все остальные
     * считают очки свои».
     */
    @Test
    fun `вышел первым на троих — пишут оба оставшихся`() {
        val round = roundOf(
            hands = listOf(
                listOf(tile(3, 3)),
                listOf(tile(3, 4), tile(1, 1)),
                listOf(tile(4, 5), tile(2, 2)),
            ),
            line = lineWithEnds(3, 6),
            turn = 0,
        )

        round.apply(0, KozelMove.Place(tile(3, 3), End.LEFT))

        assertEquals(0, round.out)
        val score = round.score()
        assertEquals(0, score.winner)
        assertEquals(
            listOf(0, round.handPoints(1), round.handPoints(2)),
            score.written,
            "вышедший не пишет, а оставшиеся пишут каждый своё",
        )
        assertTrue(!score.fish)
    }

    /**
     * «Рыба» на троих: не пишет тот, у кого рука легче всех, — она и выиграла,
     * — а пишут остальные. Правило то же, что и при выходе: пишет проигравший,
     * и пишет своё (KOZEL.md, 2.6).
     */
    @Test
    fun `на рыбе пишет каждый, у кого рука тяжелее лёгкой`() {
        val round = roundOf(
            hands = listOf(listOf(tile(1, 1)), listOf(tile(2, 2)), listOf(tile(5, 5))),
            line = lineWithEnds(3, 6),
            turn = 0,
        )

        round.apply(0, KozelMove.Pass)
        round.apply(1, KozelMove.Pass)
        round.apply(2, KozelMove.Pass)

        val score = round.score()
        assertEquals(0, score.winner, "рука места 0 легче всех — раунд за ним")
        assertEquals(listOf(0, round.handPoints(1), round.handPoints(2)), score.written)
        assertTrue(score.fish)
    }

    /**
     * Легче всех оказалось у двоих — первого места нет, и раунд ничейный.
     * Но запись третьему это не отменяет: свою руку он оставил, и она ему
     * так же в счёт. Вдвоём из этого выходит прежнее правило: равные руки —
     * не пишет никто (KOZEL.md, 2.5).
     */
    @Test
    fun `двое с равными лёгкими руками — ничья, но тяжёлый пишет`() {
        val round = roundOf(
            hands = listOf(listOf(tile(0, 2)), listOf(tile(1, 1)), listOf(tile(5, 5))),
            line = lineWithEnds(3, 6),
            turn = 0,
        )

        round.apply(0, KozelMove.Pass)
        round.apply(1, KozelMove.Pass)
        round.apply(2, KozelMove.Pass)

        val score = round.score()
        assertNull(score.winner, "легче всех у двоих — первого места нет")
        assertEquals(0, score.written[0])
        assertEquals(0, score.written[1])
        assertEquals(round.handPoints(2), score.written[2], "тяжёлая рука пишет своё")
    }

    /**
     * Матч на троих помнит свой стол и после диска: число мест выводится из
     * рук, поэтому старые записи на двоих читаются как читались.
     */
    @Test
    fun `матч на троих переживает запись на диск`() {
        val match = KozelMatch(KozelRules.BOOK, Random(3), seats = 3)
        // Раунд должен быть недоигранным: доигранный — итог, а не пауза.
        val seat = match.round.turn
        match.round.apply(seat, match.round.legalMoves(seat).first())

        val restored = KozelSave.read(KozelSave.write(match))
            ?: error("запись на троих не прочиталась")

        assertEquals(3, restored.seats)
        assertEquals(match.table, restored.table)
        assertEquals(match.round.turn, restored.round.turn)
        assertEquals(match.round.table, restored.round.table)
        for (seat in 0 until 3) {
            assertEquals(match.round.handOf(seat), restored.round.handOf(seat), "рука места $seat")
        }
    }

    /** Матч на троих доигрывается до козла, и козёл за столом один. */
    @Test
    fun `матч на троих доигрывается до одного козла`() {
        val goat = playMatch(
            rules = KozelRules(target = 25),
            difficulties = List(3) { Difficulty.NORMAL },
            seed = 5,
            seats = 3,
        )

        assertTrue(goat in 0 until 3, "козёл должен быть за этим столом")
    }
}
