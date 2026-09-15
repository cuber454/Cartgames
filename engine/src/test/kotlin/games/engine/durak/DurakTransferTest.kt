package games.engine.durak

import games.engine.Card
import games.engine.Rank
import games.engine.Suit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Перевод: защищающийся кладёт карту того же достоинства, что уже на столе,
 * и атака уходит соседу. Правило добровольное и с оговорками, поэтому здесь
 * проверяются обе стороны — и когда перевести можно, и когда нельзя.
 */
class DurakTransferTest {

    private val trump = Suit.SPADES

    private fun c(rank: Rank, suit: Suit) = Card(rank, suit)

    /**
     * Колода нужна не для добора, а чтобы движок не считал партию
     * доигранной: с пустой колодой он проверяет выход после каждого хода.
     */
    private val deck = Rank.entries.map { c(it, Suit.DIAMONDS) }

    @Test
    fun `перевести можно картой того же достоинства`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.EIGHT, Suit.HEARTS), c(Rank.NINE, Suit.HEARTS)),
                listOf(c(Rank.SEVEN, Suit.CLUBS), c(Rank.NINE, Suit.CLUBS)),
            ),
            deck = deck,
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))

        assertTrue(game.legalMoves(1).contains(DurakMove.Transfer(c(Rank.SEVEN, Suit.CLUBS))))
        // Девятка треф — не перевод: достоинство другое.
        assertFalse(game.legalMoves(1).contains(DurakMove.Transfer(c(Rank.NINE, Suit.CLUBS))))
    }

    @Test
    fun `переводить нельзя, когда на столе есть отбитая карта`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.SEVEN, Suit.CLUBS), c(Rank.NINE, Suit.HEARTS)),
                listOf(c(Rank.EIGHT, Suit.HEARTS), c(Rank.EIGHT, Suit.CLUBS), c(Rank.NINE, Suit.CLUBS)),
            ),
            deck = deck,
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))
        game.apply(1, DurakMove.Defend(c(Rank.EIGHT, Suit.HEARTS), 0))
        // Подкидываем вторую семёрку: одна карта на столе уже отбита.
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.CLUBS)))

        val moves = game.legalMoves(1)
        assertTrue(moves.contains(DurakMove.Defend(c(Rank.EIGHT, Suit.CLUBS), 1)))
        assertFalse(moves.any { it is DurakMove.Transfer }, "перевод — только пока стол чист")
        assertTrue(moves.contains(DurakMove.Take))
    }

    @Test
    fun `перевод не проходит, если соседу нечем отбить весь стол`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS)),
                listOf(c(Rank.SEVEN, Suit.CLUBS)),
            ),
            deck = deck,
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))

        assertFalse(game.legalMoves(1).any { it is DurakMove.Transfer })
    }

    @Test
    fun `после перевода атакует тот, кто перевёл, а отбиваться соседу`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.SIX, Suit.HEARTS), c(Rank.SIX, Suit.CLUBS)),
                listOf(c(Rank.SEVEN, Suit.CLUBS), c(Rank.KING, Suit.HEARTS)),
            ),
            deck = deck,
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))
        game.apply(1, DurakMove.Transfer(c(Rank.SEVEN, Suit.CLUBS)))

        assertEquals(1, game.attacker)
        assertEquals(0, game.defender)
        assertEquals(2, game.table.size)
        assertTrue(game.table.none { it.beaten })
        // Шестёрками стол не отбить: одна младше семёрки, вторая не той масти.
        // Перевод тоже не пройдёт — у игрока 1 осталась одна карта.
        assertEquals(listOf<DurakMove>(DurakMove.Take), game.legalMoves(0))
    }

    @Test
    fun `перевод можно вернуть обратно, пока у соседа хватает карт`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.SEVEN, Suit.CLUBS), c(Rank.SIX, Suit.HEARTS)),
                listOf(c(Rank.SEVEN, Suit.SPADES), c(Rank.KING, Suit.HEARTS), c(Rank.QUEEN, Suit.HEARTS), c(Rank.JACK, Suit.HEARTS)),
            ),
            deck = deck,
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))
        game.apply(1, DurakMove.Transfer(c(Rank.SEVEN, Suit.SPADES)))
        assertTrue(game.legalMoves(0).contains(DurakMove.Transfer(c(Rank.SEVEN, Suit.CLUBS))))

        game.apply(0, DurakMove.Transfer(c(Rank.SEVEN, Suit.CLUBS)))

        assertEquals(0, game.attacker)
        assertEquals(1, game.defender)
        assertEquals(3, game.table.size)
        // Дальше переводить нечем: у игрока 0 осталась одна карта.
        assertFalse(game.legalMoves(1).any { it is DurakMove.Transfer })
    }

    @Test
    fun `перевод не добирает карты из колоды`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.EIGHT, Suit.HEARTS), c(Rank.NINE, Suit.HEARTS)),
                listOf(c(Rank.SEVEN, Suit.CLUBS), c(Rank.NINE, Suit.CLUBS)),
            ),
            deck = deck,
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))
        game.apply(1, DurakMove.Transfer(c(Rank.SEVEN, Suit.CLUBS)))

        // Добор бывает только после «беру» и «бито»: перевёл — играешь тем, что есть.
        assertEquals(1, game.handOf(1).size)
        assertEquals(2, game.handOf(0).size)
    }

    /**
     * Подкидной дурак: перевод выключен настройкой. Защищающийся отбивается
     * или берёт — третьего не дано, и «перевести» в списке ходов не появляется.
     */
    @Test
    fun `в подкидном дураке перевода нет`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.EIGHT, Suit.HEARTS), c(Rank.NINE, Suit.HEARTS)),
                listOf(c(Rank.SEVEN, Suit.CLUBS), c(Rank.NINE, Suit.CLUBS)),
            ),
            deck = deck,
            transferAllowed = false,
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))

        val moves = game.legalMoves(1)
        assertFalse(moves.any { it is DurakMove.Transfer }, "перевод выключен — хода такого нет")
        // Отбиться семёркой треф нельзя (она не старше и не той масти) —
        // значит остаётся только взять.
        assertEquals(listOf<DurakMove>(DurakMove.Take), moves)
        assertFalse(game.transferAllowed)
    }

    /**
     * Режим — свойство партии, а не экрана: он едет в сохранение и
     * возвращается оттуда. Иначе партия, начатая подкидной, после
     * перезапуска приложения снова стала бы переводной.
     */
    @Test
    fun `режим партии переживает сохранение`() {
        val game = DurakGame.start(random = kotlin.random.Random(7), transferAllowed = false)

        val back = DurakSave.read(DurakSave.write(game))!!

        assertFalse(back.transferAllowed)
        val byDefault = DurakSave.read(
            DurakSave.write(DurakGame.start(random = kotlin.random.Random(7))),
        )!!
        assertTrue(byDefault.transferAllowed, "без строки transfer в записи режим остаётся переводным")
    }

    @Test
    fun `после перевода забранный стол достаётся тому, кто отбивался`() {
        val game = DurakGame.forTesting(
            trumpSuit = trump,
            hands = listOf(
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.SIX, Suit.HEARTS), c(Rank.SIX, Suit.CLUBS)),
                listOf(c(Rank.SEVEN, Suit.CLUBS), c(Rank.KING, Suit.HEARTS)),
            ),
            deck = deck,
        )
        game.apply(0, DurakMove.Attack(c(Rank.SEVEN, Suit.HEARTS)))
        game.apply(1, DurakMove.Transfer(c(Rank.SEVEN, Suit.CLUBS)))
        game.apply(0, DurakMove.Take)

        assertTrue(
            game.handOf(0).containsAll(
                listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.SEVEN, Suit.CLUBS)),
            ),
        )
        assertTrue(game.table.isEmpty())
        // Забрал — значит пропустил свою атаку: ходит снова тот же игрок.
        assertEquals(1, game.attacker)
    }
}
