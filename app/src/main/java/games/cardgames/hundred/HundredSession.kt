package games.cardgames.hundred

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import games.cardgames.GAME_HUNDRED
import games.cardgames.durak.clearGame
import games.cardgames.durak.loadGameText
import games.cardgames.durak.saveGame
import games.cardgames.settings.loadSettings
import games.cardgames.settings.seatsAtTable
import games.engine.hundred.Hundred
import games.engine.hundred.HundredSave
import kotlin.random.Random

/**
 * Место игрока за столом. Нулевое во всех играх приложения, и здесь оно
 * названо один раз, чтобы экран и сессия не разошлись в том, чей это матч.
 */
internal const val PLAYER_SEAT = 0

/**
 * Сколько мест за столом для нового матча — сколько их в настройках.
 *
 * Число мест матч помнит сам ([HundredSave] выводит его из числа рук), поэтому
 * поднятый с диска матч продолжается за своим столом, а не за тем, что стоит
 * в настройках сейчас: иначе смена настройки посреди матча пересдала бы его на
 * другое число мест.
 */
private fun seatsForNewMatch(context: Context): Int =
    seatsAtTable(loadSettings(context), GAME_HUNDRED)

/**
 * Матч в «101», который переживает уход с экрана и перезапуск приложения.
 *
 * Устроен как [games.cardgames.kozel.KozelSession] и по той же причине: игроку
 * нужно заглянуть в настройки прямо посреди партии, а партия, живущая внутри
 * экрана, обнулилась бы — Compose выбрасывает состояние вместе с экраном.
 * Оттуда же и запись на диск после каждого хода: при самом частом выходе —
 * свайпом из недавних — систему убивает процесс, и спросить «сохранить
 * партию?» некого.
 *
 * Отличие от «Козла» в том, что помнит себя матч, а не сессия: у «101» один
 * объект [Hundred] — это весь матч целиком, вместе со счётом, выбывшими и
 * всеми конами. Отдельного класса матча здесь нет, и заводить его незачем:
 * кон кончается и следующая раздача начинается внутри самого движка, а наружу
 * это выходит только счётом и новыми руками.
 */
class HundredSession(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Матч. Число мест берётся из настроек, а не из матча: матч его помнит
     * сам, и поднятый с диска продолжается за своим столом, а смена настройки
     * пересдаёт только следующую раздачу.
     */
    var match by mutableStateOf(
        Hundred.start(random = Random.Default, playerCount = seatsForNewMatch(context)),
    )
        private set

    /**
     * Счётчик ходов. Растёт после каждого хода — по нему экран понимает, что
     * пора играть за соперников.
     */
    var tick by mutableIntStateOf(0)

    /** Последняя фраза за столом: её повторяет кнопка «Повтори». */
    var lastPhrase by mutableStateOf("")

    /** Матч поднят с диска: раздачу объявлять не надо, надо продолжать. */
    var restored by mutableStateOf(false)
        private set

    /**
     * Матч для игрока кончен — он выбыл или остался за столом один.
     *
     * Не то же, что [Hundred.finished]: движок играет матч до последнего
     * живого, и после того, как игрок выбыл, за столом ещё двое. Для игрока
     * матч кончается на его выбывании — объявить итог и предложить новую
     * партию, а не оставлять его слушать, как соперники доигрывают без него
     * (HUNDRED_ONE.md, 2.8).
     */
    val overForPlayer: Boolean get() = match.finished || match.isOut(PLAYER_SEAT)

    init {
        val text = loadGameText(appContext, GAME_HUNDRED)
        val saved = text?.let { HundredSave.read(it) }
        // Доигранная или битая запись — не партия, держать её незачем.
        if (saved != null) {
            match = saved
            restored = true
        } else if (text != null) {
            clearGame(appContext, GAME_HUNDRED)
        }
    }

    /**
     * Записать матч на диск. Зовётся после каждого хода.
     *
     * Матч, конченный для игрока, не пишется: поднимать его нечем. Выбыл —
     * значит за стол он уже не вернётся, а запись лежала бы до первого входа
     * в игру и спрашивала «продолжить партию?» про матч, в котором игрока
     * нет. Движок при этом доигрывает за оставшихся — но это его дело, а не
     * игрока (HUNDRED_ONE.md, 2.8).
     */
    fun persist() {
        if (overForPlayer) return
        if (!loadSettings(appContext).autosave) return
        saveGame(appContext, GAME_HUNDRED, HundredSave.write(match))
    }

    /** Матч доигран — хранить его больше нечего. */
    fun forgetSaved() {
        clearGame(appContext, GAME_HUNDRED)
    }

    /**
     * Новая партия. Первую фразу не задаём: экран сам решит, объявить раздачу
     * или сказать «продолжаем» — здесь для этого нет ни голоса, ни настроек.
     *
     * Число мест берём из настроек заново: смена «за столом трое» — это про
     * следующую раздачу, и следующая начинается здесь.
     */
    fun restart() {
        match = Hundred.start(
            random = Random.Default,
            playerCount = seatsForNewMatch(appContext),
        )
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
