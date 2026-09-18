package games.cardgames.durak

import games.cardgames.speech.BotLine
import games.cardgames.speech.PhraseBag
import games.engine.Suit
import games.engine.durak.Difficulty
import games.engine.durak.DurakMove
import kotlin.random.Random

/** Повод для реплики. От него зависит и набор зачинов, и право промолчать. */
private enum class Reason { LEAD, TOSS, LAST, ENDGAME, BEAT, TRUMP_BEAT, TRANSFER, TAKE, TAKE_CLEVER, PASS }

/**
 * Зачины. Реплика собирается всегда одинаково — «зачин: карта.» — и это не
 * бедность, а необходимость: незрячий игрок ловит на слух именно карту, и
 * она должна стоять на одном и том же месте. Разнообразие живёт в зачине.
 */
private val PHRASES: Map<Reason, List<String>> = mapOf(
    // Первая карта розыгрыша: стол пуст.
    Reason.LEAD to listOf("Держи", "На", "Вот тебе", "Подкидываю", "Лови"),
    // Подкидывание: на столе уже есть чем крыть.
    Reason.TOSS to listOf("Ещё", "Подкидываю", "И вот ещё", "Добавляю", "Сверху ещё"),
    // Это его последняя карта — сейчас выйдет.
    Reason.LAST to listOf("Последняя", "Всё, последняя", "Это последняя"),
    // Колода кончилась: доигрываем тем, что на руках.
    Reason.ENDGAME to listOf(
        "Колода вышла, держи",
        "Колода пуста, держи",
        "Последние карты, держи",
        "Колода вышла, лови",
    ),
    // Отбивается некозырем.
    Reason.BEAT to listOf("Бью", "Отбиваю", "Крою", "Накрываю"),
    // Истратил козырь — это слышно и без слов, но пусть скажет.
    Reason.TRUMP_BEAT to listOf("Козырем", "Бью козырем", "Отбиваю козырем", "Придётся козырем"),
    // Перевод: атака уходит соседу. Ход редкий и неожиданный — игрок должен
    // услышать его словами, а не догадаться по одному звуку карты.
    Reason.TRANSFER to listOf("Перевожу", "Перевод", "Перевожу дальше", "Перекидываю"),
    Reason.TAKE to listOf("Беру.", "Беру, забирай.", "Ладно, беру.", "Забираю."),
    // Хитрый берёт не от бессилия, а с расчётом — и говорит иначе.
    Reason.TAKE_CLEVER to listOf("Хм. Беру.", "Так. Беру.", "Не буду тратиться. Беру.", "Ладно, беру."),
    Reason.PASS to listOf("Бито.", "Всё, бито.", "Бито, в отбой.", "Ну, бито."),
)

/** Во сколько ходов один раз бот молчит вместо подкидывания. */
private const val QUIET_ODDS = 8

/**
 * Речь бота за столом.
 *
 * Бот не должен повторяться. Три зачина, которые тянулись жребием с
 * возвратом, давали один и тот же два раза подряд в каждом третьем случае —
 * это слышно сразу и превращает партнёра в автомат. Поэтому здесь три
 * вещи разом:
 *
 *  * зачины тянутся из мешка, без возврата: пока мешок не опустеет, ни один
 *    не повторится, а на стыке мешков первый не совпадёт с последним;
 *  * зачин зависит от стола — первая карта, подкидывание, последняя карта,
 *    пустая колода, — а не от одного жребия на все случаи: живой человек
 *    говорит по обстановке, а не по считалке;
 *  * в подкидывании бот иногда молчит. Живой партнёр тоже не комментирует
 *    каждый свой ход.
 *
 * Промолчать бот может только в подкидывании и никогда — два раза подряд:
 * там первая карта розыгрыша уже названа, и молчание ничего не отнимает.
 * В «беру» и «бито» он говорит всегда — эти ходы меняют стол, и незрячий
 * игрок не должен о них догадываться.
 *
 * Движок о речи не знает: за тем же столом потом сядет живой человек, и
 * движку незачем различать, кто по ту сторону — программа или игрок.
 */
class BotTalker(private val rng: Random = Random.Default) {

    /** Мешки зачинов: внутри перемешаны, тянем без возврата. */
    private val bag = PhraseBag<Reason>(rng)

    /** Прошлый ход бот промолчал: два раза подряд молчать нельзя. */
    private var wasSilent = false

    /**
     * Реплика на ход бота.
     *
     * [ownHandSize] и [deckSize] — состояние ДО хода: экран спрашивает фразу
     * раньше, чем применяет ход к партии.
     */
    fun line(
        move: DurakMove,
        difficulty: Difficulty,
        trumpSuit: Suit,
        tableEmpty: Boolean,
        ownHandSize: Int,
        deckSize: Int,
    ): BotLine = when (move) {
        is DurakMove.Attack -> cardLine(
            reason = when {
                ownHandSize <= 1 -> Reason.LAST
                deckSize == 0 -> Reason.ENDGAME
                tableEmpty -> Reason.LEAD
                else -> Reason.TOSS
            },
            card = move.card.spoken(),
        )

        is DurakMove.Defend -> cardLine(
            reason = if (move.card.suit == trumpSuit) Reason.TRUMP_BEAT else Reason.BEAT,
            card = move.card.spoken(),
        )

        is DurakMove.Transfer -> cardLine(reason = Reason.TRANSFER, card = move.card.spoken())

        DurakMove.Take -> plainLine(
            if (difficulty == Difficulty.CLEVER) Reason.TAKE_CLEVER else Reason.TAKE,
        )

        DurakMove.Pass -> plainLine(Reason.PASS)
    }

    /** Реплика с картой: зачин из мешка плюс сама карта. */
    private fun cardLine(reason: Reason, card: String): BotLine {
        val quiet = reason == Reason.TOSS && !wasSilent && rng.nextInt(QUIET_ODDS) == 0
        wasSilent = quiet
        return BotLine("${draw(reason)}: $card.", speak = !quiet)
    }

    /** Реплика без карты: «беру», «бито». Молчать здесь нельзя. */
    private fun plainLine(reason: Reason): BotLine {
        wasSilent = false
        return BotLine(draw(reason), speak = true)
    }

    /** Зачин из группы: мешок общий на все игры, повод — свой у каждой. */
    private fun draw(reason: Reason): String = bag.draw(reason, PHRASES.getValue(reason))
}
