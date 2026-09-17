package games.engine.thousand

import games.engine.Card
import games.engine.Rank
import games.engine.Suit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThousandMatchTest {

    private fun c(rank: Rank, suit: Suit) = Card(rank, suit)

    /** Сыграть кон «на бумаге»: матчу для счёта нужны очки и взятки, а не ходы. */
    private fun ThousandMatch.play(
        declarer: Int,
        bid: Int,
        points: List<Int>,
        tricks: List<Int>,
    ): RoundSummary = record(declarer, bid, points.toIntArray(), tricks.toIntArray())

    @Test
    fun `заказчик выполнил заказ — пишет ровно заказ`() {
        val match = ThousandMatch.forTesting(scores = listOf(0, 0))
        val summary = match.play(declarer = 0, bid = 100, points = listOf(110, 25), tricks = listOf(7, 4))

        assertEquals(listOf(100, 25), summary.deltas)
        assertEquals(listOf(100, 25), match.scores.toList())
    }

    @Test
    fun `не добрал хотя бы очко — списывает заказ`() {
        val match = ThousandMatch.forTesting(scores = listOf(200, 200))
        val summary = match.play(declarer = 0, bid = 105, points = listOf(104, 60), tricks = listOf(6, 5))

        assertEquals(-105, summary.deltas[0])
        assertEquals(95, match.scores[0])
    }

    @Test
    fun `защитник пишет своё, округлённое до пяти`() {
        val match = ThousandMatch.forTesting(scores = listOf(0, 0))
        val summary = match.play(declarer = 0, bid = 100, points = listOf(110, 17), tricks = listOf(8, 3))
        assertEquals(15, summary.deltas[1])

        val another = ThousandMatch.forTesting(scores = listOf(0, 0))
        val rounded = another.play(declarer = 0, bid = 100, points = listOf(110, 18), tricks = listOf(8, 3))
        assertEquals(20, rounded.deltas[1])
    }

    @Test
    fun `кон без единой взятки — болт`() {
        val match = ThousandMatch.forTesting(scores = listOf(0, 300))
        val summary = match.play(declarer = 0, bid = 100, points = listOf(110, 0), tricks = listOf(11, 0))

        assertEquals(listOf(1), summary.bolted)
        assertEquals(1, match.bolts[1])
        assertEquals(300, match.scores[1])
    }

    @Test
    fun `севший заказчик без взяток тоже получает болт`() {
        // Минус заказ — за недобор, болт — за пустой кон. Это разные
        // наказания, и севшему заказчику книга исключения не делает.
        val match = ThousandMatch.forTesting(scores = listOf(300, 300))
        val summary = match.play(declarer = 0, bid = 120, points = listOf(95, 20), tricks = listOf(0, 11))

        assertEquals(listOf(0), summary.bolted)
        assertEquals(1, match.bolts[0])
        assertEquals(180, match.scores[0], "минус заказ болт не отменяет и не удваивает")
    }

    @Test
    fun `третий болт — минус сто двадцать и болты обнуляются`() {
        val match = ThousandMatch.forTesting(scores = listOf(0, 300), bolts = listOf(0, 2))
        val summary = match.play(declarer = 0, bid = 100, points = listOf(110, 0), tricks = listOf(11, 0))

        assertEquals(listOf(1), summary.bolted)
        assertEquals(180, match.scores[1])
        assertEquals(0, match.bolts[1])
    }

    @Test
    fun `дошёл до восьмисот восьмидесяти — садится на бочку`() {
        val match = ThousandMatch.forTesting(scores = listOf(870, 300))
        val summary = match.play(declarer = 0, bid = 100, points = listOf(110, 0), tricks = listOf(11, 0))

        assertEquals(0, summary.barrelSat)
        assertEquals(0, match.barrelSeat)
        assertEquals(880, match.scores[0])
        assertEquals(0, match.barrelTries)
    }

    @Test
    fun `на бочке очки не пишутся, пока не одолеешь заказ`() {
        val match = ThousandMatch.forTesting(scores = listOf(880, 300), barrelSeat = 0)
        val summary = match.play(declarer = 0, bid = 100, points = listOf(50, 110), tricks = listOf(5, 6))

        assertEquals(0, summary.deltas[0])
        assertEquals(880, match.scores[0])
        assertEquals(1, match.barrelTries, "свой кон не одолела — попытка сгорела")
    }

    @Test
    fun `кон играл не тот, кто на бочке, — попытка не сгорает`() {
        // Бочка в этом кону не играла и провалить его не могла: три чужих
        // кона не должны сбрасывать её с бочки.
        val match = ThousandMatch.forTesting(scores = listOf(880, 300), barrelSeat = 0, barrelTries = 2)
        match.play(declarer = 1, bid = 100, points = listOf(50, 110), tricks = listOf(5, 6))

        assertEquals(2, match.barrelTries)
        assertEquals(0, match.barrelSeat)
        assertEquals(880, match.scores[0])
    }

    @Test
    fun `одолел бочку — победа`() {
        val match = ThousandMatch.forTesting(scores = listOf(880, 300), barrelSeat = 0)
        val summary = match.play(declarer = 0, bid = 100, points = listOf(110, 10), tricks = listOf(8, 3))

        assertEquals(0, summary.winner)
        assertEquals(1000, match.scores[0])
        assertTrue(match.isOver)
    }

    @Test
    fun `три своих провала на бочке — минус сто двадцать и с бочки`() {
        val match = ThousandMatch.forTesting(scores = listOf(880, 300), barrelSeat = 0, barrelTries = 2)
        val summary = match.play(declarer = 0, bid = 100, points = listOf(40, 120), tricks = listOf(4, 7))

        assertEquals(listOf(0), summary.barrelsDropped)
        assertNull(match.barrelSeat)
        assertEquals(760, match.scores[0])
        assertEquals(0, match.barrelTries)
    }

    @Test
    fun `второй на бочке сбивает первого`() {
        val match = ThousandMatch.forTesting(scores = listOf(880, 870), barrelSeat = 0)
        val summary = match.play(declarer = 1, bid = 100, points = listOf(30, 110), tricks = listOf(4, 7))

        assertEquals(listOf(0), summary.barrelsDropped)
        assertEquals(1, summary.barrelSat)
        assertEquals(1, match.barrelSeat)
        assertEquals(760, match.scores[0])
        assertEquals(880, match.scores[1])
    }

    @Test
    fun `перевалил за тысячу — победа`() {
        val match = ThousandMatch.forTesting(scores = listOf(910, 100))
        val summary = match.play(declarer = 0, bid = 100, points = listOf(110, 10), tricks = listOf(8, 3))

        assertEquals(0, summary.winner)
        assertEquals(1010, match.scores[0])
    }

    @Test
    fun `кон засчитывается в счёт матча`() {
        val match = ThousandMatch.forTesting(scores = listOf(1000, 100))
        val summary = match.play(declarer = 0, bid = 100, points = listOf(110, 10), tricks = listOf(8, 3))

        assertEquals(0, summary.winner)
        assertEquals(1, match.roundsPlayed)
    }

    @Test
    fun `втроём бочка обязана назвать потолок и пасовать не может`() {
        val three = ThousandRound.forTesting(
            hands = List(3) { listOf(c(Rank.NINE, Suit.SPADES)) },
            barrelSeat = 1,
        )
        // Ход первого — обычная сотня; бочка следом обязана взять потолок.
        three.apply(0, ThousandMove.Bid(100))
        assertEquals(listOf(120), three.legalMoves(1).filterIsInstance<ThousandMove.Bid>().map { it.amount })
        assertTrue(three.legalMoves(1).none { it == ThousandMove.Pass })
    }

    @Test
    fun `втроём потолок назван до бочки — ей остаётся только пас`() {
        val three = ThousandRound.forTesting(
            hands = List(3) { listOf(c(Rank.NINE, Suit.SPADES)) },
            barrelSeat = 2,
        )
        three.apply(0, ThousandMove.Bid(100))
        three.apply(1, ThousandMove.Bid(120))
        assertEquals(listOf(ThousandMove.Pass), three.legalMoves(2))
    }

    @Test
    fun `вдвоём бочка торгуется как все — обязательства на потолок нет`() {
        val two = ThousandRound.forTesting(
            hands = List(2) { listOf(c(Rank.NINE, Suit.SPADES)) },
            barrelSeat = 1,
        )
        two.apply(0, ThousandMove.Bid(100))
        assertTrue(two.legalMoves(1).contains(ThousandMove.Pass))
    }
}
