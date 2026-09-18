package games.engine.thousand

import games.engine.Card
import games.engine.Rank
import games.engine.Suit

/**
 * Матч «Тысячи» на диск и обратно — простым текстом, как и «Дурак».
 *
 * Текстом, а не двоичным форматом: такую запись можно открыть и прочитать
 * глазами, когда что-то поедет, — а поедет рано или поздно. И починить
 * руками, не пересобирая приложение.
 *
 * Формат «терпимый к возрасту»: строки читаются по ключу, чужие ключи молча
 * пропускаются. Ломает только смена [FORMAT], и это единственный случай,
 * когда запись честно отвергается: лучше начать новый матч, чем собрать его
 * из разночтений.
 *
 * Сохраняется и матч, и текущий кон: счёт, болты, бочка и сдача — это одно
 * состояние, а раздача, торг и взятки — другое. Держать их врозь незачем:
 * игрок возвращается не в «матч вообще», а за конкретный стол.
 */
object ThousandSave {

    /** Версия формата. Меняется вместе с составом полей. */
    const val FORMAT = 4

    /**
     * Записи какой давности ещё читаются.
     *
     * Версия 2 добавила договорённости сторон и счётчик росписей, версия 3 —
     * тузовый марьяж и признак того, у кого тузы на руке, версия 4 — признак
     * золотого кона. Каждая из этих строк в старой записи означает ровно то
     * же, что и умолчание: партия, начатая по книге, с нулём росписей, без
     * тузового марьяжа и с обычным коном. Поэтому записи версий 1–3 читаются
     * без потерь, и брошенная партия после обновления не пропадает.
     */
    private val READABLE_FORMATS = 1..FORMAT

    private const val HEADER = "game thousand"
    private const val DASH = "-"

    /**
     * Состояние матча и кон, который в нём идёт.
     *
     * [recorded] — доигранный кон уже занесён в счёт матча. Без этого флага
     * поднятая с диска партия засчитала бы последний кон второй раз: кон
     * доигран, а результат его — ещё нет.
     */
    fun write(match: ThousandMatch, round: ThousandRound, recorded: Boolean = false): String = buildString {
        appendLine(HEADER)
        appendLine("format $FORMAT")
        appendLine("players ${match.playerCount}")
        appendLine("scores ${match.scores.joinToString(" ")}")
        appendLine("bolts ${match.bolts.joinToString(" ")}")
        appendLine("raspises ${match.raspises.joinToString(" ")}")
        // Договорённости сторон пишутся вместе с партией: поднятая с диска
        // партия должна доигрываться по тем правилам, по каким её начали.
        appendLine("samosval ${if (match.rules.samosval) 1 else 0}")
        appendLine("raspisPenalty ${if (match.rules.raspisPenalty) 1 else 0}")
        appendLine("aceMarriage ${if (match.rules.aceMarriage) 1 else 0}")
        appendLine("barrel ${match.barrelSeat?.toString() ?: DASH}")
        appendLine("barrelTries ${match.barrelTries}")
        appendLine("rounds ${match.roundsPlayed}")
        appendLine("next ${match.nextBidder()}")
        appendLine("winner ${match.winner?.toString() ?: DASH}")
        appendLine("recorded ${if (recorded) 1 else 0}")

        appendLine("phase ${round.phase.name}")
        appendLine("turn ${round.turn}")
        appendLine("first ${round.firstBidderSeat()}")
        appendLine("bid ${round.currentBid}")
        appendLine("declarer ${round.declarer?.toString() ?: DASH}")
        // Золотой кон — свойство кона, а не договорённость партии: он либо
        // объявлен в этом коне, либо нет. Настройка говорит лишь о том,
        // можно ли было его объявить.
        appendLine("golden ${if (round.golden) 1 else 0}")
        appendLine("raspis ${round.raspised?.toString() ?: DASH}")
        appendLine("trump ${round.trumpSuit?.let(::letterOf) ?: DASH}")
        appendLine("passed ${round.passedSeats().joinToString(" ")}")
        appendLine("named ${round.namedSeats().joinToString(" ")}")
        appendLine("points ${round.trickPointsAll().joinToString(" ")}")
        appendLine("marriage ${round.marriageAll().joinToString(" ")}")
        appendLine("tricks ${round.tricksAll().joinToString(" ")}")
        // Тузы, ушедшие во взятки, на руке уже не найти: признак пишется
        // отдельной строкой, иначе поднятая посреди розыгрыша партия
        // потеряла бы тузовый марьяж.
        appendLine("allAces ${(0 until round.playerCount).filter { round.hadAllAces(it) }.joinToString(" ")}")

        for (seat in 0 until round.playerCount) {
            appendLine("hand$seat ${round.handOf(seat).joinToString(" ") { codeOf(it) }}")
        }
        // Взятый прикуп и в руке, и в своей строке — это одни и те же карты
        // дважды. При чтении они сложились бы в 25 карт, и запись отверглась
        // бы целиком: партия, брошенная посреди розыгрыша, не поднялась бы
        // вовсе. Поэтому в прикупной строке остаётся только то, что ещё
        // лежит на столе, — нетронутый прикуп или его остаток.
        val inPlay = (0 until round.playerCount).flatMap { round.handOf(it) } +
            round.tableSeats().map { it.second }
        for (index in 0 until round.prikupCount) {
            val left = round.prikup(index).filterNot { it in inPlay }
            appendLine("prikup$index ${left.joinToString(" ") { codeOf(it) }}")
        }
        round.tableSeats().forEach { (seat, card) ->
            appendLine("table $seat:${codeOf(card)}")
        }
    }

