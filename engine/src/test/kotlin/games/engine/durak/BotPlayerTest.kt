package games.engine.durak

import games.engine.Card
import games.engine.Rank
import games.engine.Suit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BotPlayerTest {

    private val trump = Suit.SPADES

    private fun c(rank: Rank, suit: Suit) = Card(rank, suit)

    /** Колода на десять карт: нужна, чтобы бот не считал партию конченной. */
    private val smallDeck = Rank.entries.map { c(it, Suit.DIAMONDS) } + c(Rank.SIX, Suit.CLUBS)

    @Test
    fun `новичок отдаёт допустимый ход`() {
        val game = DurakGame.start(Random(5))
        val seat = game.attacker
        val move = BotPlayer.chooseMove(game, seat, Difficulty.NOVICE, Random(3))

        assertNotNull(move)
        assertTrue(game.legalMoves(seat).contains(move))
    }

    @Test
    fun `обычный бьётся самой дешёвой картой`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS)),
                listOf(c(Rank.NINE, Suit.HEARTS), c(Rank.KING, Suit.HEARTS)),
            ),
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))

        assertEquals(
            DurakMove.Defend(c(Rank.NINE, Suit.HEARTS), 0),
            BotPlayer.chooseMove(game, 1, Difficulty.NORMAL),
        )
    }

    @Test
    fun `обычный ходит младшей картой`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SIX, Suit.CLUBS), c(Rank.TEN, Suit.CLUBS)),
                listOf(c(Rank.SEVEN, Suit.HEARTS)),
            ),
        )

        assertEquals(
            DurakMove.Attack(c(Rank.SIX, Suit.CLUBS)),
            BotPlayer.chooseMove(game, 0, Difficulty.NORMAL),
        )
    }

    @Test
    fun `хитрый не тратит козырь на мелкую карту в начале партии`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS)),
                listOf(c(Rank.SIX, Suit.SPADES), c(Rank.EIGHT, Suit.DIAMONDS)),
            ),
            deck = smallDeck,
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))

        assertEquals(
            DurakMove.Take,
            BotPlayer.chooseMove(game, 1, Difficulty.CLEVER),
            "единственный козырь на семёрку — выгоднее забрать",
        )
    }

    @Test
    fun `хитрый бьёт козырем, когда козырей много`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS)),
                listOf(
                    c(Rank.SIX, Suit.SPADES),
                    c(Rank.SEVEN, Suit.SPADES),
                    c(Rank.EIGHT, Suit.SPADES),
                    c(Rank.NINE, Suit.SPADES),
                ),
            ),
            deck = smallDeck,
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))

        assertEquals(
            DurakMove.Defend(c(Rank.SIX, Suit.SPADES), 0),
            BotPlayer.chooseMove(game, 1, Difficulty.CLEVER),
        )
    }

    @Test
    fun `хитрый подкидывает младшую, а старшую придерживает`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SIX, Suit.HEARTS), c(Rank.SIX, Suit.CLUBS), c(Rank.SEVEN, Suit.CLUBS)),
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.EIGHT, Suit.HEARTS), c(Rank.NINE, Suit.HEARTS)),
            ),
            deck = smallDeck,
        )
        game.apply(0, DurakMove.Attack(c(Rank.SIX, Suit.HEARTS)))
        game.apply(1, DurakMove.Defend(c(Rank.SEVEN, Suit.HEARTS), 0))

        assertEquals(
            DurakMove.Attack(c(Rank.SIX, Suit.CLUBS)),
            BotPlayer.chooseMove(game, 0, Difficulty.CLEVER),
        )
    }

    @Test
    fun `хитрый говорит бито, когда подкидывать нечем дешёвым`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.CLUBS)),
                listOf(c(Rank.ACE, Suit.HEARTS), c(Rank.EIGHT, Suit.DIAMONDS), c(Rank.NINE, Suit.DIAMONDS)),
            ),
            deck = smallDeck,
        )
        game.apply(0, DurakMove.Attack(c(Rank.KING, Suit.HEARTS)))
        game.apply(1, DurakMove.Defend(c(Rank.ACE, Suit.HEARTS), 0))

        assertEquals(
            DurakMove.Pass,
            BotPlayer.chooseMove(game, 0, Difficulty.CLEVER),
            "второй король — слишком дорогая карта для подкидывания",
        )
    }

    @Test
    fun `партия двух ботов доигрывается до конца`() {
        for (seed in 1..10) {
            val game = DurakGame.start(Random(seed))
            val difficulty = if (seed % 2 == 0) Difficulty.CLEVER else Difficulty.NORMAL
            var guard = 0

            while (!game.finished && guard++ < 2000) {
                val seat = if (game.legalMoves(game.attacker).isNotEmpty()) game.attacker else game.defender
                val move = BotPlayer.chooseMove(game, seat, difficulty, Random(seed * 31 + guard))
                assertNotNull(move, "ход должен быть, seed=$seed")
                game.apply(seat, move)
            }

            assertTrue(game.finished, "партия на seed=$seed не доигралась за 2000 ходов")
        }
    }

    @Test
    fun `обычный переводит, когда отбиться нечем`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.EIGHT, Suit.HEARTS), c(Rank.NINE, Suit.HEARTS)),
                listOf(c(Rank.SEVEN, Suit.CLUBS), c(Rank.SIX, Suit.CLUBS)),
            ),
            deck = smallDeck,
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))

        assertEquals(
            DurakMove.Transfer(c(Rank.SEVEN, Suit.CLUBS)),
            BotPlayer.chooseMove(game, 1, Difficulty.NORMAL),
            "трефы не бьют черву, а перевести семёрку есть чем",
        )
    }

    @Test
    fun `хитрый переводит, чтобы не тратить козырь на мелкую карту`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.EIGHT, Suit.HEARTS), c(Rank.NINE, Suit.HEARTS)),
                listOf(c(Rank.SIX, Suit.SPADES), c(Rank.SEVEN, Suit.CLUBS)),
            ),
            deck = smallDeck,
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))

        assertEquals(
            DurakMove.Transfer(c(Rank.SEVEN, Suit.CLUBS)),
            BotPlayer.chooseMove(game, 1, Difficulty.CLEVER),
            "единственный козырь на семёрку — выгоднее перевести",
        )
    }
}
