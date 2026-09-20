package games.engine.hundred

import games.engine.Card
import games.engine.Deck
import games.engine.GameRules
import games.engine.Move
import games.engine.Rank
import games.engine.Suit
import games.engine.fullDeck36
import kotlin.random.Random

/**
 * Ходы в «101». Их всего два, и это всё, что можно сделать за столом: сыграть
 * подходящую карту или взять одну из колоды, когда подходящей нет.
 *
 * Больше ходов здесь не появляется нарочно. В «Дураке» их пять, потому что
 * там есть отбой, перевод и «бито»; тут стол устроен проще — сыграл и передал
 * ход, — и лишний ход значил бы лишнюю кнопку на экране, которую надо
 * объяснять вслух.
 */
sealed interface HundredMove : Move {
    /** Положить карту на кон. Допустима только подходящая — той же масти или того же достоинства. */
    data class Play(val card: Card) : HundredMove

    /** Взять одну карту из колоды. Подошла — играешь её, не подошла — ход переходит. */
    data object Draw : HundredMove
}

/**
 * «101» (она же «Мавр») — игра на сбрасывание, 36 карт, от двух до трёх
 * игроков. Договор целиком в `HUNDRED_ONE.md`.
 *
 * За столом раскладывают карты на кон: подходит карта той же масти или того же
 * достоинства, что верхняя на кону, — старшинства нет и козыря нет. Кто первый
 * сбросил руку, выиграл кон; остальные записывают себе очки за оставшиеся на
 * руках карты. Больше 101 очка — выбывание из матча, ровно 101 — счёт
 * обнуляется.
 *
 * Три правила, о которых легко споткнуться, поэтому они зашиты явно:
 *
 * 1. **Добор — одной картой** (правило Катерины, 20.09). Нет подходящей карты
 *    — берёшь одну из колоды; подошла — играешь её, не подошла — ход переходит
 *    дальше. Тянуть «до подходящей» мы не играем: это вариант статьи, но не
 *    её. Отсюда и то, что после удачного добора ход остаётся у того же игрока,
 *    а сыграть он может ровно одну карту — принесённую.
 * 2. **Пятая карта сдатчика ложится на кон сама** и считается его первым
 *    ходом. В живой игре её не выбирают, её назначает раздача; выбор дал бы
 *    заходящему преимущество, которого в правилах нет.
 * 3. **Ровно 101 — обнуление, больше — выбывание.** Выбывший из матча выходит,
 *    побеждает последний оставшийся за столом.
 *
 * Не путать с «Козлом»: там матч идёт до 101 очка и очки игровые, а здесь 101 —
 * штрафные, за карты на руках.
 */
