package games.cardgames.settings

import android.content.Context

/**
 * Когда игровая настройка вступает в силу.
 *
 * Не украшение, а то, что игрок обязан услышать: «выключил» само по себе не
 * говорит, подействует ли это на текущую партию. Правила партии на ходу не
 * меняются — партия помнит, по каким её начали (SETTINGS.md, 4).
 */
enum class Activation(val title: String) {
    NOW("сразу"),
    NEW_DEAL("с новой раздачи"),
    NEW_MATCH("с новой партии"),
}

/**
 * Игровая настройка, описанная самой игрой, — строкой, а не экраном.
 *
 * Экран настроек на всё приложение один: игры разные, а экран один, и в
 * третью игру он не пишется заново. Поэтому игра даёт список строк, а
 * рисует их общий экран (SETTINGS.md, 2).
 *
 * [key] начинается с имени игры: в «Тысяче» не может всплыть переводной
 * дурак, даже если ключи лежат в одном файле.
 */
data class GameSetting(
    val key: String,
    /** То, что читает синтезатор: «Самосвал». */
    val title: String,
    /** Зачем это, одной фразой — голосом, а не мелким шрифтом. */
    val about: String,
    val activation: Activation,
    val default: Boolean,
)

/**
 * Игровые настройки в хранилище. Файл тот же, что у общих: ключи с именем
 * игры впереди и разводят их между собой (SETTINGS.md, 2).
 *
 * Значение читается и пишется по описанию, а не по имени ключа: строка
 * настройки — единственное место, где ключ вообще назван.
 */
class GameSettingStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun value(setting: GameSetting): Boolean = prefs.getBoolean(setting.key, setting.default)

    fun set(setting: GameSetting, value: Boolean) {
        prefs.edit().putBoolean(setting.key, value).apply()
    }
}

/**
 * Что сказать на переключение: и новое значение, и когда оно заработает.
 * Иначе игрок выключит договорённость и будет ждать, что партия тут же
 * переменится, — а она не переменится и не должна.
 */
fun gameSettingPhrase(setting: GameSetting, value: Boolean): String =
    "${setting.title}: ${if (value) "включено" else "выключено"}. " +
        when (setting.activation) {
            Activation.NOW -> "Действует сразу."
            Activation.NEW_DEAL -> "Действует со следующей раздачи."
            Activation.NEW_MATCH -> "Действует со следующей партии."
        }
