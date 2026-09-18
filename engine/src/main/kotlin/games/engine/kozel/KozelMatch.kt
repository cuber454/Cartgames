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
    /** Сколько записали проигравшему раунд. Ноль — не записали ничего. */
    val points: Int,
    /** Раунд кончился «рыбой», а не выходом. */
    val fish: Boolean,
    /** Счёт матча после раунда: сколько набрано каждым. */
    val scores: List<Int>,
    /** Кто выиграл матч. Пусто — матч идёт. */
    val matchWinner: Int?,
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
) {

    /** Текущий раунд. Доигранный заменяется новым. */
    var round: KozelRound = KozelRound.deal(rules, random)
        private set

    /** Сколько раундов сыграно. Нумерация с единицы. */
    var roundNumber: Int = 1
        private set

    private val scores: MutableList<Int> = MutableList(SEATS) { 0 }

    /** Сколько набрано каждым. */
    val table: List<Int> get() = scores.toList()

    /** Кто выиграл матч — то есть до цели дошёл не он. Пусто — матч идёт. */
    var matchWinner: Int? = null
        private set

    /** Кто стал «козлом» — первым набрал до цели. Пусто — матч идёт. */
    val goat: Int? get() = matchWinner?.let { KozelRound.other(it) }

    val over: Boolean get() = matchWinner != null

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
        score.winner?.let { winner -> scores[KozelRound.other(winner)] += score.points }

        val goatNow = scores.indexOfFirst { it >= rules.target }.takeIf { it >= 0 }
        matchWinner = goatNow?.let { KozelRound.other(it) }

        val summary = KozelSummary(
            winner = score.winner,
            points = score.points,
            fish = score.fish,
            scores = table,
            matchWinner = matchWinner,
        )

        if (matchWinner == null) {
            roundNumber++
            round = KozelRound.deal(rules, random)
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
            require(scores.size == SEATS) { "за столом $SEATS места" }
            require(!round.finished) { "доигранный раунд поднимать нечего" }
            require(scores.none { it >= rules.target }) { "матч уже кончен" }
            require(roundNumber >= 1) { "номер раунда считается с единицы" }

            val match = KozelMatch(rules, random)
            match.round = round
            match.roundNumber = roundNumber
            scores.forEachIndexed { seat, points -> match.scores[seat] = points }
            return match
        }
    }
}
