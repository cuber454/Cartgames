package games.cardgames.kozel

import games.cardgames.speech.BotLine
import games.cardgames.speech.PhraseBag
import games.engine.kozel.KozelMove
import kotlin.random.Random

/** Повод для реплики: от него зависит набор зачинов. */
private enum class Reason { FIRST, ADD, LAST, BOTH, DRAW, PASS }

/**
 * Зачины. Реплика собирается всегда одинаково — «зачин: кость и куда» — и это
 * не бедность, а необходимость: незрячий игрок ловит на слух именно кость, и
 * она должна стоять на одном и том же месте. Разнообразие живёт в зачине.
 */
private val PHRASES: Map<Reason, List<String>> = mapOf(
    // Первая кость линии: до неё стол был пуст, и «влево» говорить не о чем.
    Reason.FIRST to listOf("Начинаю", "Первую кладу", "Ставлю первую", "Хожу"),
    // Костью приставляются к уже лежащей линии.
    Reason.ADD to listOf("Кладу", "Приставляю", "Вот", "Держи", "Добавляю"),
    // Это его последняя кость — сейчас выйдет.
    Reason.LAST to listOf("Последняя", "Всё, последняя", "Это последняя"),
    // Оба дубля разом: ход, который за столом бывает не каждый раунд.
    Reason.BOTH to listOf("Обе разом", "Кладу обе", "Двойной", "Ставлю обе"),
    // Базар закрыт для обоих: которое именно число вышло из игры, игрок
    // иначе не узнает вовсе.
    Reason.DRAW to listOf("Беру из базара", "Беру", "Тяну", "Гляну, что там"),
    Reason.PASS to listOf("Мне нечем", "Пропускаю", "Пас", "Нечем ходить"),
)

/** Во сколько ходов один раз бот молчит вместо приставления. */
private const val QUIET_ODDS = 8

/**
 * Речь бота за столом.
 *
 * Устроена как в «Дураке» (см. `durak/BotTalker`), и по тем же причинам:
 * зачины тянутся из мешка без возврата, повод зависит от стола, а не от
 * одного жребия на все случаи, и в приставлении бот иногда молчит — живой
 * партнёр тоже не комментирует каждую кость.
 *
 * Молчать нельзя там, где ход меняет стол, а игрок по одному звуку кости
 * его не разберёт: взятие из базара и пропуск бот называет всегда.
 *
 * Движок о речи не знает: за тем же столом потом сядет живой человек, и
 * движку незачем различать, кто по ту сторону — программа или игрок.
 */
class KozelTalker(private val rng: Random = Random.Default) {

    private val bag = PhraseBag<Reason>(rng)

    /** Прошлый ход бот промолчал: два раза подряд молчать нельзя. */
    private var wasSilent = false

    /**
     * Реплика на ход бота.
     *
     * [lineEmpty] и [ownHandSize] — состояние ДО хода: экран спрашивает
     * фразу раньше, чем применяет ход к раунду, иначе «последняя» скажет о
     * кости, которой на руке уже нет.
     */
    fun line(move: KozelMove, lineEmpty: Boolean, ownHandSize: Int): BotLine = when (move) {
        is KozelMove.Place -> {
            val reason = when {
                ownHandSize <= 1 -> Reason.LAST
                lineEmpty -> Reason.FIRST
                else -> Reason.ADD
            }
            // В пустую линию кость кладут как угодно, и «влево» там ничего
            // не значит: сторона появляется только у линии.
            val where = if (lineEmpty) "" else " ${move.end.title}"
            cardLine(reason, "${move.tile.spoken()}$where")
        }

        is KozelMove.PlaceBoth -> {
            // Ход редкий и стол меняет вдвое сильнее обычного: две кости
            // сразу. Молчать о нём нельзя, поэтому зачин у него свой и
            // тишины, как в приставлении, здесь не бывает.
            cardLine(Reason.BOTH, "${move.left.spoken()} и ${move.right.spoken()}")
        }

        KozelMove.Draw -> plainLine(Reason.DRAW)
        KozelMove.Pass -> plainLine(Reason.PASS)
    }

    /** Реплика с костью: зачин из мешка плюс сама кость. */
    private fun cardLine(reason: Reason, tile: String): BotLine {
        // Молчит бот только в приставлении и никогда — два раза подряд: в
        // первой кости розыгрыша она уже названа, и молчание ничего не
        // отнимает.
        val quiet = reason == Reason.ADD && !wasSilent && rng.nextInt(QUIET_ODDS) == 0
        wasSilent = quiet
        return BotLine("${draw(reason)}: $tile.", speak = !quiet)
    }

    /** Реплика без кости: «беру», «пропускаю». Молчать здесь нельзя. */
    private fun plainLine(reason: Reason): BotLine {
        wasSilent = false
        return BotLine(draw(reason), speak = true)
    }

    private fun draw(reason: Reason): String = bag.draw(reason, PHRASES.getValue(reason))
}
