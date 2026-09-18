package games.engine.thousand

import games.engine.Card
import games.engine.Deck
import games.engine.GameRules
import games.engine.Move
import games.engine.Rank
import games.engine.Suit
import kotlin.random.Random

/**
 * «Тысяча» — взятки, торг и марьяжи, 24 карты.
 *
 * Здесь только кон: раздача, торг, прикуп, снос, розыгрыш и счёт за кон.
 * Очки к тысяче, болты и бочка — в [ThousandMatch]: кон кончается, а партия
 * идёт дальше.
 *
 * Играется вдвоём (игрок и бот) или втроём; разница — в раздаче и прикупе
 * (см. `THOUSAND.md`, 2.11), всё остальное общее.
 */

/** Достоинства «Тысячи»: младше девятки в колоде ничего нет. */
val THOUSAND_RANKS: List<Rank> =
    listOf(Rank.NINE, Rank.JACK, Rank.QUEEN, Rank.KING, Rank.TEN, Rank.ACE)

/** Колода «Тысячи» — 24 карты. */
fun fullDeck24(): MutableList<Card> =
    Suit.entries.flatMap { suit -> THOUSAND_RANKS.map { rank -> Card(rank, suit) } }.toMutableList()

/**
 * Старшинство в «Тысяче» — **десятка выше короля**, и это первое, обо что
 * спотыкается тот, кто пришёл из «Дурака»: там порядок обычный, 6…10-В-Д-К-А.
 * Поэтому старшинство здесь своё, а не `Rank.value`.
 */
val Card.weight: Int
    get() = when (rank) {
        Rank.NINE -> 0
        Rank.JACK -> 1
        Rank.QUEEN -> 2
        Rank.KING -> 3
        Rank.TEN -> 4
        Rank.ACE -> 5
        else -> error("карты «$rank» в «Тысяче» нет")
    }

/** Очки за карту. Вся колода — 120, одна масть — 30. */
val Card.points: Int
    get() = when (rank) {
        Rank.NINE -> 0
        Rank.JACK -> 2
        Rank.QUEEN -> 3
        Rank.KING -> 4
        Rank.TEN -> 10
        Rank.ACE -> 11
        else -> error("карты «$rank» в «Тысяче» нет")
    }

/** Стоимость марьяжа. Черви — сотня, поэтому их и берегут. */
fun marriagePoints(suit: Suit): Int = when (suit) {
    Suit.SPADES -> 40
    Suit.CLUBS -> 60
    Suit.DIAMONDS -> 80
    Suit.HEARTS -> 100
}

/** Сколько тузов в колоде. Их все на одной руке — тузовый марьяж. */
const val ACE_COUNT = 4

/** Король и дама одной масти на руках — то есть марьяж можно объявить. */
fun List<Card>.hasMarriage(suit: Suit): Boolean =
    any { it.rank == Rank.KING && it.suit == suit } && any { it.rank == Rank.QUEEN && it.suit == suit }

/** Фаза кона. Порядок — как их видит игрок. */
enum class Phase { BIDDING, PRIKUP, DISCARD, PLAY, OVER }

/** Ходы в «Тысяче». */
sealed interface ThousandMove : Move {
    /** Назвать сумму. Шаг — 5, потолок — 120. */
    data class Bid(val amount: Int) : ThousandMove

    /** «Пас» — выход из торговли. */
    data object Pass : ThousandMove

    /** Какой прикуп взять. При игре вдвоём прикупов два и это выбор. */
    data class TakePrikups(val index: Int) : ThousandMove

    /** Снос: по одной карте каждому сопернику, в закрытую. */
    data class Discard(val cards: List<Card>) : ThousandMove

    /**
     * «Хвалю» — объявление марьяжа. Это и есть ход: карта пары уходит на
     * стол, а её масть становится козырной. Объявлять можно со второй
     * взятки и позже — пока не взял ни одной, хвалить нечего.
     */
    data class Praise(val card: Card) : ThousandMove

    /**
     * Роспись — заказчик признаёт, что заказанного не набрать, и отказывается
     * от игры: карты не доигрываются, с себя он списывает весь заказ,
     * соперникам идёт по половине заказа (см. `THOUSAND.md`, 2.13).
     *
     * Ход доступен только заказчику и только на своём ходу в розыгрыше:
     * роспись — отказ от собственной игры, а не ход в чужой.
     */
    data object Raspis : ThousandMove

