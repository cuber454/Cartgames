package games.engine.thousand

import games.engine.Card
import games.engine.HandOrder
import games.engine.Suit

/**
 * Порядок руки в «Тысяче».
 *
 * От [HandOrder.sort] отличается старшинством: здесь десятка старше короля
 * ([Card.weight]), и сортировка по номиналу поставила бы руку вверх ногами —
 * игрок читал бы «король, десятка» там, где бьёт десятка.
 *
 * Козырь до первого объявленного марьяжа неизвестен: `null` здесь — не
 * ошибка, а обычное начало кона. Тогда козырей просто нет, и все масти
 * равны.
 */
fun orderHand(cards: List<Card>, order: HandOrder, trump: Suit?): List<Card> {
    fun suitWeight(suit: Suit): Int = if (suit == trump) TRUMP_WEIGHT else suit.ordinal

    return when (order) {
        HandOrder.BY_SUIT ->
            cards.sortedWith(compareBy({ suitWeight(it.suit) }, { it.weight }))

        HandOrder.BY_RANK ->
            cards.sortedWith(compareBy({ it.weight }, { suitWeight(it.suit) }))

        HandOrder.BY_RANK_TRUMPS_LAST ->
            cards.sortedWith(compareBy({ if (it.suit == trump) 1 else 0 }, { it.weight }))

        HandOrder.TRUMPS_FIRST ->
            cards.sortedWith(compareBy({ if (it.suit == trump) 0 else 1 }, { it.weight }))
    }
}

/** Козырь — в конец при сортировке по масти. */
private const val TRUMP_WEIGHT = 100
