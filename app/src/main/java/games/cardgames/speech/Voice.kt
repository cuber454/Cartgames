package games.cardgames.speech

import android.view.View
import games.cardgames.settings.VoiceMode

/**
 * Кто говорит прямо сейчас — приложение или скринридер.
 *
 * Это одно правило на всё приложение, и звучит оно так: в каждый момент
 * говорит ровно один. Скринридер говорит своим синтезатором и о приложении
 * не знает; приложение говорит то, чего на экране нет. Если оба разом,
 * игрок слышит две речи поверх друг друга — кашу.
 *
 * [VoiceMode.ALWAYS] — говорит приложение (скринридеру велено умолкнуть),
 * [VoiceMode.NEVER] — говорит скринридер, [VoiceMode.AUTO] — скринридер,
 * если он работает.
 */
fun VoiceMode.appSpeaks(screenReaderOn: Boolean): Boolean = when (this) {
    VoiceMode.ALWAYS -> true
    VoiceMode.NEVER -> false
    VoiceMode.AUTO -> !screenReaderOn
}

/**
 * Событие за столом — ход бота, раздача, итог партии.
 *
 * Когда говорит приложение, оно произносит фразу своим синтезатором. Когда
 * говорит скринридер, фраза не теряется: она уходит ему
 * ([View.announceForAccessibility]), и он произносит её своим голосом в свою
 * очередь — следом за тем, что читал, а не поверх. Молчать в этом случае
 * нельзя: о ходе бота игрок иначе не узнает вовсе.
 */
fun sayEvent(view: View?, speaker: Speaker, appVoice: Boolean, text: String, whenReady: Boolean = false) {
    if (text.isBlank()) return
    if (appVoice) {
        if (whenReady) speaker.sayWhenReady(text) else speaker.say(text)
    } else {
        // announceForAccessibility помечен устаревшим в API 36, но замены
        // ему нет: это по-прежнему единственный способ отдать фразу
        // скринридеру, не трогая экран. Он и внутри делает ровно то же —
        // шлёт событие TYPE_ANNOUNCEMENT, — так что руками писать то же
        // самое смысла нет. Работает со времён API 16 и работает сейчас,
        // а сборка лишь предупреждает.
        runCatching { view?.announceForAccessibility(text) }
    }
}
