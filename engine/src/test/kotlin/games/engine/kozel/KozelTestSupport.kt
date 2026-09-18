package games.engine.kozel

import games.engine.Difficulty
import games.engine.tiles.Tile
import games.engine.tiles.tile
import kotlin.random.Random

/**
 * Общее для тестов «Козла»: собрать линию с нужными концами и доиграть
 * раунд или матч ботом против бота.
 *
 * Линия собирается конструктором, а не ходами: тесту нужны конкретные
 * концы, а как они получились — не его дело. Ходами такая линия собиралась
 * бы полем кода, и падение теста говорило бы сразу о двух вещах.
 */
fun lineWithEnds(left: Int, right: Int): Line = Line(listOf(tile(left, right)), left, right)

/** Раунд с одной линией на столе: расклад задают тесты, концы — тоже. */
fun roundOf(
    hands: List<List<Tile>>,
    bazaar: List<Tile> = emptyList(),
    line: Line = Line.EMPTY,
    turn: Int = 0,
    passes: Int = 0,
    rules: KozelRules = KozelRules.BOOK,
): KozelRound = KozelRound.of(rules, hands, bazaar, opener = turn, line = line, turn = turn, passes = passes)

/**
 * Доиграть раунд ботом против бота. Возвращает число ходов — по нему видно,
 * что раунд не крутится на месте.
 */
fun playRound(
    round: KozelRound,
    difficulties: List<Difficulty> = List(SEATS) { Difficulty.NORMAL },
    random: Random = Random(1),
): Int {
    var moves = 0
    while (!round.finished) {
        check(moves < 1_000) { "раунд не кончается: похоже, ход не приближает конец" }
        val seat = round.turn
        val move = KozelBot.chooseMove(KozelView.of(round, seat), difficulties[seat], random)
            ?: error("бот не нашёл хода, хотя раунд не кончен")
        round.apply(seat, move)
        moves++
    }
    return moves
}

/**
 * Сыграть матч ботом против бота. Возвращает, кто выиграл матч, — тот, кто
 * до цели не дошёл; «козлом» становится второй, набравший.
 */
fun playMatch(
    rules: KozelRules = KozelRules.BOOK,
    difficulties: List<Difficulty> = List(SEATS) { Difficulty.NORMAL },
    seed: Int = 0,
): Int {
    val random = Random(seed)
    val match = KozelMatch(rules, random)
    var rounds = 0
    while (!match.over) {
        check(rounds < 500) { "матч не кончается" }
        playRound(match.round, difficulties, random)
        match.finishRound()
        rounds++
    }
    return match.matchWinner!!
}
