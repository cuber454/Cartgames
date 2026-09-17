package games.engine.thousand

import games.engine.Card
import games.engine.Suit
import kotlin.random.Random

/**
 * Симулятор кона: раздать невидимое наугад и посмотреть, что выйдет.
 *
 * Бот, как и человек, не знает двух вещей: что у соперника на руке и что
 * лежит в прикупе, который в розыгрыш не пошёл. Зато всё остальное он знает
 * точно — свою руку, что вышло, сколько карт у каждого. Симулятор берёт
 * невидимое и раскладывает его по чужим рукам наугад — сотню раз по-разному, —
 * каждый раз доигрывает кон простой жадной политикой и усредняет результат.
 *
 * Это не гадание, а честная оценка «сколько я возьму в среднем». Именно она
 * отличает «у меня красивая рука» от «у меня сто десять очков»: сумма карт
 * на руке считает очки, но не считает, что половину из них придётся отдать
 * сопернику, потому что ходить ими нечем.
 *
 * Никакой закрытой информации симулятор не читает: соперникам достаётся
 * только то, чего не видел никто, а прикуп, в который не смотрели, — такой
 * же невидимый, как чужая рука.
 */
internal object ThousandSimulator {

    /** Сколько раскладов разыгрываем, когда не сказано иного. */
    const val DEFAULT_DEALS = 200

    /** Что вышло из симуляции. */
    data class Outcome(
        /** Сколько очков [seat] возьмёт в среднем: взятки и марьяжи. */
        val mine: Int,
        /** Сколько возьмёт заказчик в среднем. */
        val declarer: Int,
        /** Худший расклад: меньше этого не выйдет почти никогда. */
        val worst: Int,
    )

    /**
     * Доиграть кон с текущего места: сколько возьмёт [seat] и сколько —
     * заказчик.
     *
     * Годится и посреди розыгрыша, и на пустом столе: симулятор доигрывает
     * то, что осталось, начиная с того, кто ходит.
     */
    fun take(
        round: ThousandRound,
        seat: Int,
        deals: Int = DEFAULT_DEALS,
        random: Random = Random.Default,
    ): Outcome {
        val playerCount = round.playerCount
        val declarer = round.declarer ?: seat
        val trump = round.trumpSuit
        val mine = IntArray(deals)
        val taken = IntArray(deals)
        val worst = IntArray(deals)

        repeat(deals) { deal ->
            val spread = spread(round, seat, random)
            val points = IntArray(playerCount)
            playOut(spread.hands, round.tableSeats(), trump, round.turn, points)
            mine[deal] = points[seat]
            taken[deal] = points[declarer]
            worst[deal] = points[seat]
        }

        // Марьяж записывают объявлением, и объявленный он и так в счёте:
        // прибавляем только тот, что ещё лежит на руке, и прибавляем целиком —
        // марьяж не отбирают, даже если карту побьют.
        val marriages = marriagesOf(round.handOf(seat))

        return Outcome(
            mine = mine.average().toInt() + marriages,
            declarer = taken.average().toInt(),
            worst = (worst.minOrNull() ?: 0) + marriages,
        )
    }

