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
 *
 * Уровней три, и различаются они не силой карт, а памятью. «Новичок» ходит
 * наугад. «Обычный» смотрит только на свою руку: берегут марьяж, сносят
 * дешёвое, бьют самой дешёвой подходящей картой. «Хитрый» держит в голове
 * весь стол — что вышло и что ещё нет, — и по этому считает, побьют ли его
 * карту.
 *
 * На торге разница глубже: обычный складывает очки на руке, а хитрый
 * раскладывает невидимое наугад и доигрывает кон — сто раз подряд, — и
 * называет по среднему, а не по сумме (см. [ThousandSimulator]). Сумма
 * карт считает очки, но не считает, что половину их придётся отдать
 * сопернику.
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

        // Новичок играет картами, а не сдаётся: роспись — решение на счёт
        // партии, и случайным ходом её выбирать нечего.
        if (difficulty == Difficulty.NOVICE) {
            val cards = moves.filterNot { it == ThousandMove.Raspis }
            val pool = cards.ifEmpty { moves }
            return pool[random.nextInt(pool.size)]
        }

        val memory = difficulty == Difficulty.CLEVER

        return when (round.phase) {
            Phase.BIDDING -> bid(round, seat, moves, difficulty, random)
            Phase.PRIKUP -> pickPrikup(moves, random)
            Phase.DISCARD -> discard(round, seat, moves)
            Phase.PLAY -> play(round, seat, moves, memory)
            Phase.OVER -> null
        }
    }

    // --- Память -----------------------------------------------------------

    /**
     * Что бот видел за столом, кроме собственной руки.
     *
     * Хранить это отдельно не нужно: кон и так помнит все разыгранные взятки
     * ([ThousandRound.tricksPlayed]) и карты на столе, а больше ничего и не
     * надо — остальное даёт полная колода. «Помнит» здесь значит «считает
     * заново на каждом ходу»: у бота нет своей жизни между ходами, и любая
     * его память всё равно жила бы в коне.
     *
     * В [unseen] попадает и то, чего не видел никто: карты соперника и второй
     * прикуп, который в розыгрыш не идёт. Для решения это одно и то же —
     * «может оказаться у соперника», — и считать невидимое его картами
     * безопасно: своей силы бот не переоценит. А вот подсматривать в прикупы
     * нельзя, и [Seen] их не читает: взять прикуп можно, смотреть в него —
     * нет.
     */
    internal class Seen(round: ThousandRound, seat: Int) {
        private val own: List<Card> = round.handOf(seat)
        private val played: List<Card> =
            round.tricksPlayed().flatMap { it.cards } + round.tableNow()

        /** Карты, которых бот не видел: рука соперника и то, что вне игры. */
        val unseen: List<Card> = fullDeck24().filterNot { it in own || it in played }

        /** Сколько карт масти бот ещё не видел. */
        fun left(suit: Suit): Int = unseen.count { it.suit == suit }

        /**
         * Никто не побьёт [card]: ни старшей карты той же масти, ни козыря
         * у соперника не осталось.
         */
        fun holds(card: Card, trump: Suit?): Boolean = unseen.none { other ->
            when {
                other.suit == card.suit -> other.weight > card.weight
                trump != null && other.suit == trump -> card.suit != trump
                else -> false
            }
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
     *
     * Себя бот не перебивает не по своей воле: движок просто не даёт назвать
     * сумму не выше текущей ([ThousandRound.legalMoves]).
     */
    private fun bid(
        round: ThousandRound,
        seat: Int,
        moves: List<ThousandMove>,
        difficulty: Difficulty,
        random: Random,
    ): ThousandMove {
        val bids = moves.filterIsInstance<ThousandMove.Bid>()
        if (bids.isEmpty()) return ThousandMove.Pass

        val margin = if (difficulty == Difficulty.CLEVER) 10 else 25
        // Обычный верит сумме карт на руке, хитрый — перебору: тот же вопрос,
        // но с оговоркой, что часть очков достанется сопернику. На слабой
        // руке разница невелика, а на сильной перебор трезвее: сорок очков
        // в марьяже уже записаны, а взяток под них может и не найтись.
        val power = if (difficulty == Difficulty.CLEVER) {
            ThousandSimulator.contract(round, seat, deals = BID_DEALS, random = random).mine
        } else {
            handPower(round.handOf(seat))
        }
        val affordable = bids.filter { it.amount + margin <= power }.maxByOrNull { it.amount }
        if (affordable != null) return affordable

        // Первое слово в торге пасовать нечем — назвать сто всё равно придётся.
        return if (moves.contains(ThousandMove.Pass)) ThousandMove.Pass else bids.first()
    }

    // --- Прикуп -----------------------------------------------------------

    /**
     * Берём прикуп, не глядя в него, — как берёт игрок.
     *
     * Прикуп закрыт для всех: заказчик выбирает из двух пар, не видя ни одной,
     * и только потом взятую пару открывают обоим. Бот не исключение. Смотреть
     * в прикуп ему нельзя — это игра краплёной колодой, и заметить её со
     * стороны нельзя: бот просто «чаще угадывает».
     *
     * Выбирать тут нечего: обе пары для бота — одни и те же невидимые карты,
     * и никакая память о вышедшем этого не меняет. Поэтому выбор случаен, и
     * уровнем сложности он не лечится: «хитрый» просто знает об этом больше,
     * а не выбирает лучше.
     */
    private fun pickPrikup(moves: List<ThousandMove>, random: Random): ThousandMove {
        val takes = moves.filterIsInstance<ThousandMove.TakePrikups>()
        return takes.random(random)
    }

    // --- Снос -------------------------------------------------------------

    /**
     * Сносим самое дешёвое: девятки и валеты — в первую очередь, карты из
     * марьяжа — никогда. Снесённая карта уходит сопернику вместе со своими
     * очками, поэтому отдавать десятку или туза — подарок.
     */
    private fun discard(round: ThousandRound, seat: Int, moves: List<ThousandMove>): ThousandMove {
        val hand = round.handOf(seat)
        val trump = round.trumpSuit
        val discards = moves.filterIsInstance<ThousandMove.Discard>()
        return discards.minByOrNull { option ->
            option.cards.sumOf { keepingValue(it, hand, trump) }
        } ?: discards.first()
    }

    // --- Розыгрыш ---------------------------------------------------------

    private fun play(
        round: ThousandRound,
        seat: Int,
        moves: List<ThousandMove>,
        memory: Boolean,
    ): ThousandMove {
        // Роспись — до всего остального: если заказ уже не набрать, ходить
        // картой поздно, а расписаться дешевле, чем сесть.
        if (moves.contains(ThousandMove.Raspis) && shouldRaspis(round, seat)) return ThousandMove.Raspis

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

        val seen = if (memory) Seen(round, seat) else null
        val lead = round.tableNow().firstOrNull()
            ?: return leading(plays, trump, seen)
        return answering(round, seat, plays, lead, trump, seen)
    }

    /**
     * Пора ли расписываться.
     *
     * Роспись — признание, что заказанного не набрать: заказчик пишет заказ
     * целиком, соперники — по половине заказа. Это мягче, чем сесть: сев,
     * заказчик пишет тот же минус, но соперники берут свои очки целиком,
     * а они бывают куда больше половины заказа.
     *
     * Считаем по самой щедрой оценке — очки за столом, вся рука и ещё не
     * объявленные марьяжи. Если и это не дотягивает до заказа, надежды нет:
     * часть взяток наверняка возьмут соперники, значит, и подавно не хватит.
     */
    private fun shouldRaspis(round: ThousandRound, seat: Int): Boolean {
        val hand = round.handOf(seat)
        var ceiling = round.roundPoints(seat) + hand.sumOf { it.points }
        Suit.entries.forEach { suit ->
            if (hand.hasMarriage(suit)) ceiling += marriagePoints(suit)
        }
        return ceiling < round.currentBid
    }

    /**
     * Веду взятку.
     *
     * Хитрый первым делом ищет верную взятку: карту, которую уже некому
     * побить, — и берёт из них самую дорогую. Всё остальное уходит на заход
     * с младшей карты длинной масти: соперник обязан отвечать в масть, и
     * такая мелочь вытягивает из него старшие карты, пока свои ещё целы.
     * Козырь на заход не идёт: он припасён на то, чтобы крыть.
     */
    private fun leading(
        plays: List<ThousandMove.Play>,
        trump: Suit?,
        seen: Seen?,
    ): ThousandMove {
        if (seen != null) {
            val sure = plays.filter { it.card.suit != trump && seen.holds(it.card, trump) }
            sure.maxByOrNull { it.card.points }?.let { return it }
        }

        val plain = plays.filter { it.card.suit != trump }
        val pool = plain.ifEmpty { plays }
        return pool.minByOrNull { it.card.weight * 100 + it.card.points } ?: plays.first()
    }

    /**
     * Отвечаю на чужую карту. Есть чем бить — бью самым дешёвым: козырь
     * дороже, поэтому он идёт в ход последним. Хитрый при этом смотрит, не
     * перебьёт ли его кто-то ещё, и из подходящих выбирает ту, что устоит.
     * Нечем — сбрасываю мелочь, чтобы не отдать сопернику лишних очков.
     */
    private fun answering(
        round: ThousandRound,
        seat: Int,
        plays: List<ThousandMove.Play>,
        lead: Card,
        trump: Suit?,
        seen: Seen?,
    ): ThousandMove {
        val beating = plays.filter { beats(it.card, lead, trump) }
        if (beating.isNotEmpty()) {
            if (seen != null) {
                val sure = beating.filter { seen.holds(it.card, trump) }
                (sure.ifEmpty { beating }).minByOrNull { cardCost(it.card, trump) }?.let { return it }
            }
            return beating.minByOrNull { cardCost(it.card, trump) } ?: beating.first()
        }
        val hand = round.handOf(seat)
        return plays.minByOrNull { keepingValue(it.card, hand, trump) } ?: plays.first()
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
     * марьяжей (они записываются, даже если карту побьют), надбавка за
     * тузов с десятками — это те карты, которые взятку берут чаще прочих, —
     * и надбавка за длинную масть: с четырёх карт масть уже можно тянуть.
     */
    fun handPower(hand: List<Card>): Int {
        var power = hand.sumOf { it.points }
        Suit.entries.forEach { suit ->
            if (hand.hasMarriage(suit)) power += marriagePoints(suit)
            if (hand.count { it.suit == suit } >= LONG_SUIT) power += LONG_SUIT_BONUS
        }
        power += hand.count { it.rank == Rank.ACE } * 6
        power += hand.count { it.rank == Rank.TEN } * 4
        return power
    }

    /** Чего стоит расстаться с картой: и на снос, и на сброс в взятке. */
    private fun keepingValue(card: Card, hand: List<Card>, trump: Suit?): Int {
        val inMarriage = (card.rank == Rank.KING || card.rank == Rank.QUEEN) && hand.hasMarriage(card.suit)
        // Карту из длинной масти берегут: длинная масть — это взятки, а
        // короткая — то, чем платят за снос. Козырь берегут особо: им кроют.
        val length = hand.count { it.suit == card.suit }
        return card.points * 3 + card.weight +
            if (inMarriage) 50 else 0 +
            length * 4 +
            if (card.suit == trump) 20 else 0
    }

    /** Чего стоит потратить карту на взятку: козырь придерживаем. */
    private fun cardCost(card: Card, trump: Suit?): Int =
        card.points * 3 + card.weight + if (card.suit == trump) 40 else 0

    /**
     * Сколько раскладов разыгрывает хитрый на торге.
     *
     * Не двести, как в симуляторе по умолчанию: заказ называется с шагом в
     * пять, и лишняя сотня раскладов уточняет среднее на один-два очка —
     * точность, которой шаг не стоит. Торг ждать не должен.
     */
    private const val BID_DEALS = 100

    /** С какого числа карт масть считается длинной. */
    private const val LONG_SUIT = 4

    /** Сколько очков она за это добавляет. */
    private const val LONG_SUIT_BONUS = 10
}
