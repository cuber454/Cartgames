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

    /** «Бито» — стол отбит, раунд окончен. */
    data object Pass : DurakMove
}

/** Пара на столе: карта атаки и, если отбита, карта защиты. */
data class Battle(val attack: Card, val defense: Card? = null) {
    val beaten: Boolean get() = defense != null
}

/**
 * «Дурак» подкидной с переводом, 36 карт.
 *
 * Три правила, о которых легко споткнуться, поэтому они явно зашиты
 * в код и вынесены сюда:
 *
 * 1. Кто отбился — тот и атакует в следующем раунде. Защищающийся,
 *    который забрал карты, свою атаку пропускает: ходит снова тот же
 *    игрок. Это классический вариант для игры вдвоём.
 * 2. Ходы за столом чередуются: положил карту — защищающийся ответил,
 *    только потом можно подкидывать. Пока на столе есть неотбитая карта,
 *    ход защищающегося, и атакующий ждёт. Раунд кончается явно — «бито»
 *    или «беру».
 * 3. Перевод — это ход защищающегося, и только пока на столе нет ни
 *    одной отбитой карты: он кладёт карту того же достоинства, что уже
 *    на столе, и атакующим становится сам. Отбиваться теперь соседу, и
 *    уже от всего стола разом. Цепочка переводов не ограничена, пока
 *    каждому следующему хватает карт. Правило включается настройкой:
 *    с [transferAllowed] = false выходит «подкидной» дурак, где
 *    защищающийся только отбивается или берёт.
 *
 * Партия считается оконченной, когда колода пуста и у кого-то кончились
 * карты: он вышел и выиграл, оставшийся с картами — «дурак». Если вышли
 * оба разом — ничья.
 */
