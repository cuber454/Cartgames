package games.engine.durak

import games.engine.Card
import games.engine.Rank
import games.engine.Suit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «Дурак» за столом на троих и больше.
 *
 * До этого движок знал ровно двоих: атакующий и защищающийся были двумя
 * числами, и третьему места в правилах не оставалось — он не получил бы ни
 * одного хода. Здесь проверяются те правила, которые на двоих не видны, а на
 * троих решают всё.
 *
 * Каждое из них — из книги (А. Небесова, главу присылала Катерина 18.09):
 * подкидывают по кругу, право передаётся дальше, и, «даже если у вас на руке
 * окажется король, вы не можете выложить его, пока право подкидывать снова
 * не дойдёт до вас».
 */
class DurakSeatsTest {

    private val trump = Suit.SPADES

    private fun c(rank: Rank, suit: Suit) = Card(rank, suit)

    /**
     * Заход начинает место 0, отбивается место 1, подкидывает место 2.
     * После того как защищающийся отбился, право подкидывать уходит третьему
     * — через голову защищающегося, — а не возвращается атакующему.
     */
    @Test
    fun `право подкидывать идёт по кругу, минуя защищающегося`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.SEVEN, Suit.CLUBS)),
                // Девятка бубён, а не червей: ею отбивают брошенную бубновую
                // семёрку — червовая её не побьёт, масти разные.
                listOf(c(Rank.EIGHT, Suit.HEARTS), c(Rank.NINE, Suit.DIAMONDS)),
                listOf(c(Rank.SEVEN, Suit.DIAMONDS), c(Rank.SIX, Suit.CLUBS)),
            ),
        )

        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))
        assertTrue(game.legalMoves(2).isEmpty(), "пока карта не отбита, подкидывать нечего")
        assertEquals(1, game.turn, "ход защищающегося")

        game.apply(1, DurakMove.Defend(c(Rank.EIGHT, Suit.HEARTS), 0))

        assertEquals(2, game.turn, "стол отбит — подкидывает третий")
        assertTrue(
            game.legalMoves(0).isEmpty(),
            "атакующий ждёт круга: второй раз подряд подкинуть нельзя",
        )
        val moves = game.legalMoves(2)
        assertTrue(moves.contains(DurakMove.Attack(c(Rank.SEVEN, Suit.DIAMONDS))))
        assertFalse(moves.contains(DurakMove.Attack(c(Rank.SIX, Suit.CLUBS))), "не то достоинство")
        assertTrue(moves.contains(DurakMove.Pass))

        // Третий подкинул, защищающийся отбился — право вернулось атакующему.
        game.apply(2, DurakMove.Attack(c(Rank.SEVEN, Suit.DIAMONDS)))
        // Вторая пара на столе — по её номеру и отбиваемся.
        game.apply(1, DurakMove.Defend(c(Rank.NINE, Suit.DIAMONDS), 1))
        assertEquals(0, game.turn, "право подкидывать дошло до атакующего")
        assertTrue(game.legalMoves(0).contains(DurakMove.Attack(c(Rank.SEVEN, Suit.CLUBS))))
    }

    /**
     * Отказ одного не закрывает заход: стол уходит в отбой только тогда, когда
     * отказались все, до кого дошла очередь. На двоих это неотличимо — там
     * отказавшийся один, — а на троих разница видна сразу.
     */
    @Test
    fun `заход кончается, когда отказались все, а не первый`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.SIX, Suit.HEARTS)),
                listOf(c(Rank.EIGHT, Suit.HEARTS), c(Rank.NINE, Suit.HEARTS)),
                listOf(c(Rank.KING, Suit.CLUBS)),
            ),
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))
        game.apply(1, DurakMove.Defend(c(Rank.EIGHT, Suit.HEARTS), 0))

        game.apply(2, DurakMove.Pass)

        assertEquals(0, game.turn, "отказ третьего передал право дальше")
        assertFalse(game.table.isEmpty(), "стол ещё не отбит окончательно")
        assertEquals(0, game.discardSize(), "в отбой пока ничего не ушло")

        game.apply(0, DurakMove.Pass)

        assertTrue(game.table.isEmpty(), "отказались все — стол ушёл в отбой")
        assertEquals(2, game.discardSize())
        assertEquals(1, game.attacker, "кто отбился — тот и атакует")
    }

    /** Заход по кругу обходит всех: за столом на четверых очередь видна целиком. */
    @Test
    fun `очередь обходит весь стол`() {
        // По две карты на руку: колода пуста, и однорукие игроки вышли бы из
        // партии прямо посреди захода — а очередь тогда не круг, а то, что от
        // него осталось.
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.SIX, Suit.HEARTS)),
                listOf(c(Rank.EIGHT, Suit.HEARTS), c(Rank.SIX, Suit.SPADES)),
                listOf(c(Rank.KING, Suit.CLUBS), c(Rank.QUEEN, Suit.CLUBS)),
                listOf(c(Rank.KING, Suit.DIAMONDS), c(Rank.QUEEN, Suit.DIAMONDS)),
            ),
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))
        game.apply(1, DurakMove.Defend(c(Rank.EIGHT, Suit.HEARTS), 0))

        assertEquals(2, game.turn)
        game.apply(2, DurakMove.Pass)
        assertEquals(3, game.turn)
        game.apply(3, DurakMove.Pass)
        assertEquals(0, game.turn)

        game.apply(0, DurakMove.Pass)
        assertTrue(game.table.isEmpty(), "круг замкнулся — заход окончен")
        assertEquals(1, game.attacker)
    }

    /**
     * Защищающийся забрал — свою атаку он пропускает, ход переходит через
     * него. На двоих это «ходит снова тот же игрок», на троих — следующий
     * за защищающимся.
     */
    @Test
    fun `взявший стол пропускает свою атаку`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.NINE, Suit.CLUBS)),
                listOf(c(Rank.SIX, Suit.HEARTS)),
                listOf(c(Rank.TEN, Suit.CLUBS)),
            ),
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))
        game.apply(1, DurakMove.Take)

        assertEquals(2, game.attacker, "атакует следующий за защищающимся")
        assertEquals(2, game.turn)
        assertEquals(2, game.handOf(1).size)
    }

    /**
     * Добор на троих: первым берёт тот, кто начинал заход, последним — тот,
     * под кого ходили. Середину книга не называет — она задана кругом.
     */
    @Test
    fun `добор на троих идёт по кругу, защищающийся последним`() {
        // Пятнадцать карт сверху вниз — ровно по пять на каждого. Первым
        // берёт атакующий, значит верхние достаются ему, а хвост — тому, под
        // кого ходили.
        val deck = Rank.entries.map { c(it, Suit.CLUBS) } +
            Rank.entries.take(6).map { c(it, Suit.DIAMONDS) }
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS)),
                listOf(c(Rank.SIX, Suit.HEARTS)),
                listOf(c(Rank.NINE, Suit.CLUBS)),
            ),
            deck = deck,
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))
        game.apply(1, DurakMove.Take)

        assertEquals(6, game.handOf(0).size)
        assertEquals(6, game.handOf(1).size)
        assertEquals(6, game.handOf(2).size)
        assertEquals(0, game.deckSize(), "колода разошлась целиком")
        assertTrue(game.handOf(0).contains(c(Rank.SIX, Suit.CLUBS)), "верх колоды — атакующему")
        assertTrue(
            game.handOf(1).contains(c(Rank.JACK, Suit.DIAMONDS)),
            "защищающийся берёт последним и получает хвост колоды",
        )
    }

    /**
     * Партия на троих не кончается первым вышедшим: он выиграл, но двое
     * остальных ещё доигрывают, и «дурак» определяется последним.
     */
    @Test
    fun `вышедший первым выигрывает, дурак — последний с картами`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.TEN, Suit.SPADES)),
                listOf(c(Rank.EIGHT, Suit.HEARTS)),
                listOf(c(Rank.NINE, Suit.CLUBS), c(Rank.SIX, Suit.DIAMONDS)),
            ),
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))
        game.apply(1, DurakMove.Defend(c(Rank.EIGHT, Suit.HEARTS), 0))

        assertTrue(game.exitedSeats().contains(1), "защищающемуся нечем больше играть")
        assertFalse(game.finished, "на троих партия этим не кончается")

        game.apply(2, DurakMove.Pass)
        game.apply(0, DurakMove.Pass)
        game.apply(2, DurakMove.Attack(c(Rank.NINE, Suit.CLUBS)))
        game.apply(0, DurakMove.Defend(c(Rank.TEN, Suit.SPADES), 0))

        assertTrue(game.finished)
        assertEquals(1, game.winner, "вышел первым — выиграл")
        assertEquals(2, game.loser, "последний с картами — дурак")
    }

    /** Раздача и козырь на троих: по шесть карт, козырь — по нижней. */
    @Test
    fun `новая партия на троих раздаёт по шесть карт`() {
        val game = DurakGame.start(Random(1), playerCount = 3)

        assertEquals(3, game.playerCount)
        assertEquals(6, game.handOf(0).size)
        assertEquals(6, game.handOf(1).size)
        assertEquals(6, game.handOf(2).size)
        assertEquals(18, game.deckSize(), "36 минус 18 розданных")
        assertEquals(game.trumpCard!!.suit, game.trumpSuit)
        assertEquals(game.attacker, game.turn)
        assertNull(game.winner)
    }
}
