package games.engine.thousand

import games.engine.Card
import games.engine.Difficulty
import games.engine.Rank
import games.engine.Suit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ThousandBotTest {

    private fun c(rank: Rank, suit: Suit) = Card(rank, suit)

    @Test
    fun `боты доигрывают кон до конца и ходят только по правилам`() {
        repeat(20) { seed ->
            val random = Random(seed)
            val round = ThousandRound.start(random, playerCount = 2)
            var guard = 0
            while (!round.isFinished(round)) {
                check(guard++ < 1000) { "кон не кончается" }
                val seat = round.turn
                val move = ThousandBot.chooseMove(round, seat, Difficulty.CLEVER, random)
                assertNotNull(move, "бот не нашёл хода в фазе ${round.phase}")
                assertTrue(move in round.legalMoves(seat), "недопустимый ход: $move")
                round.apply(seat, move)
            }
        }
    }

    @Test
    fun `новичок тоже доигрывает кон`() {
        repeat(10) { seed ->
            val random = Random(seed + 100)
            val round = ThousandRound.start(random, playerCount = 3)
            var guard = 0
            while (!round.isFinished(round)) {
                check(guard++ < 1000) { "кон не кончается" }
                val seat = round.turn
                val move = ThousandBot.chooseMove(round, seat, Difficulty.NOVICE, random)
                round.apply(seat, assertNotNull(move))
            }
        }
    }

    @Test
    fun `марьяж весит больше, чем взятки с той же руки`() {
        val withMarriage = listOf(
            c(Rank.KING, Suit.HEARTS), c(Rank.QUEEN, Suit.HEARTS),
            c(Rank.NINE, Suit.SPADES), c(Rank.JACK, Suit.CLUBS),
        )
        val without = listOf(
            c(Rank.KING, Suit.HEARTS), c(Rank.QUEEN, Suit.SPADES),
            c(Rank.NINE, Suit.SPADES), c(Rank.JACK, Suit.CLUBS),
        )
        assertTrue(ThousandBot.handPower(withMarriage) > ThousandBot.handPower(without) + 60)
    }

    @Test
    fun `снос не трогает марьяж`() {
        val hand = listOf(
            c(Rank.KING, Suit.HEARTS), c(Rank.QUEEN, Suit.HEARTS),
            c(Rank.NINE, Suit.SPADES), c(Rank.JACK, Suit.CLUBS),
        )
        val round = ThousandRound.forTesting(hands = listOf(hand, listOf(c(Rank.NINE, Suit.CLUBS))))
        // До сноса надо ещё дойти: торг, прикуп — и только потом снос.
        round.apply(0, ThousandMove.Bid(100))
        round.apply(1, ThousandMove.Pass)
        round.apply(0, ThousandMove.TakePrikups(0))
        assertEquals(Phase.DISCARD, round.phase)

        val move = ThousandBot.chooseMove(round, 0, Difficulty.CLEVER)
        assertTrue(move is ThousandMove.Discard)
        val discarded = (move as ThousandMove.Discard).cards
        assertEquals(listOf(c(Rank.NINE, Suit.SPADES)), discarded)
    }

    @Test
    fun `слабая рука не лезет в торг, сильная называет`() {
        val weak = listOf(
            c(Rank.NINE, Suit.SPADES), c(Rank.JACK, Suit.SPADES),
            c(Rank.NINE, Suit.CLUBS), c(Rank.JACK, Suit.CLUBS),
        )
        val strong = listOf(
            c(Rank.ACE, Suit.HEARTS), c(Rank.TEN, Suit.HEARTS),
            c(Rank.KING, Suit.HEARTS), c(Rank.QUEEN, Suit.HEARTS),
        )
        assertTrue(ThousandBot.handPower(strong) > ThousandBot.handPower(weak) + 80)
    }

    @Test
    fun `в розыгрыше бот ходит картой, а не пасует`() {
        // Вторая взятка уже за ботом — значит хвалить ему можно.
        val hand = listOf(
            c(Rank.KING, Suit.HEARTS), c(Rank.QUEEN, Suit.HEARTS),
            c(Rank.NINE, Suit.SPADES), c(Rank.JACK, Suit.CLUBS),
        )
        val round = ThousandRound.forTesting(hands = listOf(hand, listOf(c(Rank.NINE, Suit.CLUBS))))
        // Проводим бота через торг, прикуп и снос — до розыгрыша.
        round.apply(0, ThousandMove.Bid(100))
        round.apply(1, ThousandMove.Pass)
        round.apply(0, ThousandMove.TakePrikups(0))
        round.apply(0, ThousandMove.Discard(listOf(c(Rank.NINE, Suit.SPADES))))
        assertEquals(Phase.PLAY, round.phase)
        val move = ThousandBot.chooseMove(round, 0, Difficulty.CLEVER)
        assertTrue(move is ThousandMove.Play, "ожидался ход картой, а не $move")
    }
}
