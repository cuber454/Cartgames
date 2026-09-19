package games.engine.durak

import games.engine.Card
import games.engine.Deck
import games.engine.GameRules
import games.engine.Move
import games.engine.Suit
import games.engine.fullDeck36
import kotlin.random.Random

/** Размер руки, до которого добирают из колоды. */
private const val HAND_SIZE = 6

/** Больше шести карт на столе не бывает. */
private const val TABLE_LIMIT = 6

/**
 * Ходы в «Дураке».
 *
 * Кроме подкидного здесь есть перевод: защищающийся, пока ни одна карта не
 * отбита, кладёт свою карту того же достоинства и передаёт атаку соседу.
 * Правило необязательное — перевести можно, но никто не обязан.
 */
sealed interface DurakMove : Move {
    /** Положить карту на стол: первый ход или подкидывание. */
    data class Attack(val card: Card) : DurakMove

    /** Отбиться картой от карты атаки по её номеру на столе. */
    data class Defend(val card: Card, val tableIndex: Int) : DurakMove

    /** Перевести: положить карту того же достоинства и уступить атаку соседу. */
    data class Transfer(val card: Card) : DurakMove

    /** Забрать всё со стола. */
    data object Take : DurakMove

    /** «Бито» — стол отбит, заход окончен. */
    data object Pass : DurakMove
}

/** Пара на столе: карта атаки и, если отбита, карта защиты. */
data class Battle(val attack: Card, val defense: Card? = null) {
    val beaten: Boolean get() = defense != null
}

/**
 * «Дурак» подкидной с переводом, 36 карт, от двух до шести игроков.
 *
 * Каждый играет за себя: атакующий ходит под соседа, остальные подкидывают.
 * Правила, о которых легко споткнуться, поэтому они явно зашиты в код:
 *
 * 1. Кто отбился — тот и атакует в следующем заходе. Защищающийся, который
 *    забрал карты, свою атаку пропускает: атака переходит через него к
 *    следующему. На двоих это ровно «ходит снова тот же игрок».
 * 2. Подкидывают по кругу. Право подкидывать идёт от игрока к игроку, минуя
 *    защищающегося, и заход кончается только тогда, когда отказались все:
 *    отказ одного передаёт право дальше, а не закрывает стол. Пока право не
 *    вернулось, вторую карту положить нельзя — книга говорит это прямо:
 *    «даже если у вас на руке окажется король, вы не можете выложить его,
 *    пока право подкидывать снова не дойдёт до вас». Первым подкидывает
 *    атакующий: пока от первой карты не отбились, заход ещё его.
 * 3. Перевод — это ход защищающегося, и только пока на столе нет ни
 *    одной отбитой карты: он кладёт карту того же достоинства, что уже
 *    на столе, и атакующим становится сам. Отбиваться теперь соседу, и
 *    уже от всего стола разом. Цепочка переводов не ограничена, пока
 *    каждому следующему хватает карт. Правило включается настройкой:
 *    с `rules.transfer = false` выходит «подкидной» дурак, где
 *    защищающийся только отбивается или берёт.
 *
 * Партия считается оконченной, когда колода пуста и вышел предпоследний:
 * вышедший первым выиграл, оставшийся с картами — «дурак». Если вышли
 * все разом — ничья.
 *
 * Порядок добора — четвёртое место, где легко ошибиться, и ошибка эта
 * невидима: карты из колоды достанутся не тем. По книге первым берёт тот,
 * кто начинал заход, а последним — тот, под кого ходили ([refillOrder]).
 */
