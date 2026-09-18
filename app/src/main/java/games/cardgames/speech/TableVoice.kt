package games.cardgames.speech

import android.view.View
import games.cardgames.diag.Journal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Сколько примерно звучит фраза: знаков в секунду у синтезатора около
 * пятнадцати, то есть ~70 мс на знак при обычной скорости.
 *
 * Число нужно, чтобы не перебивать собственную речь. Ни наш синтезатор, ни
 * скринридер не умеют «договорить до конца и только потом сказать
 * следующее»: новая фраза обрывает предыдущую. Значит, паузу держим сами —
 * по оценке, потому что точной длины речи никто не сообщает.
 */
private const val SPEECH_MS_PER_CHAR = 70L

/** Сколько звучать этой фразе при такой скорости речи. */
fun speechMs(text: String, rate: Float): Long =
    (text.length * SPEECH_MS_PER_CHAR / rate.coerceIn(0.5f, 4.0f)).toLong()

/**
 * Зазор между звуком и фразой. Звук карты короткий (70 мс), но и этого
 * хватает: голос, начатый в тот же миг, налезает на шлепок, и на слух это
 * одна мутная каша вместо двух понятных событий.
 */
const val PHRASE_GAP_MS = 60L

/**
 * Речь за столом: кто говорит, когда и с какой паузой.
 *
 * Одна на обе игры намеренно. Правила арбитража между приложением и
 * скринридером выстраданы (0.7): говорить должен ровно один, иначе две речи
 * накладываются. Второй экземпляр этой логики в соседней игре рано или
 * поздно разошёлся бы с первым — и разошёлся бы молча, потому что на слух
 * это замечаешь не сразу.
 *
 * Что берётся лямбдами, а не значениями: скринридер включают и выключают
 * прямо посреди партии, и «кто сейчас говорит» меняется на ходу. Объект
 * живёт в `remember`, то есть переживает перерисовку, — значит, меняющиеся
 * значения он обязан спрашивать сам.
 *
 * @param appVoice говорит ли приложение прямо сейчас.
 * @param rate скорость речи — по ней считается длина паузы.
 * @param minWaitMs сколько ждать минимум, чтобы бот не тараторил.
 * @param remember куда записать фразу до того, как она прозвучит: её должна
 *   знать кнопка «Повтори» с этого же мгновения.
 */
class TableVoice(
    private val speaker: Speaker,
    /**
     * Голос соперника. null — бот говорит тем же синтезатором, что и
     * приложение: так было до 0.8, и так остаётся, если игрок не завёл
     * боту отдельный голос.
     */
    private val botSpeaker: Speaker? = null,
    private val view: View?,
    private val appVoice: () -> Boolean,
    private val rate: () -> Float,
    private val scope: CoroutineScope,
    private val minWaitMs: Long,
    private val remember: (String) -> Unit,
) {

    /** Когда договорит то, что сказано сейчас. */
    private var endsAt = 0L

    /**
     * Событие за столом: раздача, ход бота, итог партии. Когда говорит
     * скринридер, фразу отдаём ему, и он произносит её своим голосом следом
     * за тем, что читал, а не поверх. Промолчать тут нельзя: о ходе бота
     * игрок иначе не узнает вовсе.
     *
     * [afterMs] — пауза под звук хода: фраза запоминается сейчас, а звучит
     * потом.
     */
    fun say(text: String, whenReady: Boolean = false, afterMs: Long = 0L) {
        note(text, afterMs)
        if (afterMs <= 0) {
            sayEvent(view, speaker, appVoice(), text, whenReady)
            return
        }
        scope.launch {
            delay(afterMs)
            sayEvent(view, speaker, appVoice(), text, whenReady)
        }
    }

    /**
     * Реплика бота — его голосом, если он у бота свой.
     *
     * Отдельным методом, а не флагом при [say]: за столом говорят двое, и
     * перепутать, чья это фраза, значит услышать бота голосом приложения.
     * Когда говорит скринридер, разницы нет и быть не может — у него один
     * голос на всё, — поэтому фраза просто уходит ему, как и любая другая.
     */
    fun sayBot(text: String, whenReady: Boolean = false, afterMs: Long = 0L) {
        note(text, afterMs)
        val voice = botSpeaker ?: speaker
        if (afterMs <= 0) {
            sayEvent(view, voice, appVoice(), text, whenReady)
            return
        }
        scope.launch {
            delay(afterMs)
            sayEvent(view, voice, appVoice(), text, whenReady)
        }
    }

    /**
     * Свой ход. Скринридер уже прочитал карту, которую игрок нажал, — второй
     * раз называть её не надо, это и была та самая каша. Фразу всё равно
     * запоминаем: её повторит кнопка «Повтори».
     *
     * [aloud] — ход, о котором скринридер сам не расскажет: он прочитал
     * карту, которой ходили, а не то, что с ней пришло. Прикуп — ровно такой
     * случай: две новые карты игрок иначе ищет в руке сам и сравнивает на
     * слух с тем, что помнит. Такую фразу отдаём тем же путём, что и событие
     * за столом: говорит тот, чья сейчас очередь, — при работающем
     * скринридере он, при выключенном приложение.
     */
    fun sayOwnMove(text: String, afterMs: Long = 0L, aloud: Boolean = false) {
        note(text, afterMs)
        if (aloud) {
            say(text, afterMs = afterMs)
            return
        }
        if (!appVoice()) {
            Journal.note("речь", "сказано только в «Повтори» (говорит скринридер): $text")
            return
        }
        if (afterMs <= 0) {
            speaker.say(text)
        } else {
            scope.launch { delay(afterMs); speaker.say(text) }
        }
    }

    /**
     * Игрок нажал кнопку и ждёт ответа. Говорит тот же, кто говорит и за
     * столом: работает скринридер — он, выключен — приложение.
     *
     * Здесь так было не всегда: ответ на нажатие всегда уходил синтезатору
     * приложения, и при включённом скринридере он перебивал его своим
     * голосом — при том, что настройка обещала обратное. Кнопки — не
     * исключение из правила «говорит ровно один», а его часть (Катерина,
     * 18.09: «когда озвучка программы выключена, пусть и «Что можно», и
     * «Повтори» читает скринридер»).
     *
     * В «Повтор» такой ответ не идёт: повторяют событие за столом, а не
     * ответ на собственное нажатие. Иначе, спросив «Что можно» и нажав затем
     * «Повтори», игрок слышит один и тот же ответ дважды — и обе кнопки
     * выглядят как две одинаковые.
     */
    fun sayRequested(text: String) {
        note(text, repeatable = false)
        val aloud = appVoice()
        if (aloud) Journal.note("речь", "игрок спросил — отвечает приложение: $text")
        sayEvent(view, speaker, aloud, text)
    }

    /** Сколько ещё ждать, чтобы не перебить сказанное: минимум [minWaitMs]. */
    fun waitMs(): Long = (endsAt - System.currentTimeMillis()).coerceAtLeast(minWaitMs)

    private fun note(text: String, afterMs: Long = 0L, repeatable: Boolean = true) {
        if (repeatable) remember(text)
        endsAt = System.currentTimeMillis() + afterMs + speechMs(text, rate())
    }
}
