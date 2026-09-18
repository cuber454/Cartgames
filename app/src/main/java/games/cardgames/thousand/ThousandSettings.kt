package games.cardgames.thousand

import android.content.Context
import games.cardgames.settings.Activation
import games.cardgames.settings.GameSetting
import games.cardgames.settings.GameSettingStore
import games.engine.thousand.ThousandRules

/**
 * Договорённости сторон: то, о чём сговариваются до стола.
 *
 * В «Тысячу» играют по-разному в разных компаниях, и расхождения тут не
 * небрежность, а обычай. Поэтому спорное живёт выключенным, а не зашитым в
 * правила: по книге играет тот, кому так привычно, а своё включает тот, кто
 * это своё знает (SETTINGS.md, 5).
 *
 * Порядок строк — тот, что здесь, и он не меняется от открытия к открытию.
 */
val SAMOSVAL = GameSetting(
    key = "thousand.samosval",
    title = "Самосвал",
    about = "Набрав ровно 555, игрок сваливается в ноль.",
    activation = Activation.NEW_MATCH,
    default = false,
)

val RASPIS_PENALTY = GameSetting(
    key = "thousand.raspis_penalty",
    title = "Штраф за роспись",
    about = "Третья роспись за партию стоит 120, как третий болт.",
    activation = Activation.NEW_MATCH,
    default = false,
)

val ACE_MARRIAGE = GameSetting(
    key = "thousand.ace_marriage",
    title = "Тузовый марьяж",
    about = "Четыре туза на руке и хотя бы одна взятка дают 200 — в заказ или в запись.",
    activation = Activation.NEW_MATCH,
    default = false,
)

val THOUSAND_SETTINGS: List<GameSetting> = listOf(SAMOSVAL, RASPIS_PENALTY, ACE_MARRIAGE)

/**
 * Договорённости, с которыми начинают партию.
 *
 * Читаются в момент новой партии, а не на каждом ходу: партия помнит, по
 * каким договорённостям её начали, и до конца идёт по ним. Иначе выигранное
 * по одному счёту оказалось бы проигранным по другому.
 */
fun loadThousandRules(context: Context): ThousandRules {
    val store = GameSettingStore(context)
    return ThousandRules(
        samosval = store.value(SAMOSVAL),
        raspisPenalty = store.value(RASPIS_PENALTY),
        aceMarriage = store.value(ACE_MARRIAGE),
    )
}
