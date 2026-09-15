package games.engine.thousand

import games.engine.Card
import games.engine.Difficulty
import games.engine.Rank
import games.engine.Suit
import kotlin.random.Random

/**
 * Соперник для «Тысячи».
 *
 * Живёт в движке, как и соперник «Дурака»: это правило игры, а не экран, и
 * так его можно прогнать тестами без телефона.
 *
 * Оценивать руку здесь труднее, чем в «Дураке». Там сила карты — это её
 * старшинство, а тут ещё и очки: десятка бьёт короля, но берёт за взятку
 * десять против четырёх. Поэтому «сила» руки считается не по старшинству,
 * а по тому, сколько очков бот рассчитывает собрать, и по марьяжам: пара
 * король-дама приносит сорок-сто очков, и это часто больше, чем все взятки
 * кона вместе.
 */
object ThousandBot {

    fun chooseMove(
        round: ThousandRound,
        seat: Int,
        difficulty: Difficulty = Difficulty.NORMAL,
        random: Random = Random.Default,
    ): ThousandMove? {
        val moves = round.legalMoves(seat)
        if (moves.isEmpty()) return null
        if (difficulty == Difficulty.NOVICE) return moves[random.nextInt(moves.size)]

        return when (round.phase) {
            Phase.BIDDING -> bid(round, seat, moves, difficulty)
            Phase.PRIKUP -> pickPrikup(round, seat, moves)
            Phase.DISCARD -> discard(round, seat, moves)
            Phase.PLAY -> play(round, seat, moves)
            Phase.OVER -> null
        }
    }

    // --- Торг -------------------------------------------------------------

    /**
     * Торгуемся до того, во что сами верим.
     *
     * Запас — та разница между силой руки и названной суммой, которую бот
     * считает терпимой. Обычный уровень оставляет двадцать пять очков на
     * промах: назвать сто с рукой на сто десять он не станет, потому что
     * взятки ещё надо взять. Хитрый рискует плотнее.
     */
    private fun bid(
        round: ThousandRound,
        seat: Int,
        moves: List<ThousandMove>,
        difficulty: Difficulty,
    ): ThousandMove {
        val bids = moves.filterIsInstance<ThousandMove.Bid>()
        if (bids.isEmpty()) return ThousandMove.Pass

        val margin = if (difficulty == Difficulty.CLEVER) 10 else 25
        val power = handPower(round.handOf(seat))
        val affordable = bids.filter { it.amount + margin <= power }.maxByOrNull { it.amount }
        if (affordable != null) return affordable

        // Первое слово в торге пасовать нечем — назвать сто всё равно придётся.
        return if (moves.contains(ThousandMove.Pass)) ThousandMove.Pass else bids.first()
    }

    // --- Прикуп -----------------------------------------------------------

    /** Берём тот прикуп, который больше усиливает руку. */
    private fun pickPrikup(round: ThousandRound, seat: Int, moves: List<ThousandMove>): ThousandMove {
        val takes = moves.filterIsInstance<ThousandMove.TakePrikups>()
        return takes.maxByOrNull { handPower(round.handOf(seat) + round.prikup(it.index)) } ?: takes.first()
    }

    // --- Снос -------------------------------------------------------------

    /**
     * Сносим самое дешёвое: девятки и валеты — в первую очередь, карты из
     * марьяжа — никогда. Снесённая карта уходит сопернику вместе со своими
     * очками, поэтому отдавать десятку или туза — подарок.
     */
    private fun discard(round: ThousandRound, seat: Int, moves: List<ThousandMove>): ThousandMove {
        val hand = round.handOf(seat)
        val discards = moves.filterIsInstance<ThousandMove.Discard>()
        return discards.minByOrNull { option -> option.cards.sumOf { keepingValue(it, hand) } } ?: discards.first()
    }

    // --- Розыгрыш ---------------------------------------------------------

    private fun play(round: ThousandRound, seat: Int, moves: List<ThousandMove>): ThousandMove {
        val praises = moves.filterIsInstance<ThousandMove.Praise>()
        val plays = moves.filterIsInstance<ThousandMove.Play>()
        val trump = round.trumpSuit

        // Марьяж объявляем всегда, когда можем: он сразу приносит очки и
        // делает масть козырем. Отказываться от такого хода незачем.
        if (praises.isNotEmpty()) {
            val best = praises.maxByOrNull { marriagePoints(it.card.suit) * 10 + it.card.weight }
            if (best != null) return best
        }
        if (plays.isEmpty()) return moves.first()

        val lead = round.tableNow().firstOrNull()
            ?: return leading(plays, trump)
        return answering(round, seat, plays, lead, trump)
    }

    /** Веду взятку: кладу старшую карту, чтобы её труднее было побить. */
    private fun leading(plays: List<ThousandMove.Play>, trump: Suit?): ThousandMove {
        val plain = plays.filter { it.card.suit != trump }
        val pool = plain.ifEmpty { plays }
        return pool.maxByOrNull { it.card.weight * 10 + it.card.points } ?: plays.first()
    }

    /**
     * Отвечаю на чужую карту. Есть чем бить — бью самым дешёвым: козырь
     * дороже, поэтому он идёт в ход последним. Нечем — сбрасываю мелочь,
     * чтобы не отдать сопернику лишних очков.
     */
    private fun answering(
        round: ThousandRound,
        seat: Int,
        plays: List<ThousandMove.Play>,
        lead: Card,
        trump: Suit?,
    ): ThousandMove {
        val beating = plays.filter { beats(it.card, lead, trump) }
        if (beating.isNotEmpty()) {
            return beating.minByOrNull { cardCost(it.card, trump) } ?: beating.first()
        }
        val hand = round.handOf(seat)
        return plays.minByOrNull { keepingValue(it.card, hand) } ?: plays.first()
    }

    /** Бьёт ли [card] лежащую [lead] — при объявленном козыре [trump]. */
    private fun beats(card: Card, lead: Card, trump: Suit?): Boolean {
        if (trump != null && card.suit == trump && lead.suit != trump) return true
        if (card.suit != lead.suit) return false
        return card.weight > lead.weight
    }

    // --- Оценка руки ------------------------------------------------------

    /**
     * Сколько очков бот рассчитывает собрать этой рукой.
     *
     * Считаем не по старшинству, а по очкам: сумма карт, полная стоимость
     * марьяжей (они записываются, даже если карту побьют) и надбавка за
     * тузов с десятками — это те карты, которые взятку берут чаще прочих.
     */
    fun handPower(hand: List<Card>): Int {
        var power = hand.sumOf { it.points }
        Suit.entries.forEach { suit ->
            if (hand.hasMarriage(suit)) power += marriagePoints(suit)
        }
        power += hand.count { it.rank == Rank.ACE } * 6
        power += hand.count { it.rank == Rank.TEN } * 4
        return power
    }

    /** Чего стоит расстаться с картой: и на снос, и на сброс в взятке. */
    private fun keepingValue(card: Card, hand: List<Card>): Int {
        val inMarriage = (card.rank == Rank.KING || card.rank == Rank.QUEEN) && hand.hasMarriage(card.suit)
        return card.points * 3 + card.weight + if (inMarriage) 50 else 0
    }

    /** Чего стоит потратить карту на взятку: козырь придерживаем. */
    private fun cardCost(card: Card, trump: Suit?): Int =
        card.points * 3 + card.weight + if (card.suit == trump) 40 else 0
}
