package games.cardgames.speech

import kotlin.random.Random

/**
 * Мешок фраз: зачины тянутся без возврата, чтобы бот не повторялся.
 *
 * Общий на все игры намеренно. Правило «не повторяться» — не украшение
 * речи, а то, чем партнёр отличается от автомата: два одинаковых зачина
 * подряд слышны сразу. Второй экземпляр этой механики в соседней игре
 * разошёлся бы с первым молча, как и всякий второй экземпляр логики.
 *
 * [K] — повод для реплики: своя группа зачинов у первой кости, у взятия из
 * базара, у пропуска. Мешки независимы, поэтому «беру» и «кладу» никогда не
 * тянут зачины из одной кучи.
 *
 * На стыке мешков первый зачин не совпадает с последним: без этого правило
 * «без возврата» нарушалось бы ровно там, где его и замечают.
 */
class PhraseBag<K>(private val rng: Random = Random.Default) {

    private val bags = mutableMapOf<K, MutableList<String>>()
    private val lastPick = mutableMapOf<K, String>()

    /** Следующий зачин для повода [key] из его вариантов. */
    fun draw(key: K, variants: List<String>): String {
        require(variants.isNotEmpty()) { "мешок зачинов пуст: у повода $key нет ни одной фразы" }

        val bag = bags.getOrPut(key) { mutableListOf() }
        if (bag.isEmpty()) {
            bag += variants.shuffled(rng)
            if (bag.size > 1 && bag[0] == lastPick[key]) {
                val first = bag[0]
                bag[0] = bag[1]
                bag[1] = first
            }
        }
        return bag.removeAt(0).also { lastPick[key] = it }
    }
}