class DurakGame private constructor(
    val trumpSuit: Suit,
    private val deck: Deck,
    private val hands: MutableList<MutableList<Card>>,
    private var attackerSeat: Int,
    /** Договорённости сторон. Правило партии, а не хода: начали по ним — по ним и доигрывают. */
    val rules: DurakRules = DurakRules.BOOK,
) : GameRules<DurakGame> {

    /** Чьё сейчас право подкидывать. Ходит по кругу, минуя защищающегося. */
    private var throwerSeat: Int = attackerSeat

    /** Кто в этом заходе уже отказался подкидывать. */
    private val passedThrowers = mutableSetOf<Int>()

    /** Кто вышел из игры, в порядке выхода. Первый вышедший — победитель. */
    private val exited = mutableListOf<Int>()

    val playerCount: Int get() = hands.size

    /** Кто сейчас атакует — чей это заход. */
    val attacker: Int get() = attackerSeat

    /** Кто сейчас отбивается: следующий за атакующим, кто ещё в игре. */
    val defender: Int get() = nextAlive(attackerSeat)

    /**
     * Чей ход. Экран раньше выводил очередь сам из «у кого есть ходы», и на
     * двоих это сходилось; на троих и больше очередь надо знать точно — ею
     * ходят и защищающийся, и подкидывающие по кругу.
     */
    val turn: Int
        get() = when {
            tableCards.isEmpty() -> attackerSeat
            tableCards.any { !it.beaten } -> defender
            else -> throwerSeat
        }

    /** Кто уже вышел из игры. */
    fun exitedSeats(): List<Int> = exited.toList()

    val table: List<Battle> get() = tableCards.toList()

    private val tableCards = mutableListOf<Battle>()
    private val discarded = mutableListOf<Card>()

    var finished = false
        private set

    var winner: Int? = null
        private set

    /** Кто остался с картами, то есть «дурак». null — вышел ровно один. */
    var loser: Int? = null
        private set

    /** Козырь и нижняя карта — по ней он и определяется. */
    val trumpCard: Card? get() = deck.bottomOrNull()

    fun deckSize(): Int = deck.size

    fun discardSize(): Int = discarded.size

    /**
     * Карты, которые уже вышли из игры: отбой и то, что лежит на столе.
     * По ним соперник считает, чего у противника быть не может.
     */
    fun playedCards(): List<Card> =
        discarded + tableCards.flatMap { listOfNotNull(it.attack, it.defense) }

    fun handOf(seat: Int): List<Card> = hands[seat].toList()

    /** Колода сверху вниз, как она лежит. Нужно для сохранения партии. */
    fun deckCards(): List<Card> = deck.toList()

    /** Отбой: карты, ушедшие из игры «бито». */
    fun discardedCards(): List<Card> = discarded.toList()

    /** Козырь есть у руки? Нужно для подсказок вроде «козырей нет». */
    fun hasTrump(seat: Int): Boolean = hands[seat].any { it.suit == trumpSuit }

    // --- Места за столом --------------------------------------------------

    /** Кто ещё в игре: у вышедших карт нет, и роли на них не переходят. */
    private val aliveSeats: List<Int> get() = hands.indices.filter { it !in exited }

    /** Следующее место по кругу, пропуская вышедших. */
    private fun nextAlive(seat: Int): Int {
        val alive = aliveSeats
        if (alive.size < 2) return seat
        var next = (seat + 1) % playerCount
        while (next !in alive) next = (next + 1) % playerCount
        return next
    }

    /**
     * Следующий, за кем право подкидывать: по кругу и минуя защищающегося —
     * он не подкидывает, он отбивается.
     */
    private fun nextThrower(seat: Int): Int {
        if (aliveSeats.size < 2) return seat
        val under = defender
        var next = nextAlive(seat)
        while (next == under) next = nextAlive(next)
        return next
    }

    // --- Ходы -------------------------------------------------------------

    fun legalMoves(seat: Int): List<DurakMove> {
        if (finished || seat !in hands.indices || seat in exited) return emptyList()

        val hand = hands[seat]
        val moves = mutableListOf<DurakMove>()
        val unbeaten = tableCards.filter { !it.beaten }

        when {
            // Пока на столе есть неотбитая карта, ход защищающегося. Пустая
            // рука — не тупик: «беру» сказать можно всегда, иначе заход встал
            // бы, если последнюю карту игрок положил на стол.
            unbeaten.isNotEmpty() -> {
                if (seat != defender) return emptyList()
                unbeaten.forEach { battle ->
                    val index = tableCards.indexOf(battle)
                    hand.filter { beats(it, battle.attack) }
                        .forEach { moves += DurakMove.Defend(it, index) }
                }
                // Перевод: пока не отбита ни одна карта, защищающийся может
                // положить свою карту того же достоинства и уступить атаку
                // соседу. У соседа должно хватить карт на весь стол вместе
                // с этой — иначе перевод был бы способом подсунуть ему
                // больше, чем он в силах отбить.
                if (rules.transfer && tableCards.all { !it.beaten }) {
                    val next = nextAlive(seat)
                    val enoughCards = hands[next].size >= tableCards.size + 1
                    if (enoughCards) {
                        val ranksOnTable = tableCards
                            .flatMap { listOfNotNull(it.attack.rank, it.defense?.rank) }
                            .toSet()
                        hand.filter { it.rank in ranksOnTable }
                            .forEach { moves += DurakMove.Transfer(it) }
                    }
                }
                moves += DurakMove.Take
            }

            // Стол пуст — заход начинает атакующий, и первую карту кладёт
            // любую.
            tableCards.isEmpty() -> {
                if (seat != attackerSeat) return emptyList()
                hand.forEach { moves += DurakMove.Attack(it) }
            }

            // Стол отбит целиком — подкидывает тот, до кого дошло право.
            // Отказ здесь всегда возможен: нечем подкидывать или не хочется.
            else -> {
                if (seat != throwerSeat) return emptyList()
                if (tableCards.size < tableLimit()) {
                    val ranksOnTable = tableCards
                        .flatMap { listOfNotNull(it.attack.rank, it.defense?.rank) }
                        .toSet()
                    hand.filter { it.rank in ranksOnTable }
                        .forEach { moves += DurakMove.Attack(it) }
                }
                moves += DurakMove.Pass
            }
        }
        return moves
    }

    fun apply(seat: Int, move: DurakMove) {
        check(!finished) { "партия окончена" }
        check(move in legalMoves(seat)) { "недопустимый ход: $move" }

        // Кто начинал заход и под кого ходили — запоминаем до хода. «Бито»
        // передаёт атаку отбившемуся, и по полям порядка добора потом уже не
        // восстановить: оба поля начнут указывать на новых игроков.
        val started = attackerSeat
        val under = defender

        // Заход окончен этим ходом? От этого зависит добор: карты из колоды
        // берут по концу захода, а не после каждого отказа подкинуть.
        var roundOver = false

        when (move) {
            is DurakMove.Attack -> {
                hands[seat].remove(move.card)
                tableCards += Battle(move.card)
                // Карта легла — заход продолжился, прежние отказы не в счёт.
                passedThrowers.clear()
            }

            is DurakMove.Defend -> {
                hands[seat].remove(move.card)
                tableCards[move.tableIndex] = tableCards[move.tableIndex].copy(defense = move.card)
                // Отбился от всего — право подкидывать идёт дальше по кругу.
                if (tableCards.all { it.beaten }) throwerSeat = nextThrower(throwerSeat)
            }

            is DurakMove.Transfer -> {
                hands[seat].remove(move.card)
                tableCards += Battle(move.card)
                // Атака перешла тому, кто перевёл: отбивается теперь сосед,
                // а прежний атакующий встал на его место.
                attackerSeat = seat
                throwerSeat = attackerSeat
                passedThrowers.clear()
            }

            DurakMove.Take -> {
                hands[defender].addAll(tableCards.flatMap { listOfNotNull(it.attack, it.defense) })
                tableCards.clear()
                // Защищающийся пропустил свою атаку — ходит следующий за ним.
                attackerSeat = nextAlive(defender)
                roundOver = true
            }

            DurakMove.Pass -> {
                // Отказ не закрывает заход: право подкидывать идёт по кругу, и
                // стол уходит в отбой только когда отказались все — тогда
                // очередь возвращается к тому, кто отказался первым.
                passedThrowers += seat
                val next = nextThrower(seat)
                if (next in passedThrowers) {
                    discarded += tableCards.flatMap { listOfNotNull(it.attack, it.defense) }
                    tableCards.clear()
                    // Кто отбился — тот и атакует.
                    attackerSeat = under
                    roundOver = true
                } else {
                    throwerSeat = next
                }
            }
        }

        if (roundOver) {
            resetRound()
            refillAll(started, under)
        }
        checkEnd()
    }

    /** Новый заход: право подкидывать начинается с атакующего, отказы забыты. */
    private fun resetRound() {
        throwerSeat = attackerSeat
        passedThrowers.clear()
    }

    /** Может ли [candidate] побить [target]: старше в той же масти или козырь. */
    fun beats(candidate: Card, target: Card): Boolean = when {
        candidate.suit == target.suit -> candidate.rank.value > target.rank.value
        candidate.suit == trumpSuit && target.suit != trumpSuit -> true
        else -> false
    }

    /** Сколько карт ещё можно положить на стол. */
    private fun tableLimit(): Int {
        val defenderCanStillBeat = hands[defender].size + tableCards.count { it.beaten }
        return minOf(TABLE_LIMIT, defenderCanStillBeat)
    }

    /**
     * Добор до шести карт: первым берёт тот, кто начинал заход, последним —
     * тот, под кого ходили, а между ними места идут по кругу ([refillOrder]).
     *
     * Порядок здесь не формальность, а правило, и записано оно в книге
     * отдельной строкой: первым берёт тот, кто начинал заход, а в самом
     * конце карты добирает тот, под кого ходили. Добери наоборот — и карты
     * из колоды разойдутся не тем: ошибка не видна ни на столе, ни в счёте,
     * только в том, кому что пришло.
     *
     * Именно поэтому игроки приходят сюда парой, а не берутся из полей:
     * к моменту добора после «бито» атака уже передана, и `attackerSeat`
     * с `defender` называют не тех, кто заход начинал.
     */
    private fun refillAll(first: Int, second: Int) {
        for (seat in refillOrder(first, second)) {
            if (seat in exited) continue
            while (hands[seat].size < HAND_SIZE && !deck.isEmpty()) {
                hands[seat] += deck.draw()
            }
        }
    }

    /**
     * Порядок добора: начинавший заход, дальше круг остальных, а тот, под
     * кого ходили, — последний.
     *
     * Середину порядку книга не задаёт: за столом на двоих её и нет, а на
     * троих и больше ничем, кроме круга, места не упорядочить. Круг — то же
     * правило, по которому ходят и подкидывают.
     */
    private fun refillOrder(first: Int, second: Int): List<Int> {
        val order = mutableListOf(first)
        var seat = first
        repeat(playerCount - 1) {
            seat = (seat + 1) % playerCount
            if (seat != second) order += seat
        }
        order += second
        return order
    }

    private fun checkEnd() {
        if (finished) return
        if (!deck.isEmpty()) return
        // Пока на столе есть неотбитое, заход не окончен.
        if (tableCards.any { !it.beaten }) return

        hands.indices.forEach { if (hands[it].isEmpty() && it !in exited) exited += it }

        val withCards = hands.indices.filter { hands[it].isNotEmpty() }
        when (withCards.size) {
            0 -> {
                finished = true
                winner = null
                loser = null
            }

            1 -> {
                finished = true
                // Кто вышел первым — тот выиграл; последний с картами — дурак.
                winner = exited.firstOrNull()
                loser = withCards.first()
            }
        }

        // Атакующий вышел — заход переходит к следующему, кто ещё в игре.
        if (!finished && attackerSeat in exited) {
            attackerSeat = nextAlive(attackerSeat)
            resetRound()
        }
    }

    // --- Озвучка ----------------------------------------------------------

    /** Рука вслух: «семёрка червей, туз пик». */
    fun spokenHand(seat: Int): String =
        hands[seat].joinToString(", ") { it.spoken() }.ifEmpty { "карт нет" }

    /** Стол вслух. */
    fun spokenTable(): String =
        if (tableCards.isEmpty()) {
            "стол пуст"
        } else {
            tableCards.joinToString("; ") { battle ->
                val defense = battle.defense
                if (defense == null) {
                    "${battle.attack.spoken()} — не отбита"
                } else {
                    "${battle.attack.spoken()} — отбита ${defense.spoken()}"
                }
            }
        }

    /** Фраза про только что сделанный ход. */
    override fun describe(state: DurakGame, seat: Int, move: Move): String = when (move) {
        is DurakMove.Attack -> "Сыграна ${move.card.spoken()}."
        is DurakMove.Defend -> "Отбито картой ${move.card.spoken()}."
        is DurakMove.Transfer -> "Переведено картой ${move.card.spoken()}."
        DurakMove.Take -> "Карты со стола забраны."
        DurakMove.Pass -> "Бито, стол ушёл в отбой."
        else -> "Ход сделан."
    }

    // --- GameRules --------------------------------------------------------

    override fun legalMoves(state: DurakGame, seat: Int): List<Move> = legalMoves(seat)

    override fun apply(state: DurakGame, seat: Int, move: Move): DurakGame {
        apply(seat, move as DurakMove)
        return this
    }

    override fun isFinished(state: DurakGame): Boolean = finished

    override fun winner(state: DurakGame): Int? = winner

    companion object {
        /**
         * Новая партия: колода тасуется, по шесть карт каждому, козырь —
         * по нижней карте. Первым ходит тот, у кого младший козырь;
         * если козырей ни у кого нет — как решит жребий.
         *
         * [firstAttacker] задаётся, когда партия начинается не с чистого
         * листа: по договорённости первым ходит дурак прошлой партии. Кто
         * дурак, знает доигранная партия, а не эта, — поэтому и приходит
         * сюда числом, а не берётся из [DurakRules].
         */
        fun start(
            random: Random = Random.Default,
            playerCount: Int = 2,
            rules: DurakRules = DurakRules.BOOK,
            firstAttacker: Int? = null,
        ): DurakGame {
            require(playerCount in 2..6) { "от двух до шести игроков" }

            val deck = Deck(fullDeck36()).also { it.shuffle(random) }
            val hands = MutableList(playerCount) { mutableListOf<Card>() }
            repeat(HAND_SIZE) { hands.forEach { hand -> hand += deck.draw() } }

            // Вшестером тридцать шесть карт раздаются целиком, и нижней карты
            // в колоде не остаётся — открывать нечего. Книга и на это
            // отвечает: козырную масть назначает последняя карта раздачи,
            // а последней ложится карта последней руки.
            val trumpSuit = (deck.bottomOrNull() ?: hands.last().last()).suit

            val attacker = firstAttacker?.takeIf { it in hands.indices }
                ?: hands.indices
                    .filter { seat -> hands[seat].any { it.suit == trumpSuit } }
                    .minByOrNull { seat ->
                        hands[seat].filter { it.suit == trumpSuit }.minOf { it.rank.value }
                    }
                ?: random.nextInt(playerCount)

            return DurakGame(trumpSuit, deck, hands, attacker, rules)
        }

        /**
         * Восстановить партию из сохранения: колода, руки, стол и отбой
         * задаются как есть, а кто вышел — пересчитывается тем же
         * [checkEnd], что и в живой игре. Так в файле не приходится
         * держать «партия окончена» отдельным полем, которое может
         * разойтись с настоящим положением дел.
         *
         * Право подкидывать и отказы в сохранении не лежат: заход после
         * подъёма начинается заново с атакующего. Потеря это или нет —
         * зависит от того, когда записали партию, и хуже от неё никому не
         * становится: отказы игроков не право, а память о ходе.
         */
        fun restore(
            trumpSuit: Suit,
            deck: List<Card>,
            hands: List<List<Card>>,
            attacker: Int,
            table: List<Battle> = emptyList(),
            discarded: List<Card> = emptyList(),
            rules: DurakRules = DurakRules.BOOK,
        ): DurakGame {
            val game = DurakGame(
                trumpSuit = trumpSuit,
                deck = Deck(deck),
                hands = hands.map { it.toMutableList() }.toMutableList(),
                attackerSeat = attacker,
                rules = rules,
            )
            game.tableCards += table
            game.discarded += discarded
            game.checkEnd()
            return game
        }

        /** Партия с заданными руками — для тестов и разбора ситуаций. */
        fun forTesting(
            trumpSuit: Suit,
            hands: List<List<Card>>,
            deck: List<Card> = emptyList(),
            attacker: Int = 0,
            table: List<Battle> = emptyList(),
            discarded: List<Card> = emptyList(),
            rules: DurakRules = DurakRules.BOOK,
        ): DurakGame {
            val game = DurakGame(
                trumpSuit = trumpSuit,
                deck = Deck(deck),
                hands = hands.map { it.toMutableList() }.toMutableList(),
                attackerSeat = attacker,
                rules = rules,
            )
            game.tableCards += table
            game.discarded += discarded
            return game
        }
    }
}