    /**
     * Сколько кон принесёт, если играть его самому: с прикупом, сносом и
     * розыгрышем.
     *
     * Это ответ на вопрос торга — «а стоит ли называть». Козыря ещё нет,
     * прикуп бот не видел, и то и другое ему только предстоит получить:
     * прикуп вытягивается наугад из того же невидимого, козырем становится
     * старший марьяж, какой окажется на руке после сноса. Прикуп заказчик
     * берёт лучший — как взял бы за столом, — а сносит самое дешёвое.
     *
     * [Outcome.declarer] здесь то же, что [Outcome.mine]: заказчик — сам бот.
     */
    fun contract(
        round: ThousandRound,
        seat: Int,
        deals: Int = DEFAULT_DEALS,
        random: Random = Random.Default,
    ): Outcome {
        val playerCount = round.playerCount
        // Форма прикупа — правило, а не тайна: вдвоём два прикупа по две
        // карты, втроём один из трёх (см. `prikupShape` в [ThousandRound]).
        val prikupSize = if (playerCount == 2) 2 else 3
        val prikupCount = if (playerCount == 2) 2 else 1
        val discardCount = playerCount - 1

        val mine = IntArray(deals)
        val worst = IntArray(deals)

        repeat(deals) { deal ->
            val spread = spread(round, seat, random)
            val hand = spread.hands[seat].toMutableList()

            // Прикуп: заказчик выбирает лучший из прикупов, но пока не видел
            // ни одного — для него это те же невидимые карты.
            spread.left.shuffled(random).chunked(prikupSize).take(prikupCount)
                .maxByOrNull { ThousandBot.handPower(it) }
                ?.let { hand += it }

            // Снос: по одной карте каждому сопернику. Отдаём ту, которой жаль
            // меньше всего, — снесённая уходит врагу вместе со своими очками.
            val trumpGuess = hand.bestMarriageSuit()
            hand.sortedBy { it.keepingCost(trumpGuess) }.take(discardCount)
                .forEachIndexed { index, card ->
                    hand.remove(card)
                    spread.hands[(seat + index + 1) % playerCount].add(card)
                }

            // Марьяжи считаем до розыгрыша: их записывают целиком, а рука в
            // симуляции расходуется карта за картой.
            val marriages = marriagesOf(hand)
            val points = IntArray(playerCount)
            val trump = hand.bestMarriageSuit()
            playOut(spread.hands, emptyList(), trump, seat, points) { spread.hands[seat] = hand }

            mine[deal] = points[seat] + marriages
            worst[deal] = points[seat] + marriages
        }

        return Outcome(
            mine = mine.average().toInt(),
            declarer = mine.average().toInt(),
            worst = worst.minOrNull() ?: 0,
        )
    }

    // --- Раздача невидимого -------------------------------------------------

    /**
     * Что вышло из раздачи невидимого: руки и то, что осталось в стороне.
     *
     * Руки изменяемые: симуляция и разыгрывает карты, и подкладывает их
     * сопернику — той же рукой, которую раздала.
     */
    private class Spread(val hands: MutableList<MutableList<Card>>, val left: List<Card>)

    /**
     * Разложить невидимое по чужим рукам.
     *
     * Невидимое — всё, чего бот не видел: чужие карты, прикуп и то, что в
     * розыгрыш вовсе не пойдёт. Раскладываем наугад; сколько кому — знают не
     * глаза бота, а правила: руки у всех равны, а что осталось сверх того,
     * лежит в стороне.
     */
    private fun spread(round: ThousandRound, seat: Int, random: Random): Spread {
        val playerCount = round.playerCount
        val own = round.handOf(seat)
        val seen = round.tricksPlayed().flatMap { it.cards } + round.tableNow()
        val pool = fullDeck24().filterNot { it in own || it in seen }.shuffled(random)

        val hands = MutableList(playerCount) { mutableListOf<Card>() }
        hands[seat] = own.toMutableList()
        var index = 0
        for (other in 0 until playerCount) {
            if (other == seat) continue
            val size = round.handOf(other).size
            hands[other] = pool.subList(index, index + size).toMutableList()
            index += size
        }
        check(index <= pool.size) { "невидимого не хватает на чужие руки" }
        return Spread(hands, pool.drop(index))
    }

    // --- Розыгрыш -----------------------------------------------------------

