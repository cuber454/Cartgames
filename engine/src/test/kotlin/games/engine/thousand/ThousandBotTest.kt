package games.engine.thousand

import games.engine.Card
import games.engine.Difficulty
import games.engine.Rank
import games.engine.Suit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `снос отдаёт карту из короткой масти, а длинную бережёт`() {
        // Девятки не стоят ничего, и без счёта мастей бот отдал бы любую.
        // Но три пики — это будущие взятки, а одинокая черва — нет.
        val hand = listOf(
            c(Rank.NINE, Suit.SPADES), c(Rank.JACK, Suit.SPADES),
            c(Rank.QUEEN, Suit.SPADES), c(Rank.NINE, Suit.HEARTS),
        )
        val round = ThousandRound.forTesting(
            hands = listOf(hand, listOf(c(Rank.NINE, Suit.CLUBS))),
        )
        round.apply(0, ThousandMove.Bid(100))
        round.apply(1, ThousandMove.Pass)
        round.apply(0, ThousandMove.TakePrikups(0))

        assertEquals(
            ThousandMove.Discard(listOf(c(Rank.NINE, Suit.HEARTS))),
            ThousandBot.chooseMove(round, 0, Difficulty.NORMAL),
        )
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

    /**
     * Кон, в котором бот — заказчик, а заказ ему не по руке: сто пять при
     * трёх девятках и валете. Даже по самой щедрой оценке столько не взять.
     */
    private fun hopelessContract(): ThousandRound {
        val round = ThousandRound.forTesting(
            hands = listOf(
                listOf(c(Rank.NINE, Suit.CLUBS), c(Rank.JACK, Suit.SPADES)),
                listOf(
                    c(Rank.NINE, Suit.HEARTS), c(Rank.NINE, Suit.DIAMONDS),
                    c(Rank.JACK, Suit.HEARTS),
                ),
            ),
        )
        round.apply(0, ThousandMove.Bid(100))
        round.apply(1, ThousandMove.Bid(105))
        round.apply(0, ThousandMove.Pass)
        round.apply(1, ThousandMove.TakePrikups(0))
        round.apply(1, ThousandMove.Discard(listOf(c(Rank.JACK, Suit.HEARTS))))
        return round
    }

    @Test
    fun `бот расписывается, когда заказанного не набрать`() {
        val round = hopelessContract()
        assertEquals(Phase.PLAY, round.phase)
        assertEquals(1, round.declarer)
        assertEquals(ThousandMove.Raspis, ThousandBot.chooseMove(round, 1, Difficulty.CLEVER))
    }

    /**
     * Стол, на котором памяти есть что сказать: все шесть бубён у бота на
     * руке, а старшие пики ещё не вышли. Заказчик заходит первым, и снести
     * ему заказано девятку пик — так у соперника оказывается третья пика,
     * которую тоже надо помнить.
     */
    private fun leadingRound(): ThousandRound {
        val diamonds = listOf(Rank.NINE, Rank.JACK, Rank.QUEEN, Rank.KING, Rank.TEN, Rank.ACE)
            .map { c(it, Suit.DIAMONDS) }
        val spades = listOf(Rank.NINE, Rank.JACK, Rank.QUEEN, Rank.KING)
            .map { c(it, Suit.SPADES) }
        val clubs = listOf(Rank.NINE, Rank.JACK, Rank.QUEEN, Rank.KING, Rank.TEN, Rank.ACE)
            .map { c(it, Suit.CLUBS) }
        val hearts = listOf(Rank.NINE, Rank.JACK, Rank.QUEEN, Rank.KING)
            .map { c(it, Suit.HEARTS) }

        val round = ThousandRound.forTesting(hands = listOf(diamonds + spades, clubs + hearts))
        round.apply(0, ThousandMove.Bid(100))
        round.apply(1, ThousandMove.Pass)
        round.apply(0, ThousandMove.TakePrikups(0))
        round.apply(0, ThousandMove.Discard(listOf(c(Rank.NINE, Suit.SPADES))))
        assertEquals(Phase.PLAY, round.phase)
        return round
    }

    @Test
    fun `хитрый ведёт с карты, которую уже некому побить`() {
        // Буби все на руке — значит, туз бубён верная взятка, и ходить надо
        // им, а не наугад: такая карта не пропадёт и позже, но пока она
        // забирает одиннадцать очков наверняка.
        val round = leadingRound()
        assertEquals(
            ThousandMove.Play(c(Rank.ACE, Suit.DIAMONDS)),
            ThousandBot.chooseMove(round, 0, Difficulty.CLEVER),
        )
    }

    @Test
    fun `обычный заходит с младшей карты длинной масти`() {
        // Тот же стол без памяти: обычный не знает, что буби уже не побьют,
        // и заходит девяткой бубён — соперник обязан ответить в масть.
        val round = leadingRound()
        assertEquals(
            ThousandMove.Play(c(Rank.NINE, Suit.DIAMONDS)),
            ThousandBot.chooseMove(round, 0, Difficulty.NORMAL),
        )
    }

    @Test
    fun `память знает, что вышло, а что ещё может прийти`() {
        val round = leadingRound()
        val seen = ThousandBot.Seen(round, 0)

        assertEquals(0, seen.left(Suit.DIAMONDS), "все буби на руке — их больше нет ни у кого")
        assertTrue(
            seen.holds(c(Rank.ACE, Suit.DIAMONDS), trump = null),
            "бубну бить нечем, пока козырь не объявлен",
        )
        assertFalse(
            seen.holds(c(Rank.KING, Suit.SPADES), trump = null),
            "туз и десятка пик ещё не вышли — короля побьют",
        )
        // Снесённая девятка ушла сопернику, и туз с десяткой пик лежат вне
        // игры: для бота всё это «может прийти», и лишнего он себе не
        // приписывает.
        assertEquals(3, seen.left(Suit.SPADES))
    }

    @Test
    fun `память не подглядывает в прикуп`() {
        // Прикуп — закрытая карта даже для того, кто его не взял. Пусть он
        // лежит в коне и всем виден в коде: считать его своим знанием нельзя,
        // иначе бот начнёт играть краплёной колодой.
        val round = ThousandRound.forTesting(
            hands = listOf(
                listOf(c(Rank.NINE, Suit.SPADES), c(Rank.JACK, Suit.SPADES)),
                listOf(c(Rank.NINE, Suit.CLUBS), c(Rank.JACK, Suit.CLUBS)),
            ),
            prikups = listOf(listOf(c(Rank.ACE, Suit.HEARTS)), listOf(c(Rank.TEN, Suit.HEARTS))),
        )
        val seen = ThousandBot.Seen(round, 0)
        assertTrue(seen.unseen.contains(c(Rank.ACE, Suit.HEARTS)), "это не знание бота, а невидимое")
        assertTrue(seen.unseen.contains(c(Rank.TEN, Suit.HEARTS)))
    }

    @Test
    fun `новичок не расписывается — он играет картами`() {
        val round = hopelessContract()
        repeat(20) {
            assertTrue(
                ThousandBot.chooseMove(round, 1, Difficulty.NOVICE) != ThousandMove.Raspis,
                "роспись — решение на счёт партии, случайным ходом её не выбирают",
            )
        }
    }
}
