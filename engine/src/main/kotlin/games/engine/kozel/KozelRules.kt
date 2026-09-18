package games.engine.kozel

import games.engine.tiles.Tile

/**
 * Договорённости сторон в «Козле»: то, о чём сговариваются до первой кости.
 *
 * В «Козла» играют по-разному не только в разных компаниях, но и в одной:
 * до скольких очков идёт матч, считается ли пусто-пусто за надбавку. Спорное
 * живёт здесь, а не в коде хода: по книге играет тот, кому так привычно, а
 * своё включает тот, кто это своё знает (см. `SETTINGS.md`, `KOZEL.md` 2.6).
 *
 * Набор принадлежит партии, а не экрану: начали по одним договорённостям —
 * по ним и доигрывают, иначе выигранное по одному уговору окажется
 * проигранным по другому.
 */
data class KozelRules(
    /**
     * До скольких очков идёт матч. Кто первым набрал — тот проиграл матч
     * и в этой партии называется «козлом».
     */
    val target: Int = 101,

    /**
     * Сколько очков стоит пусто-пусто, оставшаяся у проигравшего на руке
     * одной-единственной костью.
     *
     * Ноль — считаем её как есть, то есть за ноль. Двадцать пять — как в
     * компаниях, где её берегут: правило это чужое и необязательное, поэтому
     * по умолчанию выключено (KOZEL.md, 2.6).
     */
    val emptyDoubleBonus: Int = 0,
) {

    /** Всё по книге — ни одной договорённости сверх неё. */
    val byTheBook: Boolean get() = this == BOOK

    companion object {
        val BOOK = KozelRules()

        /** Надбавка за пусто-пусто, как играют в некоторых компаниях. */
        const val EMPTY_DOUBLE_AS_BONUS = 25
    }
}

/** Конец линии: куда приставляют кость. */
enum class End(val title: String) {
    LEFT("влево"),
    RIGHT("вправо"),
    ;

    val opposite: End get() = if (this == LEFT) RIGHT else LEFT
}

/**
 * Линия на столе: кости по порядку слева направо и числа на её концах.
 *
 * Держать концы отдельно от костей приходится потому, что по самим костям
 * их не восстановить: кость 1-3 может стоять у левого конца любой стороной,
 * и снаружи у неё будет то один, то три. А знать это нужно всем — и ходу,
 * и экрану, и боту.
 *
 * Кость приставляется только к одному из двух концов и кладётся с ним
 * встык; наружу остаётся её вторая половина. У дубля вторая половина та же,
 * поэтому дубль на конце оставляет число конца прежним.
 *
 * Линия замкнута с двух сторон: она не разветвляется, у неё всегда ровно
 * два конца. Это упрощение против «четырёхстороннего» домино, и оно
 * сознательное (KOZEL.md, 2.3): два конца незрячему игроку описать словами
 * можно, четыре ветки — нет.
 */
data class Line(
    val tiles: List<Tile> = emptyList(),
    /** Число на левом конце; null — линия пуста. */
    val left: Int? = null,
    /** Число на правом конце; null — линия пуста. */
    val right: Int? = null,
) {

    val isEmpty: Boolean get() = tiles.isEmpty()

    /** Кость этой линии, лежащая с самого левого края. */
    val first: Tile? get() = tiles.firstOrNull()

    /** Кость этой линии, лежащая с самого правого края. */
    val last: Tile? get() = tiles.lastOrNull()

    /**
     * Куда эту кость можно приставить. Пусто — нечем ходить.
     *
     * В пустую линию первая кость кладётся как угодно: у неё обе половины
     * сразу становятся концами. Такой ход записан концом [End.LEFT] — это
     * не выбор стороны, а «положить в пустоту».
     */
    fun canPlay(tile: Tile): List<End> {
        val leftEnd = left ?: return listOf(End.LEFT)
        val rightEnd = right ?: leftEnd
        return buildList {
            if (tile.has(leftEnd)) add(End.LEFT)
            if (tile.has(rightEnd)) add(End.RIGHT)
        }
    }

    /**
     * Положить кость на конец [end]. Кость уже проверена [canPlay] и
     * обязательно подходит: у пустой линии — любая.
     */
    fun place(tile: Tile, end: End): Line {
        val leftEnd = left ?: return Line(listOf(tile), tile.low, tile.high)
        val rightEnd = right ?: leftEnd
        return when (end) {
            End.LEFT -> Line(listOf(tile) + tiles, tile.other(leftEnd), rightEnd)
            End.RIGHT -> Line(tiles + tile, leftEnd, tile.other(rightEnd))
        }
    }

    /** Число на конце [end]; null — линия пуста. */
    fun endOf(end: End): Int? = when (end) {
        End.LEFT -> left
        End.RIGHT -> right
    }

    companion object {
        val EMPTY = Line()
    }
}

/**
 * Кость линии так, как она лежит: какой половиной повёрнута к левому краю,
 * какой — к правому.
 */
