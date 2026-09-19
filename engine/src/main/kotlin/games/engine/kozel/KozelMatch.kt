package games.engine.kozel

import kotlin.random.Random

/**
 * Что случилось за раунд — глазами матча, а не раунда.
 *
 * Раунд знает только свои очки; про то, что очки эти кто-то записал себе и
 * не кончился ли на них матч, известно матчу. Экрану нужно именно это,
 * поэтому он читает итог отсюда, а не из раунда.
 */
data class KozelSummary(
    /** Кто выиграл раунд. Пусто — ничейная «рыба». */
    val winner: Int?,
    /**
     * Сколько записано каждому месту за раунд: `written[seat]` — его очки.
     *
     * Список, а не одно число, потому что проигравших за столом на троих
     * двое: вышедший не пишет ничего, а каждый из оставшихся считает свои.
     * Ноль у места — ему за этот раунд не записали ничего.
     */
    val written: List<Int>,
    /** Раунд кончился «рыбой», а не выходом. */
    val fish: Boolean,
    /** Счёт матча после раунда: сколько набрано каждым. */
    val scores: List<Int>,
    /** Кто стал «козлом» — дошёл до цели. Пусто — матч идёт. */
    val goat: Int?,
)

/**
 * Матч в «Козла»: раунд за раундом, пока кто-нибудь не наберёт сто одно
 * очко. Набравший проиграл матч и в этой партии называется «козлом».
 *
 * Очки в «Козле» штрафные: за раунд их записывают тому, кто его проиграл, —
 * тому, у кого к концу раунда остались на руке кости. Вышел первым —
 * соперник считает свои; закрыл линию «рыбой» с более лёгкой рукой — та же
 * запись ложится на соперника, только уже по его руке. Поэтому счёт матча —
 * это счёт отыгранных раздач: «козлом» становится тот, кто набрал больше
 * всех, то есть кому меньше всех удалось сбросить кости.
 *
 * Матч держит раунд, а не наоборот: раунд не знает ни про счёт, ни про
 * цель, и это позволяет гонять его тестами в одиночку.
 */
class KozelMatch(
    val rules: KozelRules = KozelRules.BOOK,
    private val random: Random = Random.Default,
    /** Сколько мест за столом: столько же, сколько в следующем раунде. */
    val seats: Int = DEFAULT_SEATS,
) {

    init {
        require(seats in MIN_SEATS..MAX_SEATS) { "за столом от $MIN_SEATS до $MAX_SEATS мест" }
    }

    /** Текущий раунд. Доигранный заменяется новым. */
    var round: KozelRound = KozelRound.deal(rules, random, seats)
        private set

    /** Сколько раундов сыграно. Нумерация с единицы. */
    var roundNumber: Int = 1
        private set

    private val scores: MutableList<Int> = MutableList(seats) { 0 }

    /** Сколько набрано каждым. */
    val table: List<Int> get() = scores.toList()

    /**
     * Кто стал «козлом» — первым набрал до цели. Пусто — матч идёт.
     *
     * Победителя матча в единственном числе тут нет: за столом на троих до
     * цели не дошёл никто из двоих, и оба они выиграли одинаково. Кто именно
     * козёл — а он ровно один, — и есть итог матча.
     */
    var goat: Int? = null
        private set

    val over: Boolean get() = goat != null

    /**
     * Раунд доигран — записать его очки и сдать следующий.
     *
     * Последний раунд матча остаётся на столе: доигранный, он и есть его
     * итог, и новый раунд поверх него сдавать нечего.
     */
    fun finishRound(): KozelSummary {
        check(round.finished) { "раунд ещё идёт" }

        val score = round.score()
        // Очки за раунд записываются проигравшему его — тому, у кого остались
        // кости на руке. Раунд о матче ничего не знает, поэтому «проигравший
        // раунд» и «тот, кому записали» — здесь одно и то же место за столом.
        score.written.forEachIndexed { seat, points -> scores[seat] += points }

        goat = scores.indexOfFirst { it >= rules.target }.takeIf { it >= 0 }

        val summary = KozelSummary(
            winner = score.winner,
            written = score.written,
            fish = score.fish,
            scores = table,
            goat = goat,
        )

        if (goat == null) {
            roundNumber++
            round = KozelRound.deal(rules, random, seats)
        }
        return summary
    }

    companion object {

        /**
         * Матч с середины: так его поднимают с диска.
         *
         * Матч на паузе — это счёт, номер раунда и недоигранная раздача.
         * Доигранный матч не поднимают: он итог, а не пауза, поэтому
         * победитель здесь всегда пуст и разыгрывается он заново.
         */
        fun restore(
            rules: KozelRules,
            round: KozelRound,
            scores: List<Int>,
            roundNumber: Int,
            random: Random = Random.Default,
        ): KozelMatch {
            // Стол берём из раунда: счёт и раздача обязаны быть про одних и
            // тех же людей, и разойтись им нечем — число мест одно на двоих.
            require(scores.size == round.seats) { "счёт не про этот стол" }
            require(!round.finished) { "доигранный раунд поднимать нечего" }
            require(scores.none { it >= rules.target }) { "матч уже кончен" }
            require(roundNumber >= 1) { "номер раунда считается с единицы" }

            val match = KozelMatch(rules, random, round.seats)
            match.round = round
            match.roundNumber = roundNumber
            scores.forEachIndexed { seat, points -> match.scores[seat] = points }
            return match
        }
    }
}
