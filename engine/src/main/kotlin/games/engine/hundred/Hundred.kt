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
    /**
     * Положить карту на кон. Обычная карта подходит по масти или достоинству,
     * дама — всегда ([order]).
     *
     * [order] — масть, которую заказывает дама: с ней ход уходит к соседу, и
     * он ходит только ею. У обычной карты заказа нет и быть не может: заказ
     * принадлежит даме, а не ходу.
     */
    data class Play(val card: Card, val order: Suit? = null) : HundredMove

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
 * Правила, о которых легко споткнуться, поэтому они зашиты явно:
 *
 * 1. **Добор — одной картой** (правило Катерины, 20.09). Нет подходящей карты
 *    — берёшь одну из колоды; подошла — играешь её, не подошла — ход переходит
 *    дальше. Тянуть «до подходящей» мы не играем: это вариант статьи, но не
 *    её. Отсюда и то, что после удачного добора ход остаётся у того же игрока,
 *    а сыграть он может ровно одну карту — принесённую. Исключение одно —
 *    непокрытая девятка: под ней тянут, пока не найдут чем покрыть.
 * 2. **Старшие карты бьют по следующему** (правило Катерины, 20.09): шестёрка
 *    — берёт одну карту и пропускает ход, семёрка — две, пиковый король —
 *    четыре, туз — пропускает ход, не беря ничего. Штраф всегда берётся у
 *    следующего живого места, и он же теряет свой ход: ход идёт через него.
 * 3. **Дама ложится в любой момент и кроет всё** (правило Катерины, 20.09).
 *    Под неё подходит любая карта на руке, и она сама ложится на любую. Ею же
 *    заказывают масть: пока дама на кону, ходят только заказанной мастью —
 *    или другой дамой, которая закажет заново.
 * 4. **Девятку покрывают своей же рукой** (правило Катерины, 20.09). Положил
 *    девятку — покрой её сам: той же мастью или другой девяткой; нет такой на
 *    руке — тяни из колоды, пока не найдёшь. Вторая девятка требует покрытия
 *    снова. Колода кончилась, а покрывать нечем — девятка остаётся непокрытой,
 *    и ход уходит дальше.
 * 5. **Пятая карта сдатчика ложится на кон сама** и считается его первым
 *    ходом. В живой игре её не выбирают, её назначает раздача; выбор дал бы
 *    заходящему преимущество, которого в правилах нет.
 * 6. **Ровно 101 — обнуление, больше — выбывание.** Выбывший из матча выходит,
 *    побеждает последний оставшийся за столом.
 * 7. **Новый кон начинают не сами** (правило Катерины, 20.09). Кон кончился —
 *    движок останавливается и ждёт ответа: играем дальше или хватит. Раздачу
 *    начинает [nextDeal]; «хватит» — и матч на этом остаётся стоять.
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

    /**
     * Девятка, которую надо покрыть прежде, чем ход уйдёт дальше. Пока она
     * здесь, ход остаётся у того, кто её положил, а играть он может только
     * покрытие ([fits]): ту же масть или другую девятку.
     *
     * Отдельным полем, а не выводом из верхней карты кона: покрыть надо именно
     * положенную девятку, а кон за время добора успевает смениться — на нём
     * может лежать и другая карта.
     */
    private var cover: Card? = null

    /**
     * Масть, заказанная дамой, что лежит на кону сейчас. Пока она здесь, ходят
     * только ею: дама на кону ходит за своим заказом, а не за тем, что
     * нарисовано на её карте.
     *
     * Снимается вместе с дамой: любую другую карту кладут поверх — и заказ
     * кончился, ходят по ней.
     */
    private var order: Suit? = null

    /**
     * Кон кончился, а новый ещё не сдан: стол ждёт ответа — играем дальше или
     * хватит (правило Катерины, 20.09).
     *
     * Движок не раздаёт сам: иначе вопрос «играем дальше?» задавали бы уже
     * после раздачи, и «хватит» пришлось бы отменять сданное. Пока флаг стоит,
     * за столом не происходит ничего — ни хода, ни добора; раздачу начинает
     * [nextDeal], и зовёт его тот, кто спросил игрока.
     */
    private var awaitingDeal = false

    var finished = false
        private set

    /** Кто выиграл матч; null — матч идёт или выбыли все разом. */
    var winner: Int? = null
        private set

    // --- Что видно за столом ----------------------------------------------

    /** Верхняя карта кона — по ней и ходят. null — кон ещё не открыт. */
    fun topCard(): Card? = pile.lastOrNull()

    /**
     * Девятка, которую ходящий обязан покрыть. null — покрывать нечего.
     *
     * Наружу, а не только внутрь: экран по ней объявляет вслух, что от игрока
     * сейчас требуется, и оставляет на столе одну кнопку «Взять карту», когда
     * покрывать нечем.
     */
    fun coverCard(): Card? = cover

    /**
     * Масть, заказанная дамой на кону. null — заказа нет, и ходят по верхней
     * карте.
     *
     * Наружу затем же, зачем и [coverCard]: экран объявляет заказ вслух —
     * молчаливая дама оставила бы игрока угадывать, чем теперь ходить.
     */
    fun orderedSuit(): Suit? = order

    /**
     * Кон кончился и ждёт ответа: играем дальше или хватит.
     *
     * Наружу затем, что решает игрок, а не движок: экран по этому флагу
     * спрашивает и только после ответа зовёт [nextDeal].
     */
    fun awaitingDeal(): Boolean = awaitingDeal

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

    /**
     * Кто берёт штраф за карту, которой ходил [seat]: следующий живой.
     *
     * Наружу, чтобы экран сказал это вслух. Для незрячего игрока штраф — это
     * выросшая рука и потерянный ход, и то и другое происходит молча, если о
     * нём не сказать: карта легла, а что за ней — не слышно (HUNDRED_ONE.md,
     * 3.2). Выбывшего тут нет: ход через него перешагивает вместе со штрафом.
     */
    fun penaltyVictim(seat: Int): Int = nextAlive(seat)

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

    /**
     * Подходит ли карта под то, что лежит на кону сейчас, — с оглядкой на
     * заказ дамы.
     *
     * Заказ есть — годится заказанная масть и другая дама, которая закажет
     * масть заново. Заказа нет — обычное правило ([fits]).
     */
    fun fitsTop(card: Card): Boolean {
        val top = pile.lastOrNull() ?: return false
        return when (val ordered = order) {
            null -> fits(card, top)
            else -> card.suit == ordered || card.rank == Rank.QUEEN
        }
    }

    /** Карты руки, которыми можно сходить. Пусто — придётся тянуть. */
    fun playable(seat: Int): List<Card> {
        if (pile.isEmpty()) return emptyList()
        // Дама подходит всегда и под всё — и под карту на кону, и под девятку,
        // которую надо покрыть, — поэтому она в списке в любом случае.
        val mustCover = cover
        return hands[seat].filter { card ->
            card.rank == Rank.QUEEN ||
                (mustCover?.let { fits(card, it) } ?: fitsTop(card))
        }
    }

    fun legalMoves(seat: Int): List<HundredMove> {
        if (finished || awaitingDeal || seat !in hands.indices || out[seat] || seat != turnSeat) {
            return emptyList()
        }

        // Дама — ход вне очереди на кон: она ложится на всё и под неё подходит
        // всё, и ею же заказывают масть. Заказов у одной дамы четыре — по
        // одному на масть, — и выбрать надо ровно один.
        val queens = hands[seat].filter { it.rank == Rank.QUEEN }
            .flatMap { queen -> Suit.entries.map { HundredMove.Play(queen, it) } }

        // Непокрытая девятка важнее верхней карты кона: пока она лежит, играть
        // можно только покрытие, а нет его — остаётся добор.
        val mustCover = cover
        val fitting = if (mustCover != null) {
            hands[seat].filter { it.rank != Rank.QUEEN && fits(it, mustCover) }
        } else {
            // Кон пуст — ходить не по чему; дама и тут ложится: она ложится
            // на всё.
            if (pile.isEmpty()) return queens
            hands[seat].filter { it.rank != Rank.QUEEN && fitsTop(it) }
        }

        // Подходящей карты нет — берёшь одну. Карты не осталось ни в колоде, ни
        // в стопке — ход всё равно переходит: «взять» здесь значит «пропустить».
        val plays = fitting.map { HundredMove.Play(it) } + queens
        return plays.ifEmpty { listOf(HundredMove.Draw) }
    }

    fun apply(seat: Int, move: HundredMove) {
        check(!finished) { "матч окончен" }
        check(!awaitingDeal) { "кон кончился: нового ещё не сдали" }
        check(move in legalMoves(seat)) { "недопустимый ход: $move" }

        when (move) {
            is HundredMove.Play -> {
                hands[seat].remove(move.card)
                pile += move.card
                lastCard = move.card
                // Заказ живёт ровно столько, сколько дама на кону: следующая
                // карта поверх — и он снят.
                order = move.order
                if (hands[seat].isEmpty()) {
                    // Последняя карта — кон за ним. Считаем очки и раздаём
                    // следующий, если матч на этом не кончился. Покрывать
                    // девятку тут некому и незачем: рука пуста, кон кончен.
                    finishCon(winnerSeat = seat, exit = move.card)
                } else if (move.card.rank == Rank.NINE) {
                    // Девятка сама по себе на кону не лежит: покрыть её — дело
                    // того, кто положил. Ход при нём.
                    cover = move.card
                } else {
                    cover = null
                    passTurn(seat, move.card)
                }
            }

            HundredMove.Draw -> {
                val drawn = takeFromStock()
                if (drawn == null) {
                    // Брать неоткуда. Под непокрытой девяткой это значит, что
                    // покрыть её нечем: она остаётся лежать как есть, и ход
                    // уходит дальше — держать его больше не за чем.
                    cover = null
                    turnSeat = nextAlive(seat)
                    return
                }
                hands[seat] += drawn
                // Под непокрытой девяткой тянут, пока не найдут чем покрыть:
                // ход не переходит, и «взять» жмут ещё раз.
                if (cover != null) return

                // Подошла — играешь её, и ход остаётся твой: сыграть её можно
                // ровно одну, другой подходящей в руке не было. Не подошла —
                // ход переходит дальше. Подходит — по тому же правилу, по
                // которому ходят вообще: заказ дамы тут тоже в силе.
                if (!fitsTop(drawn)) turnSeat = nextAlive(seat)
            }
        }
    }

    /**
     * Ход уходит от [seat] дальше, а карта, которой он сделан, бьёт по
     * следующему за ним: тот берёт [penaltyOf] карт и свой ход теряет.
     *
     * Теряет всегда, даже когда брать нечего (туз): штрафная карта отнимает
     * ход, а не только руку набивает.
     */
    private fun passTurn(seat: Int, card: Card) {
        val victim = nextAlive(seat)
        val take = penaltyOf(card)
        if (take == null) {
            turnSeat = victim
            return
        }
        if (take > 0) giveFromStock(victim, take)
        turnSeat = nextAlive(victim)
    }

    /** Выдать [count] карт из колоды — сколько найдётся; колода кончилась — сколько есть. */
    private fun giveFromStock(seat: Int, count: Int) {
        repeat(count) {
            val card = takeFromStock() ?: return
            hands[seat] += card
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

        // Кон кончился — и вместе с ним кончилось всё, что было про этот кон:
        // непокрытая девятка (её могли покрыть последней картой, и тогда
        // покрывать уже нечего) и заказ дамы. Стол между конами — не кон, и
        // держать на нём эти пометки значило бы объявлять игроку то, чего на
        // столе больше нет.
        cover = null
        order = null

        // Раздачу не начинаем: сперва игрок ответит, играем дальше или хватит
        // ([awaitingDeal]). Матч кончился бы и на «хватит», и сдать новый кон,
        // чтобы тут же его отменить, — это объявить раздачу, которой не будет.
        awaitingDeal = true
    }

    /**
     * Сдать следующий кон. Зовётся после того, как игрок ответил «да»; до
     * этого стол стоит ([awaitingDeal]).
     *
     * Сдатчиком идёт следующий за прежним: сдатчика двигаем здесь, а не в
     * конце кона, — иначе он сдвинулся бы и у того, кто на «хватит» из-за
     * стола встал.
     */
    fun nextDeal() {
        check(awaitingDeal) { "новый кон сдают только после конца прежнего" }
        awaitingDeal = false
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
        cover = null
        order = null
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
        is HundredMove.Play -> when (val ordered = move.order) {
            null -> "${move.card.spoken()} — на кон."
            else -> "${move.card.spoken()} — на кон, заказ: ${ordered.title}."
        }

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
            cover: Card? = null,
            order: Suit? = null,
            awaiting: Boolean = false,
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
            // Непокрытая девятка — часть стола, а не украшение: без неё
            // игрок после перезапуска ходил бы мимо неё. С заказом дамы то же
            // самое: он говорит, чем ходить, а не чем ходили когда-то.
            game.cover = cover
            game.order = order
            // Стол, поднятый между конами, ждёт ответа так же, как его ждал
            // живой: иначе игрок после перезапуска получил бы раздачу, которую
            // не заказывал.
            game.awaitingDeal = awaiting
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
            cover: Card? = null,
            order: Suit? = null,
            awaiting: Boolean = false,
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
                cover = cover,
                order = order,
                awaiting = awaiting,
            ).also { game -> game.lastCard = lastCard ?: pile.lastOrNull() }
        }
    }

}

/**
 * Сколько карт берёт из колоды следующий за ходом — и свой ход он при этом
 * теряет (правило Катерины, 20.09). null — карта обычная, ничего не делает.
 *
 * Штрафные карты: шестёрка — одна, семёрка — две, пиковый король — четыре,
 * туз — ни одной. Туз потому и вынесен в ноль, а не в «нет штрафа»: он тоже
 * отнимает ход, просто брать после него нечего.
 *
 * Наружу по той же причине, что и [queenPrice]: экран объявляет штраф вслух,
 * и объявление обязано сойтись с тем, сколько карт и вправду ушло соседу.
 */
fun penaltyOf(card: Card): Int? = when {
    card.rank == Rank.SIX -> 1
    card.rank == Rank.SEVEN -> 2
    card.rank == Rank.ACE -> 0
    card.rank == Rank.KING && card.suit == Suit.SPADES -> 4
    else -> null
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
