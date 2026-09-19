package games.cardgames.speech

import android.view.View
import games.cardgames.diag.Journal
import games.cardgames.settings.VoiceMode

/**
 * Кто говорит прямо сейчас — приложение, скринридер или никто.
 *
 * Это одно правило на всё приложение, и звучит оно так: в каждый момент
 * говорит ровно один. Скринридер говорит своим синтезатором и о приложении
 * не знает; приложение говорит то, чего на экране нет. Если оба разом,
 * игрок слышит две речи поверх друг друга — кашу.
 *
 * [READER] — не то же, что [NONE]: и там и тут приложение молчит, но
 * скринридеру фраза всё равно уходит, и он её читает. [NONE] не уходит
 * никому.
 */
enum class Speech(val title: String) {
    /** Говорит приложение своим синтезатором. */
    APP("приложение"),

    /** Говорит скринридер: фраза уходит ему, приложение молчит. */
    READER("скринридер"),

    /** Не говорит никто: игра молчит целиком. */
    NONE("никто"),
    ;

    /** Говорит ли сейчас приложение — то же правило, что было до «никто». */
    val speaks: Boolean get() = this == APP
}

/**
 * Кто говорит при этой настройке.
 *
 * [VoiceMode.ALWAYS] — приложение (скринридеру велено умолкнуть), [VoiceMode.NEVER]
 * — скринридер, [VoiceMode.AUTO] — скринридер, если он работает, [VoiceMode.SILENT]
 * — никто.
 */
fun VoiceMode.speech(screenReaderOn: Boolean): Speech = when (this) {
    VoiceMode.ALWAYS -> Speech.APP
    VoiceMode.NEVER -> Speech.READER
    VoiceMode.AUTO -> if (screenReaderOn) Speech.READER else Speech.APP
    VoiceMode.SILENT -> Speech.NONE
}

/**
 * Событие за столом — ход бота, раздача, итог партии.
 *
 * Когда говорит приложение, оно произносит фразу своим синтезатором. Когда
 * говорит скринридер, фраза не теряется: она уходит ему
 * ([View.announceForAccessibility]), и он произносит её своим голосом в свою
 * очередь — следом за тем, что читал, а не поверх. Молчать в этом случае
 * нельзя: о ходе бота игрок иначе не узнает вовсе.
 *
 * А вот при [Speech.NONE] молчание и есть ответ: так играют зрячие, и тут
 * фраза не потеряна, а не нужна — читать вслух то, что и так видно, им
 * незачем. В журнал её тоже не пишем: журнал — про речь, которой не было.
 */
fun sayEvent(view: View?, speaker: Speaker, speech: Speech, text: String, whenReady: Boolean = false) {
    if (text.isBlank()) return
    when (speech) {
        Speech.APP -> if (whenReady) speaker.sayWhenReady(text) else speaker.say(text)

        Speech.READER -> {
            Journal.note("речь", "скринридеру (${speaker.readerTitle()}): $text")
            // announceForAccessibility помечен устаревшим в API 36, но замены
            // ему нет: это по-прежнему единственный способ отдать фразу
            // скринридеру, не трогая экран. Он и внутри делает ровно то же —
            // шлёт событие TYPE_ANNOUNCEMENT, — так что руками писать то же
            // самое смысла нет. Работает со времён API 16 и работает сейчас,
            // а сборка лишь предупреждает.
            runCatching { view?.announceForAccessibility(text) }
        }

        Speech.NONE -> Unit
    }
}
