package games.engine.hundred

import games.engine.Card
import games.engine.Difficulty
import games.engine.Rank
import games.engine.Suit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Соперник «101». Выбор у него небогатый, и это как раз то, что надо
 * проверить: законный ход в любом положении, дорогое вперёд, дама — на
 * последний ход. И матч целиком: движок и бот вместе обязаны доходить до
 * выбывания, а не кружить за столом вечно.
 */
class HundredBotTest {

    private fun card(rank: Rank, suit: Suit) = Card(rank, suit)

    /**
     * Ход бота, а на конце кона — «играем дальше»: раздачу движок сам не
     * начинает, и без этого ответа матч за столом встал бы ([Hundred.nextDeal]).
     */
    private fun Hundred.step(seat: Int, move: HundredMove) {
        apply(seat, move)
        if (awaitingDeal()) nextDeal()
    }

    /** Ход бота всегда среди допустимых — этого от него и требуется. */
    @Test
    fun `бот ходит только законно`() {
        val random = Random(21)
        repeat(200) { attempt ->
            val game = Hundred.start(random = Random(attempt), playerCount = 3)
            var moves = 0
            while (!game.finished && moves < 400) {
                val seat = game.turn
                val move = HundredBot.chooseMove(game, seat, Difficulty.CLEVER, random)
                if (move == null) break
                assertTrue(
                    move in game.legalMoves(seat),
                    "незаконный ход: $move (ходы ${game.legalMoves(seat)})",
                )
                game.step(seat, move)
                moves++
            }
        }
    }

    /** Нечем ходить — бот берёт карту, а не ищет невозможное. */
    @Test
    fun `когда нечем — бот берёт карту`() {
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(
                listOf(card(Rank.SEVEN, Suit.SPADES), card(Rank.SIX, Suit.HEARTS)),
                listOf(card(Rank.KING, Suit.DIAMONDS)),
            ),
            pile = listOf(card(Rank.ACE, Suit.DIAMONDS)),
            stock = listOf(card(Rank.NINE, Suit.CLUBS)),
            turn = 0,
        )

        assertEquals(HundredMove.Draw, HundredBot.chooseMove(game, 0, Difficulty.CLEVER))
    }

    /** Чужой ход бот не придумывает: двигать нечего — вернёт null. */
    @Test
    fun `не свой ход — бот молчит`() {
        val game = Hundred.start(random = Random(4), playerCount = 2, firstDealer = 0)

        assertNull(HundredBot.chooseMove(game, 0))
    }

    /**
     * Дорогое вперёд, дешёвое назад: оставшиеся на руке карты записываются
     * штрафом, поэтому первым уходит то, что дороже стоит.
     *
     * Король крести здесь дешевле туза, хоть и не перекликается с рукой,
     * а девятка пик дешевле обоих — она и остаётся.
     */
    @Test
    fun `бот сбрасывает дорогое`() {
        val expensive = card(Rank.ACE, Suit.SPADES)
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(
                listOf(expensive, card(Rank.NINE, Suit.SPADES), card(Rank.SIX, Suit.CLUBS)),
                listOf(card(Rank.SIX, Suit.HEARTS)),
            ),
            pile = listOf(card(Rank.SIX, Suit.SPADES)),
            turn = 0,
        )

        assertEquals(
            HundredMove.Play(expensive),
            HundredBot.chooseMove(game, 0, Difficulty.NORMAL),
            "туз дороже шестёрки — первым на кон идёт он",
        )
    }

    /**
     * Даму бот придерживает на выход: выйти дамой выгодно, но выйти ею надо
     * последней картой. Пока рука коротка, даму держат — девятка дешевле и
     * уходит первой, а дама остаётся на последний ход.
     */
    @Test
    fun `бот придерживает даму на выход`() {
        val queen = card(Rank.QUEEN, Suit.SPADES)
        val nine = card(Rank.NINE, Suit.SPADES)
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(
                listOf(queen, nine),
                listOf(card(Rank.SIX, Suit.HEARTS)),
            ),
            pile = listOf(card(Rank.SIX, Suit.SPADES)),
            turn = 0,
        )

        assertEquals(
            HundredMove.Play(nine),
            HundredBot.chooseMove(game, 0, Difficulty.NORMAL),
            "даму держат, девятку отдают",
        )
    }

    /**
     * Матч целиком, все три места за ботом: движок обязан довести его до
     * конца — выбывания и победителя, — и ни разу не пустить ход мимо правил.
     */
    @Test
    fun `матч ботов доходит до выбывания`() {
        val random = Random(99)
        repeat(20) { seed ->
            val game = Hundred.start(random = Random(seed), playerCount = 3)
            var moves = 0
            while (!game.finished && moves < 20_000) {
                val seat = game.turn
                val move = HundredBot.chooseMove(game, seat, Difficulty.NORMAL, random)
                if (move == null) break
                game.step(seat, move)
                moves++
            }

            assertTrue(game.finished, "матч на seed $seed не кончился за $moves ходов")
            assertNotNull(game.winner, "матч на seed $seed кончился без победителя")
            assertEquals(1, game.aliveSeats().size, "за столом должен остаться один")
        }
    }

    /**
     * Хитрый бот считает вышедшие карты и не ходит мастью, которая вот-вот
     * кончится, — но правило остаётся правилом: он так же обязан ходить
     * только законно, и партия с ним тоже доходит до конца.
     */
    @Test
    fun `хитрый бот доводит матч до конца`() {
        val random = Random(5)
        repeat(10) { seed ->
            val game = Hundred.start(random = Random(seed), playerCount = 2)
            var moves = 0
            while (!game.finished && moves < 20_000) {
                val seat = game.turn
                val move = HundredBot.chooseMove(game, seat, Difficulty.CLEVER, random) ?: break
                game.step(seat, move)
                moves++
            }

            assertTrue(game.finished, "матч на seed $seed не кончился")
        }
    }
}
