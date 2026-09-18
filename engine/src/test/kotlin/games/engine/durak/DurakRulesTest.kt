package games.engine.durak

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Договорённости сторон: кто ходит первым в новой партии.
 *
 * Спорно ровно настолько, чтобы жить выключенной: по умолчанию партию
 * начинает тот, у кого младший козырь, и включённая договорённость обязана
 * быть видна и в партии, и в сохранении.
 */
class DurakRulesTest {

    /**
     * «Дурак ходит первым»: партия начинается не с чистого листа, и первый
     * ход приходит в раздачу числом. Кто дурак, знает прошлая партия, а не
     * эта, поэтому и правило не спрашивают у самой партии.
     */
    @Test
    fun `первым можно назначить дурака прошлой партии`() {
        assertEquals(1, DurakGame.start(random = Random(3), firstAttacker = 1).attacker)
        assertEquals(0, DurakGame.start(random = Random(3), firstAttacker = 0).attacker)
    }

    /** Несуществующий игрок первым не станет: раздача возвращается к книге. */
    @Test
    fun `чужое место в firstAttacker не ломает раздачу`() {
        val game = DurakGame.start(random = Random(3), firstAttacker = 5)

        assertTrue(game.attacker in 0 until game.playerCount)
    }

    /**
     * Договорённости — часть партии, а не настройки экрана: партия, начатая
     * подкидной и с ходом дурака, после перезапуска приложения остаётся
     * такой же.
     */
    @Test
    fun `договорённости переживают сохранение`() {
        val rules = DurakRules(transfer = false, loserLeads = true)
        val game = DurakGame.start(random = Random(11), rules = rules)

        assertEquals(rules, DurakSave.read(DurakSave.write(game))!!.rules)
    }

    /**
     * Запись, сделанная до появления новой строки, читается по книге: чужие
     * ключи пропускаются, а отсутствующее берётся умолчанием. Иначе игрок
     * после обновления нашёл бы партию с чужим первым ходом.
     */
    @Test
    fun `старая запись без новой строки читается по книге`() {
        val text = DurakSave.write(DurakGame.start(random = Random(11)))
            .lines()
            .filterNot { it.startsWith("loserleads ") }
            .joinToString("\n")

        assertEquals(DurakRules.BOOK, DurakSave.read(text)!!.rules)
    }
}
