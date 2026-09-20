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
     * Пустая возможность: за столом соперников разводят синтезатором и
     * голосом, и ею давно никто не пользуется (Катерина, 19.09: «убери, чтобы
     * повышение голоса было у каждого бота — зачем разная высота»). Оставлена
     * затем, что ею говорят пробы голоса в настройках, а движку высоту всё
     * равно надо чем-то выставить.
     */
    private val pitch: Float = 1.0f,
    /**
     * Как этот синтезатор звать в журнале: «приложение», «соперник», «второй
     * соперник». За столом говорят несколько синтезаторов, и запись без имени
     * читается как одна речь — по ней не видно, чей это был голос. Имя нужно
     * только для этого: на звук оно не влияет.
     */
    private val title: String = "приложение",
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

    /**
     * Проба попросила вернуть движку его голос по умолчанию —
     * `previewVoice(null, …)`. Только этой просьбой голос движка и ставится:
     * во всех прочих случаях он не трогается (см. [applyPendingVoice]).
     */
    private var pendingDefault = false

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
     * [games.cardgames.speech.speech]).
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
        tts.language = speechLanguage(tts)

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
        // Каким голосом заговорил этот синтезатор — в журнал, до первой
        // фразы. Выбранный голос молча остаётся невыбранным, если движок его
        // не знает, и по звуку это не отличить от «настройка не дошла»: обе
        // поломки слышны одинаково — «говорят одним голосом, хотя в
        // настройках разные». По этой записи видно, чей голос просили и чей
        // вышел.
        val spoken = currentVoiceName()
        val mismatch = if (voiceName != null && spoken != voiceName) {
            " (просили $voiceName — движок его не знает)"
        } else {
            ""
        }
        Journal.note(
            "речь",
            "$title: движок ${enginePackage ?: "системный"}, " +
                "говорит голосом ${spoken ?: "по умолчанию"}$mismatch",
        )
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

    /**
     * Текущая скорость речи. По ней считают, сколько фраза будет звучать:
     * у бота скорость своя (SETTINGS.md, 8), и паузу после его реплики
     * мерить скоростью приложения значило бы обрывать её на полуслове.
     */
    val rate: Float get() = speechRate

    /** Сказать фразу. [interrupt] — перебить то, что говорится сейчас. */
    fun say(text: String, interrupt: Boolean = true) {
        if (!ready || text.isBlank()) return
        // Свою фразу начинаем с тишины: скринридер, если он читает, умолкает.
        if (interrupt) interruptScreenReader()
        Journal.note("речь", "$title говорит: $text")
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

    /**
     * Голоса текущего синтезатора на языке системы. Если таких нет — русские,
     * если и русских нет — все, какие есть.
     *
     * Язык берётся у системы, а не стоит русским жёстко (Катерина, 20.09:
     * «язык голосов чтобы был такой же как в системе»): список из голосов,
     * которых игрок в телефоне не выбирал и не слышал, — это список чужих
     * голосов. Откат на русский — не украшение: там, где система не
     * по-русски, список иначе остался бы пустым, и выбирать было бы не из чего.
     */
    fun voices(): List<Voice> = runCatching {
        val all = engine?.voices?.sortedBy { it.name } ?: emptyList()
        // Голос со словарём «не установлен» промолчит: выбрать его — значит
        // остаться без озвучки. Если же установленных нет вовсе, показываем
        // всё, что есть: пусть игрок слышит, что выбор существует.
        val installed = all.filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in it.features }
        val usable = installed.ifEmpty { all }
        val wanted = systemLanguage().language
        usable.filter { it.locale.language == wanted }
            .ifEmpty { usable.filter { it.locale.language == RUSSIAN.language } }
            .ifEmpty { usable }
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
        // Пустое имя — это просьба «верни голос движка», а не «ничего не
        // меняй»: пробуют и его тоже.
        pendingDefault = name == null
        pendingPitch = pitch
        applyPendingVoice()
        sayWhenReady(text)
    }

    /**
     * Поставить голос и высоту, выбранные пробой. До подъёма движка ставить
     * некуда — тогда значения приедут в [onInit].
     *
     * Голос движка по умолчанию ставится здесь ровно тогда, когда о нём
     * попросила проба. Без этой оговорки выбор голоса затирался бы: синтезатор
     * поднимается, ставит выбранный голос ([onInit]) и следом — тем же
     * методом — сбрасывал бы его на голос движка. За столом это звучало так,
     * что все говорят одним голосом, а в настройках выбор работал: там голос
     * звучит пробой (Катерина, 19.09: «в козле играет трое, у двух ботов
     * одинаковый голос, в настройках установлены разные»).
     */
    private fun applyPendingVoice() {
        if (!ready) return
        val tts = engine ?: return
        runCatching {
            val voice = when {
                pendingVoice != null -> tts.voices?.firstOrNull { it.name == pendingVoice }
                pendingDefault -> tts.defaultVoice
                // Ни пробы, ни просьбы о голосе движка не было: голос этому
                // синтезатору уже выбран при подъёме движка, и подменять его
                // здесь нечем.
                else -> null
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

/** Русский: язык, на котором написаны все фразы приложения. */
private val RUSSIAN = Locale.forLanguageTag("ru-RU")

/** Язык системы — тот, что стоит в настройках телефона у самого игрока. */
private fun systemLanguage(): Locale = Locale.getDefault()

/**
 * Язык, которым этому движку говорить: язык системы, а если движок его не
 * знает — русский.
 *
 * Откат нужен затем, что фразы у нас русские: движок, которому нечего сказать
 * про системный язык, прочитал бы их чужим голосом, а то и промолчал. Русский
 * тут — не «как было», а последний язык, на котором приложению точно есть что
 * сказать.
 */
private fun speechLanguage(tts: TextToSpeech): Locale = systemLanguage()
    .takeIf { supported(tts, it) }
    ?: RUSSIAN

/** Знает ли движок этот язык. */
private fun supported(tts: TextToSpeech, locale: Locale): Boolean =
    runCatching { tts.isLanguageAvailable(locale) }
        .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