    /**
     * Разобранная партия: матч, кон в нём и признак того, что доигранный кон
     * уже занесён в счёт. null — запись не наша или битая.
     */
    data class Saved(
        val match: ThousandMatch,
        val round: ThousandRound,
        val recorded: Boolean,
    )

    fun read(text: String): Saved? {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

        if (lines.firstOrNull() != HEADER) return null

        val fields = mutableMapOf<String, String>()
        val hands = sortedMapOf<Int, List<Card>>()
        val prikups = sortedMapOf<Int, List<Card>>()
        val table = mutableListOf<Pair<Int, Card>>()

        for (line in lines.drop(1)) {
            val key = line.substringBefore(' ').lowercase()
            val value = line.substringAfter(' ', "").trim()
            when {
                key == "table" -> table += parseTable(value) ?: return null
                key.startsWith("hand") ->
                    hands[key.removePrefix("hand").toIntOrNull() ?: return null] =
                        parseCards(value) ?: return null

                key.startsWith("prikup") ->
                    prikups[key.removePrefix("prikup").toIntOrNull() ?: return null] =
                        parseCards(value) ?: return null

                // Ключи кладём в нижнем регистре: запись может пройти через
                // чужой редактор, и «Barrel» в ней — та же «barrel».
                else -> fields[key] = value
            }
        }

        if (fields["format"]?.toIntOrNull() !in READABLE_FORMATS) return null
        val playerCount = fields["players"]?.toIntOrNull() ?: return null
        if (playerCount !in 2..3) return null
        // Руки идут подряд, без пропусков: hand0, hand1, ... Иначе игрок за
        // столом «потеряется» и партия соберётся не та.
        if (hands.keys != (0 until playerCount).toSet()) return null

        val scores = parseInts(fields["scores"], playerCount) ?: return null
        val bolts = parseInts(fields["bolts"], playerCount) ?: return null
        // В записи версии 1 счётчика росписей нет, и это не поломка: партия
        // шла без штрафа за них, то есть с нулём.
        val raspises = fields["raspises"]?.let { parseInts(it, playerCount) ?: return null }
            ?: List(playerCount) { 0 }
        val rules = ThousandRules(
            samosval = fields["samosval"]?.let { it == "1" } ?: false,
            raspisPenalty = fields["raspispenalty"]?.let { it == "1" } ?: false,
            aceMarriage = fields["acemarriage"]?.let { it == "1" } ?: false,
        )
        // Как и у росписей: в записи постарше строки нет, и это не поломка —
        // тузового марьяжа в той партии не было ни у кого.
        val allAces = fields["allaces"]?.let { parseSeats(it, playerCount) ?: return null }
            ?: emptySet()
        val points = parseInts(fields["points"], playerCount) ?: return null
        val marriage = parseInts(fields["marriage"], playerCount) ?: return null
        val tricks = parseInts(fields["tricks"], playerCount) ?: return null

        val phase = Phase.entries.firstOrNull { it.name == fields["phase"] } ?: return null
        val turn = fields["turn"]?.toIntOrNull() ?: return null
        if (turn !in 0 until playerCount) return null
        val first = fields["first"]?.toIntOrNull() ?: return null
        if (first !in 0 until playerCount) return null
        val bid = fields["bid"]?.toIntOrNull() ?: return null
        val declarer = parseIntOrNull(fields["declarer"])
        val golden = fields["golden"]?.let { it == "1" } ?: false
        val raspised = parseIntOrNull(fields["raspis"])?.takeIf { it in 0 until playerCount }
        val trump = parseSuitOrNull(fields["trump"])
        val barrel = parseIntOrNull(fields["barrel"])
        val barrelTries = fields["barreltries"]?.toIntOrNull() ?: return null
        val rounds = fields["rounds"]?.toIntOrNull() ?: return null
        val next = fields["next"]?.toIntOrNull() ?: return null
        val winner = parseIntOrNull(fields["winner"])
        val recorded = fields["recorded"]?.toIntOrNull() == 1

        // Колода «Тысячи» — ровно 24 карты, и ни одна не берётся из воздуха
        // и не исчезает. Всё, что не сошлось, — битая запись.
        val all = hands.values.flatten() + prikups.values.flatten() + table.map { it.second }
        if (all.size != 24 || all.toSet().size != 24) return null

        val round = ThousandRound.restore(
            hands = hands.values.toList(),
            prikups = prikups.values.toList().ifEmpty { listOf(emptyList()) },
            firstBidder = first,
            turnSeat = turn,
            phase = phase,
            currentBid = bid,
            declarer = declarer,
            trumpSuit = trump,
            passed = parseSeats(fields["passed"], playerCount) ?: return null,
            named = parseSeats(fields["named"], playerCount) ?: return null,
            trickPoints = points,
            marriage = marriage,
            tricks = tricks,
            table = table,
            barrelSeat = barrel,
            raspised = raspised,
            allAces = allAces,
            golden = golden,
        )

        val match = ThousandMatch.restore(
            playerCount = playerCount,
            scores = scores,
            bolts = bolts,
            barrelSeat = barrel,
            barrelTries = barrelTries,
            roundsPlayed = rounds,
            nextFirst = next,
            winner = winner,
            rules = rules,
            raspises = raspises,
        )
        return Saved(match, round, recorded)
    }

