package games.engine.thousand

import kotlin.random.Random

/** С какого счёта садятся на бочку. */
const val BARREL_AT = 880

/** Штраф за три болта — и за бочку, которую не одолел. */
const val BOLT_PENALTY = 120

/** Сколько болтов копить до штрафа. */
const val BOLTS_TO_PENALTY = 3

/** Сколько конов даётся на бочке, прежде чем с неё слетишь. */
const val BARREL_TRIES = 3

/**
 * Что случилось за кон — глазами матча, а не кона.
 *
 * Одними числами тут не обойтись: экрану надо не только «стало столько-то»,
 * но и что об этом сказать вслух — сел ли кто на бочку, слетел ли, получил
 * ли болт. Всё это видно только на уровне матча, где счёт и живёт.
 */
data class RoundSummary(
    /** Сколько записал каждый за этот кон. 0 — не записал ничего. */
    val deltas: List<Int>,
    /** Кто получил болт: не взял ни одной взятки. */
    val bolted: List<Int>,
    /** Кто слетел с бочки — не одолел её или уступил место. */
    val barrelsDropped: List<Int>,
    /** Кто сел на бочку этим коном. */
    val barrelSat: Int?,
    /** Кто выиграл матч этим коном. */
    val winner: Int?,
)

/**
 * Матч «Тысячи»: кон за коном до тысячи.
 *
 * Кон ([ThousandRound]) знает только про себя — раздачу, торг, взятки. Всё,
 * что тянется дальше одного кона, живёт здесь: счёт, болты, бочка и победа.
 * Разделение не формальное: кон можно разыграть и выбросить, а матч решает,
 * что из этого кона следует для партии.
 */
