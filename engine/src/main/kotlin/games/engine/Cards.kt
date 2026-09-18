package games.engine

import kotlin.random.Random

/**
 * Масть. У масти две формы, и это не прихоть: по-русски «семёрка бубён»,
 * но «козырь — буба» — родительный падеж и именительный не совпадают.
 *
 * [title] — как масть называется сама по себе: «Козырь — буба», «Хвалить
 * крести», [spoken] — форма для названия карты: «семёрка бубён»,
 * [sign] — значок для нарисованной карты.
 */
enum class Suit(val title: String, val spoken: String, val sign: String) {
    SPADES("пики", "пик", "♠"),
    HEARTS("черви", "червей", "♥"),
    DIAMONDS("буба", "бубён", "♦"),
    CLUBS("крести", "треф", "♣"),
}

/**
 * Достоинство. [value] — для сравнения карт, [spoken] — для озвучки,
 * [sign] — короткая надпись на нарисованной карте.
 */
enum class Rank(val value: Int, val spoken: String, val sign: String) {
    SIX(6, "шестёрка", "6"),
    SEVEN(7, "семёрка", "7"),
    EIGHT(8, "восьмёрка", "8"),
    NINE(9, "девятка", "9"),
    TEN(10, "десятка", "10"),
    JACK(11, "валет", "В"),
    QUEEN(12, "дама", "Д"),
    KING(13, "король", "К"),
    ACE(14, "туз", "Т"),
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
