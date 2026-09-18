package games.engine.kozel

import games.engine.tiles.TILE_COUNT
import games.engine.tiles.Tile
import games.engine.tiles.tile

/**
 * Матч «Козла» на диск и обратно — простым текстом.
 *
 * Как и в «Дураке», текстом: запись можно открыть глазами, когда что-то
 * поедет, и починить руками, не пересобирая приложение. Строки читаются по
 * ключу, чужие ключи молча пропускаются, поэтому поле, добавленное в новых
 * версиях, старую запись не сломает. Ломает только смена [FORMAT] — тогда
 * запись честно отвергается: начать новую партию лучше, чем собрать её из
 * разночтений.
 *
 * Кость записывается двумя числами через дефис — «0-5» это пусто-пять,
 * «6-6» — дубль. Порядок половин при чтении не важен: кости 3-6 и 6-3 —
 * одна и та же.
 *
 * Доигранный раунд не сохраняется: он уже итог, продолжать его нечем. На
 * диске лежит матч на паузе — счёт, номер раунда и недоигранная раздача.
 */
object KozelSave {

    /** Версия формата. Меняется вместе с составом полей. */
    const val FORMAT = 1

    private const val HEADER = "game kozel"

    /** Пустой линии концов нет — на их месте в записи стоит прочерк. */
    private const val NO_END = "-"

    /** Матч одной записью. */
    fun write(match: KozelMatch): String = buildString {
        val round = match.round
        appendLine(HEADER)
        appendLine("format $FORMAT")
        appendLine("target ${match.rules.target}")
        appendLine("emptybonus ${match.rules.emptyDoubleBonus}")
        appendLine("round ${match.roundNumber}")
        match.table.forEachIndexed { seat, points -> appendLine("score$seat $points") }
        appendLine("turn ${round.turn}")
        // Пропуски едут на диск, а не сбрасываются: два пропуска подряд
        // закрывают раунд «рыбой», и партия, поднятая после первого, обязана
        // помнить, что он был.
        appendLine("passes ${round.passes}")
        appendLine("bazaar ${round.bazaarTiles.joinToString(" ") { codeOf(it) }}")
        for (seat in 0 until SEATS) {
            appendLine("hand$seat ${round.handOf(seat).joinToString(" ") { codeOf(it) }}")
        }
        appendLine("line ${round.table.tiles.joinToString(" ") { codeOf(it) }}")
        appendLine("ends ${round.table.left?.toString() ?: NO_END} ${round.table.right?.toString() ?: NO_END}")
    }

    /**
     * Прочитать матч. null — если запись не наша, побита или не сходится
     * (кость потерялась, задвоилась или линия не складывается). Звать это
     * будет экран при запуске, и на null он просто начинает новую партию:
     * пугать игрока разбором поломанного файла незачем.
     */
    fun read(text: String): KozelMatch? {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

        if (lines.firstOrNull() != HEADER) return null

        val fields = mutableMapOf<String, String>()
        val hands = sortedMapOf<Int, List<Tile>>()

        for (line in lines.drop(1)) {
            val key = line.substringBefore(' ').lowercase()
            val value = line.substringAfter(' ', "").trim()
            if (key.startsWith("hand")) {
                hands[key.removePrefix("hand").toIntOrNull() ?: return null] =
                    parseTiles(value) ?: return null
            } else {
                fields[key] = value
            }
        }

        if (fields["format"]?.toIntOrNull() != FORMAT) return null
        // Руки идут подряд, без пропусков: hand0, hand1, ... Иначе запись
        // собрана не про этот стол.
        if (hands.keys != (0 until SEATS).toSet()) return null

        val target = fields["target"]?.toIntOrNull() ?: return null
        val roundNumber = fields["round"]?.toIntOrNull() ?: return null
        val turn = fields["turn"]?.toIntOrNull() ?: return null
        if (target <= 0 || roundNumber < 1 || turn !in 0 until SEATS) return null

        val scores = (0 until SEATS).map { fields["score$it"]?.toIntOrNull() ?: return null }
        // Матч, у которого кто-то уже дошёл до цели, доигран: это не пауза,
        // а итог, и поднимать его незачем.
        if (scores.any { it >= target }) return null

        val rules = KozelRules(
            target = target,
            emptyDoubleBonus = fields["emptybonus"]?.toIntOrNull() ?: 0,
        )
        val bazaar = parseTiles(fields["bazaar"].orEmpty()) ?: return null
        val line = parseLine(fields["line"].orEmpty(), fields["ends"]) ?: return null

        // Набор «дубль-шесть» — ровно 28 костей, и ни одна не берётся из
        // воздуха и не исчезает. Не сходится — запись битая.
        val all = bazaar + line.tiles + hands.values.flatten()
        if (all.size != TILE_COUNT || all.toSet().size != TILE_COUNT) return null

        return KozelMatch.restore(
            rules = rules,
            round = KozelRound.of(
                rules = rules,
                hands = hands.values.toList(),
                bazaar = bazaar,
                opener = turn,
                line = line,
                turn = turn,
                passes = fields["passes"]?.toIntOrNull() ?: 0,
            ),
            scores = scores,
            roundNumber = roundNumber,
        )
    }

    // --- Кости в текст и обратно ------------------------------------------

    private fun codeOf(tile: Tile): String = "${tile.low}-${tile.high}"

    private fun parseTile(token: String): Tile? {
        val halves = token.split('-')
        if (halves.size != 2) return null
        val low = halves[0].toIntOrNull() ?: return null
        val high = halves[1].toIntOrNull() ?: return null
        return runCatching { tile(low, high) }.getOrNull()
    }

    /** Пустые куски пропускаем: лишний пробел при ручной правке — не поломка. */
    private fun parseTiles(value: String): List<Tile>? =
        value.split(' ').filter { it.isNotBlank() }.map { parseTile(it) ?: return null }

    /**
     * Линия и её концы.
     *
     * Концы хранятся отдельно от костей, потому что по самим костям их не
     * восстановить: кость 1-3 у левого края может стоять наружу и единицей,
     * и тройкой (KOZEL.md, 2.3). А раз они записаны отдельно, их надо
     * сверить с костями — иначе запись с перепутанным порядком костей
     * прочиталась бы как настоящая партия, только с другой линией.
     */
    private fun parseLine(tiles: String, ends: String?): Line? {
        val laid = parseTiles(tiles) ?: return null
        val parts = ends?.split(' ')?.filter { it.isNotBlank() } ?: return null

        if (laid.isEmpty()) {
            return if (parts == listOf(NO_END, NO_END)) Line.EMPTY else null
        }
        // Кость не лежит на столе дважды.
        if (laid.toSet().size != laid.size) return null
        if (parts.size != SEATS) return null

        val left = parts[0].toIntOrNull() ?: return null
        val right = parts[1].toIntOrNull() ?: return null
        val line = Line(laid, left, right)
        return if (line.coherent()) line else null
    }

    /**
     * Складывается ли линия: соседние кости сходятся числом, а крайние
     * держат наружу те концы, что записаны.
     */
    private fun Line.coherent(): Boolean {
        for (i in 0 until tiles.size - 1) {
            val here = tiles[i]
            val next = tiles[i + 1]
            if (!next.has(here.low) && !next.has(here.high)) return false
        }
        val first = tiles.first()
        val last = tiles.last()
        // У одинокой кости концами стали обе половины — так её кладут
        // в пустую линию (Line.place).
        if (tiles.size == 1) return left == first.low && right == first.high
        return first.has(left ?: return false) && last.has(right ?: return false)
    }
}
