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

    /** Рука, с которой золотой кон и объявляют: все четыре марьяжа на десяти картах. */
    private fun goldenHand(): List<Card> = listOf(
        c(Rank.KING, Suit.SPADES), c(Rank.QUEEN, Suit.SPADES),
        c(Rank.KING, Suit.CLUBS), c(Rank.QUEEN, Suit.CLUBS),
        c(Rank.KING, Suit.DIAMONDS), c(Rank.QUEEN, Suit.DIAMONDS),
        c(Rank.KING, Suit.HEARTS), c(Rank.QUEEN, Suit.HEARTS),
        c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.CLUBS),
    )

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
    fun `пустая раздача — не игра, а пересдача`() {
        // Поводы из книги, по которой сверяли правила (THOUSAND.md, 2.12).
        val fourNines = listOf(
            listOf(
                c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS),
                c(Rank.NINE, Suit.CLUBS), c(Rank.NINE, Suit.DIAMONDS),
                c(Rank.ACE, Suit.SPADES), c(Rank.ACE, Suit.HEARTS),
                c(Rank.TEN, Suit.SPADES), c(Rank.TEN, Suit.HEARTS),
            ),
            listOf(
                c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.HEARTS),
                c(Rank.KING, Suit.CLUBS), c(Rank.KING, Suit.DIAMONDS),
                c(Rank.QUEEN, Suit.SPADES), c(Rank.QUEEN, Suit.HEARTS),
            ),
        )
        assertTrue(
            ThousandRound.badDeal(fourNines, listOf(listOf(c(Rank.JACK, Suit.SPADES), c(Rank.JACK, Suit.HEARTS)))),
            "четыре девятки на одних руках — играть нечем",
        )

        val rich = listOf(
            listOf(
                c(Rank.ACE, Suit.SPADES), c(Rank.ACE, Suit.HEARTS),
                c(Rank.TEN, Suit.SPADES), c(Rank.TEN, Suit.HEARTS),
            ),
            listOf(
                c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.HEARTS),
                c(Rank.QUEEN, Suit.SPADES), c(Rank.QUEEN, Suit.HEARTS),
            ),
        )
        assertTrue(
            ThousandRound.badDeal(rich, listOf(listOf(c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS)))),
            "две девятки в прикупе",
        )
        assertTrue(
            ThousandRound.badDeal(rich, listOf(listOf(c(Rank.NINE, Suit.SPADES), c(Rank.JACK, Suit.HEARTS)))),
            "прикуп дешевле четырёх очков",
        )
        assertFalse(
            ThousandRound.badDeal(rich, listOf(listOf(c(Rank.JACK, Suit.SPADES), c(Rank.JACK, Suit.HEARTS)))),
            "обычный прикуп игре не мешает",
        )

        val bare = listOf(
            listOf(
                c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS), c(Rank.NINE, Suit.CLUBS),
                c(Rank.JACK, Suit.SPADES), c(Rank.JACK, Suit.HEARTS),
                c(Rank.JACK, Suit.CLUBS), c(Rank.JACK, Suit.DIAMONDS),
                c(Rank.QUEEN, Suit.SPADES),
            ),
            listOf(
                c(Rank.ACE, Suit.SPADES), c(Rank.ACE, Suit.HEARTS),
                c(Rank.ACE, Suit.CLUBS), c(Rank.ACE, Suit.DIAMONDS),
                c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.HEARTS),
                c(Rank.KING, Suit.CLUBS), c(Rank.KING, Suit.DIAMONDS),
            ),
        )
        assertTrue(
            ThousandRound.badDeal(bare, listOf(listOf(c(Rank.TEN, Suit.SPADES), c(Rank.TEN, Suit.HEARTS)))),
            "голая рука: меньше тринадцати очков",
        )
    }

    @Test
    fun `вдвоём дорогой прикуп оставил бы в игре меньше сотни — пересдача`() {
        val hands = listOf(
            listOf(
                c(Rank.TEN, Suit.SPADES), c(Rank.TEN, Suit.HEARTS),
                c(Rank.TEN, Suit.CLUBS), c(Rank.TEN, Suit.DIAMONDS),
            ),
            listOf(c(Rank.ACE, Suit.CLUBS), c(Rank.ACE, Suit.DIAMONDS), c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.HEARTS)),
        )
        val prikups = listOf(
            listOf(c(Rank.ACE, Suit.SPADES), c(Rank.ACE, Suit.HEARTS)),
            listOf(c(Rank.JACK, Suit.SPADES), c(Rank.JACK, Suit.HEARTS)),
        )
        assertTrue(
            ThousandRound.badDeal(hands, prikups),
            "второй прикуп уходит из игры: туз с тузом оставят в колоде девяносто восемь",
        )

        val three = hands + listOf(
            listOf(c(Rank.KING, Suit.CLUBS), c(Rank.KING, Suit.DIAMONDS), c(Rank.QUEEN, Suit.SPADES), c(Rank.QUEEN, Suit.HEARTS)),
        )
        assertFalse(
            ThousandRound.badDeal(three, prikups),
            "втроём из игры ничего не выпадает — правило не про них",
        )
    }

    @Test
    fun `раздача всегда играется, а вдвоём сотня всегда достижима`() {
        repeat(300) { seed ->
            val two = ThousandRound.start(Random(seed), playerCount = 2)
            val prikups = listOf(two.prikup(0), two.prikup(1))
            assertFalse(
                ThousandRound.badDeal(listOf(two.handOf(0), two.handOf(1)), prikups),
                "пересдача не пересдалась, seed $seed",
            )
            assertTrue(
                ThousandRound.TOTAL_POINTS - prikups.maxOf { it.sumOf { card -> card.points } } >= ThousandRound.MIN_BID,
                "вдвоём обязательная сотня должна быть достижима, seed $seed",
            )

            val three = ThousandRound.start(Random(seed), playerCount = 3)
            assertFalse(
                ThousandRound.badDeal((0..2).map { three.handOf(it) }, listOf(three.prikup(0))),
                "пересдача не пересдалась, seed $seed, трое",
            )
        }
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
    fun `четыре туза на руке видно тогда, когда снос лёг`() {
        // До сноса карты ещё меняются: прикуп не взят, снос не сделан, —
        // и «на руке» ничего не значит.
        val round = ThousandRound.forTesting(
            hands = listOf(
                listOf(
                    c(Rank.ACE, Suit.SPADES),
                    c(Rank.ACE, Suit.CLUBS),
                    c(Rank.ACE, Suit.DIAMONDS),
                    c(Rank.ACE, Suit.HEARTS),
                    c(Rank.NINE, Suit.SPADES),
                ),
                listOf(c(Rank.NINE, Suit.CLUBS)),
            ),
        )
        round.apply(0, ThousandMove.Bid(100))
        round.apply(1, ThousandMove.Pass)
        assertFalse(round.hadAllAces(0), "торг ещё не кончился, прикуп не взят")

        round.apply(0, ThousandMove.TakePrikups(0))
        assertFalse(round.hadAllAces(0), "прикуп взят, но снос ещё не сделан")

        round.apply(0, ThousandMove.Discard(listOf(c(Rank.NINE, Suit.SPADES))))
        assertTrue(round.hadAllAces(0))
        assertFalse(round.hadAllAces(1), "у защищающегося тузов нет вовсе")
    }

    @Test
    fun `ушедший в снос туз четырёх не оставляет`() {
        val round = ThousandRound.forTesting(
            hands = listOf(
                listOf(
                    c(Rank.ACE, Suit.SPADES),
                    c(Rank.ACE, Suit.CLUBS),
                    c(Rank.ACE, Suit.DIAMONDS),
                    c(Rank.ACE, Suit.HEARTS),
                    c(Rank.NINE, Suit.SPADES),
                ),
                listOf(c(Rank.NINE, Suit.CLUBS)),
            ),
        )
        round.apply(0, ThousandMove.Bid(100))
        round.apply(1, ThousandMove.Pass)
        round.apply(0, ThousandMove.TakePrikups(0))
        round.apply(0, ThousandMove.Discard(listOf(c(Rank.ACE, Suit.HEARTS))))

        assertFalse(round.hadAllAces(0), "туз ушёл защитнику, и тузов осталось три")
    }

    @Test
    fun `золотой кон объявляется вместо первой ставки`() {
        val round = ThousandRound.forTesting(
            hands = listOf(goldenHand(), listOf(c(Rank.NINE, Suit.CLUBS))),
            goldenAllowed = true,
        )
        assertTrue(round.legalMoves(0).contains(ThousandMove.Golden))

        round.apply(0, ThousandMove.Golden)

        assertTrue(round.golden)
        assertEquals(0, round.declarer)
        assertEquals(120, round.currentBid, "золотой кон играется на весь заказ")
        assertEquals(Phase.PLAY, round.phase, "торга и прикупа в нём нет")
        assertEquals(0, round.turn, "заказчик ходит первым")
        assertEquals(10, round.handOf(0).size, "прикуп не берут — рука та же, что сдана")
    }

    @Test
    fun `без договорённости золотой кон не объявить`() {
        val round = ThousandRound.forTesting(
            hands = listOf(goldenHand(), listOf(c(Rank.NINE, Suit.CLUBS))),
        )
        assertFalse(round.legalMoves(0).contains(ThousandMove.Golden))
    }

    @Test
    fun `слабая рука золотого кона не даёт`() {
        // Золотой кон — не ставка вслепую: объявить его можно только с рукой,
        // которая заказ уже держит.
        val round = ThousandRound.forTesting(
            hands = listOf(
                listOf(c(Rank.NINE, Suit.SPADES), c(Rank.JACK, Suit.CLUBS)),
                listOf(c(Rank.NINE, Suit.CLUBS), c(Rank.JACK, Suit.DIAMONDS)),
            ),
            goldenAllowed = true,
        )
        assertFalse(round.legalMoves(0).contains(ThousandMove.Golden))
    }

    @Test
    fun `после первой ставки золотой кон не объявить`() {
        val round = ThousandRound.forTesting(
            hands = listOf(goldenHand(), goldenHand()),
            goldenAllowed = true,
        )
        round.apply(0, ThousandMove.Bid(100))

        assertFalse(
            round.legalMoves(1).contains(ThousandMove.Golden),
            "золотой кон и значит «без торга» — после ставки он не золотой",
        )
    }

    @Test
    fun `рука золотого кона считается с марьяжами`() {
        val round = ThousandRound.forTesting(hands = listOf(goldenHand(), emptyList()))

        assertEquals(280, round.handValue(0) - round.handOf(0).sumOf { it.points })
        assertTrue(round.handValue(0) >= 120)
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
        // На черву у заказчика есть туз — обязан положить его. Расписаться
        // он тоже может: заказчик в розыгрыше, а роспись — его право.
        assertEquals(
            listOf(ThousandMove.Play(c(Rank.ACE, Suit.HEARTS)), ThousandMove.Raspis),
            round.legalMoves(0),
        )
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
    fun `карту с марьяжем можно и сыграть, и похвалить`() {
        // Экран переспрашивает игрока ровно потому, что у карты два равных
        // права хода: «положить короля» и «похвалить черви». Если движок
        // когда-нибудь оставит одно, диалог станет враньём — этот тест
        // и скажет, что спрашивать больше не о чем.
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

        val moves = round.legalMoves(1)
        assertTrue(
            moves.contains(ThousandMove.Play(c(Rank.KING, Suit.HEARTS))),
            "король ходит и без похвалы",
        )
        assertTrue(
            moves.contains(ThousandMove.Praise(c(Rank.KING, Suit.HEARTS))),
            "той же картой марьяж и объявляют",
        )
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

    @Test
    fun `расписаться может только заказчик и только в розыгрыше`() {
        val round = ThousandRound.forTesting(
            hands = listOf(
                listOf(c(Rank.NINE, Suit.CLUBS), c(Rank.NINE, Suit.SPADES)),
                listOf(c(Rank.ACE, Suit.CLUBS), c(Rank.ACE, Suit.SPADES)),
            ),
        )
        round.apply(0, ThousandMove.Bid(100))
        assertFalse(
            round.legalMoves(1).contains(ThousandMove.Raspis),
            "в торге расписываться нечем: заказа ещё нет",
        )
        round.apply(1, ThousandMove.Pass)
        round.apply(0, ThousandMove.TakePrikups(0))
        assertFalse(
            round.legalMoves(0).contains(ThousandMove.Raspis),
            "до розыгрыша заказ ещё не проигран",
        )
        round.apply(0, ThousandMove.Discard(listOf(c(Rank.NINE, Suit.CLUBS))))

        assertTrue(round.legalMoves(0).contains(ThousandMove.Raspis), "заказчику есть чем расписаться")
        round.apply(0, ThousandMove.Play(c(Rank.NINE, Suit.SPADES)))
        assertFalse(
            round.legalMoves(1).contains(ThousandMove.Raspis),
            "защитник чужой заказ не расписывает",
        )
    }

    @Test
    fun `роспись кончает кон — заказчик пишет заказ, сопернику половина`() {
        val round = ThousandRound.forTesting(
            hands = listOf(
                listOf(c(Rank.NINE, Suit.CLUBS), c(Rank.NINE, Suit.SPADES)),
                listOf(c(Rank.ACE, Suit.CLUBS), c(Rank.ACE, Suit.SPADES)),
            ),
        )
        round.apply(0, ThousandMove.Bid(100))
        round.apply(1, ThousandMove.Pass)
        round.apply(0, ThousandMove.TakePrikups(0))
        round.apply(0, ThousandMove.Discard(listOf(c(Rank.NINE, Suit.CLUBS))))
        round.apply(0, ThousandMove.Raspis)

        assertEquals(Phase.OVER, round.phase)
        assertEquals(0, round.raspised)
        val deltas = round.deltas()
        assertEquals(-100, deltas[0], "роспись — весь заказ заказчику")
        assertEquals(50, deltas[1], "и половина заказа защитнику")
        assertTrue(round.bolted().isEmpty(), "расписанный кон болтов не приносит: взяток в нём нет ни у кого")
    }

    @Test
    fun `половина заказа округляется вниз до пяти`() {
        assertEquals(50, ThousandRound.raspisShare(100))
        assertEquals(50, ThousandRound.raspisShare(105), "пятьдесят два с половиной — не очко за столом")
        assertEquals(55, ThousandRound.raspisShare(115))
        assertEquals(60, ThousandRound.raspisShare(120))
    }
}
