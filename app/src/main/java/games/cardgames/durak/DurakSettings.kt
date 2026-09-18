package games.cardgames.durak

import android.content.Context
import games.cardgames.settings.Activation
import games.cardgames.settings.GameSetting
import games.cardgames.settings.GameSettingStore
import games.cardgames.settings.LEGACY_KEY_TRANSFER

/**
 * Правила стола в «Дураке»: то, о чём сговариваются до раздачи.
 *
 * Здесь только то, что меняет игру, а не приложение: переводной дурак или
 * подкидной — уговор, а не оформление, и в «Тысяче» ему делать нечего.
 * Поэтому настройка живёт в игровом слое, рядом со своей игрой, и в общих
 * настройках не показывается вовсе (SETTINGS.md, 2).
 *
 * Порядок строк — тот, что здесь, и он не меняется от открытия к открытию.
 */
val TRANSFER = GameSetting(
    key = "durak.transfer",
    title = "Перевод карты",
    about = "Защищающийся кладёт карту того же достоинства и передаёт атаку " +
        "соседу. Выключено — подкидной дурак: защищающийся только отбивается " +
        "или берёт.",
    activation = Activation.NEW_DEAL,
    default = true,
)

val DURAK_SETTINGS: List<GameSetting> = listOf(TRANSFER)

/**
 * Разрешён ли перевод в этой раздаче.
 *
 * Читается в момент раздачи, а не на каждом ходу: партия помнит, по каким
 * правилам её начали, и переключение посреди партии ход не меняет.
 *
 * Настройка переехала сюда из общих, и прежний её ключ читается как ответ по
 * умолчанию: кто выключил перевод до обновления, продолжит играть без него,
 * а не обнаружит его снова включённым.
 */
fun durakTransferAllowed(context: Context): Boolean =
    GameSettingStore(context).value(TRANSFER, legacyKey = LEGACY_KEY_TRANSFER)