    /**
     * Золотой кон — договорённость сторон: кон играется **без торга**, сразу
     * на весь заказ, и очки за него двойные (см. `THOUSAND.md`, 2.14).
     *
     * Объявить его можно вместо первой ставки и только с рукой, которая
     * заказанное уже держит: золотой кон — не ставка вслепую, а признание
     * того, что карты пришли сами. Прикуп при нём не берут вовсе — вся
     * прелесть в том, что и без прикупа есть чем играть.
     */
    data object Golden : ThousandMove

    /** Положить карту в взятку. */
    data class Play(val card: Card) : ThousandMove
}

/** Взятка: кто сколько положил и чья взяла. */
data class Trick(val cards: List<Card>, val winner: Int, val points: Int)

class ThousandRound private constructor(
    private val hands: MutableList<MutableList<Card>>,
    private val prikups: List<List<Card>>,
    private val firstBidder: Int,
    private var turnSeat: Int,
    /**
     * Кто сидит на бочке. Бочка — единственный, кому за кон не пишутся очки,
     * пока он не выполнит обязательный заказ ([barrelBid]): поэтому о бочке
     * знает и сам кон — она меняет правила торга. Матч передаёт сюда того,
     * кто до неё дошёл (см. [ThousandMatch]).
     */
    private val barrelSeat: Int? = null,
    /**
     * Что бочка обязана заказать. Втроём это потолок в 120; вдвоём — сто,
     * потому что в игре на двоих 120 очков не набрать: две карты второго
     * прикупа в розыгрыше не участвуют.
     */
    private val barrelBid: Int = MAX_BID,
    /**
     * Можно ли в этом коне объявить золотой кон — договорённость сторон.
     * Кон о ней только спрашивает; решает матч, он же и держит флаг
     * ([ThousandRules.golden]).
     */
    private val goldenAllowed: Boolean = false,
) : GameRules<ThousandRound> {

    val playerCount: Int get() = hands.size

    var phase: Phase = Phase.BIDDING
        private set

    /** Сколько карт сносит заказчик: по одной каждому сопернику. */
    val discardCount: Int get() = playerCount - 1

    /** Текущая ставка на торге. 0 — ещё никто не называл. */
    var currentBid: Int = 0
        private set

    /** Кто выиграл торг. До конца торга — null. */
    var declarer: Int? = null
        private set

    /** Козырь — масть последнего объявленного марьяжа. До первого — null. */
    var trumpSuit: Suit? = null
        private set

    /** Кто расписался. Расписаться может только заказчик, поэтому тут либо он, либо null. */
    var raspised: Int? = null
        private set

    /**
     * Кон объявлен золотым: торга не было, прикуп не брали, очки двойные.
     *
     * Помнит это кон, а не матч: поднятая с диска партия должна доигрываться
     * так же, как шла, — и не только в счёте, но и в том, сколько карт у
     * заказчика на руке.
     */
    var golden: Boolean = false
        private set

    private val passed = BooleanArray(playerCount)

    /** Кто уже называл сумму: бочке важно — она обязана назвать свою. */
    private val namedBid = BooleanArray(playerCount)
    private val trickPoints = IntArray(playerCount)
    private val marriage = IntArray(playerCount)
    private val tricks = IntArray(playerCount)

    /**
     * У кого на руке все четыре туза к началу розыгрыша — тузовый марьяж.
     *
     * Считается один раз, когда снос лёг и карты больше не меняются: после
     * этого тузы уходят во взятки по одному, и «на руке» уже не проверить.
     * Договорённость это или нет, решает матч — кон только помнит, у кого
     * тузы были (см. [ThousandMatch]).
     */
    private val allAces = BooleanArray(playerCount)
    private val currentTrick = mutableListOf<Pair<Int, Card>>()
    private val playedTricks = mutableListOf<Trick>()

    /** Взятки: сколько очков взял каждый. */
    fun trickPointsOf(seat: Int): Int = trickPoints[seat]

    /** Марьяжи: сколько очков объявил каждый. */
    fun marriagePointsOf(seat: Int): Int = marriage[seat]

    fun tricksOf(seat: Int): Int = tricks[seat]

    /** Были ли у места все четыре туза к началу розыгрыша. */
    fun hadAllAces(seat: Int): Boolean = allAces[seat]

    /**
     * Сколько стоит рука до торга: очки карт и марьяжи, которые на ней уже
     * собраны.
     *
     * Не то же самое, что [roundPoints]: тот считает взятое и объявленное, а
     * тут — то, что видно в руке. Нужно ровно для одного: золотой кон
     * объявляют с рукой, которая заказ держит, а не с надеждой его взять.
     */
    fun handValue(seat: Int): Int =
        hands[seat].sumOf { it.points } +
            Suit.entries.filter { hands[seat].hasMarriage(it) }.sumOf { marriagePoints(it) }

    fun handOf(seat: Int): List<Card> = hands[seat].toList()

    /** Карты прикупа: у заказчика они уже в руке, остальным — на посмотреть. */
    fun prikup(index: Int): List<Card> = prikups[index]

    val prikupCount: Int get() = prikups.size

    fun tricksPlayed(): List<Trick> = playedTricks.toList()

    /** Кто ходит сейчас. */
    val turn: Int get() = turnSeat

    // --- Для сохранения на диск -------------------------------------------
    //
    // Торг помнит не только ставку: кто уже пасовал и кто называл сумму, —
    // иначе поднятая с диска партия начнёт торг заново с теми же голосами.

    /** Кто открывал торг в этом коне. */
    fun firstBidderSeat(): Int = firstBidder

    fun passedSeats(): List<Int> = passed.indices.filter { passed[it] }

    fun namedSeats(): List<Int> = namedBid.indices.filter { namedBid[it] }

    /** Карты на столе вместе с местами: порядок захода тоже надо помнить. */
    fun tableSeats(): List<Pair<Int, Card>> = currentTrick.toList()

    fun trickPointsAll(): List<Int> = trickPoints.toList()

    fun marriageAll(): List<Int> = marriage.toList()

    fun tricksAll(): List<Int> = tricks.toList()

    /** Разыгранные взятки по порядку — для озвучки и разбора. */
    fun tableNow(): List<Card> = currentTrick.map { it.second }

    // --- Ходы -------------------------------------------------------------

    fun legalMoves(seat: Int): List<ThousandMove> {
        if (phase == Phase.OVER || seat !in hands.indices || seat != turnSeat) return emptyList()
        val hand = hands[seat]

        return when (phase) {
            // Торг: первое слово — всегда ровно сто, потолок — 120.
            // Бочке пасовать нечем: она обязана назвать свой заказ, пока
            // может его назвать. Не может — только пас, и попытка сгорела.
            // Обязательство это ровно про потолок: назвав его, бочка уже
            // никем не перебивается. Там, где потолок недостижим (вдвоём
            // сорока очков не хватает до всей колоды), обязательства нет —
            // бочка торгуется как все, а попытка засчитывается по факту.
            Phase.BIDDING -> buildList {
                val mustRaise = seat == barrelSeat && !namedBid[seat] && barrelBid == MAX_BID
                val floor = if (mustRaise) {
                    maxOf(currentBid + BID_STEP, barrelBid)
                } else {
                    maxOf(currentBid + BID_STEP, MIN_BID)
                }
                if (currentBid == 0) {
                    // Золотой кон — вместо первой ставки, а не после неё:
                    // он и значит «без торга». Рука при этом должна держать
                    // заказ уже сейчас — иначе это не золотой кон, а блеф,
                    // которого правила не знают.
                    if (goldenAllowed && handValue(seat) >= GOLDEN_BID) add(ThousandMove.Golden)
                    add(ThousandMove.Bid(floor))
                } else {
                    var next = floor
                    while (next <= MAX_BID) {
                        add(ThousandMove.Bid(next))
                        next += BID_STEP
                    }
                    if (!mustRaise || floor > MAX_BID) add(ThousandMove.Pass)
                }
            }

            // Прикуп: один — берём его, два — выбираем.
            Phase.PRIKUP -> prikups.indices.map { ThousandMove.TakePrikups(it) }

            // Снос: ровно по одной карте каждому сопернику.
            Phase.DISCARD -> combinations(hand, discardCount).map { ThousandMove.Discard(it) }

            Phase.PLAY -> buildList {
                addAll(playableCards(seat).map { ThousandMove.Play(it) })
                addAll(praisableCards(seat).map { ThousandMove.Praise(it) })
                // Расписаться может заказчик и только на своём ходу. Взяток
                // к этому моменту может быть сколько угодно: роспись — это
                // «дальше не наберу», а не «ещё ничего не взял».
                if (seat == declarer) add(ThousandMove.Raspis)
            }

            Phase.OVER -> emptyList()
        }
    }

    /**
     * Чем можно сыграть: есть масть захода — только она; нет — обязан
     * крыть козырем, если козырь объявлен и он есть; нет ни того ни другого —
     * любая карта.
     */
    private fun playableCards(seat: Int): List<Card> {
        val hand = hands[seat]
        val lead = currentTrick.firstOrNull()?.second ?: return hand
        val trump = trumpSuit
        val inSuit = hand.filter { it.suit == lead.suit }
        if (inSuit.isNotEmpty()) return inSuit
        val trumps = if (trump != null) hand.filter { it.suit == trump } else emptyList()
        return trumps.ifEmpty { hand }
    }

    /**
     * Что можно похвалить. Хвалить можно со второй взятки: марьяж объявляют
     * уже за столом, а не на пустой стол. Хвалить можно только той картой,
     * которой сейчас вообще можно ходить, — объявление и есть ход.
     */
    private fun praisableCards(seat: Int): List<Card> {
        if (tricks[seat] == 0) return emptyList()
        val playable = playableCards(seat)
        return playable.filter { card ->
            (card.rank == Rank.KING || card.rank == Rank.QUEEN) && hands[seat].hasMarriage(card.suit)
        }
    }

    fun apply(seat: Int, move: ThousandMove) {
        check(phase != Phase.OVER) { "кон окончен" }
        check(move in legalMoves(seat)) { "недопустимый ход: $move" }

        when (move) {
            is ThousandMove.Bid -> {
                currentBid = move.amount
                namedBid[seat] = true
                turnSeat = next(seat)
                endBiddingIfOver()
            }

            ThousandMove.Pass -> {
                passed[seat] = true
                turnSeat = next(seat)
                endBiddingIfOver()
            }

            is ThousandMove.TakePrikups -> {
                val prikup = prikups[move.index]
                hands[seat] += prikup
                phase = Phase.DISCARD
            }

            is ThousandMove.Discard -> {
                val receiver = partiesTo(seat, move.cards.size)
                move.cards.forEachIndexed { index, card ->
                    hands[seat].remove(card)
                    hands[receiver[index]] += card
                }
                phase = Phase.PLAY
                captureAces()
                // Заказчик ходит первым.
                turnSeat = seat
            }

            // Золотой кон: торг не начинается вовсе, прикуп не берут, сноса
            // нет — карты у всех те же, что сданы. Играется сразу на весь
            // заказ, и заказчиком становится объявивший.
            ThousandMove.Golden -> {
                golden = true
                declarer = seat
                currentBid = GOLDEN_BID
                phase = Phase.PLAY
                captureAces()
                turnSeat = seat
            }

            is ThousandMove.Praise -> {
                hands[seat].remove(move.card)
                marriage[seat] += marriagePoints(move.card.suit)
                // Козырь — масть марьяжа; объявил второй — козырь сменился,
                // а очки марьяжей сложились.
                trumpSuit = move.card.suit
                layCard(seat, move.card)
            }

            is ThousandMove.Play -> {
                hands[seat].remove(move.card)
                layCard(seat, move.card)
            }

            // Кон на этом кончается: карты остаются на руках, стол — как
            // стоял. Счёт за него считает уже не розыгрыш, а роспись.
            ThousandMove.Raspis -> {
                raspised = seat
                phase = Phase.OVER
            }
        }
    }

    /**
     * Запомнить, у кого на руке все четыре туза. Считается в тот момент,
     * когда карты перестали меняться и розыгрыш начался: дальше тузы уходят
     * во взятки по одному, и «на руке» уже не проверить.
     */
    private fun captureAces() {
        for (s in 0 until playerCount) {
            allAces[s] = hands[s].count { it.rank == Rank.ACE } == ACE_COUNT
        }
    }

    private fun layCard(seat: Int, card: Card) {
        currentTrick += seat to card
        if (currentTrick.size == playerCount) {
            finishTrick()
        } else {
            turnSeat = next(seat)
        }
    }

    /** Взятку берёт старшая карта масти захода; козырь бьёт всё некозырное. */
    private fun finishTrick() {
        val lead = currentTrick.first().second.suit
        val trump = trumpSuit
        val best = currentTrick.maxWith { a, b -> compareForTrick(a.second, b.second, lead, trump) }
        val points = currentTrick.sumOf { it.second.points }
        val winner = best.first
        trickPoints[winner] += points
        tricks[winner]++
        playedTricks += Trick(currentTrick.map { it.second }, winner, points)
        currentTrick.clear()
        turnSeat = winner
        if (hands.all { it.isEmpty() }) phase = Phase.OVER
    }

    /**
     * Сравнение в взятке. Козырь козыря не бьёт, если младше; сброшенная не в
     * масть и не козырь карта не берёт никогда — поэтому она получает вес
     * ниже любой карты захода.
     */
    private fun compareForTrick(a: Card, b: Card, lead: Suit, trump: Suit?): Int {
        fun weight(card: Card): Int = when {
            trump != null && card.suit == trump -> TRUMP_BASE + card.weight
            card.suit == lead -> card.weight
            else -> -1
        }
        return weight(a).compareTo(weight(b))
    }

    // --- Торг -------------------------------------------------------------

    private fun endBiddingIfOver() {
        if (passed.count { it } < playerCount - 1) return
        val last = (0 until playerCount).first { !passed[it] }
        declarer = last
        phase = Phase.PRIKUP
        turnSeat = last
    }

    // --- Итог кона --------------------------------------------------------

    /** Очки за стол: взятки и объявленные марьяжи. */
    fun roundPoints(seat: Int): Int = trickPoints[seat] + marriage[seat]

    /**
     * Что каждый записывает за этот кон. Заказчик пишет ровно заказ или
     * минус заказ, остальные — своё, округлённое до пяти.
     */
    fun deltas(): IntArray {
        check(phase == Phase.OVER) { "кон ещё не доигран" }
        val dec = checkNotNull(declarer) { "без заказчика кон не кончается" }
        if (raspised != null) {
            return IntArray(playerCount) { seat ->
                if (seat == dec) -currentBid else raspisShare(currentBid)
            }
        }
        val result = IntArray(playerCount)
        for (seat in 0 until playerCount) {
            val got = roundPoints(seat)
            result[seat] = if (seat == dec) {
                if (got >= currentBid) currentBid else -currentBid
            } else {
                (got + 2) / 5 * 5
            }
        }
        return result
    }

    /**
     * Кто не взял ни одной взятки — тому болт.
     *
     * Расписанный кон взяток не считает вовсе: карты на нём не доиграны, и
     * пустые взятки у всех — это не игра, а отказ от неё. Болт за роспись
     * никому не пишется (`THOUSAND.md`, 2.13).
     */
    fun bolted(): List<Int> =
        if (raspised != null) emptyList() else hands.indices.filter { tricks[it] == 0 }

    // --- Озвучка ----------------------------------------------------------

    /** Рука вслух — тем же порядком, что в «Дураке»: «девятка пик, туз червей». */
    fun spokenHand(seat: Int): String =
        hands[seat].joinToString(", ") { it.spoken() }.ifEmpty { "карт нет" }

    override fun describe(state: ThousandRound, seat: Int, move: Move): String = when (move) {
        is ThousandMove.Bid -> "Ставка ${move.amount}."
        ThousandMove.Pass -> "Пас."
        is ThousandMove.TakePrikups -> "Прикуп взят."
        is ThousandMove.Discard -> "Снос: ${move.cards.joinToString(", ") { it.spoken() }}."
        is ThousandMove.Praise -> "Хвалю ${move.card.suit.spoken}."
        ThousandMove.Golden -> "Золотой кон: без торга, заказ $GOLDEN_BID."
        ThousandMove.Raspis -> "Роспись: заказ не играется."
        is ThousandMove.Play -> "Сыграна ${move.card.spoken()}."
        else -> "Ход сделан."
    }

    // --- GameRules --------------------------------------------------------

    override fun legalMoves(state: ThousandRound, seat: Int): List<Move> = legalMoves(seat)

    override fun apply(state: ThousandRound, seat: Int, move: Move): ThousandRound {
        apply(seat, move as ThousandMove)
        return this
    }

    override fun isFinished(state: ThousandRound): Boolean = phase == Phase.OVER

    override fun winner(state: ThousandRound): Int? = null

    private fun next(seat: Int): Int = (seat + 1) % playerCount

    /** Кому достанутся снесённые карты: по одной каждому сопернику по кругу. */
    private fun partiesTo(seat: Int, count: Int): List<Int> =
        (1..count).map { next(seat + it - 1) }

    companion object {
        const val MIN_BID = 100
        const val MAX_BID = 120
        const val BID_STEP = 5

        /**
         * Заказ золотого кона: он играется сразу на весь заказ, минуя торг.
         * Столько же, сколько и потолок торга, — золотой кон не про сумму,
         * а про то, что её не называют.
         */
        const val GOLDEN_BID = MAX_BID

        /** Сколько очков в колоде: вся она, до последней девятки. */
        const val TOTAL_POINTS = 120

        /** Дешевле этого прикуп — не прикуп, а наказание. */
        private const val MIN_PRIKUP_POINTS = 4

        /** С такой рукой взяток не взять: на неё и играть нечего. */
        private const val MIN_HAND_POINTS = 13

        /** Сколько раз пересдаём, прежде чем играть тем, что вышло. */
        private const val MAX_DEALS = 50

        /**
         * Что роспись пишет каждому сопернику: половина заказа.
         *
         * Округляем вниз до пяти — тем же шагом, что и очки защитников:
         * сто пятёрка заказа делится пополам неровно, а дробных очков за
         * столом не бывает. Заказчик при этом пишет заказ целиком, без
         * округления: он за него и садился.
         */
        fun raspisShare(bid: Int): Int = bid / 2 / BID_STEP * BID_STEP

        private const val TRUMP_BASE = 100

        /** Сколько карт на руках и в прикупе — от числа игроков. */
        private fun handSize(playerCount: Int) = if (playerCount == 2) 10 else 7
        private fun prikupShape(playerCount: Int): Pair<Int, Int> =
            if (playerCount == 2) 2 to 2 else 1 to 3

        /** Одна раздача: перемешать колоду и разложить её по рукам и прикупам. */
        private fun deal(
            random: Random,
            playerCount: Int,
        ): Pair<MutableList<MutableList<Card>>, List<List<Card>>> {
            val deck = Deck(fullDeck24()).also { it.shuffle(random) }
            val hands = MutableList(playerCount) { mutableListOf<Card>() }
            repeat(handSize(playerCount)) { hands.forEach { hand -> hand += deck.draw() } }
            val (prikupCount, prikupSize) = prikupShape(playerCount)
            return hands to List(prikupCount) { List(prikupSize) { deck.draw() } }
        }

        /**
         * Что бочка обязана заказать за столом на [playerCount] человек.
         * Втроём — потолок 120: только он и даёт недостающие до тысячи очки.
         * Вдвоём 120 не набрать никогда — две карты второго прикупа в
         * розыгрыше не участвуют, и всей колоды в игре нет, — поэтому там
         * бочка обязана взять сотню, как и всякий торг.
         */
        fun barrelBid(playerCount: Int): Int = if (playerCount == 2) MIN_BID else MAX_BID

        /**
         * Новая раздача. Вдвоём — по 10 карт и два прикупа по 2;
         * втроём — по 7 карт и один прикуп из 3. Вся колода уходит
         * со стола: 24 карты и там и там.
         *
         * Пустую раздачу пересдаём ([badDeal]): с ней кон не играется, а
         * застрять на раздаче хуже, чем сыграть неудачный кон, — поэтому
         * после [MAX_DEALS] попыток играем тем, что вышло.
         */
        fun start(
            random: Random = Random.Default,
            playerCount: Int = 2,
            firstBidder: Int = 0,
            barrelSeat: Int? = null,
            goldenAllowed: Boolean = false,
        ): ThousandRound {
            require(playerCount in 2..3) { "«Тысяча» бывает на двоих или на троих" }

            var (hands, prikups) = deal(random, playerCount)
            var attempt = 1
            while (attempt < MAX_DEALS && badDeal(hands, prikups)) {
                val next = deal(random, playerCount)
                hands = next.first
                prikups = next.second
                attempt++
            }

            return ThousandRound(
                hands = hands,
                prikups = prikups,
                firstBidder = firstBidder,
                turnSeat = firstBidder,
                barrelSeat = barrelSeat,
                barrelBid = barrelBid(playerCount),
                goldenAllowed = goldenAllowed,
            )
        }

        /**
         * Раздача, с которой кон не играется, — её пересдают.
         *
         * Поводы взяты из книги, по которой сверяли правила (см. `THOUSAND.md`,
         * 2.12): четыре девятки на одних руках, пустой прикуп, голая рука.
         * Пятый повод наш, и он только про игру вдвоём: там один прикуп
         * уходит из колоды целиком, и если он дорогой, обязательной сотни
         * в игре может не остаться вовсе — заказ будет не выполнить никому.
         */
        internal fun badDeal(hands: List<List<Card>>, prikups: List<List<Card>>): Boolean {
            if (hands.any { hand -> hand.count { it.rank == Rank.NINE } > 3 }) return true
            if (prikups.any { prikup -> prikup.count { it.rank == Rank.NINE } > 1 }) return true
            if (prikups.any { prikup -> prikup.sumOf { it.points } < MIN_PRIKUP_POINTS }) return true
            if (hands.any { hand -> hand.sumOf { it.points } < MIN_HAND_POINTS }) return true

            if (hands.size == 2) {
                val dead = prikups.maxOf { prikup -> prikup.sumOf { it.points } }
                if (TOTAL_POINTS - dead < MIN_BID) return true
            }

            return false
        }

        /**
         * Собрать кон из того, что лежало на диске. Отдельно от [start],
         * потому что поднятая партия — это не раздача: у неё уже есть и
         * ставка, и заказчик, и взятки за спиной.
         */
        fun restore(
            hands: List<List<Card>>,
            prikups: List<List<Card>>,
            firstBidder: Int,
            turnSeat: Int,
            phase: Phase,
            currentBid: Int,
            declarer: Int?,
            trumpSuit: Suit?,
            passed: Set<Int>,
            named: Set<Int>,
            trickPoints: List<Int>,
            marriage: List<Int>,
            tricks: List<Int>,
            table: List<Pair<Int, Card>>,
            barrelSeat: Int?,
            raspised: Int? = null,
            allAces: Set<Int> = emptySet(),
            golden: Boolean = false,
        ): ThousandRound {
            val round = ThousandRound(
                hands = hands.map { it.toMutableList() }.toMutableList(),
                prikups = prikups,
                firstBidder = firstBidder,
                turnSeat = turnSeat,
                barrelSeat = barrelSeat,
                barrelBid = barrelBid(hands.size),
            )
            round.phase = phase
            round.currentBid = currentBid
            round.declarer = declarer
            round.trumpSuit = trumpSuit
            round.raspised = raspised
            // Договорённость поднятой партии не переспрашивают: раз кон
            // игрался золотым, он им и доигрывается.
            round.golden = golden
            passed.forEach { round.passed[it] = true }
            named.forEach { round.namedBid[it] = true }
            trickPoints.forEachIndexed { seat, value -> round.trickPoints[seat] = value }
            marriage.forEachIndexed { seat, value -> round.marriage[seat] = value }
            tricks.forEachIndexed { seat, value -> round.tricks[seat] = value }
            // Тузы, ушедшие во взятки, на руке уже не найти — поэтому и
            // хранится признак, а не сами карты.
            allAces.forEach { round.allAces[it] = true }
            round.currentTrick += table
            return round
        }

        /** Кон с заданными руками — для тестов и разбора ситуаций. */
        fun forTesting(
            hands: List<List<Card>>,
            prikups: List<List<Card>> = listOf(emptyList()),
            firstBidder: Int = 0,
            barrelSeat: Int? = null,
            goldenAllowed: Boolean = false,
        ): ThousandRound = ThousandRound(
            hands = hands.map { it.toMutableList() }.toMutableList(),
            prikups = prikups,
            firstBidder = firstBidder,
            turnSeat = firstBidder,
            barrelSeat = barrelSeat,
            barrelBid = barrelBid(hands.size),
            goldenAllowed = goldenAllowed,
        )
    }
}

/** Все сочетания по [size] карт — для перебора вариантов сноса. */
internal fun combinations(cards: List<Card>, size: Int): List<List<Card>> {
    if (size == 0) return listOf(emptyList())
    if (size > cards.size) return emptyList()
    if (size == 1) return cards.map { listOf(it) }
    return cards.flatMapIndexed { index, card ->
        combinations(cards.drop(index + 1), size - 1).map { listOf(card) + it }
    }
}
