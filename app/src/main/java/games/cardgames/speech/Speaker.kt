package games.cardgames.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import games.cardgames.diag.Journal
import java.util.Locale

/**
 * Озвучка приложения. Всё, что игра говорит вслух, идёт через этот класс.
 *
 * Синтезатор и голос выбирает игрок в настройках: в системе их может быть
 * несколько, и голоса у них звучат по-разному, а для незрячего голос — это
 * и есть приложение. Поэтому здесь можно и послушать выбранный голос,
 * не выходя из настроек.
 *
 * Синтезатор поднимается не сразу, и до готовности говорить нечего —
 * для этого есть [sayWhenReady]: фраза подождёт и прозвучит, как только
 * движок ответит.
 */
class Speaker(
    context: Context,
    rate: Float = 1.0f,
    private val enginePackage: String? = null,
    private val voiceName: String? = null,
    /**
     * Высота голоса: 1.0 — как он звучит сам, меньше — ниже, больше — выше.
     *
     * Нужна не для красоты: за столом говорят трое — приложение, бот в дураке
     * и бот в тысяче, — и на слух их надо различать. Если игрок выбрал разные
     * голоса, высота не нужна; если нет, ею и разводим.
     */
    private val pitch: Float = 1.0f,
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext

    /**
     * Текущая скорость. В отличие от движка и голоса её меняют на живом
     * синтезаторе: пересобирать Speaker ради этого не нужно.
     */
    private var speechRate = rate
    private var engine: TextToSpeech? = TextToSpeech(appContext, this, enginePackage)
    private var ready = false
    private var retried = false
    private var pending: String? = null

    /**
     * Голос и высота для ближайшей фразы — их ставит проба голоса.
     * Отдельно от [voiceName] и [pitch] затем, что проба меняет их на живом
     * движке и на ходу, а эти два заданы раз и навсегда при сборке.
     */
    private var pendingVoice: String? = null
    private var pendingPitch: Float? = null

    /** Позвать, когда синтезатор поднялся: экран настроек по этому сигналу
     *  перечитывает список голосов. */
    var onReady: (() -> Unit)? = null

    private val accessibility =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager

    /**
     * Работает ли сейчас скринридер (TalkBack, «Специальные возможности» и т. п.).
     *
     * Это важно: скринридер говорит своим синтезатором и о приложении не
     * знает — если приложение вдобавок произнесёт ту же карту, игрок услышит
     * две речи разом. От этого значения зависит, кто говорит (см.
     * [games.cardgames.speech.appSpeaks]).
     *
     * Значение живое: скринридер включают и выключают прямо посреди партии.
     */
    var screenReaderOn by mutableStateOf(readsScreen())
        private set

    private val touchExplorationListener =
        AccessibilityManager.TouchExplorationStateChangeListener {
            screenReaderOn = readsScreen()
            Journal.note("речь", "скринридер теперь ${readerTitle()}")
        }

    init {
        runCatching { accessibility?.addTouchExplorationStateChangeListener(touchExplorationListener) }
        // Кто говорит — половина всех жалоб «слышу не то». Пишем при создании:
        // по этой строке видно, считало ли приложение скринридер работающим.
        Journal.note(
            "речь",
            "скринридер: ${readerTitle()}, " +
                "доступность ${if (accessibility?.isEnabled == true) "включена" else "выключена"}, " +
                "обзор касанием ${if (accessibility?.isTouchExplorationEnabled == true) "включён" else "выключен"}",
        )
    }

    private fun readsScreen(): Boolean = runCatching {
        accessibility?.isEnabled == true && accessibility?.isTouchExplorationEnabled == true
    }.getOrDefault(false)

    /**
     * Попросить скринридер замолчать — перед своей фразой.
     *
     * Он о приложении не знает и сам не уступит: без этого он продолжает
     * читать кнопку поверх нашей фразы, и выходит каша. `interrupt` — штатный
     * способ Android ровно для этого случая.
     */
    fun interruptScreenReader() {
        if (!screenReaderOn) return
        runCatching { accessibility?.interrupt() }
    }

    /** Работает ли скринридер по мнению приложения. Пишется в журнал при запуске. */
    fun readerTitle(): String = if (screenReaderOn) "работает" else "не работает"

    override fun onInit(status: Int) {
        // Выбранный синтезатор мог исчезнуть — тогда молча возвращаемся
        // к системному, а не остаёмся без голоса.
        if (status != TextToSpeech.SUCCESS) {
            if (enginePackage != null && !retried) {
                retried = true
                engine?.shutdown()
                engine = TextToSpeech(appContext, this, null)
            }
            return
        }

        val tts = engine ?: return
        tts.language = Locale.forLanguageTag("ru-RU")

        if (voiceName != null) {
            runCatching {
                tts.voices?.firstOrNull { it.name == voiceName }?.let { tts.voice = it }
            }
        }
        runCatching { tts.setSpeechRate(speechRate) }
        runCatching { tts.setPitch(pitch) }

        ready = true
        // Пробу голоса могли попросить ещё до подъёма движка: тогда она
        // запомнена и приезжает здесь — иначе первый образец прозвучал бы
        // не тем голосом, который выбрали.
        applyPendingVoice()
        onReady?.invoke()
        pending?.let { text ->
            pending = null
            say(text)
        }
    }

    /**
     * Сменить скорость. Работает и до подъёма синтезатора — тогда значение
     * применится, как только он ответит. Это важно для настроек: скорость
     * там крутят десятком нажатий подряд, и пересобирать движок на каждое
     * (полсекунды ожидания) было бы невыносимо.
     */
    fun setRate(value: Float) {
        speechRate = value
        if (ready) runCatching { engine?.setSpeechRate(value) }
    }

    /** Сказать фразу. [interrupt] — перебить то, что говорится сейчас. */
    fun say(text: String, interrupt: Boolean = true) {
        if (!ready || text.isBlank()) return
        // Свою фразу начинаем с тишины: скринридер, если он читает, умолкает.
        if (interrupt) interruptScreenReader()
        Journal.note("речь", "приложение говорит: $text")
        val mode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        engine?.speak(text, mode, null, text)
    }

    /** Сказать, как только синтезатор поднимется: до готовности он немой. */
    fun sayWhenReady(text: String) {
        if (ready) say(text) else pending = text
    }

    /**
     * Синтезаторы, установленные в системе.
     *
     * Список неполон без объявления `<queries>` с действием
     * `android.intent.action.TTS_SERVICE` в манифесте: с Android 11 чужие
     * пакеты приложению не видны, и здесь остаётся один системный движок.
     */
    fun engines(): List<TextToSpeech.EngineInfo> =
        runCatching { engine?.engines ?: emptyList() }.getOrDefault(emptyList())

    /**
     * Пакет синтезатора, который в системе выбран по умолчанию. Спрашиваем
     * у своего движка: это его метод, а не статический (статического
     * `TextToSpeech.getDefaultEngine` в Android нет).
     */
    fun defaultEngine(): String? = runCatching { engine?.defaultEngine }.getOrNull()

    /** Русские голоса текущего синтезатора. Если русских нет — все, какие есть. */
    fun voices(): List<Voice> = runCatching {
        val all = engine?.voices?.sortedBy { it.name } ?: emptyList()
        // Голос со словарём «не установлен» промолчит: выбрать его — значит
        // остаться без озвучки. Если же установленных нет вовсе, показываем
        // всё, что есть: пусть игрок слышит, что выбор существует.
        val installed = all.filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in it.features }
        val usable = installed.ifEmpty { all }
        usable.filter { it.locale.language == "ru" }.ifEmpty { usable }
    }.getOrDefault(emptyList())

    /** Имя голоса, которым говорим сейчас. */
    fun currentVoiceName(): String? = runCatching { engine?.voice?.name }.getOrNull()

    /**
     * Проба голоса: включаем его на этом синтезаторе и говорим образец.
     * Настройку это не меняет — её сохраняет экран настроек.
     *
     * [name] = null — голос движка по умолчанию, им же и возвращаемся назад
     * после того, как послушали чужой. [pitch] — высота для этой пробы.
     */
    fun previewVoice(name: String?, text: String, pitch: Float = 1f) {
        pendingVoice = name
        pendingPitch = pitch
        applyPendingVoice()
        sayWhenReady(text)
    }

    /**
     * Поставить голос и высоту, выбранные пробой. До подъёма движка ставить
     * некуда — тогда значения приедут в [onInit].
     */
    private fun applyPendingVoice() {
        if (!ready) return
        val tts = engine ?: return
        runCatching {
            val voice = if (pendingVoice == null) {
                tts.defaultVoice
            } else {
                tts.voices?.firstOrNull { it.name == pendingVoice }
            }
            voice?.let { tts.voice = it }
            tts.setPitch(pendingPitch ?: pitch)
            // Смена голоса у части движков сбрасывает скорость — возвращаем.
            tts.setSpeechRate(speechRate)
        }
    }

    fun stop() {
        engine?.stop()
    }

    fun shutdown() {
        runCatching { accessibility?.removeTouchExplorationStateChangeListener(touchExplorationListener) }
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
    }
}
