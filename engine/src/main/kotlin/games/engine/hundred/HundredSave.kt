package games.engine.hundred

import games.engine.Card
import games.engine.Rank
import games.engine.Suit

/**
 * Партия «101» на диск и обратно — простым текстом, как «Дурак» и «Козёл».
 *
 * Текстом, а не двоичным форматом: такую запись можно прочитать глазами,
 * когда что-то поедет, и починить руками, не пересобирая приложение.
 *
 * Запись нарочно терпима к возрасту: строки читаются по ключу, чужие ключи
 * молча пропускаются. Ломает только смена [FORMAT] — тогда запись честно
 * отвергается: лучше начать новый матч, чем собрать его из разночтений.
 *
 * Счёт и выбывшие здесь не украшение, а суть матча: без них игрок после
 * перезапуска оказался бы в новом матче с чистого счёта, а выбывший вернулся
 * бы за стол. Поэтому запись проверяется на сходимость целиком: тридцать шесть
 * карт, ни одной лишней, ни одной потерянной.
 */
object HundredSave {

    /** Версия формата. Меняется вместе с составом полей. */
    const val FORMAT = 1

    private const val HEADER = "game hundred"

    /** Вся партия одной записью. */
    fun write(game: Hundred): String = buildString {
        appendLine(HEADER)
        appendLine("format $FORMAT")
        appendLine("seats ${game.playerCount}")
        appendLine("dealer ${game.dealer()}")
        appendLine("turn ${game.turn}")
        // Сколько раз переворачивали стопку. Числом, а не «да/нет»: по нему
        // экран отличает «карту взял» от «колода кончилась, переворачиваем
        // стопку» — событие, которое объявляют вслух обязательно.
        appendLine("turnovers ${game.stockTurnovers()}")
        appendLine("pile ${game.pileCards().joinToString(" ") { codeOf(it) }}")
        appendLine("stock ${game.stockCards().joinToString(" ") { codeOf(it) }}")
        for (seat in 0 until game.playerCount) {
            appendLine("hand$seat ${game.handOf(seat).joinToString(" ") { codeOf(it) }}")
        }
        appendLine("scores ${game.scoresAll().joinToString(" ")}")
        appendLine("out ${game.outSeats().joinToString(" ") { it.toString() }}")
    }

    /**
     * Прочитать матч. null — если запись не наша, побита или не сходится.
     * На null экран начинает новый матч: пугать игрока разбором поломанного
     * файла незачем.
     */
    fun read(text: String): Hundred? {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

        if (lines.firstOrNull() != HEADER) return null

        val fields = mutableMapOf<String, String>()
        val hands = sortedMapOf<Int, List<Card>>()

        for (line in lines.drop(1)) {
            val key = line.substringBefore(' ').lowercase()
            val value = line.substringAfter(' ', "").trim()
            if (key.startsWith("hand")) {
                val seat = key.removePrefix("hand").toIntOrNull() ?: return null
                hands[seat] = parseCards(value) ?: return null
            } else {
                fields[key] = value
            }
        }

        if (fields["format"]?.toIntOrNull() != FORMAT) return null
        // Руки идут подряд, без пропусков: hand0, hand1, ... Иначе игрок за
        // столом «потеряется» и матч соберётся не тот.
        if (hands.keys != (0 until hands.size).toSet()) return null
        if (hands.size !in 2..3) return null
        if (fields["seats"]?.toIntOrNull() != hands.size) return null

        val pile = parseCards(fields["pile"].orEmpty()) ?: return null
        val stock = parseCards(fields["stock"].orEmpty()) ?: return null
        // Кон без верхней карты — не кон: по ней ходят.
        if (pile.isEmpty()) return null

        val dealer = fields["dealer"]?.toIntOrNull() ?: return null
        val turn = fields["turn"]?.toIntOrNull() ?: return null
        if (dealer !in 0 until hands.size || turn !in 0 until hands.size) return null

        val scores = parseInts(fields["scores"]) ?: return null
        if (scores.size != hands.size) return null
        val out = parseOut(fields["out"], hands.size) ?: return null
        // Выбывший за стол не возвращается: ход выбывшему запись не отдаёт.
        if (out[turn]) return null

        val all = hands.values.flatten() + pile + stock
        // Колода ровно в 36 карт: ни одна не берётся из воздуха и не исчезает.
        if (all.size != 36 || all.toSet().size != 36) return null

        return Hundred.restore(
            hands = hands.values.toList(),
            pile = pile,
            stock = stock,
            dealer = dealer,
            turn = turn,
            scores = scores,
            out = out,
            turnovers = fields["turnovers"]?.toIntOrNull() ?: 0,
        )
    }

    // --- Числа и выбывшие --------------------------------------------------

    private fun parseInts(value: String?): List<Int>? {
        val tokens = value.orEmpty().split(' ').filter { it.isNotBlank() }
        return tokens.map { it.toIntOrNull() ?: return null }
    }

    /**
     * Выбывшие — номерами мест через пробел. Пусто — никто не выбыл, и это
     * не ошибка: так выглядит матч в самом начале.
     */
    private fun parseOut(value: String?, playerCount: Int): List<Boolean>? {
        val seats = parseInts(value) ?: return null
        if (seats.any { it !in 0 until playerCount }) return null
        if (seats.toSet().size != seats.size) return null
        return List(playerCount) { it in seats }
    }

    // --- Карты в текст и обратно ------------------------------------------

    private fun codeOf(card: Card): String = "${card.rank.value}${letterOf(card.suit)}"

    private fun letterOf(suit: Suit): String = when (suit) {
        Suit.SPADES -> "S"
        Suit.HEARTS -> "H"
        Suit.DIAMONDS -> "D"
        Suit.CLUBS -> "C"
    }

    private fun parseCard(token: String): Card? {
        if (token.length < 2) return null
        val suit = when (token.takeLast(1).uppercase()) {
            "S" -> Suit.SPADES
            "H" -> Suit.HEARTS
            "D" -> Suit.DIAMONDS
            "C" -> Suit.CLUBS
            else -> return null
        }
        val value = token.dropLast(1).toIntOrNull() ?: return null
        val rank = Rank.entries.firstOrNull { it.value == value } ?: return null
        return Card(rank, suit)
    }

    /** Пустые куски пропускаем: лишний пробел при ручной правке — не поломка. */
    private fun parseCards(value: String): List<Card>? =
        value.split(' ').filter { it.isNotBlank() }.map { parseCard(it) ?: return null }
}
