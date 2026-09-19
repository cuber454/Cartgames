package games.cardgames.kozel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import games.cardgames.GAME_KOZEL
import games.cardgames.durak.clearGame
import games.cardgames.durak.loadGameText
import games.cardgames.durak.saveGame
import games.cardgames.settings.loadSettings
import games.cardgames.settings.seatsAtTable
import games.engine.kozel.KozelMatch
import games.engine.kozel.KozelRules
import games.engine.kozel.KozelSave

/**
 * Матч в «Козла», который переживает уход с экрана и перезапуск приложения.
 *
 * Устроен как [games.cardgames.durak.DurakSession] и по той же причине:
 * игроку нужно заглянуть в настройки прямо посреди партии, а партия, живущая
 * внутри экрана, обнулилась бы — Compose выбрасывает состояние вместе с
 * экраном. Оттуда же и запись на диск после каждого хода: при самом частом
 * выходе — свайпом из недавних — систему убивает процесс, и спросить
 * «сохранить партию?» некого.
 *
 * Отличие от «Дурака» одно: там партия — это одна раздача, а здесь матч из
 * раздач, и на диск ложится он целиком, вместе со счётом и номером раунда.
 * Доигранный матч не хранится: он итог, а не пауза.
 *
 * В общий счёт партий «Козёл» не пишется намеренно: там считаются партии, а
 * здесь матч из десятка раздач, и одна победа в «Козле» весила бы как десять
 * побед в «Дураке». Свой счёт у матча свой — до ста одного.
 */
/**
 * Сколько мест за столом для нового матча — сколько их в настройках.
 *
 * Число мест матч помнит сам (KozelSave выводит его из рук), поэтому
 * поднятый с диска матч продолжается за своим столом, а не за тем, что
 * стоит в настройках сейчас: иначе смена настройки посреди матча пересдала
 * бы его на другое число мест.
 */
private fun seatsForNewMatch(context: Context): Int =
    seatsAtTable(loadSettings(context), GAME_KOZEL)

class KozelSession(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Матч. Число мест берётся из настроек, а не из матча: матч его помнит
     * сам (KozelSave), и поднятый с диска продолжается за своим столом, а
     * смена настройки пересдаёт только следующую раздачу.
     */
    var match by mutableStateOf(
        KozelMatch(loadKozelRules(context), seats = seatsForNewMatch(context)),
    )
        private set

    /**
     * Счётчик ходов. Растёт после каждого хода — по нему экран понимает, что
     * пора играть за бота.
     */
    var tick by mutableIntStateOf(0)

    /** Последняя фраза за столом: её повторяет кнопка «Повтори». */
    var lastPhrase by mutableStateOf("")

    /** Матч поднят с диска: раздачу объявлять не надо, надо продолжать. */
    var restored by mutableStateOf(false)
        private set

    init {
        val text = loadGameText(appContext, GAME_KOZEL)
        val saved = text?.let { KozelSave.read(it) }
        // Доигранная или битая запись — не партия, держать её незачем.
        if (saved != null) {
            match = saved
            restored = true
        } else if (text != null) {
            clearGame(appContext, GAME_KOZEL)
        }
    }

    /**
     * Записать матч на диск. Зовётся после каждого хода.
     *
     * Доигранный раунд не пишется: поднять его нечем. О том, что кто-то вышел
     * или что линия закрыта «рыбой», помнит только сам раунд, а из записи это
     * не читается — на диске лежали бы руки без вышедшего и вечная раздача.
     * Раунд доигран — запись либо заменит следующий, либо уберётся совсем.
     */
    fun persist() {
        if (match.round.finished) return
        if (!loadSettings(appContext).autosave) return
        saveGame(appContext, GAME_KOZEL, KozelSave.write(match))
    }

    /** Матч доигран — хранить его больше нечего. */
    fun forgetSaved() {
        clearGame(appContext, GAME_KOZEL)
    }

    /**
     * Новая партия. Первую фразу не задаём: экран сам решит, объявить раздачу
     * или сказать «продолжаем» — здесь для этого нет ни голоса, ни настроек.
     *
     * Число мест берём из настроек заново: смена «за столом трое» — это про
     * следующую раздачу, и следующая начинается здесь.
     */
    fun restart(rules: KozelRules = KozelRules.BOOK) {
        match = KozelMatch(rules, seats = seatsForNewMatch(appContext))
        lastPhrase = ""
        restored = false
        forgetSaved()
        tick++
    }

    /** Экран игры подтвердил, что поднятый матч принял в работу. */
    fun acceptRestored() {
        restored = false
    }
}
