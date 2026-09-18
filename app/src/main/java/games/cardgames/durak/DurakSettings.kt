package games.cardgames.durak

import android.content.Context
import games.cardgames.settings.Activation
import games.cardgames.settings.GameSetting
import games.cardgames.settings.GameSettingStore
import games.cardgames.settings.LEGACY_KEY_TRANSFER
import games.engine.durak.DurakRules

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

/**
 * Кто ходит первым в новой партии.
 *
 * Книга даёт на выбор два обычая: ходят либо из-под дурака, либо сам дурак.
 * Здесь оставлен второй — вдвоём «из-под дурака» значит «ходит победитель»,
 * то есть тот же выбор с другого конца, и двумя выключателями его не
 * выразить, не сделав один из них перевёртышем другого.
 */
val LOSER_LEADS = GameSetting(
    key = "durak.loser_leads",
    title = "Дурак ходит первым",
    about = "Новую партию начинает проигравший прошлую. Выключено — по книге: " +
        "первым ходит тот, у кого младший козырь.",
    activation = Activation.NEW_MATCH,
    default = false,
)

val DURAK_SETTINGS: List<GameSetting> = listOf(TRANSFER, LOSER_LEADS)

/**
 * Договорённости, с которыми начинают партию.
 *
 * Читаются в момент раздачи, а не на каждом ходу: партия помнит, по каким
 * правилам её начали, и переключение посреди партии ход не меняет.
 *
 * У перевода прежний ключ читается как ответ по умолчанию: настройка
 * переехала сюда из общих, и кто выключил перевод до обновления, продолжит
 * играть без него, а не обнаружит его снова включённым.
 */
fun loadDurakRules(context: Context): DurakRules {
    val store = GameSettingStore(context)
    return DurakRules(
        transfer = store.value(TRANSFER, legacyKey = LEGACY_KEY_TRANSFER),
        loserLeads = store.value(LOSER_LEADS),
    )
}
