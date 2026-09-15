package games.engine.thousand

import games.engine.Card
import games.engine.Rank
import games.engine.Suit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThousandRoundTest {

    private fun c(rank: Rank, suit: Suit) = Card(rank, suit)

    @Test
    fun `в колоде 24 карты и 120 очков`() {
        val deck = fullDeck24()
        assertEquals(24, deck.size)
        assertEquals(24, deck.toSet().size, "двух одинаковых карт быть не может")
        assertEquals(120, deck.sumOf { it.points })
        assertEquals(30, deck.filter { it.suit == Suit.HEARTS }.sumOf { it.points })
    }

    @Test
    fun `десятка старше короля — в «Тысяче» порядок не как в «Дураке»`() {
        assertTrue(c(Rank.TEN, Suit.HEARTS).weight > c(Rank.KING, Suit.HEARTS).weight)
        assertTrue(c(Rank.ACE, Suit.HEARTS).weight > c(Rank.TEN, Suit.HEARTS).weight)
        assertTrue(c(Rank.NINE, Suit.HEARTS).weight < c(Rank.JACK, Suit.HEARTS).weight)
        assertEquals(0, c(Rank.NINE, Suit.CLUBS).points)
        assertEquals(11, c(Rank.ACE, Suit.CLUBS).points)
    }

    @Test
    fun `первое слово в торге — ровно сто, пасовать первым нельзя`() {
        val round = ThousandRound.start(Random(1))
        assertEquals(listOf(ThousandMove.Bid(100)), round.legalMoves(0))
        assertTrue(round.legalMoves(1).isEmpty(), "первым говорит не он")
    }

    @Test
    fun `второй игрок поднимает не меньше чем на пять или пасует`() {
        val round = ThousandRound.start(Random(2))
        round.apply(0, ThousandMove.Bid(100))

        val moves = round.legalMoves(1)
        assertFalse(moves.contains(ThousandMove.Bid(100)), "свою же ставку не повторяют")
        assertFalse(moves.contains(ThousandMove.Bid(95)))
        assertTrue(moves.contains(ThousandMove.Bid(105)))
        assertTrue(moves.contains(ThousandMove.Bid(120)))
        assertTrue(moves.contains(ThousandMove.Pass))
    }

    @Test
    fun `на ста двадцати поднимать некуда — только пас`() {
        val round = ThousandRound.start(Random(3))
        round.apply(0, ThousandMove.Bid(100))
        round.apply(1, ThousandMove.Bid(120))

        assertEquals(listOf(ThousandMove.Pass), round.legalMoves(0))
    }

    @Test
    fun `пас соперника отдаёт торг, и заказчик берёт прикуп`() {
        val round = ThousandRound.start(Random(4))
        round.apply(0, ThousandMove.Bid(100))
        round.apply(1, ThousandMove.Pass)

        assertEquals(0, round.declarer)
        assertEquals(100, round.currentBid)
        assertEquals(Phase.PRIKUP, round.phase)
        assertEquals(listOf(ThousandMove.TakePrikups(0), ThousandMove.TakePrikups(1)), round.legalMoves(0))
    }

    @Test
    fun `вдвоём сдают по десять карт и два прикупа по две`() {
        val round = ThousandRound.start(Random(5), playerCount = 2)

        assertEquals(10, round.handOf(0).size)
        assertEquals(10, round.handOf(1).size)
        assertEquals(2, round.prikupCount)
        assertEquals(2, round.prikup(0).size)
        assertEquals(2, round.prikup(1).size)
        assertTrue(round.prikup(0).none { it in round.prikup(1) }, "прикупы не пересекаются")
        assertEquals(
            24,
            round.handOf(0).size + round.handOf(1).size + round.prikup(0).size + round.prikup(1).size,
            "вся колода уходит со стола",
        )
    }

    @Test
    fun `втроём сдают по семь карт и один прикуп из трёх`() {
        val round = ThousandRound.start(Random(6), playerCount = 3)

        assertEquals(7, round.handOf(0).size)
        assertEquals(7, round.handOf(2).size)
        assertEquals(1, round.prikupCount)
        assertEquals(3, round.prikup(0).size)
        assertEquals(2, round.discardCount, "втроём сносят по карте каждому сопернику")
    }

    @Test
    fun `вдвоём заказчик выбирает прикуп и после сноса у всех по одиннадцать`() {
        val round = ThousandRound.start(Random(7), playerCount = 2)
        round.apply(0, ThousandMove.Bid(100))
        round.apply(1, ThousandMove.Pass)
        round.apply(0, ThousandMove.TakePrikups(1))

        assertEquals(12, round.handOf(0).size, "десять своих плюс два из прикупа")
        assertEquals(Phase.DISCARD, round.phase)

        val discards = round.legalMoves(0)
        assertEquals(12, discards.size, "сносим ровно одну карту — двенадцать вариантов")
        val discard = discards.first() as ThousandMove.Discard
        val chosen = discard.cards.single()
        round.apply(0, discard)

        assertEquals(11, round.handOf(0).size)
        assertEquals(11, round.handOf(1).size)
        assertEquals(Phase.PLAY, round.phase)
        assertEquals(0, round.turn, "заказчик ходит первым")
        assertTrue(chosen in round.handOf(1), "снесённая карта ушла сопернику")
    }

    @Test
    fun `масть захода обязательна, а без масти — любая карта`() {
        // Заказчик отдал девятку бубён: у защищающегося остались девятка
        // треф и девятка бубён. Зайдя в трефу, заказчик заставляет его
        // класть трефу — бубну сбросить нельзя.
        val round = ThousandRound.forTesting(
            hands = listOf(
                listOf(c(Rank.ACE, Suit.HEARTS), c(Rank.TEN, Suit.CLUBS), c(Rank.NINE, Suit.DIAMONDS)),
                listOf(c(Rank.NINE, Suit.CLUBS)),
            ),
        )
        round.apply(0, ThousandMove.Bid(100))
        round.apply(1, ThousandMove.Pass)
        round.apply(0, ThousandMove.TakePrikups(0))
        round.apply(0, ThousandMove.Discard(listOf(c(Rank.NINE, Suit.DIAMONDS))))

        round.apply(0, ThousandMove.Play(c(Rank.TEN, Suit.CLUBS)))
        assertEquals(
            listOf(ThousandMove.Play(c(Rank.NINE, Suit.CLUBS))),
            round.legalMoves(1),
            "есть масть захода — идём в масть, а не сбрасываем бубну",
        )
        round.apply(1, ThousandMove.Play(c(Rank.NINE, Suit.CLUBS)))
        assertEquals(10, round.trickPointsOf(0), "десятка взяла девятку")

        // Вторая взятка: заход червой, а у защищающегося ни червей, ни
        // козыря — можно любую карту.
        round.apply(0, ThousandMove.Play(c(Rank.ACE, Suit.HEARTS)))
        assertEquals(listOf(ThousandMove.Play(c(Rank.NINE, Suit.DIAMONDS))), round.legalMoves(1))
        round.apply(1, ThousandMove.Play(c(Rank.NINE, Suit.DIAMONDS)))

        assertEquals(Phase.OVER, round.phase)
        assertEquals(21, round.trickPointsOf(0))
        assertEquals(listOf(1), round.bolted(), "защищающийся не взял ни одной взятки")
    }

    @Test
    fun `хвалить нельзя, пока не взял ни одной взятки`() {
        val round = ThousandRound.forTesting(
            hands = listOf(
                listOf(c(Rank.KING, Suit.HEARTS), c(Rank.QUEEN, Suit.HEARTS), c(Rank.NINE, Suit.CLUBS)),
                listOf(c(Rank.TEN, Suit.CLUBS)),
            ),
        )
        round.apply(0, ThousandMove.Bid(100))
        round.apply(1, ThousandMove.Pass)
        round.apply(0, ThousandMove.TakePrikups(0))
        round.apply(0, ThousandMove.Discard(listOf(c(Rank.QUEEN, Suit.HEARTS))))

        val moves = round.legalMoves(0)
        assertTrue(moves.any { it is ThousandMove.Play }, "ходить можно")
        assertFalse(
            moves.any { it is ThousandMove.Praise },
            "первый ход кона — не для похвальбы: взяток ещё нет",
        )
        assertNull(round.trumpSuit, "козыря до марьяжа нет")
    }

    @Test
    fun `марьяж даёт очки, объявляет козырь и обязывает крыть`() {
        // Заказчик теряет первую взятку на трефах, защитник хвалит черви —
        // и с этого хода черви козырь, а заказчик, у которого они есть,
        // обязан ими крыть.
        val round = ThousandRound.forTesting(
            hands = listOf(
                listOf(
                    c(Rank.NINE, Suit.CLUBS), c(Rank.ACE, Suit.HEARTS),
                    c(Rank.NINE, Suit.SPADES), c(Rank.TEN, Suit.DIAMONDS), c(Rank.JACK, Suit.DIAMONDS),
                ),
                listOf(c(Rank.ACE, Suit.CLUBS), c(Rank.KING, Suit.HEARTS), c(Rank.QUEEN, Suit.HEARTS)),
            ),
        )
        round.apply(0, ThousandMove.Bid(100))
        round.apply(1, ThousandMove.Pass)
        round.apply(0, ThousandMove.TakePrikups(0))
        round.apply(0, ThousandMove.Discard(listOf(c(Rank.JACK, Suit.DIAMONDS))))

        // Первая взятка: девятка треф против туза треф.
        round.apply(0, ThousandMove.Play(c(Rank.NINE, Suit.CLUBS)))
        round.apply(1, ThousandMove.Play(c(Rank.ACE, Suit.CLUBS)))
        assertEquals(1, round.tricksOf(1))
        assertEquals(11, round.trickPointsOf(1))

        // Вторая взятка: защитник хвалит черви — король уходит на стол,
        // черви становятся козырными.
        assertTrue(round.legalMoves(1).contains(ThousandMove.Praise(c(Rank.KING, Suit.HEARTS))))
        round.apply(1, ThousandMove.Praise(c(Rank.KING, Suit.HEARTS)))

        assertEquals(Suit.HEARTS, round.trumpSuit)
        assertEquals(100, round.marriagePointsOf(1))
        assertFalse(
            round.handOf(1).contains(c(Rank.KING, Suit.HEARTS)),
            "объявление марьяжа — это и есть ход: король уже на столе",
        )
        // На черву у заказчика есть туз — обязан положить его.
        assertEquals(listOf(ThousandMove.Play(c(Rank.ACE, Suit.HEARTS))), round.legalMoves(0))
        round.apply(0, ThousandMove.Play(c(Rank.ACE, Suit.HEARTS)))

        // Третья взятка: заказчик заходит девяткой пик, червей у него нет,
        // а козырь есть — обязан крыть.
        round.apply(0, ThousandMove.Play(c(Rank.NINE, Suit.SPADES)))
        assertTrue(
            round.legalMoves(1).contains(ThousandMove.Play(c(Rank.QUEEN, Suit.HEARTS))),
            "козырь из руки — единственный ход",
        )
        assertEquals(listOf(ThousandMove.Play(c(Rank.QUEEN, Suit.HEARTS))), round.legalMoves(1))
        round.apply(1, ThousandMove.Play(c(Rank.QUEEN, Suit.HEARTS)))
        assertEquals(14, round.trickPointsOf(1), "одиннадцать за туза плюс три за даму")
        assertEquals(114, round.roundPoints(1), "и сотня за объявленный марьяж")
    }

    @Test
    fun `заказчик пишет заказ или минус заказ, защитник — округлённое до пяти`() {
        // Заказчик набрал 27 очков при заказе 100 — пишет минус сотню.
        // Защитник взял 11 и 3 — пишет 15.
        val round = ThousandRound.forTesting(
            hands = listOf(
                listOf(
                    c(Rank.NINE, Suit.CLUBS), c(Rank.ACE, Suit.HEARTS),
                    c(Rank.NINE, Suit.SPADES), c(Rank.TEN, Suit.DIAMONDS), c(Rank.JACK, Suit.DIAMONDS),
                ),
                listOf(c(Rank.ACE, Suit.CLUBS), c(Rank.KING, Suit.HEARTS), c(Rank.QUEEN, Suit.HEARTS)),
            ),
        )
        round.apply(0, ThousandMove.Bid(100))
        round.apply(1, ThousandMove.Pass)
        round.apply(0, ThousandMove.TakePrikups(0))
        round.apply(0, ThousandMove.Discard(listOf(c(Rank.JACK, Suit.DIAMONDS))))

        round.apply(0, ThousandMove.Play(c(Rank.NINE, Suit.CLUBS)))
        round.apply(1, ThousandMove.Play(c(Rank.ACE, Suit.CLUBS)))
        round.apply(1, ThousandMove.Praise(c(Rank.KING, Suit.HEARTS)))
        round.apply(0, ThousandMove.Play(c(Rank.ACE, Suit.HEARTS)))
        round.apply(0, ThousandMove.Play(c(Rank.NINE, Suit.SPADES)))
        round.apply(1, ThousandMove.Play(c(Rank.QUEEN, Suit.HEARTS)))
        round.apply(1, ThousandMove.Play(c(Rank.JACK, Suit.DIAMONDS)))
        round.apply(0, ThousandMove.Play(c(Rank.TEN, Suit.DIAMONDS)))

        assertEquals(Phase.OVER, round.phase)
        assertEquals(27, round.roundPoints(0), "взятки заказчика")
        assertEquals(114, round.roundPoints(1), "туз с дамой на трефах, сто за марьяж и взятка на бубнах")

        val deltas = round.deltas()
        assertEquals(-100, deltas[0], "не добрал — минус заказ")
        assertEquals(115, deltas[1], "сто четырнадцать округляется до ста пятнадцати")
        assertTrue(round.bolted().isEmpty(), "взятки взяли оба")
    }
}
