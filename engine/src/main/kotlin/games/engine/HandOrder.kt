package games.engine

/**
 * Порядок, в котором рука показывается и проговаривается игроку.
 *
 * Для игры на слух порядок важнее, чем для игры глазами: карты нельзя
 * окинуть взглядом, их приходится держать в голове списком. Поэтому
 * порядок должен быть предсказуемым и переключаться одной командой.
 *
 * Внутри всегда возрастание — от шестёрки к тузу: слабые карты, которыми
 * ходят первыми, лежат в начале.
 */
enum class HandOrder(val title: String) {

    /** Масти идут группами, козыри — в конце. Как раскладывают в жизни. */
    BY_SUIT("по масти, козыри в конце"),

    /** От шестёрки к тузу, масти перемешаны. */
    BY_RANK("по достоинству"),

    /** Козыри в начале — когда важно сразу видеть, чем крыть. */
    TRUMPS_FIRST("козыри вперёд");

    fun sort(cards: List<Card>, trump: Suit): List<Card> = when (this) {
        BY_SUIT -> cards.sortedWith(compareBy({ suitWeight(it.suit, trump) }, { it.rank.value }))
        BY_RANK -> cards.sortedWith(compareBy({ it.rank.value }, { suitWeight(it.suit, trump) }))
        TRUMPS_FIRST -> cards.sortedWith(
            compareBy({ if (it.suit == trump) 0 else 1 }, { it.suit.ordinal }, { it.rank.value }),
        )
    }

    /** Следующий порядок по кругу — для кнопки «Порядок». */
    fun next(): HandOrder = entries[(ordinal + 1) % entries.size]

    /**
     * Вес масти для сортировки. Козырь получает большой вес, поэтому
     * оказывается последним, но остаётся отдельной группой: внутри неё
     * карты тоже идут по возрастанию.
     */
    private fun suitWeight(suit: Suit, trump: Suit): Int =
        if (suit == trump) TRUMP_WEIGHT else suit.ordinal

    private companion object {
        const val TRUMP_WEIGHT = 100
    }
}
