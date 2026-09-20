package games.engine.hundred

import games.engine.Card
import games.engine.Rank
import games.engine.Suit
import games.engine.fullDeck36
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Запись матча «101» на диск и обратно. Суть матча — счёт и выбывшие: без них
 * игрок после перезапуска вернулся бы в новый матч с чистого счёта, а выбывший
 * сел бы за стол заново. Поэтому проверяется не только «читается обратно»,
 * но и то, что запись со счётом читается именно тем счётом.
 */
class HundredSaveTest {

    /** Матч, каким он уходит на диск в первый раз. */
    @Test
    fun `начатый матч переживает запись`() {
        val game = Hundred.start(random = Random(7), playerCount = 3)

        val restored = HundredSave.read(HundredSave.write(game))

        assertNotNull(restored)
        assertEquals(game.playerCount, restored.playerCount)
        assertEquals(game.dealer(), restored.dealer())
        assertEquals(game.turn, restored.turn)
        assertEquals(game.pileCards(), restored.pileCards())
        assertEquals(game.stockCards(), restored.stockCards())
        assertEquals(game.scoresAll(), restored.scoresAll())
        assertEquals(game.outSeats(), restored.outSeats())
        assertEquals(game.stockTurnovers(), restored.stockTurnovers())
        for (seat in 0 until game.playerCount) {
            assertEquals(game.handOf(seat), restored.handOf(seat))
        }
    }

    /** Счёт и выбывшие — часть матча, а не настройка экрана. */
    @Test
    fun `счёт и выбывшие переживают запись`() {
        val seven = Card(Rank.SEVEN, Suit.SPADES)
        val nine = Card(Rank.NINE, Suit.CLUBS)
        val top = Card(Rank.SEVEN, Suit.DIAMONDS)
        val game = Hundred.forTesting(
            playerCount = 3,
            hands = listOf(listOf(seven), emptyList(), listOf(nine)),
            pile = listOf(top),
            stock = restOf(listOf(seven, nine, top)),
            turn = 0,
            scores = listOf(-60, 0, 44),
            out = listOf(false, true, false),
        )

        val restored = HundredSave.read(HundredSave.write(game))

        assertNotNull(restored)
        assertEquals(listOf(-60, 0, 44), restored.scoresAll())
        assertEquals(44, restored.scoreOf(2))
        assertEquals(listOf(1), restored.outSeats())
        assertEquals(listOf(seven), restored.handOf(0))
    }

    /** Сколько раз переворачивали стопку — тоже часть матча: по нему говорят. */
    @Test
    fun `переворот стопки переживает запись`() {
        val seven = Card(Rank.SEVEN, Suit.SPADES)
        val ace = Card(Rank.ACE, Suit.CLUBS)
        val top = Card(Rank.SEVEN, Suit.DIAMONDS)
        val game = Hundred.forTesting(
            playerCount = 2,
            hands = listOf(listOf(seven), listOf(ace)),
            pile = listOf(top),
            stock = restOf(listOf(seven, ace, top)),
            turn = 0,
            turnovers = 2,
        )

        val restored = HundredSave.read(HundredSave.write(game))

        assertNotNull(restored)
        assertEquals(2, restored.stockTurnovers())
    }

    /** Две карты одного достоинства и масти в записи — это не колода. */
    @Test
    fun `задвоенная карта отвергает запись`() {
        val game = Hundred.start(random = Random(9), playerCount = 2)
        val top = game.pileCards().single()
        val doubled = HundredSave.write(game)
            .replace("hand0 ", "hand0 ${codeOf(top)} ")

        assertNull(HundredSave.read(doubled))
    }

    /** Потерянная карта — тоже не колода. */
    @Test
    fun `недостача карты отвергает запись`() {
        val game = Hundred.start(random = Random(10), playerCount = 2)
        val text = HundredSave.write(game)
            .lines()
            .filterNot { it.startsWith("stock ") }
            .joinToString("\n")

        assertNull(HundredSave.read(text))
    }

    /** Чужая запись — не наша: заголовок у неё другой. */
    @Test
    fun `чужой заголовок отвергает запись`() {
        val game = Hundred.start(random = Random(11), playerCount = 2)
        val text = HundredSave.write(game).replaceFirst("game hundred", "game kozel")

        assertNull(HundredSave.read(text))
    }

    /** Смена формата — единственное, что нарочно ломает запись. */
    @Test
    fun `другой формат отвергает запись`() {
        val game = Hundred.start(random = Random(12), playerCount = 2)
        val text = HundredSave.write(game).replaceFirst("format 1", "format 2")

        assertNull(HundredSave.read(text))
    }

    /**
     * Запись, сделанная до появления новой строки, читается: чужие ключи
     * пропускаются, отсутствующее берётся умолчанием. Иначе игрок после
     * обновления нашёл бы матч с чужой стопкой.
     */
    @Test
    fun `старая запись без turnovers читается`() {
        val game = Hundred.start(random = Random(13), playerCount = 2)
        val text = HundredSave.write(game)
            .lines()
            .filterNot { it.startsWith("turnovers ") }
            .joinToString("\n")

        val restored = HundredSave.read(text)

        assertNotNull(restored)
        assertEquals(0, restored.stockTurnovers())
    }

    /** Ход выбывшему не отдают: такого стола не бывает. */
    @Test
    fun `ход выбывшему отвергает запись`() {
        // Сдатчик назначен, значит ход известен: он у соседа — место 1.
        val game = Hundred.start(random = Random(14), playerCount = 2, firstDealer = 0)
        val text = HundredSave.write(game).replaceFirst("out ", "out 1 ")

        assertNull(HundredSave.read(text))
    }

    /** Пустой кон — не кон: по верхней карте ходят, и без неё играть нечем. */
    @Test
    fun `пустой кон отвергает запись`() {
        val game = Hundred.start(random = Random(15), playerCount = 2)
        val text = HundredSave.write(game).replaceFirst("pile ", "pile")

        assertNull(HundredSave.read(text))
    }

    /** Всё, что не попало в руки и на кон, лежит в колоде. */
    private fun restOf(used: List<Card>): List<Card> = fullDeck36().filterNot { it in used }

    private fun codeOf(card: Card): String = "${card.rank.value}${letterOf(card.suit)}"

    private fun letterOf(suit: Suit): String = when (suit) {
        Suit.SPADES -> "S"
        Suit.HEARTS -> "H"
        Suit.DIAMONDS -> "D"
        Suit.CLUBS -> "C"
    }
}
