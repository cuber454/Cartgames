package games.engine.hundred

import games.engine.Card
import games.engine.Rank
import games.engine.Suit
import games.engine.fullDeck36
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Правила «101». Спорного в этой игре три вещи — добор одной картой, пятая
 * карта сдатчика на кону и счёт со обнулением ровно на 101, — и на каждую
 * здесь есть тест: договорено именно так, и переиграть это случайно нельзя.
 */
class HundredTest {

    private fun card(rank: Rank, suit: Suit) = Card(rank, suit)

    private val ace = card(Rank.ACE, Suit.DIAMONDS)

    // --- Раздача -----------------------------------------------------------

    /**
     * Всем по пять, сдатчику четыре, и его пятая карта лежит на кону открытой.
     * Заходит сосед сдатчика: сдатчик свой ход уже сделал — этой же картой.
     */
    @Test
    fun `сдатчику четыре карты, пятая на кону`() {
        val game = Hundred.start(random = Random(1), playerCount = 3, firstDealer = 0)

        assertEquals(4, game.handSize(0))
        assertEquals(5, game.handSize(1))
        assertEquals(5, game.handSize(2))
        assertEquals(1, game.pileSize())
        assertEquals(21, game.stockSize())
        assertEquals(1, game.turn)
        assertFalse(game.finished)
    }

    /** Двое за столом — та же раздача, колода только длиннее. */
    @Test
    fun `за двоих раздача та же`() {
        val game = Hundred.start(random = Random(2), playerCount = 2, firstDealer = 0)

        assertEquals(4, game.handSize(0))
        assertEquals(5, game.handSize(1))
        assertEquals(26, game.stockSize())
        assertEquals(1, game.pileSize())
    }

    /** Ни одна карта не потерялась и не задвоилась. */
    @Test
    fun `в раздаче ровно тридцать шесть карт`() {
        val game = Hundred.start(random = Random(3), playerCount = 3)

        val all = (0 until game.playerCount).flatMap { game.handOf(it) } +
            game.stockCards() +
            game.pileCards()

        assertEquals(36, all.size)
        assertEquals(36, all.toSet().size)
    }

    // --- Ход ---------------------------------------------------------------

    /** Подходит карта той же масти или того же достоинства; больше ничего. */
    @Test
    fun `ходят мастью или достоинством`() {
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(
                listOf(card(Rank.SIX, Suit.DIAMONDS), card(Rank.KING, Suit.HEARTS)),
                listOf(card(Rank.NINE, Suit.HEARTS)),
            ),
            pile = listOf(card(Rank.SIX, Suit.SPADES)),
            turn = 0,
        )

