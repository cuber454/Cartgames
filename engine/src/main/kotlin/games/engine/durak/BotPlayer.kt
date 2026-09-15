package games.engine.durak

import games.engine.Card
import games.engine.fullDeck36
import kotlin.random.Random

/**
 * Соперник выбирается в настройках один на все игры, поэтому сам уровень
 * живёт в корне движка. Здесь остаётся псевдоним: код «Дурака» и старые
 * экраны обращаются к нему по-прежнему.
 */
typealias Difficulty = games.engine.Difficulty

/**
 * Соперник для одиночной игры.
 *
 * Живёт в движке, а не в приложении, по двум причинам: это правило игры,
 * а не экран, и так его можно прогнать тестами без телефона.
 *
 * Хитрый уровень считает вышедшие карты: всё, что попало в отбой или лежит
 * на столе, у противника уже невозможно, поэтому шанс получить отпор
 * считается по оставшимся картам.
 */
object BotPlayer {

    fun chooseMove(
        game: DurakGame,
        seat: Int,
        difficulty: Difficulty = Difficulty.NORMAL,
        random: Random = Random.Default,
    ): DurakMove? {
        val moves = game.legalMoves(seat)
        if (moves.isEmpty()) return null

        return when (difficulty) {
            Difficulty.NOVICE -> moves[random.nextInt(moves.size)]
            Difficulty.NORMAL -> normal(game, seat, moves)
            Difficulty.CLEVER -> clever(game, seat, moves)
        }
    }

    // --- Обычный уровень --------------------------------------------------

    /** Просто и без хитрости: бьётся самой дешёвой картой, ходит младшей. */
    private fun normal(game: DurakGame, seat: Int, moves: List<DurakMove>): DurakMove {
        if (seat == game.defender) {
            val best = moves.filterIsInstance<DurakMove.Defend>()
                .minByOrNull { cost(it.card, game) }
            if (best != null) return best

            // Отбиться нечем — переводим, если есть чем: перевести дешевле,
            // чем забрать стол.
            return moves.filterIsInstance<DurakMove.Transfer>()
                .minByOrNull { cost(it.card, game) } ?: DurakMove.Take
        }

        val attacks = moves.filterIsInstance<DurakMove.Attack>()
        val best = attacks.minByOrNull { cost(it.card, game) }
        return when {
            best == null -> DurakMove.Pass
            game.table.isEmpty() -> best
            game.table.any { !it.beaten } -> best
            else -> DurakMove.Pass
        }
    }

    /** Козырь дорог: его тратим последним. */
    private fun cost(card: Card, game: DurakGame): Int =
        card.rank.value + if (card.suit == game.trumpSuit) 100 else 0

    // --- Хитрый уровень ---------------------------------------------------

    private fun clever(game: DurakGame, seat: Int, moves: List<DurakMove>): DurakMove =
        if (seat == game.defender) {
            cleverDefense(game, seat, moves)
        } else {
            cleverAttack(game, seat, moves)
        }

    /**
     * Защита. Отбиваемся самой дешёвой картой, но козырь на мелкую карту
     * в начале партии не тратим — выгоднее забрать и сохранить козыри.
     */
    private fun cleverDefense(game: DurakGame, seat: Int, moves: List<DurakMove>): DurakMove {
        val best = moves.filterIsInstance<DurakMove.Defend>()
            .minByOrNull { cost(it.card, game) }
        val transfer = moves.filterIsInstance<DurakMove.Transfer>()
            .minByOrNull { cost(it.card, game) }

        // Отбиться нечем — перевод дешевле, чем забрать стол.
        if (best == null) return transfer ?: DurakMove.Take

        val attack = game.table[best.tableIndex].attack
        val myTrumps = game.handOf(seat).count { it.suit == game.trumpSuit }

        // Козырь тратится, только если он и так последний или отбивать больше нечем.
        val trumpForSmallCard = best.card.suit == game.trumpSuit && attack.suit != game.trumpSuit
        val worthIt = myTrumps >= 3 || game.deckSize() <= 4 || attack.rank.value >= 12
        if (!trumpForSmallCard || worthIt) return best

        // Козырь жалко — а с переводом и не надо: пусть отбивается сосед,
        // а козырь останется в руке.
        return transfer ?: DurakMove.Take
    }

    /**
     * Атака и подкидывание. Выбираем карту, которую противнику труднее
     * всего побить: младшую, не козырь и не из тех, что он наверняка держит.
     */
    private fun cleverAttack(game: DurakGame, seat: Int, moves: List<DurakMove>): DurakMove {
        val attacks = moves.filterIsInstance<DurakMove.Attack>()
        val best = attacks.minByOrNull { attackScore(it.card, game, seat) }
            ?: return DurakMove.Pass

        // Стол уже отбит — подкидывать не обязательно, можно сказать «бито».
        // Подкидываем только младшее: хорошие карты пусть останутся в руке.
        val tableBeaten = game.table.isNotEmpty() && game.table.all { it.beaten }
        return if (tableBeaten && cost(best.card, game) > 10) DurakMove.Pass else best
    }

    /** Чем меньше число, тем охотнее бот кладёт эту карту. */
    private fun attackScore(card: Card, game: DurakGame, seat: Int): Double {
        val pair = game.handOf(seat).count { it.rank == card.rank } >= 2
        return cost(card, game).toDouble() +
            beatChance(card, game, seat) * 25.0 -
            if (pair) 6.0 else 0.0
    }

    /**
     * Доля невиданных карт, которые побьют эту. Считается по всем картам,
     * которых бот не видел: они лежат либо в колоде, либо у противника.
     */
    private fun beatChance(card: Card, game: DurakGame, seat: Int): Double {
        val unseen = unseenCards(game, seat)
        if (unseen.isEmpty()) return 0.0
        return unseen.count { game.beats(it, card) }.toDouble() / unseen.size
    }

    /** Карты, которых бот не видел: ни у себя, ни в отбое, ни на столе. */
    private fun unseenCards(game: DurakGame, seat: Int): List<Card> {
        val seen = HashSet<Card>(game.handOf(seat))
        seen += game.playedCards()
        return fullDeck36().filterNot { it in seen }
    }
}
