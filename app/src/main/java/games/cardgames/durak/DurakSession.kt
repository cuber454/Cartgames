package games.cardgames.durak

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import games.cardgames.GAME_DURAK
import games.cardgames.score.Outcome
import games.cardgames.score.Score
import games.cardgames.score.loadScore
import games.cardgames.settings.loadSettings
import games.engine.durak.DurakGame
import games.engine.durak.DurakRules
import games.engine.durak.DurakSave

/**
 * Партия, которая переживает уход с экрана и перезапуск приложения.
 *
 * Игроку нужно заглянуть в настройки прямо посреди партии — выключить
 * болтливого бота или надоевший звук — и вернуться за тот же стол. Если
 * держать партию внутри экрана, она бы обнулилась: Compose выбрасывает
 * состояние вместе с экраном. Поэтому партия живёт выше экранов.
 *
 * Оттуда же и сохранение на диск. Спрашивать «сохранить партию?» при
 * выходе бесполезно: при самом частом выходе — на рабочий стол или
 * свайпом из недавних — система убивает процесс, и спросить некого.
 * Поэтому пишем молча после каждого хода, а решение «продолжать или
 * начать новую» переносим в момент возвращения, где оно уместно.
 */
class DurakSession(context: Context) {

    private val appContext = context.applicationContext

    var game by mutableStateOf(DurakGame.start())
        private set

    /**
     * Счётчик ходов. Растёт после каждого хода — по нему экран понимает,
     * что пора играть за бота. Партию меняет только экран игры, поэтому
     * здесь обычные открытые поля: городить на каждое сеттер с методом
     * ради одного вызывающего — лишний этаж.
     */
    var tick by mutableIntStateOf(0)

    var lastPhrase by mutableStateOf("")

    var outcome by mutableStateOf<Outcome?>(null)

    var score by mutableStateOf(loadScore(appContext))

    /** Итог партии уже засчитан — второй раз не считаем. */
    var finishSaid = false

    /**
     * Партия поднята с диска. Экран игры по этому флагу понимает, что
     * раздачу объявлять не надо, — надо продолжать с того же места.
     */
    var restored by mutableStateOf(false)
        private set

    init {
        val text = loadGameText(appContext, GAME_DURAK)
        val saved = text?.let { DurakSave.read(it) }
        // Доигранная или битая запись — не партия, держать её незачем.
        if (saved != null && !saved.finished) {
            game = saved
            restored = true
        } else if (text != null) {
            clearGame(appContext, GAME_DURAK)
        }
    }

    /** Записать партию на диск. Зовётся после каждого хода. */
    fun persist() {
        if (!loadSettings(appContext).autosave) return
        saveGame(appContext, GAME_DURAK, DurakSave.write(game))
    }

    /** Партия доиграна — хранить её больше нечего. */
    fun forgetSaved() {
        clearGame(appContext, GAME_DURAK)
    }

    /**
     * Новая партия. Первую фразу не задаём: экран сам решит, объявить
     * раздачу или сказать «продолжаем» — здесь для этого нет ни голоса,
     * ни настроек.
     *
     * По договорённости «дурак ходит первым» первый ход отдаётся проигравшему
     * прошлую партию. Кто это, знает только доигранная партия — и знает ровно
     * до тех пор, пока мы её не выбросили, поэтому спрашиваем до, а не после.
     */
    fun restart(rules: DurakRules = DurakRules.BOOK) {
        val loserLeads = game.loser?.takeIf { rules.loserLeads }
        game = DurakGame.start(rules = rules, firstAttacker = loserLeads)
        lastPhrase = ""
        outcome = null
        finishSaid = false
        restored = false
        forgetSaved()
        tick++
    }

    /** Экран игры подтвердил, что поднятую партию принял в работу. */
    fun acceptRestored() {
        restored = false
    }
}
