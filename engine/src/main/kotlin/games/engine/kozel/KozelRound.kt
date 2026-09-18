package games.engine.kozel

import games.engine.GameRules
import games.engine.Move
import games.engine.tiles.Tile
import games.engine.tiles.TileSet
import games.engine.tiles.pipsName
import kotlin.random.Random

/** Игроков за столом: ты и бот. Игру вчетвером парами оставляем на потом. */
const val SEATS = 2

/**
 * Сколько костей на руке в игре вдвоём. Остальные четырнадцать уходят
 * в базар — именно поэтому вдвоём рука длиннее, чем вчетвером.
 */
const val HAND_SIZE = 7

/**
 * Сколько пропусков подряд закрывают раунд «рыбой». Двое пропустили —
 * ходить некому: базар пуст, а подходящих костей нет ни у кого.
 */
const val PASSES_TO_FISH = SEATS

/** Ход в «Козле». */
sealed interface KozelMove : Move {

    /** Положить кость на конец [end]. В пустую линию — любую, концом «влево». */
    data class Place(val tile: Tile, val end: End) : KozelMove

    /**
     * Положить оба дубля разом: [left] на левый конец, [right] на правый.
     *
     * Так можно, только когда концы показывают разные числа и на руке лежит
     * дубль к каждому из них (см. [bothDoubles]): дубль идёт к своему числу,
     * и другого числа у него нет, поэтому спутать концы нечем. Ход
     * необязательный — каждый из этих дублей кладётся и порознь, обычным
     * [Place].
     */
    data class PlaceBoth(val left: Tile, val right: Tile) : KozelMove

    /**
     * Взять одну кость из базара.
     *
     * Один ход — одна кость, а не «тянуть, пока не найдёшь»: так ход
     * остаётся одним решением и его слышно по одной кости за раз. Вытянутая
     * кость, если она подходит, кладётся в тот же ход — иначе подходящей
     * было бы чем ходить, и брать из базара ход не давал бы (KOZEL.md, 2.4).
     */
    data object Draw : KozelMove

    /** Пропустить ход: подходящих костей нет и базар пуст. */
    data object Pass : KozelMove
}

/**
 * Итог раунда: кто записал очки и сколько.
 *
 * [winner] пуст только при ничейной «рыбе» — тогда никто никому ничего не
 * пишет. [fish] говорит, чем раунд кончился: выходом или закрытой линией.
 */
data class RoundScore(val winner: Int?, val points: Int, val fish: Boolean)

/**
 * Раунд «Козла»: раздача, ходы, закрытый базар, «рыба» и очки.
 *
 * Раунд — это правило игры, а не экран: он отвечает на вопросы, а как их
 * произнести, решает слой доступности. Из этого же следует главное
 * свойство базара: он закрытый. Косточки перетасованы один раз при раздаче,
 * и порядок этот не знает никто — ни игрок, ни бот; и тот и другой берут
 * сверху, то есть вслепую и равновероятно (KOZEL.md, 4.2).
 *
 * Бот за столом не получает раунд вовсе — только [KozelView] (KOZEL.md, 4.1).
 * Держать базар в тайне от него приходится не обещанием, а типом: в раунде
 * он открыт экрану и тестам, а в [KozelView] его нет — там только счётчик.
 */