        assertEquals(
            listOf(card(Rank.SIX, Suit.DIAMONDS)),
            game.playable(0),
        )
        assertEquals(HundredMove.Play(card(Rank.SIX, Suit.DIAMONDS)), game.legalMoves(0).single())
    }

    /** Не подошло ничего — «взять» единственный ход. */
    @Test
    fun `когда нечем ходить — берут карту`() {
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(
                listOf(card(Rank.SEVEN, Suit.SPADES), card(Rank.SIX, Suit.HEARTS)),
                listOf(ace),
            ),
            pile = listOf(ace),
            stock = listOf(card(Rank.NINE, Suit.CLUBS)),
            turn = 0,
        )

        assertTrue(game.playable(0).isEmpty())
        assertEquals(listOf(HundredMove.Draw), game.legalMoves(0))
    }

    /** Чужого хода не бывает: у соседа список ходов пуст. */
    @Test
    fun `не свой ход — нечего делать`() {
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(
                listOf(card(Rank.SIX, Suit.DIAMONDS)),
                listOf(card(Rank.SIX, Suit.SPADES)),
            ),
            pile = listOf(card(Rank.SIX, Suit.HEARTS)),
            turn = 0,
        )

        assertTrue(game.legalMoves(1).isEmpty())
    }

    // --- Добор одной картой ------------------------------------------------

    /**
     * Подошла — играешь её, и ход остаётся твой: другой подходящей карты в
     * руке не было, выбирать не из чего.
     */
    @Test
    fun `подошедшая карта остаётся у того же игрока`() {
        val drawn = card(Rank.SIX, Suit.DIAMONDS)
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(
                listOf(card(Rank.SEVEN, Suit.SPADES), card(Rank.SIX, Suit.HEARTS)),
                listOf(ace),
            ),
            pile = listOf(ace),
            stock = listOf(drawn),
            turn = 0,
        )

        game.apply(0, HundredMove.Draw)

        assertEquals(0, game.turn)
        assertEquals(3, game.handSize(0))
        assertEquals(listOf(HundredMove.Play(drawn)), game.legalMoves(0))
    }

    /** Не подошла — ход переходит дальше, и карта остаётся в руке. */
    @Test
    fun `не подошедшая карта передаёт ход`() {
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(
                listOf(card(Rank.SEVEN, Suit.SPADES), card(Rank.SIX, Suit.HEARTS)),
                listOf(ace),
            ),
            pile = listOf(ace),
            stock = listOf(card(Rank.NINE, Suit.CLUBS)),
            turn = 0,
        )

        game.apply(0, HundredMove.Draw)

        assertEquals(1, game.turn)
        assertEquals(3, game.handSize(0))
        assertEquals(0, game.stockSize())
    }

    /** Тянуть «до подходящей» мы не играем: за ход ровно одна карта. */
    @Test
    fun `за добор берут ровно одну карту`() {
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(
                listOf(card(Rank.SEVEN, Suit.SPADES), card(Rank.SIX, Suit.HEARTS)),
                listOf(ace),
            ),
            pile = listOf(ace),
            stock = listOf(card(Rank.NINE, Suit.CLUBS), card(Rank.EIGHT, Suit.CLUBS)),
            turn = 0,
        )

        game.apply(0, HundredMove.Draw)

        assertEquals(1, game.stockSize())
        assertEquals(1, game.turn)
    }

    /** Карты не осталось нигде — ход всё равно переходит: «взять» значит «пропустить». */
    @Test
    fun `когда тянуть нечего — ход переходит`() {
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(
                listOf(card(Rank.SEVEN, Suit.SPADES), card(Rank.SIX, Suit.HEARTS)),
                listOf(ace),
            ),
            pile = listOf(ace),
            stock = emptyList(),
            turn = 0,
        )

        game.apply(0, HundredMove.Draw)

        assertEquals(2, game.handSize(0))
        assertEquals(1, game.turn)
    }

    // --- Переворот стопки --------------------------------------------------

    /**
     * Колода кончилась — стопку переворачивают целиком, кроме верхней карты:
     * она остаётся на кону, и по ней продолжают ходить.
     *
     * Первой из перевёрнутой стопки достаётся та карта, что лежала под верхней:
     * стопку переворачивают как есть, а не тасуют.
     */
    @Test
    fun `кончилась колода — стопку переворачивают`() {
        val under = card(Rank.EIGHT, Suit.DIAMONDS)
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(
                listOf(card(Rank.SEVEN, Suit.SPADES), card(Rank.SIX, Suit.HEARTS)),
                listOf(ace),
            ),
            pile = listOf(card(Rank.SEVEN, Suit.DIAMONDS), under, ace),
            stock = emptyList(),
            turn = 0,
        )

        game.apply(0, HundredMove.Draw)

        assertEquals(1, game.stockTurnovers())
        assertEquals(listOf(ace), game.pileCards())
        assertEquals(ace, game.topCard())
        // Из перевёрнутой стопки взяли восьмёрку бубна — она подошла к тузу бубна.
        assertEquals(3, game.handSize(0))
        assertEquals(0, game.turn)
        assertEquals(listOf(HundredMove.Play(under)), game.legalMoves(0))
    }

    // --- Конец кона и счёт -------------------------------------------------

    /** Очки за руку: туз 11, девятка 0, король 4. */
    @Test
    fun `очки за карты считаются по лестнице`() {
        assertEquals(11, points(listOf(card(Rank.ACE, Suit.SPADES))))
        assertEquals(0, points(listOf(card(Rank.NINE, Suit.SPADES))))
        assertEquals(4, points(listOf(card(Rank.KING, Suit.SPADES))))
        // Вся колода: туз 11, десятка 10, восьмёрка 8, семёрка 7, шестёрка 6,
        // король 4, дама 3, валет 2, девятка 0 — по четыре карты каждой.
        assertEquals(4 * (11 + 10 + 8 + 7 + 6 + 4 + 3 + 2), points(fullDeck36()))
    }

    /** Вышел — очков не получаешь, остальные записывают за оставшиеся карты. */
    @Test
    fun `вышедший очков не получает`() {
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(
                listOf(card(Rank.SEVEN, Suit.SPADES)),
                listOf(card(Rank.ACE, Suit.CLUBS), card(Rank.NINE, Suit.HEARTS), card(Rank.KING, Suit.DIAMONDS)),
            ),
            pile = listOf(card(Rank.SEVEN, Suit.DIAMONDS)),
            turn = 0,
        )

        game.apply(0, HundredMove.Play(card(Rank.SEVEN, Suit.SPADES)))

        assertEquals(0, game.scoreOf(0))
        assertEquals(11 + 0 + 4, game.scoreOf(1))
        // Кон кончен — новая раздача, и сдаёт её сосед.
        assertEquals(1, game.dealer())
        assertEquals(5, game.handSize(0))
        assertEquals(4, game.handSize(1))
    }

    /** Ровно 101 — счёт обнуляется, и игрок остаётся в матче. */
    @Test
    fun `ровно сто один обнуляет счёт`() {
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(
                listOf(card(Rank.SEVEN, Suit.SPADES)),
                listOf(card(Rank.ACE, Suit.CLUBS)),
            ),
            pile = listOf(card(Rank.SEVEN, Suit.DIAMONDS)),
            turn = 0,
            scores = listOf(0, 90),
        )

        game.apply(0, HundredMove.Play(card(Rank.SEVEN, Suit.SPADES)))

        assertEquals(0, game.scoreOf(1))
        assertFalse(game.isOut(1))
        assertFalse(game.finished)
    }

    /** Больше 101 — выбывание. Последний оставшийся выигрывает матч. */
    @Test
    fun `больше ста одного выбывает`() {
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(
                listOf(card(Rank.SEVEN, Suit.SPADES)),
                listOf(card(Rank.ACE, Suit.CLUBS)),
            ),
            pile = listOf(card(Rank.SEVEN, Suit.DIAMONDS)),
            turn = 0,
            scores = listOf(0, 91),
        )

        game.apply(0, HundredMove.Play(card(Rank.SEVEN, Suit.SPADES)))

        assertEquals(102, game.scoreOf(1))
        assertTrue(game.isOut(1))
        assertTrue(game.finished)
        assertEquals(0, game.winner)
    }

    /**
     * Дама на выходе: вышедший списывает себе её цену, остальным счёт
     * удваивается — и удвоение считается до проверки 101, иначе дама была бы
     * бесплатной.
     */
    @Test
    fun `дама на выходе удваивает чужой счёт`() {
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(
                listOf(card(Rank.QUEEN, Suit.HEARTS)),
                listOf(card(Rank.ACE, Suit.CLUBS)),
            ),
            pile = listOf(card(Rank.SIX, Suit.HEARTS)),
            turn = 0,
        )

        game.apply(0, HundredMove.Play(card(Rank.QUEEN, Suit.HEARTS)))

        assertEquals(-60, game.scoreOf(0))
        assertEquals(22, game.scoreOf(1))
    }

    /** Дама крести дешевле, бубна дороже — лестница по цене, а не по старшинству. */
    @Test
    fun `цена дамы идёт по масти`() {
        assertEquals(20, queenPrice(Suit.CLUBS))
        assertEquals(40, queenPrice(Suit.SPADES))
        assertEquals(60, queenPrice(Suit.HEARTS))
        assertEquals(80, queenPrice(Suit.DIAMONDS))
    }

    /** Выбывших в следующий кон не сдают: они уже не за столом. */
    @Test
    fun `выбывшему карт не сдают`() {
        val game = Hundred.forTesting(
            playerCount = 3,
            hands = listOf(
                listOf(card(Rank.SEVEN, Suit.SPADES)),
                emptyList<Card>(),
                listOf(card(Rank.NINE, Suit.CLUBS)),
            ),
            pile = listOf(card(Rank.SEVEN, Suit.DIAMONDS)),
            turn = 0,
            out = listOf(false, true, false),
        )

        game.apply(0, HundredMove.Play(card(Rank.SEVEN, Suit.SPADES)))

        assertEquals(0, game.handSize(1))
        assertEquals(5, game.handSize(0))
        assertEquals(4, game.handSize(2))
        assertEquals(2, game.dealer())
        assertEquals(listOf(1), game.outSeats())
    }

    /** Ход через выбывшего перешагивает: за столом его больше нет. */
    @Test
    fun `ход перешагивает через выбывшего`() {
        val game = Hundred.forTesting(
            playerCount = 3,
            hands = listOf(
                listOf(card(Rank.SIX, Suit.DIAMONDS), card(Rank.SIX, Suit.HEARTS)),
                emptyList<Card>(),
                listOf(card(Rank.NINE, Suit.CLUBS)),
            ),
            pile = listOf(card(Rank.SIX, Suit.SPADES)),
            turn = 0,
            out = listOf(false, true, false),
        )

        game.apply(0, HundredMove.Play(card(Rank.SIX, Suit.DIAMONDS)))

        assertEquals(2, game.turn)
    }

    /** Ход недопустимый делать нечем: движок его отвергает. */
    @Test
    fun `чужую карту на кон не положить`() {
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(
                listOf(card(Rank.SEVEN, Suit.SPADES), card(Rank.SIX, Suit.HEARTS)),
                listOf(ace),
            ),
            pile = listOf(ace),
            turn = 0,
        )

        val rejected = runCatching { game.apply(0, HundredMove.Play(card(Rank.SIX, Suit.HEARTS))) }

        assertTrue(rejected.isFailure)
    }
}
