package games.cardgames.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Озвучка приложения. Всё, что игра говорит вслух, идёт через этот класс,
 * поэтому позже здесь же появится выбор голоса и скорости речи.
 *
 * Сейчас используется системный синтезатор (тот же, что у скринридера).
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    private var engine: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            engine?.language = Locale("ru", "RU")
            ready = true
        }
    }

    /** Сказать фразу. [interrupt] — перебить то, что говорится сейчас. */
    fun say(text: String, interrupt: Boolean = true) {
        if (!ready || text.isBlank()) return
        val mode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        engine?.speak(text, mode, null, text)
    }

    fun stop() {
        engine?.stop()
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
    }
}