    /**
     * Доиграть кон в открытую, жадно.
     *
     * Жадность здесь — мера, а не глупость: в среднем по сотне раскладов
     * любая разумная политика даёт сопоставимую оценку, зато честная
     * жадность не приписывает боту знаний, которых у него за столом не
     * будет.
     *
     * [before] даёт подменить руку перед самым розыгрышем: на этом стоит
     * оценка торга, где рука собирается из прикупа и сноса.
     */
    private fun playOut(
        hands: List<MutableList<Card>>,
        table: List<Pair<Int, Card>>,
        trump: Suit?,
        turn: Int,
        taken: IntArray,
        before: () -> Unit = {},
    ) {
        before()
        val playerCount = hands.size
        val trick = table.toMutableList()
        var seat = turn
        var lead: Suit? = table.firstOrNull()?.second?.suit
        var guard = 0

        while (true) {
            check(guard++ < 1000) { "симуляция не кончается" }

            // Взятка собрана, когда положили все, у кого есть карты: кто уже
            // сыграл в неё, тот в счёте, даже если рука у него опустела.
            val active = hands.indices.filter { hands[it].isNotEmpty() || trick.any { p -> p.first == it } }
            if (active.isEmpty()) return

            if (trick.size == active.size) {
                val winner = settle(trick, trump, lead, taken)
                trick.clear()
                lead = null
                if (hands.all { it.isEmpty() }) return
                seat = if (hands[winner].isNotEmpty()) winner else hands.indexOfFirst { it.isNotEmpty() }
                continue
            }

            if (hands[seat].isEmpty()) {
                seat = (seat + 1) % playerCount
                continue
            }

            val card = choose(hands[seat], trick.firstOrNull()?.second, trump)
            hands[seat].remove(card)
            trick += seat to card
            if (lead == null) lead = card.suit
            seat = (seat + 1) % playerCount
        }
    }

    /** Взятка разошлась: считаем очки и отдаём ход победителю. */
    private fun settle(
        trick: MutableList<Pair<Int, Card>>,
        trump: Suit?,
        lead: Suit?,
        taken: IntArray,
    ): Int {
        val suit = lead ?: trick.first().second.suit
        val best = trick.maxWith { a, b -> weight(a.second, suit, trump).compareTo(weight(b.second, suit, trump)) }
        taken[best.first] += trick.sumOf { it.second.points }
        return best.first
    }

    /**
     * Чем ходить в симуляции.
     *
     * Ведущий идёт с младшей карты некозырной масти: мелочь вытягивает чужие
     * старшие карты. Отвечающий бьёт самой дешёвой из бьющих карт; нечем —
     * сбрасывает мелочь. Обязанности те же, что за столом: есть масть
     * захода — ходи ею, нет — крывай козырем.
     */
    private fun choose(hand: List<Card>, lead: Card?, trump: Suit?): Card {
        val following: List<Card> = if (lead == null) {
            hand.filter { it.suit != trump }.ifEmpty { hand }
        } else {
            val inSuit = hand.filter { it.suit == lead.suit }
            when {
                inSuit.isEmpty() -> hand.filter { it.suit == trump }.ifEmpty { hand }
                else -> inSuit.filter { it.weight > lead.weight }.ifEmpty { inSuit }
            }
        }
        // Из подходящих берём самую дешёвую: десятка и туз — очки, ими не
        // сорят, а козырь придерживают до того, как им придётся крыть.
        return following.minByOrNull { it.points * 3 + it.weight } ?: hand.first()
    }

    /** Вес карты в взятке: козырь выше всего, чужая масть не берёт. */
    private fun weight(card: Card, lead: Suit, trump: Suit?): Int = when {
        trump != null && card.suit == trump -> TRUMP_BASE + card.weight
        card.suit == lead -> card.weight
        else -> -1
    }

    // --- Мелочи очков -------------------------------------------------------

    /** Необъявленные марьяжи на руке: их записывают целиком, без розыгрыша. */
    private fun marriagesOf(hand: List<Card>): Int =
        Suit.entries.filter { hand.hasMarriage(it) }.sumOf { marriagePoints(it) }

    /**
     * Масть, которую бот объявит козырем. Козырь — масть марьяжа, и берут
     * его у старшего: он и очков приносит больше.
     */
    private fun List<Card>.bestMarriageSuit(): Suit? =
        Suit.entries.filter { hasMarriage(it) }.maxByOrNull { marriagePoints(it) }

    /** Чего стоит расстаться с картой: очки, старшинство и козырь. */
    private fun Card.keepingCost(trump: Suit?): Int =
        points * 3 + weight + if (suit == trump) 20 else 0

    private const val TRUMP_BASE = 100
}
