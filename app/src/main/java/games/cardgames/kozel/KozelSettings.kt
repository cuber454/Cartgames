package games.cardgames.kozel

import android.content.Context
import games.cardgames.settings.Activation
import games.cardgames.settings.GameSetting
import games.cardgames.settings.GameSettingStore
import games.engine.kozel.KozelRules

/**
 * Договорённости сторон в «Козле»: то, о чём сговариваются до первой кости.
 *
 * Здесь только то, что меняет игру, а не приложение. До скольких очков идёт
 * матч, здесь нет намеренно: это решено раз и навсегда — до ста одного, и кто
 * набрал, тот козёл (Катерина, 18.09). Обычай играть до другой цифры в наших
 * компаниях не встречался, а выключатель, которым никто не пользуется, — это
 * лишняя остановка для пальца на пути к тому, что и вправду нужно.
 *
 * Порядок строк — тот, что здесь, и он не меняется от открытия к открытию.
 */
val EMPTY_DOUBLE = GameSetting(
    key = "kozel.empty_double",
    title = "Пусто-пусто стоит двадцать пять",
    about = "Оставшаяся на руке одна пусто-пусто считается не за ноль, а за " +
        "двадцать пять — так играют там, где её берегут до конца. Выключено — " +
        "кость стоит столько, сколько на ней точек.",
    activation = Activation.NEW_MATCH,
    default = false,
)

val KOZEL_SETTINGS: List<GameSetting> = listOf(EMPTY_DOUBLE)

/**
 * Договорённости, с которыми начинают матч.
 *
 * Читаются в момент раздачи, а не на каждом ходу: матч помнит, по каким
 * правилам его начали, и переключение посреди партии хода не меняет.
 */
fun loadKozelRules(context: Context): KozelRules {
    val store = GameSettingStore(context)
    val bonus = if (store.value(EMPTY_DOUBLE)) KozelRules.EMPTY_DOUBLE_AS_BONUS else 0
    return KozelRules(emptyDoubleBonus = bonus)
}