class Hundred private constructor(
    /** Сколько мест за столом. Оно не меняется до конца матча. */
    val playerCount: Int,
    private val random: Random,
) : GameRules<Hundred> {

    init {
        require(playerCount in 2..3) { "от двух до трёх игроков" }
    }

    private val stock = ArrayDeque<Card>()

    /**
     * Кон: стопка сыгранных карт, [pile.last] — верхняя, та, по которой ходят.
     * Верхняя карта кладётся сдачей, а не ходом, — она же первый ход сдатчика.
     */
    private val pile = mutableListOf<Card>()

    private val hands = MutableList(playerCount) { mutableListOf<Card>() }

    /** Штрафные очки. Набираются за карты, оставшиеся на руках. */
    private val scores = MutableList(playerCount) { 0 }

    /** Кто выбыл из матча: перебрал 101. Выбывших в следующий кон не сдают. */
    private val out = MutableList(playerCount) { false }

    private var dealerSeat = 0
    private var turnSeat = 0

    /**
     * Последняя сыгранная карта. Нужна для правила о даме на выходе: кон
     * кончается ходом, и важно, чем именно он был сделан.
     */
    private var lastCard: Card? = null

    /**
     * Сколько раз переворачивали стопку. Счётчик, а не флаг: экран объявляет
     * это событие вслух обязательно — иначе «тянешь карту» однажды прозвучит
     * из пустоты, — и ему надо знать, что оно случилось ровно этим ходом.
     */
    private var turnovers = 0

    var finished = false
        private set

    /** Кто выиграл матч; null — матч идёт или выбыли все разом. */
    var winner: Int? = null
        private set

    // --- Что видно за столом ----------------------------------------------

    /** Верхняя карта кона — по ней и ходят. null — кон ещё не открыт. */
    fun topCard(): Card? = pile.lastOrNull()

    fun handOf(seat: Int): List<Card> = hands[seat].toList()

    fun scoreOf(seat: Int): Int = scores[seat]

    fun scoresAll(): List<Int> = scores.toList()

    fun isOut(seat: Int): Boolean = out[seat]

    fun outSeats(): List<Int> = out.indices.filter { out[it] }

    fun stockSize(): Int = stock.size

    /** Сколько карт ушло на кон. По ним считают, что уже вышло из игры. */
    fun pileSize(): Int = pile.size

    fun pileCards(): List<Card> = pile.toList()

    /** Колода сверху вниз, как она лежит. По ней партия и восстанавливается. */
    fun stockCards(): List<Card> = stock.toList()

    /** Кому сдавать этот кон. */
    fun dealer(): Int = dealerSeat

    /** Чей ход. */
    val turn: Int get() = turnSeat

    fun handSize(seat: Int): Int = hands[seat].size

    /** Сколько раз за матч переворачивали стопку. */
    fun stockTurnovers(): Int = turnovers

    /** Кто ещё в матче. */
    fun aliveSeats(): List<Int> = out.indices.filter { !out[it] }

    // --- Места за столом ---------------------------------------------------

    /** Следующее место по кругу, пропуская выбывших. */
    private fun nextAlive(seat: Int): Int {
        var next = (seat + 1) % playerCount
        repeat(playerCount) {
            if (!out[next]) return next
            next = (next + 1) % playerCount
        }
        return seat
    }

    // --- Ходы --------------------------------------------------------------

    /** Подходит ли карта на кон: та же масть или то же достоинство. */
    fun fits(card: Card, top: Card): Boolean = card.suit == top.suit || card.rank == top.rank

    /** Карты руки, которыми можно сходить. Пусто — придётся тянуть. */
    fun playable(seat: Int): List<Card> {
        val top = pile.lastOrNull() ?: return emptyList()
        return hands[seat].filter { fits(it, top) }
    }

    fun legalMoves(seat: Int): List<HundredMove> {
        if (finished || seat !in hands.indices || out[seat] || seat != turnSeat) return emptyList()
        val top = pile.lastOrNull() ?: return emptyList()
        val fitting = hands[seat].filter { fits(it, top) }
        // Подходящей карты нет — берёшь одну. Карты не осталось ни в колоде, ни
        // в стопке — ход всё равно переходит: «взять» здесь значит «пропустить».
        return if (fitting.isEmpty()) listOf(HundredMove.Draw) else fitting.map { HundredMove.Play(it) }
    }

    fun apply(seat: Int, move: HundredMove) {
        check(!finished) { "матч окончен" }
        check(move in legalMoves(seat)) { "недопустимый ход: $move" }

        when (move) {
            is HundredMove.Play -> {
                hands[seat].remove(move.card)
                pile += move.card
                lastCard = move.card
                if (hands[seat].isEmpty()) {
                    // Последняя карта — кон за ним. Считаем очки и раздаём
                    // следующий, если матч на этом не кончился.
                    finishCon(winnerSeat = seat, exit = move.card)
                } else {
                    turnSeat = nextAlive(seat)
                }
            }

            HundredMove.Draw -> {
                val drawn = takeFromStock()
                if (drawn == null) {
                    turnSeat = nextAlive(seat)
                    return
                }
                hands[seat] += drawn
                val top = pile.last()
                // Подошла — играешь её, и ход остаётся твой: сыграть её можно
                // ровно одну, другой подходящей в руке не было. Не подошла —
                // ход переходит дальше.
                if (!fits(drawn, top)) turnSeat = nextAlive(seat)
            }
        }
    }

    /**
     * Взять карту из колоды. Кончилась — переворачиваем стопку: верхняя карта
     * остаётся на кону, остальные уходят под неё рубашкой вверх, и играют ими.
     *
     * Порядок переворота не безразличен: под верхней картой лежит та, что
     * сыграна перед ней, и достаётся теперь первой — стопку переворачивают как
     * есть, а не тасуют.
     */
    private fun takeFromStock(): Card? {
        if (stock.isNotEmpty()) return stock.removeFirst()
        if (pile.size <= 1) return null

        val top = pile.removeAt(pile.lastIndex)
        val under = pile.reversed()
        pile.clear()
        pile += top
        stock.addAll(under)
        turnovers++
        return stock.removeFirst()
    }

    // --- Конец кона и счёт -------------------------------------------------

    /**
     * Кон кончен: вышедший очков не получает, остальные записывают себе за
     * карты, оставшиеся на руках. Дальше — дама на выходе, обнуление ровно на
     * 101, выбывание за 101, новая раздача.
     */
    private fun finishCon(winnerSeat: Int, exit: Card) {
        for (seat in out.indices) {
            if (out[seat] || seat == winnerSeat) continue
            scores[seat] += points(hands[seat])
        }

        // Дама на выходе: вышедший последней картой-дамой списывает себе её
        // цену и удваивает счёт всем остальным. Правило домашнее, но это
        // единственное событие в игре, кроме выбывания, которое стоит
        // объявить как событие.
        if (exit.rank == Rank.QUEEN) {
            scores[winnerSeat] -= queenPrice(exit.suit)
            for (seat in out.indices) {
                if (out[seat] || seat == winnerSeat) continue
                scores[seat] *= 2
            }
        }

        checkScores()

        val alive = aliveSeats()
        if (alive.size <= 1) {
            finished = true
            winner = alive.firstOrNull()
            return
        }

        dealerSeat = nextAlive(dealerSeat)
        dealNext()
    }

    /**
     * Ровно 101 — счёт обнуляется, и игрок остаётся в матче. Больше 101 —
     * выбывает. Обнуление проверяем там же: перебор и ровно 101 — разные
     * события, и на слух их надо различать.
     */
    private fun checkScores() {
        for (seat in out.indices) {
            if (out[seat]) continue
            when {
                scores[seat] > HUNDRED_ONE -> out[seat] = true
                scores[seat] == HUNDRED_ONE -> scores[seat] = 0
            }
        }
    }

    /** Новая раздача: сдатчик — следующий за прежним, он же и заходит. */
    private fun dealNext() {
        val deck = Deck(fullDeck36()).also { it.shuffle(random) }
        stock.clear()
        pile.clear()
        hands.forEach { it.clear() }

        // Всем по пять, сдатчику четыре: его пятая ложится на кон и считается
        // его первым ходом. Раздаём по кругу, начиная с соседа сдатчика, — так
        // же, как это делают за столом.
        val alive = aliveSeats()
        for (round in 0 until HAND_SIZE) {
            for (offset in 1..playerCount) {
                val seat = (dealerSeat + offset) % playerCount
                if (seat !in alive) continue
                if (round == HAND_SIZE - 1 && seat == dealerSeat) continue
                hands[seat] += deck.draw()
            }
        }

        stock.addAll(deck.toList())
        pile += stock.removeFirst()
        lastCard = pile.last()
        turnSeat = nextAlive(dealerSeat)
    }

    override fun legalMoves(state: Hundred, seat: Int): List<Move> = legalMoves(seat)

    override fun apply(state: Hundred, seat: Int, move: Move): Hundred {
        apply(seat, move as HundredMove)
        return this
    }

    override fun isFinished(state: Hundred): Boolean = finished

    override fun winner(state: Hundred): Int? = winner

    /**
     * Короткая фраза про ход. Экран говорит подробнее — что именно подошло,
     * сколько карт осталось, чей ход, — здесь же только суть хода.
     */
    override fun describe(state: Hundred, seat: Int, move: Move): String = when (move) {
        is HundredMove.Play -> "${move.card.spoken()} — на кон."
        HundredMove.Draw -> "Карта взята."
        else -> "Ход сделан."
    }

    companion object {
        /** Пять карт — рука, четыре — сдатчику, пятая на кон. */
        const val HAND_SIZE = 5

        /** Штраф, после которого игрок выбывает из матча. */
        const val HUNDRED_ONE = 101

        /**
         * Новая партия. [firstDealer] — кому сдавать первому; в живом столе
         * сдатчика назначает жребий, а в приложении — оно же, поэтому по
         * умолчанию случай.
         */
        fun start(
            random: Random = Random.Default,
            playerCount: Int = 3,
            firstDealer: Int? = null,
        ): Hundred {
            require(playerCount in 2..3) { "от двух до трёх игроков" }
            val game = Hundred(playerCount, random)
            game.dealerSeat = firstDealer?.takeIf { it in 0 until playerCount }
                ?: random.nextInt(playerCount)
            game.dealNext()
            return game
        }

        /**
         * Партия из записи: стол собран как есть, раздача не повторяется —
         * она уже была. Число мест берётся из числа рук: за столом их ровно
         * столько и ни одной лишней.
         *
         * Последней сыгранной картой становится верхняя на кону, и это не
         * догадка: кон кончается ходом, и по верхней карте видно, чем он был
         * сделан. Отдельной строки для неё в записи не нужно.
         */
        fun restore(
            hands: List<List<Card>>,
            pile: List<Card>,
            stock: List<Card>,
            dealer: Int,
            turn: Int,
            scores: List<Int>,
            out: List<Boolean>,
            turnovers: Int = 0,
        ): Hundred {
            val playerCount = hands.size
            require(playerCount in 2..3) { "от двух до трёх игроков" }
            require(scores.size == playerCount && out.size == playerCount) {
                "счёт и выбывшие — по числу мест"
            }

            val game = Hundred(playerCount, Random(0))
            game.hands.forEachIndexed { seat, hand -> hand += hands[seat] }
            game.pile += pile
            game.stock.addAll(stock)
            game.dealerSeat = dealer
            game.turnSeat = turn
            scores.forEachIndexed { seat, value -> game.scores[seat] = value }
            out.forEachIndexed { seat, value -> game.out[seat] = value }
            game.turnovers = turnovers
            game.lastCard = pile.lastOrNull()
            return game
        }

        /**
         * Партия с заданными руками — для тестов и разбора ситуаций. Как
         * `forTesting` у «Дурака»: раздача случайна, а проверить надо правило.
         */
        fun forTesting(
            playerCount: Int,
            hands: List<List<Card>>,
            pile: List<Card>,
            stock: List<Card> = emptyList(),
            dealer: Int = 0,
            turn: Int = 0,
            scores: List<Int> = List(playerCount) { 0 },
            out: List<Boolean> = List(playerCount) { false },
            lastCard: Card? = null,
            turnovers: Int = 0,
        ): Hundred {
            require(hands.size == playerCount) { "рук должно быть столько же, сколько мест" }
            return restore(
                hands = hands,
                pile = pile,
                stock = stock,
                dealer = dealer,
                turn = turn,
                scores = scores,
                out = out,
                turnovers = turnovers,
            ).also { game -> game.lastCard = lastCard ?: pile.lastOrNull() }
        }
    }

}