class ThousandMatch(
    val playerCount: Int = 2,
    val target: Int = 1000,
    firstBidder: Int = 0,
) {
    val scores = IntArray(playerCount)

    /** Болты: прочерки за коны без взяток. Три — и минус 120. */
    val bolts = IntArray(playerCount)

    /** Кто на бочке. Бочка одна на всех — второй пришедший сбивает первого. */
    var barrelSeat: Int? = null
        private set

    /** Сколько попыток бочка уже провалила. */
    var barrelTries: Int = 0
        private set

    var roundsPlayed: Int = 0
        private set

    var winner: Int? = null
        private set

    private var nextFirst = firstBidder.coerceIn(0, playerCount - 1)

    val isOver: Boolean get() = winner != null

    /** Раздать кон. Матч помнит, кто на бочке, — кон обязан это знать. */
    fun startRound(random: Random = Random.Default): ThousandRound =
        ThousandRound.start(random, playerCount, nextFirst, barrelSeat)

    /** Разыграть кон до конца: записать его в счёт и решить, что дальше. */
    fun finishRound(round: ThousandRound): RoundSummary {
        check(round.phase == Phase.OVER) { "кон ещё не доигран" }
        val declarer = checkNotNull(round.declarer) { "кон без заказчика" }
        val points = IntArray(playerCount) { round.roundPoints(it) }
        val tricks = IntArray(playerCount) { round.tricksOf(it) }
        return record(declarer, round.currentBid, points, tricks)
    }

    /**
     * Записать кон, описанный голыми числами.
     *
     * Отдельно от [finishRound], потому что разыгранный кон — неудобный
     * инструмент проверки: чтобы получить в нём нужный расклад, его надо
     * подобрать, а правила счёта и бочки проверяются именно здесь.
     */
    internal fun record(declarer: Int, bid: Int, points: IntArray, tricks: IntArray): RoundSummary {
        check(winner == null) { "матч уже выигран" }
        roundsPlayed++

        val deltas = IntArray(playerCount)
        val bolted = mutableListOf<Int>()
        val dropped = mutableListOf<Int>()
        val barrel = barrelSeat
        val madeIt = points[declarer] >= bid
        var sat: Int? = null

        // Бочка очков не пишет, пока не одолеет свой заказ. Одолела — матч
        // её. Не одолела — попытка сгорела, а три попытки стоят 120.
        //
        // Попытка — это свой кон: бочка взяла игру и не добрала. Кон, который
        // играл кто-то другой, попытки не тратит: бочка в нём не участвовала
        // и провалить его не могла, а три чужих кона не должны сбрасывать её
        // с бочки (THOUSAND.md, 2.8).
        if (barrel != null) {
            if (barrel == declarer && madeIt) {
                deltas[barrel] = target - scores[barrel]
                scores[barrel] = target
                winner = barrel
            } else if (barrel == declarer) {
                barrelTries++
                if (barrelTries >= BARREL_TRIES) {
                    scores[barrel] -= BOLT_PENALTY
                    deltas[barrel] = -BOLT_PENALTY
                    dropped += barrel
                    barrelSeat = null
                    barrelTries = 0
                }
            }
        }

        // Остальным — по правилам кона: заказчик пишет ровно заказ или минус
        // заказ, защитники — своё, округлённое до пяти. Бочку из этого списка
        // выбрасываем: у неё свой счёт, см. выше.
        if (winner == null) {
            for (seat in 0 until playerCount) {
                if (seat == barrel) continue
                val delta = if (seat == declarer) {
                    if (madeIt) bid else -bid
                } else {
                    (points[seat] + 2) / 5 * 5
                }
                deltas[seat] = delta
                scores[seat] += delta
            }
        }

        // Болт: кон без единой взятки — и севшему заказчику тоже. Минус заказ
        // и болт — разные наказания: первое за недобор, второе за пустой кон,
        // и книга, по которой сверяли правила, исключения для заказчика не
        // делает (THOUSAND.md, 2.7).
        for (seat in 0 until playerCount) {
            if (winner != null) break
            if (tricks[seat] > 0) continue
            bolts[seat]++
            bolted += seat
            if (bolts[seat] >= BOLTS_TO_PENALTY) {
                scores[seat] -= BOLT_PENALTY
                deltas[seat] -= BOLT_PENALTY
                bolts[seat] = 0
            }
        }

        // Победа: перевалил за тысячу — выиграл.
        if (winner == null) {
            winner = (0 until playerCount).firstOrNull { scores[it] >= target }
        }

        // Бочка: дошёл до 880 — садишься, счёт на ней замирает. Бочка одна:
        // пришёл второй — первый слетает с минусом 120, как за три болта.
        if (winner == null) {
            val candidate = (0 until playerCount).firstOrNull { seat ->
                seat != barrelSeat && scores[seat] >= BARREL_AT && scores[seat] < target
            }
            if (candidate != null) {
                val previous = barrelSeat
                if (previous != null) {
                    scores[previous] -= BOLT_PENALTY
                    deltas[previous] -= BOLT_PENALTY
                    dropped += previous
                }
                deltas[candidate] += BARREL_AT - scores[candidate]
                scores[candidate] = BARREL_AT
                barrelSeat = candidate
                barrelTries = 0
                sat = candidate
            }
        }

        nextFirst = (nextFirst + 1) % playerCount

        return RoundSummary(
            deltas = deltas.toList(),
            bolted = bolted,
            barrelsDropped = dropped,
            barrelSat = sat,
            winner = winner,
        )
    }

    /** Кто сдаёт следующий кон. Нужно, чтобы поднятая партия шла своим чередом. */
    fun nextBidder(): Int = nextFirst

    companion object {
        /** Собрать матч из того, что лежало на диске. */
        fun restore(
            playerCount: Int,
            scores: List<Int>,
            bolts: List<Int>,
            barrelSeat: Int?,
            barrelTries: Int,
            roundsPlayed: Int,
            nextFirst: Int,
            winner: Int?,
        ): ThousandMatch = ThousandMatch(playerCount, 1000, nextFirst).also { match ->
            scores.forEachIndexed { seat, value -> match.scores[seat] = value }
            bolts.forEachIndexed { seat, value -> match.bolts[seat] = value }
            match.barrelSeat = barrelSeat
            match.barrelTries = barrelTries
            match.roundsPlayed = roundsPlayed
            match.nextFirst = nextFirst.coerceIn(0, playerCount - 1)
            match.winner = winner
        }

        /** Матч с готовым счётом — для тестов и разбора концовок. */
        fun forTesting(
            scores: List<Int>,
            bolts: List<Int> = List(scores.size) { 0 },
            barrelSeat: Int? = null,
            barrelTries: Int = 0,
            firstBidder: Int = 0,
        ): ThousandMatch = ThousandMatch(scores.size, 1000, firstBidder).also { match ->
            scores.forEachIndexed { seat, value -> match.scores[seat] = value }
            bolts.forEachIndexed { seat, value -> match.bolts[seat] = value }
            match.barrelSeat = barrelSeat
            match.barrelTries = barrelTries
        }
    }
}