    // --- Мелочи разбора ---------------------------------------------------

    /** Пустая строка — это пустое множество мест, а не ошибка. */
    private fun parseInts(value: String?, expected: Int): List<Int>? {
        if (value == null) return null
        val parts = value.split(' ').filter { it.isNotBlank() }
        if (parts.isEmpty()) return List(expected) { 0 }
        if (parts.size != expected) return null
        return parts.map { it.toIntOrNull() ?: return null }
    }

    /**
     * Кто пасовал и кто называл сумму. Пустая строка — это «никто», а не
     * «нулевое место»: ноль здесь такое же место за столом, как единица.
     */
    private fun parseSeats(value: String?, playerCount: Int): Set<Int>? {
        if (value == null) return null
        val parts = value.split(' ').filter { it.isNotBlank() }
        val seats = parts.map { it.toIntOrNull() ?: return null }
        if (seats.any { it !in 0 until playerCount }) return null
        return seats.toSet()
    }

    private fun parseIntOrNull(value: String?): Int? =
        if (value == null || value == DASH || value.isBlank()) null else value.toIntOrNull()

    private fun parseSuitOrNull(value: String?): Suit? =
        if (value == null || value == DASH || value.isBlank()) null else parseSuit(value)

    private fun parseTable(value: String): Pair<Int, Card>? {
        val seat = value.substringBefore(':').toIntOrNull() ?: return null
        val card = parseCard(value.substringAfter(':', "")) ?: return null
        return seat to card
    }

    private fun codeOf(card: Card): String = "${card.rank.value}${letterOf(card.suit)}"

    private fun letterOf(suit: Suit): String = when (suit) {
        Suit.SPADES -> "S"
        Suit.HEARTS -> "H"
        Suit.DIAMONDS -> "D"
        Suit.CLUBS -> "C"
    }

    private fun parseSuit(value: String): Suit? = when (value.uppercase()) {
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
}