/**
 * Цена дамы, которой вышли: крести 20, пики 40, черви 60, бубна 80.
 *
 * Порядок мастей не случаен и не переставляется: он идёт по цене, а не по
 * старшинству, — так эта лестница записана и в правилах.
 *
 * Наружу, а не только движку: цену дамы экран объявляет вслух, и объявление
 * должно быть тем же числом, которым счёт сведён.
 */
fun queenPrice(suit: Suit): Int = when (suit) {
    Suit.CLUBS -> 20
    Suit.SPADES -> 40
    Suit.HEARTS -> 60
    Suit.DIAMONDS -> 80
}

/**
 * Очки за карты: туз 11, десятка 10, восьмёрка 8, семёрка 7, шестёрка 6,
 * король 4, дама 3, валет 2, девятка 0.
 *
 * Туз — **11**, а не 1: так решила Катерина (20.09), и так лестница очков
 * совпадает с «Тысячей». В статье Википедии туз стоит 1 очко, но это
 * единственный источник с таким счётом.
 *
 * Наружу по той же причине, что и [queenPrice]: экран называет цену руки
 * вслух, и она обязана сойтись со счётом матча.
 */
fun points(cards: List<Card>): Int = cards.sumOf { card ->
    when (card.rank) {
        Rank.ACE -> 11
        Rank.TEN -> 10
        Rank.EIGHT -> 8
        Rank.SEVEN -> 7
        Rank.SIX -> 6
        Rank.KING -> 4
        Rank.QUEEN -> 3
        Rank.JACK -> 2
        Rank.NINE -> 0
    }
}
