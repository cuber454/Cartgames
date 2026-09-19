package games.cardgames.thousand

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import games.cardgames.GAME_THOUSAND
import games.cardgames.durak.clearGame
import games.cardgames.durak.loadGameText
import games.cardgames.durak.saveGame
import games.cardgames.settings.loadSettings
import games.cardgames.settings.seatsAtTable
import games.engine.thousand.Phase
import games.engine.thousand.RoundSummary
import games.engine.thousand.ThousandMatch
import games.engine.thousand.ThousandRound
import games.engine.thousand.ThousandSave

/**
 * За каким столом садиться в новую партию — из настроек.
 *
 * Число мест выбирает игрок: вдвоём торг идёт один на один, втроём — как за
 * обычным столом, и прикуп берёт один из трёх. Движок держит оба стола
 * (прикуп 2+2 на двоих и 1+3 на троих), поэтому здесь только выбор.
 *
 * В самой партии число мест уже записано, и поднятая с диска партия
 * продолжается за своим столом, а не за тем, что стоит в настройках сейчас:
 * иначе смена настройки посреди партии пересдала бы её на другое число
 * игроков (ThousandSave, строка «players»).
 */
private fun seatsForNewMatch(context: Context): Int = seatsAtTable(loadSettings(context))

/**
 * Партия в «Тысячу», переживающая уход с экрана и перезапуск приложения.
 *
 * Устроена как [games.cardgames.durak.DurakSession], но состояний тут два
 * этажа, а не один: матч до тысячи и кон, который в нём идёт. Игрок вышел
 * посреди кона — он вернётся в тот же кон, а не в начало матча; вышел между
 * конами — вернётся к счёту и новой раздаче.
 *
 * Доигранный кон — отдельная забота. Он уже кончился, но в счёт матча его
 * ещё надо занести ([record]), и это заметное действие: счёт меняется,
 * кто-то садится на бочку, кто-то ловит третий болт. Поэтому между «кон
 * доигран» и «занесён» партия может стоять, и этот шаг записан на диск
 * ([recorded]) — иначе после перезапуска кон засчитался бы второй раз.
 */
class ThousandSession(context: Context) {

    private val appContext = context.applicationContext

    // Договорённости берутся здесь, на новую партию, и дальше живут в самом
    // матче: переключение настройки посреди партии её не меняет.
    var match by mutableStateOf(
        ThousandMatch(seatsForNewMatch(appContext), rules = loadThousandRules(appContext)),
    )
        private set

    var round by mutableStateOf(match.startRound())
        private set

    /** Итог доигранного кона уже в счёте матча. */
    var recorded by mutableStateOf(false)

    /** Что случилось за последний доигранный кон — для экрана. */
    var summary by mutableStateOf<RoundSummary?>(null)

    /** Счётчик ходов: по нему экран понимает, что пора играть за бота. */
    var tick by mutableIntStateOf(0)

    var lastPhrase by mutableStateOf("")

    var restored by mutableStateOf(false)
        private set

    init {
        val text = loadGameText(appContext, GAME_THOUSAND)
        val saved = text?.let { ThousandSave.read(it) }
        if (saved != null && saved.match.winner == null) {
            match = saved.match
            round = saved.round
            recorded = saved.recorded
            // Кон доигран и уже засчитан — значит о нём игроку сказали до
            // того, как приложение закрылось. Начинаем следующий, а не
            // показываем доигранный стол.
            if (recorded && round.phase == Phase.OVER) {
                round = match.startRound()
                recorded = false
            }
            restored = true
        } else if (text != null) {
            // Выигранный или битый матч — не партия, держать его незачем.
            clearGame(appContext, GAME_THOUSAND)
        }
    }

    /** Записать партию на диск. Зовётся после каждого хода. */
    fun persist() {
        if (!loadSettings(appContext).autosave) return
        saveGame(appContext, GAME_THOUSAND, ThousandSave.write(match, round, recorded))
    }

    /** Партия доиграна — хранить её больше нечего. */
    fun forgetSaved() {
        clearGame(appContext, GAME_THOUSAND)
    }

    /**
     * Занести доигранный кон в счёт матча. Зовётся один раз на кон: второй
     * вызов вернёт тот же итог, а не засчитает кон заново.
     */
    fun record(): RoundSummary {
        if (!recorded) {
            summary = match.finishRound(round)
            recorded = true
            persist()
        }
        return checkNotNull(summary) { "кон ещё не доигран" }
    }

    /** Раздать следующий кон. */
    fun nextRound() {
        round = match.startRound()
        recorded = false
        summary = null
        lastPhrase = ""
        persist()
        tick++
    }

    /**
     * Новый матч. Первую фразу не задаём: экран сам решит, объявить раздачу
     * или сказать «продолжаем», — здесь для этого нет ни голоса, ни настроек.
     */
    fun restart() {
        match = ThousandMatch(seatsForNewMatch(appContext), rules = loadThousandRules(appContext))
        round = match.startRound()
        recorded = false
        summary = null
        lastPhrase = ""
        restored = false
        forgetSaved()
        tick++
    }

    /** Экран игры подтвердил, что поднятую партию принял в работу. */
    fun acceptRestored() {
        restored = false
    }
}
