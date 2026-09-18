package games.engine.thousand

import games.engine.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThousandSaveTest {

    /** Доиграть кон самыми простыми ходами: важно не «как», а «одинаково». */
    private fun autoPlay(round: ThousandRound) {
        var guard = 0
        while (!round.isFinished(round)) {
            check(guard++ < 1000) { "кон не кончается" }
            round.apply(round.turn, round.legalMoves(round.turn).first())
        }
    }

    private fun midRound(seed: Int): Pair<ThousandMatch, ThousandRound> {
        val random = Random(seed)
        val match = ThousandMatch(2)
        val round = match.startRound(random)
        repeat(5) { round.apply(round.turn, round.legalMoves(round.turn).first()) }
        return match to round
    }

    @Test
    fun `партия переживает запись и чтение`() {
        val (match, round) = midRound(seed = 7)
        val text = ThousandSave.write(match, round)
        val saved = ThousandSave.read(text)
        assertNotNull(saved) { "не прочиталось" }

        assertEquals(match.scores.toList(), saved.match.scores.toList())
        assertEquals(match.bolts.toList(), saved.match.bolts.toList())
        assertEquals(match.barrelSeat, saved.match.barrelSeat)
        assertEquals(match.roundsPlayed, saved.match.roundsPlayed)
        assertEquals(match.nextBidder(), saved.match.nextBidder())

        assertEquals(round.phase, saved.round.phase)
        assertEquals(round.turn, saved.round.turn)
        assertEquals(round.currentBid, saved.round.currentBid)
        assertEquals(round.declarer, saved.round.declarer)
        assertEquals(round.trumpSuit, saved.round.trumpSuit)
        assertEquals(round.passedSeats(), saved.round.passedSeats())
        assertEquals(round.namedSeats(), saved.round.namedSeats())
        assertEquals(round.tableSeats(), saved.round.tableSeats())
        assertEquals(round.handOf(0), saved.round.handOf(0))
        assertEquals(round.handOf(1), saved.round.handOf(1))
        assertEquals(round.trickPointsAll(), saved.round.trickPointsAll())
        assertEquals(round.tricksAll(), saved.round.tricksAll())
    }

    @Test
    fun `поднятая партия доигрывается так же, как исходная`() {
        val (match, round) = midRound(seed = 11)
        val saved = assertNotNull(ThousandSave.read(ThousandSave.write(match, round)))

        autoPlay(round)
        autoPlay(saved.round)

        val first = match.finishRound(round)
        val second = saved.match.finishRound(saved.round)

        assertEquals(first.deltas, second.deltas)
        assertEquals(match.scores.toList(), saved.match.scores.toList())
        assertEquals(first.winner, second.winner)
    }

    @Test
    fun `чужая запись не читается`() {
        assertNull(ThousandSave.read("game durak\nformat 1\n"))
        assertNull(ThousandSave.read(""))
    }

    @Test
    fun `битая запись не читается`() {
        val (match, round) = midRound(seed = 3)
        val text = ThousandSave.write(match, round)
        // Одна карта потерялась — партия собрана быть не может.
        val broken = text.lineSequence()
            .map { line -> if (line.startsWith("hand0 ")) "hand0" else line }
            .joinToString("\n")
        assertNull(ThousandSave.read(broken))
    }

    @Test
    fun `счёт и бочка переживают запись`() {
        val match = ThousandMatch.forTesting(scores = listOf(880, 760), bolts = listOf(0, 2), barrelSeat = 0, barrelTries = 2)
        val random = Random(5)
        val round = match.startRound(random)
        val saved = assertNotNull(ThousandSave.read(ThousandSave.write(match, round)))

        assertEquals(880, saved.match.scores[0])
        assertEquals(0, saved.match.barrelSeat)
        assertEquals(2, saved.match.barrelTries)
        assertEquals(2, saved.match.bolts[1])
        assertTrue(saved.round.isFinished(saved.round) == false)
    }

    @Test
    fun `тузовый марьяж переживает запись и в договорённости, и в признаке`() {
        // Тузы уходят во взятки по одному, и на руке их потом не найти:
        // признак пишется отдельной строкой, иначе поднятая посреди
        // розыгрыша партия потеряла бы марьяж.
        val round = aceRound()
        assertTrue(round.hadAllAces(0), "расклад для теста собран с четырьмя тузами у первого")

        val match = ThousandMatch.forTesting(
            scores = listOf(0, 0),
            rules = ThousandRules(aceMarriage = true),
        )
        val saved = assertNotNull(ThousandSave.read(ThousandSave.write(match, round)))

        assertTrue(saved.match.rules.aceMarriage)
        assertTrue(saved.round.hadAllAces(0))
        assertFalse(saved.round.hadAllAces(1))
    }

    /**
     * Раздача, в которой все четыре туза у первого, а снос уже сделан: тузы
     * на руке, розыгрыш начат. В колоде 24 карты, и все они должны быть на
     * столе — иначе запись не поднимется.
     */
    private fun aceRound(): ThousandRound {
        val deck = fullDeck24()
        val aces = deck.filter { it.rank == Rank.ACE }
        val rest = deck.filterNot { it.rank == Rank.ACE }
        val round = ThousandRound.forTesting(
            hands = listOf((aces + rest.take(6)).toList(), rest.drop(6).take(10).toList()),
            prikups = listOf(rest.drop(16).take(2).toList(), rest.drop(18).take(2).toList()),
        )
        round.apply(0, ThousandMove.Bid(100))
        round.apply(1, ThousandMove.Pass)
        round.apply(0, ThousandMove.TakePrikups(0))
        round.apply(0, ThousandMove.Discard(listOf(rest[16])))
        return round
    }

    @Test
    fun `договорённости сторон и счётчик росписей переживают запись`() {
        val rules = ThousandRules(samosval = true, raspisPenalty = true)
        val match = ThousandMatch.forTesting(
            scores = listOf(300, 400),
            rules = rules,
            raspises = listOf(2, 1),
        )
        val saved = assertNotNull(ThousandSave.read(ThousandSave.write(match, match.startRound(Random(9)))))

        assertEquals(rules, saved.match.rules)
        assertEquals(listOf(2, 1), saved.match.raspises.toList())
    }

    @Test
    fun `партия по книге пишется без договорённостей`() {
        val match = ThousandMatch.forTesting(scores = listOf(300, 400))
        val saved = assertNotNull(ThousandSave.read(ThousandSave.write(match, match.startRound(Random(9)))))

        assertTrue(saved.match.rules.byTheBook)
        assertEquals(listOf(0, 0), saved.match.raspises.toList())
    }

    @Test
    fun `запись прежней версии поднимается как партия по книге`() {
        // Запись версии 1 не знала ни договорённостей, ни счётчика росписей,
        // ни тузового марьяжа. Все недостающие строки означают ровно то же,
        // что умолчание, — поэтому брошенная партия после обновления не
        // пропадает.
        val missing = setOf(
            "raspises", "samosval", "raspisPenalty", "aceMarriage", "allAces",
        )
        val match = ThousandMatch.forTesting(scores = listOf(300, 400), bolts = listOf(1, 0))
        val old = ThousandSave.write(match, match.startRound(Random(4)))
            .lineSequence()
            .filterNot { it.substringBefore(' ') in missing }
            .joinToString("\n")
            .replace("format ${ThousandSave.FORMAT}", "format 1")

        val restored = ThousandSave.read(old)
        assertNotNull(restored, "запись версии 1 должна читаться")
        assertEquals(300, restored.match.scores[0])
        assertEquals(1, restored.match.bolts[0])
        assertTrue(restored.match.rules.byTheBook)
        assertEquals(listOf(0, 0), restored.match.raspises.toList())
    }

    /** Раздача целиком: десять на десять и два прикупа по две — все 24 карты. */
    private fun dealtRound(): ThousandRound {
        val deck = fullDeck24()
        return ThousandRound.forTesting(
            hands = listOf(deck.subList(0, 10).toList(), deck.subList(10, 20).toList()),
            prikups = listOf(deck.subList(20, 22).toList(), deck.subList(22, 24).toList()),
        )
    }

    @Test
    fun `партия, брошенная посреди розыгрыша, поднимается`() {
        // Взятый прикуп лежит в руке заказчика. Если записать его ещё и
        // прикупной строкой, при чтении карты посчитаются дважды, и партия
        // не поднимется вовсе — а пишется она после каждого хода.
        val round = dealtRound()
        round.apply(0, ThousandMove.Bid(100))
        round.apply(1, ThousandMove.Pass)
        round.apply(0, ThousandMove.TakePrikups(0))
        round.apply(0, ThousandMove.Discard(round.handOf(0).take(1)))
        round.apply(0, ThousandMove.Play(round.handOf(0).first()))

        val match = ThousandMatch.forTesting(scores = listOf(0, 0))
        val saved = assertNotNull(ThousandSave.read(ThousandSave.write(match, round)))
        assertEquals(round.handOf(0), saved.round.handOf(0))
        assertEquals(round.handOf(1), saved.round.handOf(1))
        assertEquals(round.tableSeats(), saved.round.tableSeats())
    }

    @Test
    fun `расписанный кон переживает запись на диск`() {
        val round = dealtRound()
        round.apply(0, ThousandMove.Bid(100))
        round.apply(1, ThousandMove.Pass)
        round.apply(0, ThousandMove.TakePrikups(0))
        round.apply(0, ThousandMove.Discard(round.handOf(0).take(1)))
        round.apply(0, ThousandMove.Raspis)
        assertEquals(Phase.OVER, round.phase)

        val match = ThousandMatch.forTesting(scores = listOf(100, 100))
        val saved = assertNotNull(ThousandSave.read(ThousandSave.write(match, round)))
        assertEquals(0, saved.round.raspised, "роспись — это состояние кона, и она тоже пишется")

        val first = match.finishRound(round)
        val second = saved.match.finishRound(saved.round)
        assertEquals(first.deltas, second.deltas)
        assertEquals(first.raspised, second.raspised)
        assertEquals(match.scores.toList(), saved.match.scores.toList())
    }
}