class DurakGame private constructor(
    val trumpSuit: Suit,
    private val deck: Deck,
    private val hands: MutableList<MutableList<Card>>,
    private var attackerSeat: Int,
    /** Разрешён ли перевод. Правило партии, а не хода: переключается настройкой. */
    val transferAllowed: Boolean,
) : GameRules<DurakGame> {

    val playerCount: Int get() = hands.size

    /** Кто сейчас атакует. */
    val attacker: Int get() = attackerSeat

    /** Кто сейчас отбивается. */
    val defender: Int get() = (attackerSeat + 1) % playerCount

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

    // --- Ходы -------------------------------------------------------------

    fun legalMoves(seat: Int): List<DurakMove> {
        if (finished || seat !in hands.indices) return emptyList()

        val hand = hands[seat]
        val moves = mutableListOf<DurakMove>()
        val unbeaten = tableCards.filter { !it.beaten }

        when (seat) {
            attackerSeat -> {
                if (tableCards.isEmpty()) {
                    // Первый ход в раунде — можно любую карту.
                    hand.forEach { moves += DurakMove.Attack(it) }
                } else {
                    // Подкидывать можно, только когда защищающийся отбился от
                    // всего, что уже лежит на столе. За столом ходы чередуются:
                    // положил карту — получил ответ — положил следующую. Пока
                    // лежит неотбитая карта, ход не наш, и второй раз положить
                    // нельзя. Без этого правила атакующий вываливал на стол
                    // несколько карт подряд, а защищающийся «молчал», потому
                    // что приложение честно ждало его хода.
                    if (unbeaten.isEmpty()) {
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
            }

            defender -> {
                if (unbeaten.isNotEmpty()) {
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
                    if (transferAllowed && tableCards.all { !it.beaten }) {
                        val next = (seat + 1) % playerCount
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
            }
        }
        return moves
    }

    fun apply(seat: Int, move: DurakMove) {
        check(!finished) { "партия окончена" }
        check(move in legalMoves(seat)) { "недопустимый ход: $move" }

        when (move) {
            is DurakMove.Attack -> {
                hands[seat].remove(move.card)
                tableCards += Battle(move.card)
            }

            is DurakMove.Defend -> {
                hands[seat].remove(move.card)
                tableCards[move.tableIndex] = tableCards[move.tableIndex].copy(defense = move.card)
            }

            is DurakMove.Transfer -> {
                hands[seat].remove(move.card)
                tableCards += Battle(move.card)
                // Атака перешла тому, кто перевёл: отбивается теперь сосед,
                // а прежний атакующий встал на его место.
                attackerSeat = seat
            }

            DurakMove.Take -> {
                hands[defender].addAll(tableCards.flatMap { listOfNotNull(it.attack, it.defense) })
                tableCards.clear()
                // Защищающийся пропустил свою атаку — ходит снова тот же игрок.
            }

            DurakMove.Pass -> {
                discarded += tableCards.flatMap { listOfNotNull(it.attack, it.defense) }
                tableCards.clear()
                // Кто отбился — тот и атакует.
                attackerSeat = defender
            }
        }

        if (move is DurakMove.Take || move is DurakMove.Pass) refillAll()
        checkEnd()
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

    /** Добор до шести карт: сначала атакующий, потом защищающийся. */
    private fun refillAll() {
        for (seat in listOf(attackerSeat, defender)) {
            while (hands[seat].size < HAND_SIZE && !deck.isEmpty()) {
                hands[seat] += deck.draw()
            }
        }
    }

    private fun checkEnd() {
        if (finished) return
        if (!deck.isEmpty()) return
        // Пока на столе есть неотбитое, раунд не окончен.
        if (tableCards.any { !it.beaten }) return

        val withCards = hands.indices.filter { hands[it].isNotEmpty() }
        when (withCards.size) {
            0 -> {
                finished = true
                winner = null
                loser = null
            }

            1 -> {
                finished = true
                winner = hands.indices.first { it != withCards.first() }
                loser = withCards.first()
            }
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
         */
        fun start(
            random: Random = Random.Default,
            playerCount: Int = 2,
            transferAllowed: Boolean = true,
        ): DurakGame {
            require(playerCount in 2..6) { "от двух до шести игроков" }

            val deck = Deck(fullDeck36()).also { it.shuffle(random) }
            val hands = MutableList(playerCount) { mutableListOf<Card>() }
            repeat(HAND_SIZE) { hands.forEach { hand -> hand += deck.draw() } }

            val trumpSuit = deck.bottomOrNull()?.suit
                ?: error("колода пуста — такого быть не может")

            val attacker = hands.indices
                .filter { seat -> hands[seat].any { it.suit == trumpSuit } }
                .minByOrNull { seat -> hands[seat].filter { it.suit == trumpSuit }.minOf { it.rank.value } }
                ?: random.nextInt(playerCount)

            return DurakGame(trumpSuit, deck, hands, attacker, transferAllowed)
        }

        /**
         * Восстановить партию из сохранения: колода, руки, стол и отбой
         * задаются как есть, а кто вышел — пересчитывается тем же
         * [checkEnd], что и в живой игре. Так в файле не приходится
         * держать «партия окончена» отдельным полем, которое может
         * разойтись с настоящим положением дел.
         */
        fun restore(
            trumpSuit: Suit,
            deck: List<Card>,
            hands: List<List<Card>>,
            attacker: Int,
            table: List<Battle> = emptyList(),
            discarded: List<Card> = emptyList(),
            transferAllowed: Boolean = true,
        ): DurakGame {
            val game = DurakGame(
                trumpSuit = trumpSuit,
                deck = Deck(deck),
                hands = hands.map { it.toMutableList() }.toMutableList(),
                attackerSeat = attacker,
                transferAllowed = transferAllowed,
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
            transferAllowed: Boolean = true,
        ): DurakGame = DurakGame(
            trumpSuit = trumpSuit,
            deck = Deck(deck),
            hands = hands.map { it.toMutableList() }.toMutableList(),
            attackerSeat = attacker,
            transferAllowed = transferAllowed,
        )
    }
}
