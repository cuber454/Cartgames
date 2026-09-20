package games.engine.hundred

import games.engine.Card
import games.engine.Difficulty
import games.engine.Rank
import games.engine.Suit
import games.engine.fullDeck36
import kotlin.random.Random

/**
 * Соперник для «101».
 *
 * Выбор у бота здесь куда беднее, чем за другими столами: подходящая карта
 * часто ровно одна, а нет подходящей — он обязан взять из колоды. Думать
 * приходится в одном случае: когда подходит несколько карт и надо решить,
 * какую из них отдать кону и что оставить себе.
 *
 * Решает он по цене: карты, оставшиеся на руках в конце кона, записываются
 * игроку штрафом, — значит, дорогое сбрасывают первым, а дешёвое держат.
 * Дама — исключение: она дешева (три очка), а вышедший ею списывает себе
 * от двадцати до восьмидесяти и удваивает чужой счёт. Поэтому даму бот
 * придерживает, когда рука уже коротка и ею, похоже, придётся выходить.
 */
object HundredBot {

    fun chooseMove(
        game: Hundred,
        seat: Int,
        difficulty: Difficulty = Difficulty.NORMAL,
        random: Random = Random.Default,
    ): HundredMove? {
        val moves = game.legalMoves(seat)
        if (moves.size <= 1) return moves.firstOrNull()

        val plays = moves.filterIsInstance<HundredMove.Play>()
        if (plays.isEmpty()) return moves.first()

        val chosen = when (difficulty) {
            Difficulty.NOVICE -> plays[random.nextInt(plays.size)]
            Difficulty.NORMAL -> plays.maxByOrNull { score(it.card, game, seat) } ?: plays.first()
            Difficulty.CLEVER -> plays.maxByOrNull { cleverScore(it.card, game, seat) }
                ?: plays.first()
        }
        return withOrder(chosen, game, seat, random)
    }

    /**
     * Дама без заказа не ходит: заказ — часть хода, а не украшение. Заказывает
     * бот ту масть, которой у него на руке больше: пока дама на кону, ходят
     * заказом, и своя длинная масть после такого заказа как раз и остаётся при
     * нём дольше всех.
     *
     * Из равных мастей выбирается случайная, а не первая по списку: иначе бот
     * заказывал бы пики при всяком пустом раскладе, и заказ перестал бы что-то
     * значить.
     */
    private fun withOrder(move: HundredMove.Play, game: Hundred, seat: Int, random: Random): HundredMove {
        if (move.card.rank != Rank.QUEEN) return move
        val hand = game.handOf(seat)
        val counts = Suit.entries.map { suit -> suit to hand.count { it.suit == suit } }
        val best = counts.maxOf { it.second }
        val suits = counts.filter { it.second == best }.map { it.first }
        return move.copy(order = suits[random.nextInt(suits.size)])
    }

    // --- Обычный уровень --------------------------------------------------

    /**
     * Чем больше число, тем охотнее бот кладёт эту карту на кон.
     *
     * Дорогое вперёд, свои связи назад: карта, которая перекликается с другими
     * твоими картами мастью или достоинством, стоит дороже — ею ещё найдётся
     * чем ходить, а одинокая карта так и останется на руке.
     */
    private fun score(card: Card, game: Hundred, seat: Int): Double {
        val hand = game.handOf(seat)
        val supporters = hand.count { it != card && (it.suit == card.suit || it.rank == card.rank) }
        return points(listOf(card)) * 2.0 - supporters * 3.0 + queenDelay(card, hand)
    }

    /**
     * Дама, которой выходят, стоит дорого — но только если выйти ею успеешь.
     * Пока рука длинна, это просто дешёвая карта; когда в руке остаётся
     * третья карта или меньше, её держат на последний ход.
     */
    private fun queenDelay(card: Card, hand: List<Card>): Double =
        if (card.rank == Rank.QUEEN && hand.size <= 3) -12.0 else 0.0

    // --- Хитрый уровень ---------------------------------------------------

    /**
     * То же самое, но с оглядкой на вышедшие карты.
     *
     * Все сыгранные карты лежат открыто на кону, а колода и руки соперников
     * закрыты — значит, «невиданных» карт ровно столько, сколько бот не
     * видит. Масть, которой почти не осталось на руках, скоро кончится: свою
     * карту этой масти выгодно отдать сейчас, пока её ещё можно положить.
     */
    private fun cleverScore(card: Card, game: Hundred, seat: Int): Double {
        val unseen = unseenCards(game, seat)
        val sameSuit = unseen.count { it.suit == card.suit }
        val sameRank = unseen.count { it.rank == card.rank }

        // Если у соперников нет ни этой масти, ни этого достоинства, следующий
        // обязан взять карту (или вообще пропустить ход) — это подарок, и такой
        // картой ходят охотнее.
        val gift = if (sameSuit == 0 && sameRank == 0) 8.0 else 0.0

        return score(card, game, seat) + gift - sameSuit * 0.5 - sameRank * 0.5
    }

    /** Карты, которых бот не видел: ни своей руки, ни открытого кона. */
    private fun unseenCards(game: Hundred, seat: Int): List<Card> {
        val seen = HashSet<Card>(game.handOf(seat))
        seen += game.pileCards()
        return fullDeck36().filterNot { it in seen }
    }
}
