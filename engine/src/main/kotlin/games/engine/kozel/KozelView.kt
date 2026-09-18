package games.engine.kozel

import games.engine.tiles.Tile
import games.engine.tiles.TileSet

/**
 * Что бот знает о раунде: открытое состояние и только оно.
 *
 * Здесь нет ни твоей руки, ни костей в базаре — вместо них счётчики. Это
 * не осторожность и не обещание, а свойство типа: решение бота принимает
 * [KozelView], а не [KozelRound], поэтому подсмотреть ему попросту неоткуда.
 * Всё, что есть в этом классе, человек за столом видит своими глазами: свои
 * кости, линия с её двумя концами, сколько костей у противника и сколько
 * осталось в закрытом базаре.
 *
 * Отсюда же следует, что базар бот берёт вслепую: он знает только, сколько
 * там костей, а какая попадётся — не знает никто, и разыгрывает её раунд
 * ([KozelRound.apply]), а не бот (KOZEL.md, 4.1–4.2).
 */
data class KozelView(
    /** Место, за которое решает бот. */
    val seat: Int,
    /** Своя рука. Её видит и человек. */
    val hand: List<Tile>,
    /** Линия на столе: она открыта всем. */
    val line: Line,
    /** Сколько костей на руке у противника. Которые именно — неизвестно. */
    val opponentHandSize: Int,
    /** Сколько костей осталось в базаре. Которые именно — неизвестно. */
    val bazaarSize: Int,
    /** Чей ход. */
    val turn: Int,
) {

    /** Место противника. За столом их двое, поэтому это просто «не он». */
    val opponentSeat: Int get() = KozelRound.other(seat)

    /**
     * Допустимые ходы — те же, что предложили бы игроку: подходящая кость
     * есть — ходить ею, нет — брать из базара, а пустой базар — пропуск.
     *
     * Считаются они по открытому состоянию и тем же кодом, что и в раунде
     * ([placements]): разойтись этим двум нельзя.
     */
    fun legalMoves(): List<KozelMove> {
        if (turn != seat) return emptyList()

        val places = line.placements(hand)
        if (places.isNotEmpty()) return places

        return if (bazaarSize > 0) listOf(KozelMove.Draw) else listOf(KozelMove.Pass)
    }

    /** Кости, которых бот не видел: ни у себя, ни на столе. */
    fun unseen(): List<Tile> {
        val seen = HashSet(hand)
        seen += line.tiles
        return TileSet.full.filterNot { it in seen }
    }

    companion object {

        /**
         * Открытая часть раунда глазами места [seat].
         *
         * Единственный мост между раундом и ботом — и мост односторонний:
         * наружу отсюда уходят размеры рук и базара, а не их содержимое.
         */
        fun of(round: KozelRound, seat: Int): KozelView = KozelView(
            seat = seat,
            hand = round.handOf(seat).toList(),
            line = round.table,
            opponentHandSize = round.handSize(KozelRound.other(seat)),
            bazaarSize = round.bazaarSize,
            turn = round.turn,
        )
    }
}
