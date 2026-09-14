package games.engine

/**
 * Ход игрока в любой игре. Конкретная игра объявляет свои ходы,
 * реализуя этот интерфейс (см. `games.engine.durak.DurakMove`).
 */
interface Move

/**
 * Шаблон игры. Всё, что нужно интерфейсу и боту, — эти пять вещей:
 * какие ходы сейчас возможны, что будет после хода, кто выиграл.
 *
 * [State] — состояние конкретной игры. Движок не знает ни про экран,
 * ни про озвучку: он отвечает на вопросы, а как это произнести, решает
 * слой доступности.
 */
interface GameRules<State> {
    /** Допустимые сейчас ходы этого места. Пусто — ход не его. */
    fun legalMoves(state: State, seat: Int): List<Move>

    /** Применить ход и вернуть новое состояние. */
    fun apply(state: State, seat: Int, move: Move): State

    fun isFinished(state: State): Boolean

    /** Кто выиграл; null — партия идёт или ничья. */
    fun winner(state: State): Int?

    /** Что сказать вслух про этот ход. */
    fun describe(state: State, seat: Int, move: Move): String
}
