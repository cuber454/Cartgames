package games.engine.durak

import games.engine.Card
import games.engine.Rank
import games.engine.Suit
import games.engine.fullDeck36
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DurakGameTest {

    private val trump = Suit.SPADES

    private fun c(rank: Rank, suit: Suit) = Card(rank, suit)

    @Test
    fun `в колоде 36 разных карт`() {
        val deck = fullDeck36()
        assertEquals(36, deck.size)
        assertEquals(36, deck.toSet().size)
    }

    @Test
    fun `первый ход — любая карта из руки`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(listOf(c(Rank.SIX, Suit.HEARTS), c(Rank.ACE, Suit.CLUBS)), listOf(c(Rank.SEVEN, Suit.HEARTS))),
        )
        assertEquals(2, game.legalMoves(0).size)
        assertTrue(game.legalMoves(1).isEmpty())
    }

    @Test
    fun `защита старшей картой той же масти возможна, младшей — нет`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(listOf(c(Rank.SEVEN, Suit.HEARTS)), listOf(c(Rank.SIX, Suit.HEARTS), c(Rank.EIGHT, Suit.HEARTS))),
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))

        val moves = game.legalMoves(1)
        assertTrue(moves.contains(DurakMove.Defend(c(Rank.EIGHT, Suit.HEARTS), 0)))
        assertFalse(moves.contains(DurakMove.Defend(c(Rank.SIX, Suit.HEARTS), 0)))
        assertTrue(moves.contains(DurakMove.Take))
    }

    @Test
    fun `козырь бьёт некозырную карту, но не наоборот`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(listOf(c(Rank.ACE, Suit.HEARTS)), listOf(c(Rank.SIX, Suit.SPADES))),
        )
        assertTrue(game.beats(c(Rank.SIX, Suit.SPADES), c(Rank.ACE, Suit.HEARTS)))
        assertFalse(game.beats(c(Rank.ACE, Suit.HEARTS), c(Rank.SIX, Suit.SPADES)))
        // Козырь козыря не бьёт, если младше.
        assertFalse(game.beats(c(Rank.SIX, Suit.SPADES), c(Rank.KING, Suit.SPADES)))
    }

    @Test
    fun `подкидывать можно только подходящие достоинства`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.SEVEN, Suit.CLUBS), c(Rank.ACE, Suit.CLUBS)),
                listOf(c(Rank.EIGHT, Suit.HEARTS), c(Rank.NINE, Suit.HEARTS)),
            ),
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))

        val moves = game.legalMoves(0)
        assertTrue(moves.contains(DurakMove.Attack(c(Rank.SEVEN, Suit.CLUBS))))
        assertFalse(moves.contains(DurakMove.Attack(c(Rank.ACE, Suit.CLUBS))))
        // «Бито» рано: карта ещё не отбита.
        assertFalse(moves.contains(DurakMove.Pass))
    }

    @Test
    fun `бито — стол уходит в отбой, атакует тот, кто отбился`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.NINE, Suit.CLUBS)),
                listOf(c(Rank.EIGHT, Suit.HEARTS), c(Rank.TEN, Suit.HEARTS)),
            ),
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))
        game.apply(1, DurakMove.Defend(c(Rank.EIGHT, Suit.HEARTS), 0))
        assertEquals(0, game.attacker)

        game.apply(0, DurakMove.Pass)

        assertEquals(1, game.attacker, "атакует тот, кто отбился")
        assertTrue(game.table.isEmpty())
        assertEquals(2, game.discardSize())
    }

    @Test
    fun `беру — карты уходят защищающемуся, атакует снова тот же игрок`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.NINE, Suit.CLUBS)), listOf(c(Rank.SIX, Suit.HEARTS))),
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))
        game.apply(1, DurakMove.Take)

        assertEquals(0, game.attacker, "защищающийся пропустил атаку")
        assertTrue(game.table.isEmpty())
        assertEquals(2, game.handOf(1).size)
    }

    @Test
    fun `добор идёт до шести карт, атакующий берёт первым`() {
        // Десять карт в колоде, по одной в руках: атакующему нужно шесть,
        // защищающемуся — четыре. Верх колоды достаётся тому, кто берёт первым.
        val deck = Rank.entries.map { c(it, Suit.CLUBS) } + c(Rank.SIX, Suit.DIAMONDS)
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(listOf(c(Rank.SEVEN, Suit.HEARTS)), listOf(c(Rank.SIX, Suit.HEARTS))),
            deck = deck,
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))
        game.apply(1, DurakMove.Take)

        assertEquals(6, game.handOf(0).size)
        assertEquals(6, game.handOf(1).size)
        assertEquals(0, game.deckSize())
        assertTrue(
            game.handOf(0).contains(c(Rank.SIX, Suit.CLUBS)),
            "верхнюю карту колоды берёт атакующий",
        )
    }

    @Test
    fun `партия кончается, когда колода пуста и кто-то вышел`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.NINE, Suit.CLUBS)), listOf(c(Rank.EIGHT, Suit.HEARTS))),
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))
        game.apply(1, DurakMove.Defend(c(Rank.EIGHT, Suit.HEARTS), 0))

        assertTrue(game.finished)
        assertEquals(1, game.winner, "вышел защищающийся — он и выиграл")
        assertEquals(0, game.loser, "у атакующего осталась карта — он дурак")
    }

    @Test
    fun `новая партия раздаёт по шесть карт и назначает козырь`() {
        val game = DurakGame.start(kotlin.random.Random(1))

        assertEquals(2, game.playerCount)
        assertEquals(6, game.handOf(0).size)
        assertEquals(6, game.handOf(1).size)
        assertEquals(24, game.deckSize(), "36 минус 12 розданных")
        assertEquals(game.trumpCard!!.suit, game.trumpSuit, "козырь — по нижней карте колоды")
        assertTrue(game.legalMoves(game.attacker).isNotEmpty())
        assertNull(game.winner)
    }
}
