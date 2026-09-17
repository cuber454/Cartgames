package games.cardgames.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import games.cardgames.R

/**
 * Звуки стола: шорох раздачи, стук карты, сгребание со стола.
 *
 * Зрячий видит, как карты летят на стол. Незрячему то же самое даёт звук —
 * поэтому каждый ход озвучен не только голосом, но и шлепком карты.
 *
 * Файлы короткие и лежат в res/raw, собраны скриптом tools/make-sounds.py.
 * SoundPool, а не MediaPlayer: он держит звук в памяти и играет без
 * задержки, а нам важно, чтобы стук совпал с ходом.
 *
 * Здесь два разных набора, и выключаются они порознь. **Шум стола**
 * ([deal], [card], [take]) — это то, что происходит с картами: он идёт
 * всегда, поверх игры, и мешать ему незачем. **Сигналы** ([start], [turn],
 * [win], [lose], [bolt]) — это ноты про события, и они куда навязчивее:
 * сигнал «твой ход» звучит по сто раз за партию, а играют и по десять
 * партий подряд. Поэтому у сигналов свой выключатель — игрок, которому
 * они надоели, гасит их, не теряя стук карт.
 */
class TableSounds(context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    // Ноль означает «не загрузилось» — тогда просто молчим, а не падаем.
    private val dealId = load(context, R.raw.deal)
    private val cardId = load(context, R.raw.card)
    private val takeId = load(context, R.raw.take)
    private val startId = load(context, R.raw.start)
    private val turnId = load(context, R.raw.turn)
    private val winId = load(context, R.raw.win)
    private val loseId = load(context, R.raw.lose)
    private val boltId = load(context, R.raw.bolt)

    private fun load(context: Context, res: Int): Int =
        runCatching { pool.load(context.applicationContext, res, 1) }.getOrDefault(0)

    /** Раздача в начале партии. */
    fun deal() = play(dealId)

    /** Карта легла на стол или отбита. */
    fun card() = play(cardId)

    /** Карты забрали со стола. */
    fun take() = play(takeId)

    /** Начало партии или кона: восходящая квинта. */
    fun start() = play(startId)

    /** Ход вернулся к игроку. Самый частый сигнал, поэтому самый тихий. */
    fun turn() = play(turnId)

    /** Партия выиграна. */
    fun win() = play(winId)

    /** Партия проиграна. */
    fun lose() = play(loseId)

    /** Болт: кон не сыгран, запись в минус. */
    fun bolt() = play(boltId)

    private fun play(id: Int) {
        if (id != 0) pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }

    companion object {
        // Длительность каждого звука — её приходится знать наизусть: SoundPool
        // не сообщает, сколько играет семпл, а фразу надо начинать после него,
        // иначе голос и звук накладываются и выходит каша. Числа сняты с
        // файлов, которые собирает tools/make-sounds.py.
        const val CARD_MS = 70
        const val TAKE_MS = 380
        const val DEAL_MS = 680
        const val START_MS = 460
        const val TURN_MS = 90
        const val WIN_MS = 840
        const val LOSE_MS = 630
        const val BOLT_MS = 300
    }
}