class KozelRound private constructor(
    val rules: KozelRules,
    private val hands: MutableList<MutableList<Tile>>,
    private val bazaar: MutableList<Tile>,
    private var line: Line,
    /** Кто ходит сейчас. */
    private var turnSeat: Int,
    /** Сколько ходов подряд пропущено. Сброс — как только кость легла. */
    private var passesCount: Int,
) : GameRules<KozelRound> {

    /** Кто вышел, выложив последнюю кость. Пусто — ещё не вышел никто. */
    var out: Int? = null
        private set

    /** Линия закрыта: базар пуст и ходить нечем никому. */
    var fish: Boolean = false
        private set

    /** Чей ход. */
    val turn: Int get() = turnSeat

    /** Кости на руке места [seat]. */
    fun handOf(seat: Int): List<Tile> = hands[seat]

    /** Сколько костей у места [seat]. */
    fun handSize(seat: Int): Int = hands[seat].size

    /** Сколько костей осталось в базаре. Содержимого не показываем никому. */
    val bazaarSize: Int get() = bazaar.size

    /**
     * Сколько ходов подряд пропущено.
     *
     * Наружу — затем, что это часть состояния партии, а не мелкая подробность
     * хода: раунд, поднятый с диска после первого пропуска, обязан помнить,
     * что он был, иначе второй пропуск не закроет линию «рыбой».
     */
    val passes: Int get() = passesCount

    /**
     * Кости, оставшиеся в базаре, — в том порядке, в каком их будут брать.
     *
     * `internal` нарочно: наружу из движка содержимое базара не уходит
     * вовсе, поэтому ни экран, ни бот его не увидят — экрану оно и не нужно,
     * а боту и подавно. Знать, какие кости ещё лежат в закрытом базаре,
     * нельзя никому: за столом этого не знает и человек (KOZEL.md, 4.1).
     */
    internal val bazaarTiles: List<Tile> get() = bazaar.toList()

    /** Линия на столе. */
    val table: Line get() = line

    /** Сколько костей лежит на столе. */
    val tableSize: Int get() = line.tiles.size

    val finished: Boolean get() = out != null || fish

    /** Кто выиграл раунд. Пусто — раунд идёт или кончился ничьей. */
    val winner: Int? get() = if (finished) score().winner else null

    // --- Ходы -------------------------------------------------------------

    /**
     * Допустимые ходы места [seat].
     *
     * Подходящая кость есть — ходить обязательно ею, брать из базара нельзя.
     * Подходящих нет — берём из базара, пока он не пуст; пуст — пропускаем.
     * Считает их [movesFor] — тот же код, что и у бота: разойтись этим двум
     * нельзя.
     */
    fun legalMoves(seat: Int): List<KozelMove> {
        if (finished || seat != turnSeat) return emptyList()
        return line.movesFor(hands[seat], bazaar.size)
    }

    fun apply(seat: Int, move: KozelMove) {
        require(!finished) { "раунд уже кончился" }
        require(seat == turnSeat) { "сейчас ходит не это место" }

        when (move) {
            is KozelMove.Place -> {
                require(hands[seat].remove(move.tile)) { "кости ${move.tile.spoken()} нет на руке" }
                line = line.place(move.tile, move.end)
                // Кость легла — счёт пропусков начинается заново.
                passesCount = 0
                if (hands[seat].isEmpty()) {
                    out = seat
                    return
                }
                turnSeat = other(seat)
            }

            is KozelMove.PlaceBoth -> {
                require(hands[seat].remove(move.left)) {
                    "кости ${move.left.spoken()} нет на руке"
                }
                require(hands[seat].remove(move.right)) {
                    "кости ${move.right.spoken()} нет на руке"
                }
                // Слева направо: сначала левый конец, потом правый. Порядок
                // здесь ни на что не влияет — оба дубля оставляют свой конец
                // прежним, — но так линия собирается в том же порядке, в
                // каком читается.
                line = line.place(move.left, End.LEFT).place(move.right, End.RIGHT)
                passesCount = 0
                if (hands[seat].isEmpty()) {
                    out = seat
                    return
                }
                turnSeat = other(seat)
            }

            KozelMove.Draw -> {
                hands[seat] += bazaar.removeAt(0)
                // Ход остаётся у того же места: вытянутая кость, если она
                // подходит, кладётся в этот же ход. Не подошла — следующим
                // ходом он возьмёт ещё, пока базар не опустеет.
            }

            KozelMove.Pass -> {
                passesCount++
                if (passesCount >= PASSES_TO_FISH) {
                    fish = true
                    return
                }
                turnSeat = other(seat)
            }
        }
    }

    // --- Подсчёт ----------------------------------------------------------

    /**
     * Очки на руке места [seat]: точки всех его костей.
     *
     * Пусто-пусто при договорённости [KozelRules.emptyDoubleBonus] стоит не
     * ноль, а двадцать пять — и только когда она осталась на руке одна.
     */
    fun handPoints(seat: Int): Int {
        val hand = hands[seat]
        val bonus = if (
            rules.emptyDoubleBonus != 0 &&
            hand.size == 1 &&
            hand[0].isDouble &&
            hand[0].low == 0
        ) {
            rules.emptyDoubleBonus - hand[0].pips
        } else {
            0
        }
        return hand.sumOf { it.pips } + bonus
    }

    /**
     * Итог раунда. У вышедшего — очки проигравшего; при «рыбе» — очки того,
     * у кого рука легче, а при равных руках никто не получает ничего.
     */
    fun score(): RoundScore {
        check(finished) { "раунд ещё идёт" }

        out?.let { winner ->
            val loser = other(winner)
            return RoundScore(winner, handPoints(loser), fish = false)
        }

        val mine = handPoints(0)
        val theirs = handPoints(1)
        return when {
            mine < theirs -> RoundScore(0, theirs, fish = true)
            theirs < mine -> RoundScore(1, mine, fish = true)
            else -> RoundScore(null, 0, fish = true)
        }
    }

    // --- Озвучка ----------------------------------------------------------

    /** Рука вслух: «шесть-три, пусто-пять». */
    fun spokenHand(seat: Int): String =
        hands[seat].joinToString(", ") { it.spoken() }.ifEmpty { "костей нет" }

    /**
     * Концы вслух: «Концы: три и пять». Пустая линия — «концов нет»:
     * первая кость кладётся в пустоту и ни к чему не приставляется.
     */
    fun spokenEnds(): String {
        val left = line.left ?: return "Концов нет"
        val right = line.right ?: left
        return if (left == right) {
            "Конец: ${pipsName(left)}"
        } else {
            "Концы: ${pipsName(left)} и ${pipsName(right)}"
        }
    }

    /** Фраза про только что сделанный ход. */
    override fun describe(state: KozelRound, seat: Int, move: Move): String = when (move) {
        is KozelMove.Place -> "Кость ${move.tile.spoken()} ${move.end.title}."
        is KozelMove.PlaceBoth ->
            "Две кости: ${move.left.spoken()} ${End.LEFT.title}, " +
                "${move.right.spoken()} ${End.RIGHT.title}."
        KozelMove.Draw -> "Взято из базара."
        KozelMove.Pass -> "Ход пропущен."
        else -> "Ход сделан."
    }

    // --- GameRules --------------------------------------------------------

    override fun legalMoves(state: KozelRound, seat: Int): List<Move> = legalMoves(seat)

    override fun apply(state: KozelRound, seat: Int, move: Move): KozelRound {
        apply(seat, move as KozelMove)
        return this
    }

    override fun isFinished(state: KozelRound): Boolean = finished

    override fun winner(state: KozelRound): Int? = winner

    companion object {

        /**
         * Новая раздача: набор тасуется, по семь костей каждому, остальные
         * четырнадцать — закрытый базар. Первым ходит тот, у кого младший
         * дубль; дублей ни у кого нет — у кого старшая кость по сумме точек.
         */
        fun deal(
            rules: KozelRules = KozelRules.BOOK,
            random: Random = Random.Default,
        ): KozelRound {
            val set = TileSet.shuffled(random)
            val hands = MutableList(SEATS) { mutableListOf<Tile>() }
            repeat(HAND_SIZE) { hands.forEach { hand -> hand += set.removeAt(0) } }
            return of(rules, hands, set, opener = openerSeat(hands))
        }

        /**
         * Раунд из готового расклада: так его собирают тесты и так его
         * поднимают с диска. Порядок костей в [bazaar] — тот самый порядок,
         * в котором их будут брать: первый элемент уходит первым.
         *
         * [line], [turn] и [passes] задают, когда раунд поднимают не с
         * начала, а с середины: линия на столе уже лежит, ход не у первого,
         * и кто-то только что пропустил.
         */
        fun of(
            rules: KozelRules = KozelRules.BOOK,
            hands: List<List<Tile>>,
            bazaar: List<Tile> = emptyList(),
            opener: Int = 0,
            line: Line = Line.EMPTY,
            turn: Int = opener,
            passes: Int = 0,
        ): KozelRound {
            require(hands.size == SEATS) { "за столом $SEATS места" }
            require(opener in 0 until SEATS) { "первым ходит место за столом" }
            require(turn in 0 until SEATS) { "ходит место за столом" }
            return KozelRound(
                rules = rules,
                hands = MutableList(SEATS) { hands[it].toMutableList() },
                bazaar = bazaar.toMutableList(),
                line = line,
                turnSeat = turn,
                passesCount = passes,
            )
        }

        /** Второе место за столом. Игроков двое, поэтому это просто «не он». */
        fun other(seat: Int): Int = (seat + 1) % SEATS

        /**
         * Кто начинает раунд.
         *
         * Младший дубль: пусто-пусто, потом один-один и так далее — чей
         * дубль младше, тот и ходит первым, и он же обязан открыть раунд
         * этим дублем (см. [movesFor]). Дублей ни у кого нет — старшая
         * кость по сумме точек; при равной сумме смотрим на старшую
         * половину, а если и она равна, берёт тот, кто за столом младше по
         * месту. Полное равенство возможно: шесть-три и пять-четыре стоят
         * одинаково, а сдать их может обоим.
         */
        fun openerSeat(hands: List<List<Tile>>): Int {
            /** Младший дубль на руке — по нему место и торопится с ходом. */
            fun lowestDouble(seat: Int): Int? = hands[seat].lowestDouble()?.high

            val withDouble = hands.indices.filter { lowestDouble(it) != null }
            if (withDouble.isNotEmpty()) {
                var best = withDouble.first()
                for (seat in withDouble) {
                    if (lowestDouble(seat)!! < lowestDouble(best)!!) best = seat
                }
                return best
            }

            fun rank(seat: Int): Pair<Int, Int> {
                val best = hands[seat].maxByOrNull { it.pips }
                return (best?.pips ?: 0) to (best?.high ?: 0)
            }

            var best = 0
            for (seat in 1 until hands.size) {
                val (pips, half) = rank(seat)
                val (bestPips, bestHalf) = rank(best)
                if (pips > bestPips || (pips == bestPips && half > bestHalf)) best = seat
            }
            return best
        }
    }
}