data class LaidTile(val left: Int, val right: Int) {
    /** Число на половине, смотрящей в сторону [end]. */
    fun endOf(end: End): Int = if (end == End.LEFT) left else right
}

/**
 * Кости линии по порядку слева направо — и каждая той стороной, которой она
 * в линии повёрнута.
 *
 * По одним костям поворота не видно: 1-3 у левого края стоит наружу и
 * единицей, и тройкой (KOZEL.md, 2.3). Восстановить его можно только от
 * конца: крайняя кость держит наружу записанный конец, её вторая половина
 * смотрит на соседа, и так до другого края.
 *
 * Нужно это рисующему экрану: линия из костей, повёрнутых как попало, не
 * сходится — соседние половины не совпадают, и нарисованное противоречит
 * сказанному.
 */
fun Line.laid(): List<LaidTile> {
    val start = left ?: return emptyList()
    val laid = mutableListOf<LaidTile>()
    var boundary = start
    for (tile in tiles) {
        if (!tile.has(boundary)) return laid
        val outer = tile.other(boundary)
        laid += LaidTile(boundary, outer)
        boundary = outer
    }
    return laid
}

/**
 * Какие кости из [hand] можно приставить к этой линии и куда именно.
 *
 * Одно место на всех: так ход, который предлагают игроку, и ход, который
 * видит бот, считаются одинаково. Разойтись им нельзя — иначе бот однажды
 * сыграет то, чего игроку не предложили.
 */
fun Line.placements(hand: List<Tile>): List<KozelMove.Place> =
    hand.flatMap { tile -> canPlay(tile).map { end -> KozelMove.Place(tile, end) } }

/**
 * Оба дубля разом — если по концам лежат их числа.
 *
 * Концы показывают разные числа, и к каждому на руке есть дубль: тогда за
 * один ход можно выложить оба. Дубль идёт только к своему числу — четвёрка к
 * четвёрке, единица к единице, — поэтому концы здесь не выбирают, а узнают:
 * спутать их нечем.
 *
 * Пусто — такого хода нет: концы одинаковые (дубль к этому числу в наборе
 * один, а второй приставить некуда), линия пуста, или к одному из концов
 * дубля на руке не случилось.
 *
 * Ход необязательный: и тот и другой дубль кладутся поодиночке обычным
 * [KozelMove.Place]. Это разрешение, а не обязанность (решение Катерины,
 * 18.09).
 */
fun Line.bothDoubles(hand: List<Tile>): KozelMove.PlaceBoth? {
    val leftEnd = left ?: return null
    val rightEnd = right ?: return null
    if (leftEnd == rightEnd) return null

    val left = hand.firstOrNull { it.isDouble && it.high == leftEnd } ?: return null
    val right = hand.firstOrNull { it.isDouble && it.high == rightEnd } ?: return null
    return KozelMove.PlaceBoth(left, right)
}

/**
 * Младший дубль на руке: пусто-пусто, потом один-один и так далее.
 *
 * Пусто — дублей на руке нет вовсе. По нему и выбирают, кому начинать раунд,
 * и им же этот раунд открывают: одно и то же правило, поэтому и одно место
 * (см. `KozelRound.openerSeat`).
 */
fun List<Tile>.lowestDouble(): Tile? = filter { it.isDouble }.minByOrNull { it.high }

/**
 * Все ходы, доступные месту с рукой [hand] при базаре в [bazaarSize] костей.
 *
 * Одно место на всех — и для игрока, и для бота. Разойтись им нельзя: ходы
 * бота считает [KozelView], ходы игрока — [KozelRound], и если правило
 * осядет в одном из них, бот однажды сыграет то, чего игроку не предложили.
 *
 * Подходящая кость есть — ходить ею, и тогда к обычным приставлениям
 * добавляется [Line.bothDoubles], если он возможен. Подходящих нет — берём из
 * базара, пока он не пуст; пуст — пропускаем. Открывают раунд младшим дублем,
 * и это единственный ход на пустой линии (см. [lowestDouble]).
 */
fun Line.movesFor(hand: List<Tile>, bazaarSize: Int): List<KozelMove> {
    // Начало раунда: линия пуста, и открыть её можно только младшим дублем.
    // Тем самым, по которому и выбрано, кому начинать, — у кого дубль младше,
    // тот им и ходит (решение Катерины, 18.09). Это не выбор игрока, а
    // правило, поэтому ход здесь один и тот же у всех: один ход — одна
    // подсказка, и та же самая у бота. Дублей нет ни у кого — открывать
    // нечем, и первый ход свободен, как всякий другой.
    if (isEmpty) {
        hand.lowestDouble()?.let { return listOf(KozelMove.Place(it, End.LEFT)) }
    }

    val places = placements(hand)
    if (places.isEmpty()) {
        return if (bazaarSize > 0) listOf(KozelMove.Draw) else listOf(KozelMove.Pass)
    }

    val both = bothDoubles(hand)
    return if (both == null) places else places + both
}
