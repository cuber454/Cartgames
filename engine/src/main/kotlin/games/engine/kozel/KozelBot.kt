package games.engine.kozel

import games.engine.Difficulty
import games.engine.tiles.Tile
import kotlin.random.Random

/**
 * Соперник для «Козла».
 *
 * Живёт в движке, как и соперники прочих игр: это правило игры, а не экран,
 * и так его можно прогнать тестами без телефона.
 *
 * Главное отличие от «Дурака» и «Тысячи» — бот играет по открытому
 * состоянию. На вход ему идёт [KozelView], где нет ни твоей руки, ни
 * базара: только своя рука, линия, да два счётчика. Поэтому «ум» его живёт
 * не в подсматривании, а в подсчёте: сколько костей с этим числом уже
 * вышло, какие концы закрыты, что он оставит себе на следующий ход.
 *
 * Взять из базара он тоже не решает — это делает раунд, вслепую и
 * равновероятно (KOZEL.md, 4.2). У бота на такой ход один ответ: взять.
 *
 * Уровней три, и различаются они памятью, а не силой.
 * «Новичок» кладёт что попало. «Обычный» смотрит только на свою руку:
 * сбрасывает тяжёлое и оставляет себе продолжения. «Хитрый» помнит всё,
 * что вышло за стол, и по невиданным костям считает, ответят ему или нет.
 */
object KozelBot {

    fun chooseMove(
        view: KozelView,
        difficulty: Difficulty = Difficulty.NORMAL,
        random: Random = Random.Default,
    ): KozelMove? {
        val moves = view.legalMoves()
        if (moves.isEmpty()) return null

        // Ходить нечем — решать нечего: берём из базара, а пустой базар
        // пропускаем. Выбор здесь делает правило, а не бот.
        val places = moves.filterIsInstance<KozelMove.Place>()
        if (places.isEmpty()) return moves.first()

        return when (difficulty) {
            Difficulty.NOVICE -> places[random.nextInt(places.size)]
            Difficulty.NORMAL -> places.maxBy { score(view, it, memory = false) }
            Difficulty.CLEVER -> places.maxBy { score(view, it, memory = true) }
        }
    }

    /**
     * Насколько ход хорош: чем больше, тем охотнее бот его сделает.
     *
     * Слагаемых четыре (KOZEL.md, 4.3):
     *  - **вес кости**: тяжёлое на руке — это штраф, который запишут ему,
     *    если раунд кончится не в его пользу: и чужой выход, и «рыба»
     *    считаются по руке, оставшейся на столе;
     *  - **свои продолжения**: сколько костей останется ходить после этого
     *    хода — своим ходом надо дорожить, он может быть последним;
     *  - **что осталось на концах**: если число, которое бот выставляет
     *    наружу, почти вышло, ответить ему нечем;
     *  - **закрытый конец**: совсем закрытый конец — это «рыба», а в «рыбе»
     *    выигрывает тот, у кого рука легче.
     *
     * [memory] отличает обычный уровень от хитрого: первый считает по своей
     * руке, второй — ещё и по невиданным костям. Своей руки не хватает,
     * чтобы понять, ответят тебе или нет: то, что у тебя тройка, ничего не
     * говорит о тройках соперника, а вот сколько троек уже вышло — говорит.
     */
    private fun score(view: KozelView, move: KozelMove.Place, memory: Boolean): Int {
        val after = view.line.place(move.tile, move.end)
        val rest = view.hand - move.tile

        // Тяжёлое сбрасываем вдвойне охотнее: очки на руке — единственное,
        // что записывают за столом, и записывают их тому, у кого рука осталась.
        var score = move.tile.pips * 2

        score += rest.count { after.canPlay(it).isNotEmpty() } * CONTINUATION

        if (!memory) return score

        val unseen = view.unseen()
        for (end in ENDS) {
            val value = after.endOf(end) ?: continue
            val reach = unseen.count { it.has(value) }
            score -= reach * REACH
            if (reach == 0) {
                // Конец закрыт наглухо: ни у соперника, ни у базара костей
                // с этим числом нет. Чем меньше базар, тем вернее это «рыба».
                score += if (view.bazaarSize == 0) CLOSED_EMPTY else CLOSED_OPEN
            }
        }

        return score
    }

    /** Сколько весит каждый вариант. Подобраны прогонами бота против бота. */
    private const val CONTINUATION = 3
    private const val REACH = 2
    private const val CLOSED_OPEN = 5
    private const val CLOSED_EMPTY = 12

    /** Концы линии. Пустая линия — частный случай: у неё концов нет вовсе. */
    private val ENDS = End.entries.toList()
}

/** Рука без одной кости: кость одна, повторов в наборе не бывает. */
private operator fun List<Tile>.minus(tile: Tile): List<Tile> = filterNot { it == tile }
