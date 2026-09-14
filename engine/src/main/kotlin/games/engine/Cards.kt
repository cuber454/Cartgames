package games.engine

import kotlin.random.Random

/**
 * Масть. [spoken] — форма для озвучки: «семёрка червей».
 */
enum class Suit(val spoken: String) {
    SPADES("пик"),
    HEARTS("червей"),
    DIAMONDS("бубён"),
    CLUBS("треф"),
}

/**
 * Достоинство. [value] — для сравнения карт, [spoken] — для озвучки.
 */
enum class Rank(val value: Int, val spoken: String) {
    SIX(6, "шестёрка"),
    SEVEN(7, "семёрка"),
    EIGHT(8, "восьмёрка"),
    NINE(9, "девятка"),
    TEN(10, "десятка"),
    JACK(11, "валет"),
    QUEEN(12, "дама"),
    KING(13, "король"),
    ACE(14, "туз"),
}

/**
 * Карта. Озвучка собирается из [Rank.spoken] и [Suit.spoken] —
 * «семёрка червей», «туз пик». Это единственное место, где карта
 * превращается в речь: экран, голос и подсказки говорят одно и то же.
 */
data class Card(val rank: Rank, val suit: Suit) {
    fun spoken(): String = "${rank.spoken} ${suit.spoken}"

    override fun toString(): String = spoken()
}

/** Полная колода «Дурака» — 36 карт, от шестёрки до туза. */
fun fullDeck36(): MutableList<Card> =
    Suit.entries.flatMap { suit -> Rank.entries.map { rank -> Card(rank, suit) } }.toMutableList()

/**
 * Колода. Верх — оттуда берут карты, низ — по нему в «Дураке» определяют
 * козырь, поэтому нижняя карта остаётся в колоде до самого конца.
 */
class Deck(cards: List<Card> = fullDeck36()) {
    private val cards: ArrayDeque<Card> = ArrayDeque(cards)

    val size: Int get() = cards.size

    fun isEmpty(): Boolean = cards.isEmpty()

    /** Взять верхнюю карту. Вызывать только у непустой колоды. */
    fun draw(): Card = cards.removeFirst()

    /** Нижняя карта — та, по которой виден козырь. */
    fun bottomOrNull(): Card? = cards.lastOrNull()

    fun shuffle(random: Random) = cards.shuffle(random)

    fun toList(): List<Card> = cards.toList()
}
