package games.cardgames.durak

import games.engine.Card
import games.engine.durak.DurakGame
import games.engine.durak.DurakMove

/**
 * Соперник для одиночной игры.
 *
 * Играет честно, но без хитрости: отбивается самой дешёвой картой,
 * подкидывает младшую подходящую, а если отбиться нечем — забирает.
 * Такой бот не пугает новичка и предсказуем, а сложность добавим
 * отдельным уровнем позже.
 */
object BotPlayer {

    fun chooseMove(game: DurakGame, seat: Int): DurakMove? {
        val moves = game.legalMoves(seat)
        if (moves.isEmpty()) return null

        if (seat == game.defender) {
            val defends = moves.filterIsInstance<DurakMove.Defend>()
            return defends.minByOrNull { cost(it.card, game) } ?: DurakMove.Take
        }

        val attacks = moves.filterIsInstance<DurakMove.Attack>()
        if (game.table.isEmpty()) {
            return attacks.minByOrNull { cost(it.card, game) }
        }
        return if (game.table.any { !it.beaten }) {
            attacks.minByOrNull { cost(it.card, game) }
        } else {
            DurakMove.Pass
        }
    }

    /** Козырь дорог: его тратим последним. */
    private fun cost(card: Card, game: DurakGame): Int =
        card.rank.value + if (card.suit == game.trumpSuit) 100 else 0
}
