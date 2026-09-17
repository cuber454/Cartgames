package games.engine.thousand

import games.engine.Card
import games.engine.Difficulty
import games.engine.Rank
import games.engine.Suit
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ThousandSimulatorTest {

    private fun c(rank: Rank, suit: Suit) = Card(rank, suit)

    /**
     * Рука, которой грех не взять: все тузы и десятки — старшие карты каждой
     * масти, — да ещё червовый марьяж в придачу. Девяносто одно очко из ста
     * двадцати.
     */
    private fun strongHand(): List<Card> = listOf(
        c(Rank.ACE, Suit.HEARTS), c(Rank.TEN, Suit.HEARTS),
        c(Rank.KING, Suit.HEARTS), c(Rank.QUEEN, Suit.HEARTS),
        c(Rank.ACE, Suit.SPADES), c(Rank.TEN, Suit.SPADES),
        c(Rank.ACE, Suit.CLUBS), c(Rank.TEN, Suit.CLUBS),
        c(Rank.ACE, Suit.DIAMONDS), c(Rank.TEN, Suit.DIAMONDS),
    )

    /** Голая рука: девятки, валеты и две дамы — четырнадцать очков. */
    private fun weakHand(): List<Card> = listOf(
        c(Rank.NINE, Suit.SPADES), c(Rank.JACK, Suit.SPADES),
        c(Rank.NINE, Suit.CLUBS), c(Rank.JACK, Suit.CLUBS),
        c(Rank.NINE, Suit.DIAMONDS), c(Rank.JACK, Suit.DIAMONDS),
        c(Rank.NINE, Suit.HEARTS), c(Rank.JACK, Suit.HEARTS),
        c(Rank.QUEEN, Suit.SPADES), c(Rank.QUEEN, Suit.CLUBS),
    )

    /**
     * Чужая рука: симулятору от неё нужен только размер — что в ней лежит,
     * он не читает. Поэтому в тестах это просто «сколько-то карт не с моей
     * руки».
     */
    private fun other(hand: List<Card>, size: Int = 10): List<Card> =
        fullDeck24().filterNot { it in hand }.take(size)

    private fun roundOf(hand: List<Card>): ThousandRound =
        ThousandRound.forTesting(hands = listOf(hand, other(hand)))

    @Test
    fun `перебор видит разницу между сильной рукой и слабой`() {
        val strong = ThousandSimulator.take(roundOf(strongHand()), 0, random = Random(1))
        val weak = ThousandSimulator.take(roundOf(weakHand()), 0, random = Random(1))
        assertTrue(
            strong.mine > weak.mine + 40,
            "сильная рука берёт ${strong.mine}, слабая ${weak.mine}",
        )
    }

    /**
     * Два кона отличаются только тем, что лежит у соперника. Бот этого не
     * видит — и оценка не должна шевелиться: иначе он играет краплёной
     * колодой.
     */
    @Test
    fun `перебор не подглядывает в чужую руку`() {
        val hand = strongHand()
        val pool = fullDeck24().filterNot { it in hand }
        val one = ThousandRound.forTesting(hands = listOf(hand, pool.take(10)))
        val two = ThousandRound.forTesting(hands = listOf(hand, pool.drop(4).take(10)))

        assertEquals(
            ThousandSimulator.take(one, 0, random = Random(3)).mine,
            ThousandSimulator.take(two, 0, random = Random(3)).mine,
            "оценка не имеет права зависеть от чужих карт",
        )
    }

    /**
     * То же про прикуп: он лежит в коне и всем виден в коде, но бот в него не
     * смотрел. Меняем прикуп — оценка стоять должна.
     */
    @Test
    fun `перебор не подглядывает в прикуп`() {
        val hand = strongHand()
        val theirs = other(hand)
        val first = ThousandRound.forTesting(
            hands = listOf(hand, theirs),
            prikups = listOf(listOf(c(Rank.NINE, Suit.HEARTS)), listOf(c(Rank.JACK, Suit.HEARTS))),
        )
        val second = ThousandRound.forTesting(
            hands = listOf(hand, theirs),
            prikups = listOf(listOf(c(Rank.QUEEN, Suit.DIAMONDS)), listOf(c(Rank.KING, Suit.DIAMONDS))),
        )

        assertEquals(
            ThousandSimulator.take(first, 0, random = Random(5)).mine,
            ThousandSimulator.take(second, 0, random = Random(5)).mine,
            "оценка не имеет права зависеть от того, что лежит в прикупе",
        )
    }

    @Test
    fun `на торге хитрый считает не очки на руке, а то, что возьмёт`() {
        val rich = ThousandSimulator.contract(roundOf(strongHand()), 0, random = Random(2))
        val poor = ThousandSimulator.contract(roundOf(weakHand()), 0, random = Random(2))

        assertTrue(rich.mine > 150, "с такой рукой сотня берётся с запасом, а вышло ${rich.mine}")
        assertTrue(poor.mine < 60, "с такой рукой сотни не набрать, а вышло ${poor.mine}")
        assertTrue(rich.worst > poor.mine, "худший расклад сильной руки лучше лучшего у слабой")
    }

    @Test
    fun `перебор не трогает кон, по которому считает`() {
        repeat(5) { seed ->
            val random = Random(seed)
            val round = ThousandRound.start(random, playerCount = 2)
            var guard = 0
            while (!round.isFinished(round)) {
                check(guard++ < 200) { "кон не кончается" }
                val seat = round.turn
                val hand = round.handOf(seat)
                val points = round.trickPointsAll()

                ThousandSimulator.take(round, seat, deals = 20, random = random)

                assertEquals(hand, round.handOf(seat), "симулятор поменял руку")
                assertEquals(points, round.trickPointsAll(), "симулятор поменял счёт")
                round.apply(seat, assertNotNull(ThousandBot.chooseMove(round, seat, Difficulty.CLEVER, random)))
            }
        }
    }

    @Test
    fun `перебор не тормозит ход`() {
        val round = roundOf(strongHand())
        val elapsed = measureTimeMillis {
            repeat(10) { ThousandSimulator.take(round, 0, random = Random(it)) }
        }
        println("десять переборов по двести раскладов: $elapsed мс")
        assertTrue(elapsed < 3000, "десять переборов заняли $elapsed мс — ход так ждать нельзя")
    }

    @Test
    fun `хитрый пасует на слабой руке и называет на сильной`() {
        val weak = ThousandRound.forTesting(
            hands = listOf(weakHand(), other(weakHand())),
            firstBidder = 1,
        )
        weak.apply(1, ThousandMove.Bid(100))
        assertEquals(
            ThousandMove.Pass,
            ThousandBot.chooseMove(weak, 0, Difficulty.CLEVER, Random(4)),
            "с такой рукой торговаться нечем",
        )

        val strong = ThousandRound.forTesting(
            hands = listOf(strongHand(), other(strongHand())),
            firstBidder = 1,
        )
        strong.apply(1, ThousandMove.Bid(100))
        val move = ThousandBot.chooseMove(strong, 0, Difficulty.CLEVER, Random(4))
        assertTrue(move is ThousandMove.Bid && move.amount >= 105, "сильная рука должна называть, а не пасовать: $move")
    }
}
