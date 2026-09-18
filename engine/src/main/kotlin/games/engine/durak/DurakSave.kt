package games.engine.durak

import games.engine.Card
import games.engine.Rank
import games.engine.Suit

/**
 * Партия «Дурака» на диск и обратно — простым текстом.
 *
 * Текстом, а не двоичным форматом, по двум причинам. Такую запись можно
 * открыть и прочитать глазами, когда что-то поедет, — а поедет рано или
 * поздно. И её можно починить руками, не пересобирая приложение.
 *
 * Формат нарочно «терпимый к возрасту»: строки читаются по ключу, чужие
 * ключи молча пропускаются. Поле, добавленное в новых версиях, старую
 * запись не сломает — она просто не будет его знать. Ломает только смена
 * [FORMAT], и это единственный случай, когда запись честно отвергается:
 * лучше начать новую партию, чем собрать её из разночтений.
 *
 * Карта записывается коротко: достоинство числом и масть буквой —
 * «7H» это семёрка червей, «14S» — туз пик. Латинские буквы здесь не
 * для красоты: русские «Ч» и «Б» на слух не различить и легко
 * перепутать при починке руками.
 */
object DurakSave {

    /** Версия формата. Меняется вместе с составом полей. */
    const val FORMAT = 1

    private const val HEADER = "game durak"

    /** Вся партия одной строкой-записью. */
    fun write(game: DurakGame): String = buildString {
        appendLine(HEADER)
        appendLine("format $FORMAT")
        appendLine("trump ${letterOf(game.trumpSuit)}")
        appendLine("attacker ${game.attacker}")
        // Договорённости — режим партии, а не настройка экрана: партия помнит,
        // по каким правилам её начали, и переключение настройки посреди неё её
        // не меняет. Ключи читаются по имени и чужие пропускаются, поэтому
        // новые строки старую запись не ломают: там, где их нет, играет
        // умолчание. Строки `transfer` нет в записях до 0.8 — там читается
        // «да»: перевод был единственным вариантом.
        appendLine("transfer ${yesNo(game.rules.transfer)}")
        appendLine("loserleads ${yesNo(game.rules.loserLeads)}")
        appendLine("deck ${game.deckCards().joinToString(" ") { codeOf(it) }}")
        for (seat in 0 until game.playerCount) {
            appendLine("hand$seat ${game.handOf(seat).joinToString(" ") { codeOf(it) }}")
        }
        game.table.forEach { battle ->
            appendLine("table ${codeOf(battle.attack)}=${battle.defense?.let(::codeOf) ?: "-"}")
        }
        appendLine("discard ${game.discardedCards().joinToString(" ") { codeOf(it) }}")
    }

    /**
     * Прочитать партию. null — если запись не наша, побита или не сходится
     * (карта потерялась или задвоилась). Звать это будет экран при запуске,
     * и на null он просто начинает новую партию: пугать игрока разбором
     * поломанного файла незачем.
     */
    fun read(text: String): DurakGame? {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

        if (lines.firstOrNull() != HEADER) return null

        val fields = mutableMapOf<String, String>()
        val hands = sortedMapOf<Int, List<Card>>()
        val table = mutableListOf<Battle>()

        for (line in lines.drop(1)) {
            val key = line.substringBefore(' ').lowercase()
            val value = line.substringAfter(' ', "").trim()
            when {
                key == "table" -> table += parseBattle(value) ?: return null
                key.startsWith("hand") ->
                    hands[key.removePrefix("hand").toIntOrNull() ?: return null] =
                        parseCards(value) ?: return null

                else -> fields[key] = value
            }
        }

        if (fields["format"]?.toIntOrNull() != FORMAT) return null
        if (hands.isEmpty()) return null
        // Руки идут подряд, без пропусков: hand0, hand1, ... Иначе игрок
        // за столом «потеряется» и партия соберётся не та.
        if (hands.keys != (0 until hands.size).toSet()) return null

        val trump = parseSuit(fields["trump"]) ?: return null
        val attacker = fields["attacker"]?.toIntOrNull() ?: return null
        if (attacker !in 0 until hands.size) return null

        val deck = parseCards(fields["deck"].orEmpty()) ?: return null
        val discarded = parseCards(fields["discard"].orEmpty()) ?: return null
        val all = deck + discarded + table.flatMap { listOfNotNull(it.attack, it.defense) } +
            hands.values.flatten()

        // Колода «Дурака» — ровно 36 карт, и ни одна не берется из воздуха
        // и не исчезает. Если сходится не всё — запись битая.
        if (all.size != 36 || all.toSet().size != 36) return null

        return DurakGame.restore(
            trumpSuit = trump,
            deck = deck,
            hands = hands.values.toList(),
            attacker = attacker,
            table = table,
            discarded = discarded,
            rules = DurakRules(
                transfer = yes(fields["transfer"], default = true),
                loserLeads = yes(fields["loserleads"]),
            ),
        )
    }

    // --- Прочее в текст и обратно -----------------------------------------

    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"

    /** [default] — ответ, когда строки в записи нет вовсе. */
    private fun yes(value: String?, default: Boolean = false): Boolean =
        when (value?.lowercase()) {
            "yes" -> true
            "no" -> false
            else -> default
        }

    // --- Карты в текст и обратно ------------------------------------------

    private fun codeOf(card: Card): String = "${card.rank.value}${letterOf(card.suit)}"

    private fun letterOf(suit: Suit): String = when (suit) {
        Suit.SPADES -> "S"
        Suit.HEARTS -> "H"
        Suit.DIAMONDS -> "D"
        Suit.CLUBS -> "C"
    }

    private fun parseSuit(value: String?): Suit? = when (value?.uppercase()) {
        "S" -> Suit.SPADES
        "H" -> Suit.HEARTS
        "D" -> Suit.DIAMONDS
        "C" -> Suit.CLUBS
        else -> null
    }

    private fun parseCard(token: String): Card? {
        if (token.length < 2) return null
        val suit = parseSuit(token.takeLast(1)) ?: return null
        val value = token.dropLast(1).toIntOrNull() ?: return null
        val rank = Rank.entries.firstOrNull { it.value == value } ?: return null
        return Card(rank, suit)
    }

    /** Пустые куски пропускаем: лишний пробел при ручной правке — не поломка. */
    private fun parseCards(value: String): List<Card>? {
        val tokens = value.split(' ').filter { it.isNotBlank() }
        return tokens.map { parseCard(it) ?: return null }
    }

    private fun parseBattle(value: String): Battle? {
        val attack = parseCard(value.substringBefore('=')) ?: return null
        val defenseText = value.substringAfter('=', "")
        val defense = if (defenseText == "-" || defenseText.isBlank()) {
            null
        } else {
            parseCard(defenseText) ?: return null
        }
        return Battle(attack, defense)
    }
}
