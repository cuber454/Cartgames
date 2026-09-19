package games.engine.kozel

import games.engine.Difficulty
import kotlin.random.Random

/**
 * Прогон бота против бота: сильный уровень обязан выигрывать у слабого.
 *
 * Это единственный способ проверить «ум» бота, не садясь за стол: иначе
 * остаётся верить на слово, что оценка из 4.3 что-то значит. Слабый уровень
 * здесь — та же честная игра, только с короткой памятью, поэтому разница в
 * счёте показывает именно силу счёта, а не поддавки.
 *
 * Уровни через матч меняются местами: место за столом не должно решать
 * исход серии, а первым ходит тот, у кого младший дубль, — это от места
 * тоже не зависит, но проверять честнее зеркалом.
 *
 * Матчи идут от одного зерна, поэтому серия воспроизводима: одна и та же
 * тысяча раздач — и по уровню, и по тасовкам.
 */
object KozelSimulator {

    /**
     * Итог серии. Индекс всюду — уровень, а не место за столом: 0 — тот, кого
     * передали первым, 1 — вторым.
     *
     * Считать по местам здесь нельзя. Уровни через матч меняются местами,
     * чтобы место не решало исход, — и в счёте по местам победа сильного за
     * первым местом складывается с победой слабого за вторым, то есть ровно
     * с тем, что мы и хотим увидеть. В такой арифметике любые два уровня
     * выходят равны (на этом я и погорела 18.09: прогон показал 50% там, где
     * разница была в двадцать).
     */
    data class Result(
        val matches: Int,
        /** Сколько матчей выиграл каждый уровень. */
        val wins: List<Int>,
        /**
         * Средний штраф, оставшийся на руке у уровня, — по раундам, которые
         * он проиграл выходом соперника (KOZEL.md, 4.6).
         */
        val averagePenalty: List<Double>,
        /** Сколько раундов кончилось «рыбой»: выходом не кончил никто. */
        val fish: Int,
        val rounds: Int,
    ) {

        /** Доля матчей, выигранных уровнем [level], от нуля до единицы. */
        fun winRate(level: Int): Double = wins[level].toDouble() / matches

        /** Итог серии в одну строку — так его читают прогон и отчёт. */
        fun spoken(): String = buildString {
            append("$matches матчей, $rounds раундов, «рыба» в $fish")
            append("; победы ${wins[0]}/${wins[1]}")
            append(" (${percent(winRate(0))} против ${percent(winRate(1))})")
            append("; средний штраф ${penalty(0)} против ${penalty(1)}")
        }

        private fun percent(rate: Double): String =
            "${Math.round(rate * 1000) / 10.0}%"

        private fun penalty(level: Int): String =
            "${Math.round(averagePenalty[level] * 10) / 10.0}"
    }

    /**
     * Серия матчей [first] против [second] — по [matches] штук.
     *
     * Зерно одно на серию: матчи идут с seed, seed+1, … — повтор даёт тот же
     * счёт до последнего очка.
     */
    fun series(
        first: Difficulty,
        second: Difficulty,
        matches: Int,
        seed: Int = 20260918,
    ): Result {
        require(matches > 0) { "серия пустой не бывает" }

        val wins = MutableList(DEFAULT_SEATS) { 0 }
        val penaltySum = MutableList(DEFAULT_SEATS) { 0.0 }
        val penaltyRounds = MutableList(DEFAULT_SEATS) { 0 }
        var fish = 0
        var rounds = 0

        repeat(matches) { index ->
            val random = Random(seed + index)
            val atSeats = if (index % 2 == 0) listOf(first, second) else listOf(second, first)
            // Место за столом → уровень, который за ним сидит. Всё, что
            // считается наружу, переводится сюда: место меняется, уровень нет.
            val levelAt = if (index % 2 == 0) listOf(0, 1) else listOf(1, 0)
            val match = KozelMatch(KozelRules.BOOK, random)

            while (!match.over) {
                playRound(match.round, atSeats, random)

                val score = match.round.score()
                rounds++
                if (score.fish) fish++
                // Штраф записывают проигравшим раунд — тем, у кого осталась
                // рука. Именно их и считаем: это и есть «цена» уровня. За
                // столом на двоих проигравший один, и это по-прежнему он.
                score.winner?.let { winner ->
                    score.written.indices.filter { it != winner }.forEach { seat ->
                        penaltySum[levelAt[seat]] += score.written[seat]
                        penaltyRounds[levelAt[seat]]++
                    }
                }
                match.finishRound()
            }
            // Матч выиграли все, кроме козла. За столом на двоих он один,
            // и выигравший тоже один — прежняя победа, посчитанная от козла.
            val goat = match.goat ?: error("матч кончился, а козла нет")
            match.table.indices.filter { it != goat }.forEach { wins[levelAt[it]]++ }
        }

        return Result(
            matches = matches,
            wins = wins,
            averagePenalty = penaltySum.zip(penaltyRounds) { sum, count ->
                if (count == 0) 0.0 else sum / count
            },
            fish = fish,
            rounds = rounds,
        )
    }
}
