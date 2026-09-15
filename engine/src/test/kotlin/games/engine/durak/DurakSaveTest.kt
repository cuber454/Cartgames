package games.engine.durak

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Сохранение партии: запись, чтение и отказ от битой записи.
 *
 * Главное здесь — не «текст туда-обратно сходится», а что за столом после
 * восстановления можно играть дальше: те же карты, тот же ход, те же
 * допустимые ходы. Поэтому партия в тестах не пустая, а отыгранная.
 */
class DurakSaveTest {

    @Test
    fun `новая партия переживает запись и чтение`() {
        val game = DurakGame.start(Random(1))
        val restored = assertNotNull(DurakSave.read(DurakSave.write(game)))
        assertSamePosition(game, restored)
    }

    @Test
    fun `отыгранная партия переживает запись и чтение со столом и отбоем`() {
        var game = DurakGame.start(Random(7))
        val rng = Random(7)
        // Ходов с запасом, чтобы случились и «беру», и «бито»: тогда в
        // записи появляются и непустой стол, и отбой.
        var guard = 0
        while (!game.finished && game.playedCards().size < 12 && guard++ < 200) {
            val seat = if (game.legalMoves(0).isNotEmpty()) 0 else 1
            val move = BotPlayer.chooseMove(game, seat, Difficulty.CLEVER, rng) ?: break
            game.apply(seat, move)
        }

        val restored = assertNotNull(DurakSave.read(DurakSave.write(game)))
        assertSamePosition(game, restored)
    }

    @Test
    fun `восстановленная партия доигрывается до конца`() {
        for (seed in 1..5) {
            val rng = Random(seed)
            var game = DurakGame.start(Random(seed))
            var warmup = 0
            while (!game.finished && warmup++ < 20) {
                val seat = if (game.legalMoves(0).isNotEmpty()) 0 else 1
                val move = BotPlayer.chooseMove(game, seat, Difficulty.NORMAL, rng) ?: break
                game.apply(seat, move)
            }
            game = assertNotNull(DurakSave.read(DurakSave.write(game)))

            var guard = 0
            while (!game.finished && guard++ < 2000) {
                val seat = if (game.legalMoves(0).isNotEmpty()) 0 else 1
                val move = BotPlayer.chooseMove(game, seat, Difficulty.NORMAL, rng) ?: break
                game.apply(seat, move)
            }
            assertEquals(true, game.finished, "партия из сохранения не доигралась, seed=$seed")
        }
    }

    @Test
    fun `законченная партия восстанавливается как законченная`() {
        val game = DurakGame.start(Random(3))
        val rng = Random(3)
        var guard = 0
        while (!game.finished && guard++ < 2000) {
            val seat = if (game.legalMoves(0).isNotEmpty()) 0 else 1
            val move = BotPlayer.chooseMove(game, seat, Difficulty.NORMAL, rng) ?: break
            game.apply(seat, move)
        }
        assertEquals(true, game.finished)

        val restored = assertNotNull(DurakSave.read(DurakSave.write(game)))
        assertEquals(game.finished, restored.finished)
        assertEquals(game.winner, restored.winner)
        assertEquals(game.loser, restored.loser)
    }

    @Test
    fun `мусор вместо записи отвергается`() {
        assertNull(DurakSave.read(""))
        assertNull(DurakSave.read("привет"))
        assertNull(DurakSave.read("game durak\nэто не поля\n"))
    }

    @Test
    fun `чужая версия формата отвергается`() {
        val text = DurakSave.write(DurakGame.start(Random(1)))
            .replace("format ${DurakSave.FORMAT}", "format 99")
        assertNull(DurakSave.read(text))
    }

    @Test
    fun `потерянная или задвоенная карта отвергается`() {
        val lines = DurakSave.write(DurakGame.start(Random(5))).lines().toMutableList()
        val deckIndex = lines.indexOfFirst { it.startsWith("deck ") }
        val handIndex = lines.indexOfFirst { it.startsWith("hand0 ") }
        val deck = lines[deckIndex].removePrefix("deck ").trim().split(' ').filter { it.isNotBlank() }
        val hand = lines[handIndex].removePrefix("hand0 ").trim().split(' ').filter { it.isNotBlank() }

        // Первую карту колоды подменяем копией карты из руки: карт
        // по-прежнему 36, но одна задвоилась, а другая потерялась.
        lines[deckIndex] = "deck " + (listOf(hand.first()) + deck.drop(1)).joinToString(" ")

        assertNull(DurakSave.read(lines.joinToString("\n")))
    }

    @Test
    fun `незнакомое поле не ломает запись`() {
        val game = DurakGame.start(Random(1))
        val text = DurakSave.write(game) + "weather sunny\n"
        assertNotNull(DurakSave.read(text))
    }

    /** Сверяет положение за столом целиком, а не только отдельные поля. */
    private fun assertSamePosition(expected: DurakGame, actual: DurakGame) {
        assertEquals(expected.trumpSuit, actual.trumpSuit)
        assertEquals(expected.attacker, actual.attacker)
        assertEquals(expected.deckCards(), actual.deckCards())
        assertEquals(expected.discardedCards(), actual.discardedCards())
        assertEquals(expected.table, actual.table)
        for (seat in 0 until expected.playerCount) {
            assertEquals(expected.handOf(seat), actual.handOf(seat), "рука игрока $seat")
            assertEquals(
                expected.legalMoves(seat).toSet(),
                actual.legalMoves(seat).toSet(),
                "допустимые ходы игрока $seat",
            )
        }
    }
}
