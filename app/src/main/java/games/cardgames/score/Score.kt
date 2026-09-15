package games.cardgames.score

import android.content.Context

/** Чем кончилась партия для игрока. */
enum class Outcome { WIN, LOSS, DRAW }

/**
 * Счёт партий.
 *
 * Партии идут одна за другой, и без счёта каждая начинается с нуля —
 * играть становится неинтересно. Серия побед подряд считается отдельно:
 * она и подстёгивает играть дальше.
 */
data class Score(
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
) {
    val games: Int get() = wins + losses + draws

    fun plus(outcome: Outcome): Score = when (outcome) {
        Outcome.WIN -> {
            val next = streak + 1
            copy(wins = wins + 1, streak = next, bestStreak = maxOf(bestStreak, next))
        }

        Outcome.LOSS -> copy(losses = losses + 1, streak = 0)
        Outcome.DRAW -> copy(draws = draws + 1)
    }

    /** Счёт вслух — одна короткая фраза, её же читает скринридер. */
    fun spoken(): String {
        if (games == 0) return "Это первая партия."
        val parts = mutableListOf("Побед $wins", "поражений $losses")
        if (draws > 0) parts += "ничьих $draws"
        val tail = if (streak >= 2) ". Побед подряд: $streak" else ""
        return parts.joinToString(", ") + tail + "."
    }
}

private const val PREFS = "score"
private const val KEY_WINS = "wins"
private const val KEY_LOSSES = "losses"
private const val KEY_DRAWS = "draws"
private const val KEY_STREAK = "streak"
private const val KEY_BEST = "best_streak"

fun loadScore(context: Context): Score {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return Score(
        wins = prefs.getInt(KEY_WINS, 0),
        losses = prefs.getInt(KEY_LOSSES, 0),
        draws = prefs.getInt(KEY_DRAWS, 0),
        streak = prefs.getInt(KEY_STREAK, 0),
        bestStreak = prefs.getInt(KEY_BEST, 0),
    )
}

fun saveScore(context: Context, score: Score) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putInt(KEY_WINS, score.wins)
        .putInt(KEY_LOSSES, score.losses)
        .putInt(KEY_DRAWS, score.draws)
        .putInt(KEY_STREAK, score.streak)
        .putInt(KEY_BEST, score.bestStreak)
        .apply()
}
