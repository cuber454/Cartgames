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
    /** Кто записал очки за раунд. Пусто — ничейная «рыба». */
    val winner: Int?,
    /** Сколько записано. Ноль — не записано ничего. */
    val points: Int,
    /** Раунд кончился «рыбой», а не выходом. */
    val fish: Boolean,
    /** Счёт матча после раунда: сколько набрано каждым. */
    val scores: List<Int>,
    /** Кто выиграл матч, дойдя до [KozelRules.target]. Пусто — матч идёт. */
    val matchWinner: Int?,
)

/**
 * Матч в «Козла»: раунд за раундом, пока кто-нибудь не наберёт сто одно
 * очко. Набравший выиграл матч, а второй — «козёл».
 *
 * Очки за раунд записывает тот, кто его выиграл, — вышел первым или
 * закрыл линию «рыбой» с более лёгкой рукой. Поэтому счёт в «Козле» — это
 * счёт выигранных раздач, а не отыгранных: «козлом» становится тот, кому
 * этих раздач не досталось.
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

    /** Кто выиграл матч — дошёл до цели. Пусто — матч идёт. */
    var matchWinner: Int? = null
        private set

    /** Кто стал «козлом»: второй участник матча. Пусто — матч идёт. */
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
        score.winner?.let { winner -> scores[winner] += score.points }

        val winnerNow = scores.indexOfFirst { it >= rules.target }.takeIf { it >= 0 }
        matchWinner = winnerNow

        val summary = KozelSummary(
            winner = score.winner,
            points = score.points,
            fish = score.fish,
            scores = table,
            matchWinner = winnerNow,
        )

        if (winnerNow == null) {
            roundNumber++
            round = KozelRound.deal(rules, random)
        }
        return summary
    }
}
