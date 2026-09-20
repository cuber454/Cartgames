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
        // Кон кончен, и новый сам не начинается: стол ждёт ответа.
        assertTrue(game.awaitingDeal())

        game.nextDeal()

        // Раздачу начали — сдаёт её сосед.
        assertFalse(game.awaitingDeal())
        assertEquals(1, game.dealer())
        assertEquals(5, game.handSize(0))
        assertEquals(4, game.handSize(1))
    }

    /**
     * Кон кончился — и на этом стол встал: раздачу начинает игрок, а не
     * движок (правило Катерины, 20.09).
     */
    @Test
    fun `новый кон сам не начинается`() {
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(
                listOf(card(Rank.SEVEN, Suit.SPADES)),
                listOf(card(Rank.ACE, Suit.CLUBS)),
            ),
            pile = listOf(card(Rank.SEVEN, Suit.DIAMONDS)),
            turn = 0,
        )

        game.apply(0, HundredMove.Play(card(Rank.SEVEN, Suit.SPADES)))

        assertTrue(game.awaitingDeal())
        // Раздачи ещё не было: руки прежние, кон прежний, ход ничей.
        assertEquals(0, game.handSize(0))
        assertEquals(1, game.handSize(1))
        assertEquals(2, game.pileSize())
        assertTrue(game.legalMoves(0).isEmpty())
        assertTrue(game.legalMoves(1).isEmpty())
        // И хода между конами нет: движок не принимает ни карту, ни добор.
        assertTrue(runCatching { game.apply(1, HundredMove.Draw) }.isFailure)
    }

    /** Ответ «дальше» — и кон сдан: сдатчик сосед, руки по пять, кон новый. */
    @Test
    fun `дальше сдаёт следующий кон`() {
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(
                listOf(card(Rank.SEVEN, Suit.SPADES)),
                listOf(card(Rank.ACE, Suit.CLUBS)),
            ),
            pile = listOf(card(Rank.SEVEN, Suit.DIAMONDS)),
            turn = 0,
        )
        game.apply(0, HundredMove.Play(card(Rank.SEVEN, Suit.SPADES)))

        game.nextDeal()

        assertFalse(game.awaitingDeal())
        assertEquals(1, game.dealer())
        assertEquals(5, game.handSize(0))
        assertEquals(4, game.handSize(1))
        assertEquals(1, game.pileSize())
        assertTrue(game.legalMoves(game.turn).isNotEmpty())
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

        // Дама кладётся с заказом: масть — часть хода, а не отдельное решение.
        game.apply(0, HundredMove.Play(card(Rank.QUEEN, Suit.HEARTS), Suit.CLUBS))

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
        // Кон кончен — новый сдают только по ответу игрока.
        assertTrue(game.awaitingDeal())
        game.nextDeal()

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
                listOf(card(Rank.TEN, Suit.DIAMONDS), card(Rank.SIX, Suit.HEARTS)),
                emptyList<Card>(),
                listOf(card(Rank.NINE, Suit.CLUBS)),
            ),
            pile = listOf(card(Rank.TEN, Suit.SPADES)),
            turn = 0,
            out = listOf(false, true, false),
        )

        game.apply(0, HundredMove.Play(card(Rank.TEN, Suit.DIAMONDS)))

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

    // --- Старшие карты бьют по следующему ----------------------------------

    /**
     * Шестёрка, семёрка и пиковый король отдают соседу карты из колоды, а ход
     * через него перешагивает: сосед и карты берёт, и свой ход теряет.
     */
    @Test
    fun `шестёрка отдаёт соседу карту и его ход`() {
        val game = table(
            first = listOf(card(Rank.SIX, Suit.DIAMONDS), card(Rank.SIX, Suit.HEARTS)),
            pile = card(Rank.SIX, Suit.SPADES),
            stock = listOf(card(Rank.TEN, Suit.CLUBS), card(Rank.JACK, Suit.CLUBS)),
        )

        game.apply(0, HundredMove.Play(card(Rank.SIX, Suit.DIAMONDS)))

        assertEquals(2, game.handSize(1))
        assertEquals(1, game.stockSize())
        assertEquals(2, game.turn)
    }

    @Test
    fun `семёрка отдаёт соседу две карты и его ход`() {
        val game = table(
            first = listOf(card(Rank.SEVEN, Suit.DIAMONDS), card(Rank.SEVEN, Suit.HEARTS)),
            pile = card(Rank.SEVEN, Suit.SPADES),
            stock = listOf(
                card(Rank.TEN, Suit.CLUBS),
                card(Rank.JACK, Suit.CLUBS),
                card(Rank.QUEEN, Suit.CLUBS),
            ),
        )

        game.apply(0, HundredMove.Play(card(Rank.SEVEN, Suit.DIAMONDS)))

        assertEquals(3, game.handSize(1))
        assertEquals(1, game.stockSize())
        assertEquals(2, game.turn)
    }

    /** Пиковый король — четыре карты; король другой масти не значит ничего. */
    @Test
    fun `пиковый король отдаёт соседу четыре карты`() {
        val game = table(
            first = listOf(card(Rank.KING, Suit.SPADES), card(Rank.KING, Suit.CLUBS)),
            pile = card(Rank.KING, Suit.HEARTS),
            stock = listOf(
                card(Rank.TEN, Suit.CLUBS),
                card(Rank.JACK, Suit.CLUBS),
                card(Rank.QUEEN, Suit.CLUBS),
                card(Rank.NINE, Suit.CLUBS),
                card(Rank.EIGHT, Suit.CLUBS),
            ),
        )

        game.apply(0, HundredMove.Play(card(Rank.KING, Suit.SPADES)))

        assertEquals(5, game.handSize(1))
        assertEquals(1, game.stockSize())
        assertEquals(2, game.turn)
    }

    @Test
    fun `король другой масти не бьёт по соседу`() {
        val game = table(
            first = listOf(card(Rank.KING, Suit.CLUBS), card(Rank.KING, Suit.DIAMONDS)),
            pile = card(Rank.KING, Suit.HEARTS),
            stock = listOf(card(Rank.TEN, Suit.CLUBS), card(Rank.JACK, Suit.CLUBS)),
        )

        game.apply(0, HundredMove.Play(card(Rank.KING, Suit.CLUBS)))

        assertEquals(1, game.handSize(1))
        assertEquals(2, game.stockSize())
        assertEquals(1, game.turn)
    }

    /**
     * Туз ход отнимает, а карт не даёт: штраф в ноль карт — тоже штраф, и
     * сосед его теряет так же, как после шестёрки.
     */
    @Test
    fun `туз отнимает ход, не давая карт`() {
        val game = table(
            first = listOf(card(Rank.ACE, Suit.DIAMONDS), card(Rank.ACE, Suit.HEARTS)),
            pile = card(Rank.ACE, Suit.SPADES),
            stock = listOf(card(Rank.TEN, Suit.CLUBS), card(Rank.JACK, Suit.CLUBS)),
        )

        game.apply(0, HundredMove.Play(card(Rank.ACE, Suit.DIAMONDS)))

        assertEquals(1, game.handSize(1))
        assertEquals(2, game.stockSize())
        assertEquals(2, game.turn)
    }

    /** Выбывший за столом штраф не получает: карты уходят живому. */
    @Test
    fun `штраф достаётся живому соседу`() {
        val game = table(
            first = listOf(card(Rank.SIX, Suit.DIAMONDS), card(Rank.SIX, Suit.HEARTS)),
            pile = card(Rank.SIX, Suit.SPADES),
            stock = listOf(card(Rank.TEN, Suit.CLUBS), card(Rank.JACK, Suit.CLUBS)),
            out = listOf(1),
        )

        game.apply(0, HundredMove.Play(card(Rank.SIX, Suit.DIAMONDS)))

        assertEquals(1, game.handSize(1))
        assertEquals(2, game.handSize(2))
        assertEquals(0, game.turn)
    }

    // --- Девятка -----------------------------------------------------------

    /**
     * Девятку покрывают своей же рукой: ход остаётся у того, кто её положил,
     * пока она не покрыта, и играть он может только покрытие.
     */
    @Test
    fun `девятку покрывают своей же рукой`() {
        val game = table(
            first = listOf(card(Rank.NINE, Suit.DIAMONDS), card(Rank.TEN, Suit.DIAMONDS), card(Rank.TEN, Suit.HEARTS)),
            pile = card(Rank.NINE, Suit.HEARTS),
        )

        game.apply(0, HundredMove.Play(card(Rank.NINE, Suit.DIAMONDS)))

        assertEquals(card(Rank.NINE, Suit.DIAMONDS), game.coverCard())
        assertEquals(0, game.turn)
        // Покрытие — бубна или другая девятка; червовый десяток не годится,
        // хотя по верхней карте кона он бы подошёл.
        assertEquals(
            listOf(HundredMove.Play(card(Rank.TEN, Suit.DIAMONDS))),
            game.legalMoves(0),
        )
    }

    /** Вторая девятка требует покрытия снова, а обычная карта закрывает вопрос. */
    @Test
    fun `вторая девятка требует покрытия снова`() {
        val game = table(
            first = listOf(
                card(Rank.NINE, Suit.DIAMONDS),
                card(Rank.NINE, Suit.CLUBS),
                card(Rank.TEN, Suit.CLUBS),
                card(Rank.EIGHT, Suit.HEARTS),
            ),
            pile = card(Rank.NINE, Suit.HEARTS),
        )

        game.apply(0, HundredMove.Play(card(Rank.NINE, Suit.DIAMONDS)))
        game.apply(0, HundredMove.Play(card(Rank.NINE, Suit.CLUBS)))

        assertEquals(card(Rank.NINE, Suit.CLUBS), game.coverCard())
        assertEquals(0, game.turn)

        game.apply(0, HundredMove.Play(card(Rank.TEN, Suit.CLUBS)))

        assertEquals(null, game.coverCard())
        assertEquals(1, game.turn)
    }

    /**
     * Покрывать нечем — тянут из колоды, и ход при этом не отдают: тянет тот,
     * кто положил девятку, а не следующий за ним.
     */
    @Test
    fun `под девяткой тянут, пока не найдут покрытие`() {
        val game = table(
            first = listOf(card(Rank.NINE, Suit.DIAMONDS), card(Rank.TEN, Suit.HEARTS)),
            pile = card(Rank.NINE, Suit.HEARTS),
            stock = listOf(card(Rank.TEN, Suit.CLUBS), card(Rank.EIGHT, Suit.DIAMONDS)),
        )

        game.apply(0, HundredMove.Play(card(Rank.NINE, Suit.DIAMONDS)))

        // Первая карта не подошла — она осталась в руке, а ход не ушёл.
        game.apply(0, HundredMove.Draw)
        assertEquals(2, game.handSize(0))
        assertEquals(0, game.turn)
        assertEquals(card(Rank.NINE, Suit.DIAMONDS), game.coverCard())

        // Вторая подошла — ей и покрывают.
        game.apply(0, HundredMove.Draw)
        assertEquals(listOf(HundredMove.Play(card(Rank.EIGHT, Suit.DIAMONDS))), game.legalMoves(0))

        game.apply(0, HundredMove.Play(card(Rank.EIGHT, Suit.DIAMONDS)))
        assertEquals(null, game.coverCard())
        assertEquals(1, game.turn)
    }

    /**
     * Колода кончилась, а покрывать нечем — держать ход больше не за чем:
     * девятка остаётся лежать как есть, ход уходит дальше. Брать нечего —
     * ни из колоды, ни из стопки: на кону только сама девятка.
     */
    @Test
    fun `непокрытая девятка остаётся, когда брать неоткуда`() {
        val game = Hundred.forTesting(
            playerCount = 3,
            hands = listOf(
                listOf(card(Rank.TEN, Suit.HEARTS)),
                listOf(card(Rank.NINE, Suit.CLUBS)),
                listOf(card(Rank.JACK, Suit.HEARTS)),
            ),
            pile = listOf(card(Rank.NINE, Suit.DIAMONDS)),
            turn = 0,
            cover = card(Rank.NINE, Suit.DIAMONDS),
        )

        game.apply(0, HundredMove.Draw)

        assertEquals(null, game.coverCard())
        assertEquals(1, game.turn)
    }

    /** Девяткой рука и кончилась — кон за вышедшим, покрывать нечего. */
    @Test
    fun `девяткой можно выйти из кона`() {
        val game = table(
            first = listOf(card(Rank.NINE, Suit.DIAMONDS)),
            pile = card(Rank.NINE, Suit.HEARTS),
        )

        game.apply(0, HundredMove.Play(card(Rank.NINE, Suit.DIAMONDS)))

        assertEquals(null, game.coverCard())
        assertFalse(game.finished)
        // Вышедший не записывает ничего, остальные — за карты на руках:
        // девятка ноль, валет два.
        assertEquals(0, game.scoreOf(1))
        assertEquals(2, game.scoreOf(2))

        // Раздача новая — но сдал её не движок: он встал и ждал ответа.
        assertTrue(game.awaitingDeal())
        game.nextDeal()
        assertEquals(1, game.pileSize())
    }

    // --- Дама --------------------------------------------------------------

    /** Дама ложится в любой момент: под неё подходит любая карта на руке. */
    @Test
    fun `дама ложится на что угодно`() {
        val queen = card(Rank.QUEEN, Suit.DIAMONDS)
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(listOf(queen, card(Rank.TEN, Suit.CLUBS)), listOf(ace)),
            pile = listOf(card(Rank.KING, Suit.HEARTS)),
            turn = 0,
        )

        // Десятка червей не подошла бы ни мастью, ни достоинством, а дама
        // ложится — и заказывает любую из четырёх мастей.
        assertEquals(Suit.entries.map { HundredMove.Play(queen, it) }, game.legalMoves(0))
    }

    /** Заказ дамы держится, пока она на кону: ходят заказанной мастью или дамой. */
    @Test
    fun `заказ дамы держится, пока она на кону`() {
        val queen = card(Rank.QUEEN, Suit.DIAMONDS)
        val game = Hundred.forTesting(
            playerCount = 3,
            hands = listOf(
                listOf(queen, card(Rank.TEN, Suit.CLUBS)),
                listOf(card(Rank.TEN, Suit.CLUBS), card(Rank.TEN, Suit.HEARTS)),
                listOf(card(Rank.JACK, Suit.HEARTS)),
            ),
            pile = listOf(card(Rank.KING, Suit.HEARTS)),
            turn = 0,
        )

        game.apply(0, HundredMove.Play(queen, Suit.CLUBS))

        assertEquals(Suit.CLUBS, game.orderedSuit())
        assertEquals(1, game.turn)
        assertTrue(game.fitsTop(card(Rank.TEN, Suit.CLUBS)))
        assertFalse(game.fitsTop(card(Rank.TEN, Suit.HEARTS)))
        assertEquals(
            listOf(HundredMove.Play(card(Rank.TEN, Suit.CLUBS))),
            game.legalMoves(1),
            "заказ червей не перебить: подходит только заказанная масть",
        )
    }

    /** Другая дама перебивает заказ и заказывает заново. */
    @Test
    fun `другая дама заказывает заново`() {
        val first = card(Rank.QUEEN, Suit.DIAMONDS)
        val second = card(Rank.QUEEN, Suit.SPADES)
        val game = Hundred.forTesting(
            playerCount = 3,
            hands = listOf(
                listOf(first, card(Rank.TEN, Suit.SPADES)),
                listOf(second, card(Rank.TEN, Suit.HEARTS)),
                listOf(card(Rank.JACK, Suit.HEARTS)),
            ),
            pile = listOf(card(Rank.KING, Suit.HEARTS)),
            turn = 0,
        )

        game.apply(0, HundredMove.Play(first, Suit.CLUBS))
        game.apply(1, HundredMove.Play(second, Suit.HEARTS))

        assertEquals(Suit.HEARTS, game.orderedSuit())
    }

    /** Обычная карта поверх дамы — и заказ кончился, ходят по ней. */
    @Test
    fun `следующая карта снимает заказ`() {
        val queen = card(Rank.QUEEN, Suit.DIAMONDS)
        val club = card(Rank.TEN, Suit.CLUBS)
        val game = Hundred.forTesting(
            playerCount = 3,
            hands = listOf(
                listOf(queen, card(Rank.TEN, Suit.SPADES)),
                listOf(club, card(Rank.NINE, Suit.HEARTS)),
                listOf(card(Rank.JACK, Suit.HEARTS)),
            ),
            pile = listOf(card(Rank.KING, Suit.HEARTS)),
            turn = 0,
        )

        game.apply(0, HundredMove.Play(queen, Suit.CLUBS))
        game.apply(1, HundredMove.Play(club))

        assertEquals(null, game.orderedSuit())
        // Под заказом червовый десяток не подходил, а по крестовому десятку —
        // подходит по достоинству: вернулось обычное правило.
        assertTrue(game.fitsTop(card(Rank.TEN, Suit.HEARTS)))
        assertFalse(game.fitsTop(card(Rank.NINE, Suit.HEARTS)))
        assertEquals(2, game.turn)
    }

    /** Дама кроет всё, и девятку тоже: накрыв её, она заказывает масть. */
    @Test
    fun `дама кроет девятку`() {
        val queen = card(Rank.QUEEN, Suit.DIAMONDS)
        val game = Hundred.forTesting(
            playerCount = 3,
            hands = listOf(
                listOf(card(Rank.NINE, Suit.HEARTS), queen, card(Rank.TEN, Suit.HEARTS)),
                listOf(card(Rank.NINE, Suit.CLUBS)),
                listOf(card(Rank.JACK, Suit.HEARTS)),
            ),
            pile = listOf(card(Rank.NINE, Suit.CLUBS)),
            turn = 0,
        )

        game.apply(0, HundredMove.Play(card(Rank.NINE, Suit.HEARTS)))
        assertEquals(card(Rank.NINE, Suit.HEARTS), game.coverCard())

        game.apply(0, HundredMove.Play(queen, Suit.SPADES))

        assertEquals(null, game.coverCard())
        assertEquals(Suit.SPADES, game.orderedSuit())
    }

    /** Дама без заказа не ходит: заказ — часть хода, а не украшение. */
    @Test
    fun `даму без заказа не кладут`() {
        val queen = card(Rank.QUEEN, Suit.DIAMONDS)
        val game = Hundred.forTesting(
            playerCount = 3,
            hands = listOf(
                listOf(queen, card(Rank.TEN, Suit.HEARTS)),
                listOf(card(Rank.NINE, Suit.CLUBS)),
                listOf(card(Rank.JACK, Suit.HEARTS)),
            ),
            pile = listOf(card(Rank.TEN, Suit.SPADES)),
            turn = 0,
        )

        val rejected = runCatching { game.apply(0, HundredMove.Play(queen)) }

        assertTrue(rejected.isFailure)
    }

    /**
     * Стол на троих с заходом от первого места. [first] — рука заходящего:
     * остальным раздаём по одной карте, чтобы штрафу было куда лечь.
     * [out] — номера выбывших мест.
     */
    private fun table(
        first: List<Card>,
        pile: Card,
        stock: List<Card> = emptyList(),
        out: List<Int> = emptyList(),
    ): Hundred = Hundred.forTesting(
        playerCount = 3,
        hands = listOf(first, listOf(card(Rank.NINE, Suit.CLUBS)), listOf(card(Rank.JACK, Suit.HEARTS))),
        pile = listOf(pile),
        stock = stock,
        turn = 0,
        out = List(3) { it in out },
    )
}
