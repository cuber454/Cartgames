package games.engine.thousand

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
