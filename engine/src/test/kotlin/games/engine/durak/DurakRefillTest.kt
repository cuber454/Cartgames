package games.engine.durak

import games.engine.Card
import games.engine.Rank
import games.engine.Suit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Добор из колоды и раздача — два места, где ошибка не видна за столом.
 *
 * Кому достались карты из колоды, игрок не проверяет: он видит свою руку и
 * не знает, что могло прийти. А раздача вшестером падала исключением —
 * и это единственный случай, когда ошибку видно, потому что партия просто
 * не начинается. Оба правила взяты из книги (Небесова, «Карты»): первым
 * берёт тот, кто начинал заход, а последняя карта раздачи назначает козырь.
 */
class DurakRefillTest {

    private val trump = Suit.SPADES

    private fun c(rank: Rank, suit: Suit) = Card(rank, suit)

    /**
     * Колода подобрана так, чтобы порядок добора читался по ней самой: все
     * карты разные, и по руке сразу видно, кто брал первым.
     */
    private val deck = listOf(
        c(Rank.SIX, Suit.DIAMONDS),
        c(Rank.SEVEN, Suit.DIAMONDS),
        c(Rank.EIGHT, Suit.DIAMONDS),
        c(Rank.NINE, Suit.DIAMONDS),
        c(Rank.TEN, Suit.DIAMONDS),
        c(Rank.JACK, Suit.DIAMONDS),
        c(Rank.QUEEN, Suit.DIAMONDS),
        c(Rank.KING, Suit.DIAMONDS),
        c(Rank.SIX, Suit.CLUBS),
        c(Rank.SEVEN, Suit.CLUBS),
    )

    /** Атакующий кладёт семёрку, защищающийся кроет восьмёркой — стол отбит. */
    private fun beatenTable(): DurakGame {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS)),
                listOf(c(Rank.EIGHT, Suit.HEARTS)),
            ),
            deck = deck,
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))
        game.apply(1, DurakMove.Defend(c(Rank.EIGHT, Suit.HEARTS), 0))
        return game
    }

    /**
     * «Бито»: атака переходит отбившемуся, но карты из колоды первым берёт
     * не он, а тот, кто заход начинал. Ошибка тут не косметическая: колода
     * общая, и от порядка зависит, кому что пришло.
     */
    @Test
    fun `после бито первым добирает тот, кто начинал заход`() {
        val game = beatenTable()
        game.apply(0, DurakMove.Pass)

        // К добору обе руки пусты: карты ушли на стол, а он ушёл в отбой.
        // Поэтому каждый берёт по шесть, и порядок виден по колоде целиком.
        assertEquals(deck.take(6), game.handOf(0), "первым берёт начинавший заход")
        assertEquals(deck.drop(6), game.handOf(1), "за ним — тот, под кого ходили")
        // Атака при этом перешла отбившемуся: он ходит, но добирал вторым.
        assertEquals(1, game.attacker)
    }

    /**
     * «Беру»: атакующий остаётся атакующим и по-прежнему берёт первым.
     * Здесь порядок не должен поехать от починки предыдущего случая.
     */
    @Test
    fun `после беру порядок добора тот же — начинавший заход первым`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS)),
                listOf(c(Rank.EIGHT, Suit.HEARTS)),
            ),
            deck = deck,
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))
        game.apply(1, DurakMove.Take)

        assertEquals(deck.take(6), game.handOf(0))
        // Забранная со стола карта легла в руку до добора, и карты колоды
        // идут после неё. Отбиваться защищающийся не стал — брал как есть.
        assertEquals(
            listOf(c(Rank.EIGHT, Suit.HEARTS), c(Rank.SEVEN, Suit.HEARTS)) + deck.drop(6),
            game.handOf(1),
        )
        assertEquals(0, game.attacker, "забравший пропускает свою атаку")
    }

    /**
     * Вшестером тридцать шесть карт раздаются целиком: нижней карты в колоде
     * не остаётся, открывать нечего. Раньше здесь было исключение —
     * «колода пуста, такого быть не может», и оно было неправдой: такое
     * бывает ровно вшестером. Козырь назначает последняя карта раздачи.
     */
    @Test
    fun `вшестером партия начинается, а козырь назначает последняя карта раздачи`() {
        val game = DurakGame.start(random = Random(4), playerCount = 6)

        assertEquals(6, game.playerCount)
        assertEquals(0, game.deckSize(), "колода разошлась целиком")
        assertEquals(6, game.handOf(0).size)
        // Последней ложится нижняя карта последней руки — она же нижняя карта
        // колоды, до которой раздача как раз и дошла.
        assertEquals(game.handOf(5).last().suit, game.trumpSuit)
    }
}
