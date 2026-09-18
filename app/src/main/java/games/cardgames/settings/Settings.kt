package games.cardgames.settings

import android.content.Context
import android.content.SharedPreferences
import games.engine.HandOrder
import games.engine.durak.Difficulty
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Кто говорит — приложение своим синтезатором или скринридер.
 *
 * Правило одно на всё приложение: в каждый момент говорит ровно один. Иначе
 * две речи накладываются и выходит каша. Скринридер говорит то, что видит на
 * экране; приложение — то, чего на экране нет: ход бота, раздачу, итог
 * партии. Когда говорит приложение, скринридеру велено умолкнуть
 * ([Speaker.interruptScreenReader]); когда говорит скринридер, приложение
 * отдаёт свои фразы ему, а не произносит поверх.
 *
 * [AUTO] — решает по обстановке: раз скринридер работает, говорить ему.
 * [ALWAYS] — говорит приложение.
 * [NEVER] — говорит скринридер.
 */
enum class VoiceMode(val title: String) {
    AUTO("авто"),
    ALWAYS("приложение"),
    NEVER("скринридер"),
    ;

    fun next(): VoiceMode = entries[(ordinal + 1) % entries.size]
}

/**
 * Пределы скорости речи. Ниже 0.5 синтезатор уже не разобрать; верхнюю
 * границу просил поднять сам владелец — привыкший к быстрой речи слушает
 * на трёх, а не на полутора. Дальше четырёх движки всё равно не тянут.
 */
const val RATE_MIN = 0.5f
const val RATE_MAX = 4.0f
const val RATE_STEP = 0.1f

/**
 * Число промежуточных ступеней ползунка. Через roundToInt, а не toInt:
 * 3.5/0.1 в дробных числах даёт 34.999996, и ползунок терял бы последнюю
 * ступень — крайние значения разъезжались бы с подписью.
 */
val RATE_STEPS: Int = ((RATE_MAX - RATE_MIN) / RATE_STEP).roundToInt() - 1

/** Ступени прошлых версий: нужны, чтобы прочитать сохранённую настройку. */
private val LEGACY_RATES = mapOf("SLOW" to 0.75f, "NORMAL" to 1.0f, "FAST" to 1.35f)

/**
 * Высота голоса бота, если своего голоса ему не выбрали.
 *
 * За столом говорят трое: приложение, бот в дураке и бот в тысяче. Голосов
 * в телефоне у игрока бывает и один, а различать их надо всех: «бот сказал»
 * и «приложение сказало» — разные вещи, и спутать их значит не понять, чей
 * ход. Поэтому бот без своего голоса говорит голосом приложения, сдвинутым
 * по высоте: в дураке ниже, в тысяче выше. Сдвиг небольшой — голос должен
 * остаться разборчивым, а на слух и четверти тона хватает.
 */
const val BOT_PITCH_DURAK = 0.85f
const val BOT_PITCH_THOUSAND = 1.2f

/** Голос бота: свой, а не выбрали — голос приложения. */
fun botVoice(own: String?, appVoice: String?): String? = own ?: appVoice

/** Высота голоса бота: своя только у бота без собственного голоса. */
fun botPitch(own: String?, fallback: Float): Float = if (own == null) fallback else 1f

/** Скорость словами: «1.35» человеку ни о чём не говорит, «быстро» — говорит. */
fun rateTitle(value: Float): String = when {
    value < 0.8f -> "очень медленно"
    value < 1.0f -> "медленно"
    value < 1.2f -> "обычно"
    value < 1.6f -> "быстро"
    value < 2.5f -> "очень быстро"
    else -> "предельно быстро"
}

/** Ближайшая ступень ползунка: скорость всегда кратна [RATE_STEP]. */
fun snapRate(value: Float): Float =
    (round(value / RATE_STEP) * RATE_STEP).coerceIn(RATE_MIN, RATE_MAX)

/**
 * Все настройки приложения в одном месте.
 *
 * Раньше каждая жила сама по себе: порядок карт в одном файле, звук в
 * другом. С седьмой настройкой в этом стало легко запутаться, поэтому
 * теперь один объект, одно хранилище и один экран.
 */
data class Settings(
    /** Скорость речи множителем: 1.0 — как синтезатор говорит сам. */
    val rate: Float = 1.0f,
    /** Пакет синтезатора. null — тот, что в системе по умолчанию. */
    val engine: String? = null,
    /** Имя голоса внутри синтезатора. null — голос по умолчанию. */
    val voice: String? = null,
    /**
     * Голос бота — свой у каждой игры. null — «как у приложения», и тогда
     * бот отличается от него высотой (см. [BOT_PITCH_DURAK]).
     *
     * Игра — не украшение: за столом говорят трое, и два бота одним голосом
     * звучали бы как один собеседник, переходящий из игры в игру.
     */
    val botVoiceDurak: String? = null,
    val botVoiceThousand: String? = null,
    val voiceMode: VoiceMode = VoiceMode.AUTO,
    val botTalk: Boolean = true,
    val sounds: Boolean = true,
    /**
     * Сигналы: короткие ноты о событиях — начало, твой ход, победа,
     * поражение, болт. Отдельно от [sounds]: шум стола — это карты, он идёт
     * поверх игры и не мешает, а сигнал «твой ход» звучит сто раз за партию
     * и надоедает первым. Выключив сигналы, игрок не теряет стук карт.
     */
    val signals: Boolean = true,
    /** Толчок, когда ход перешёл к игроку. */
    val vibration: Boolean = true,
    /** И толчок на собственный ход: карта легла. */
    val ownVibration: Boolean = true,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val order: HandOrder = HandOrder.BY_SUIT,
    /**
     * Перевод в «Дураке»: защищающийся кладёт карту того же достоинства и
     * передаёт атаку соседу. Выключенный — «подкидной» дурак. Настройка
     * берётся в момент раздачи: партия помнит, по каким правилам её начали,
     * и переключение посреди неё ход не меняет.
     */
    val transfer: Boolean = true,
    /**
     * Игра, в которую играли последней. По ней в главном меню появляется
     * кнопка быстрого входа: за стол возвращаются чаще, чем заглядывают в
     * список игр. Пока игра одна, значение задано по умолчанию; когда их
     * станет больше, сюда будет попадать та, которую открыли последней.
     */
    val lastGame: String = "durak",
    val largeText: Boolean = false,
    /**
     * Писать партию на диск после каждого хода. Так её можно продолжить,
     * если приложение закрылось само или игрок случайно вышел.
     */
    val autosave: Boolean = true,
)

/**
 * Файл настроек — один на приложение. Общие настройки кладут в него свои
 * ключи, игровые — свои, с именем игры впереди (SETTINGS.md, 2).
 */
internal const val PREFS = "settings"
private const val KEY_RATE = "rate"
private const val KEY_ENGINE = "engine"
private const val KEY_VOICE = "voice"
private const val KEY_BOT_VOICE_DURAK = "bot_voice_durak"
private const val KEY_BOT_VOICE_THOUSAND = "bot_voice_thousand"
private const val KEY_VOICE_MODE = "voice_mode"
private const val KEY_BOT_TALK = "bot_talk"
private const val KEY_SOUNDS = "sounds"
private const val KEY_SIGNALS = "signals"
private const val KEY_VIBRATION = "vibration"
private const val KEY_OWN_VIBRATION = "own_vibration"
private const val KEY_DIFFICULTY = "difficulty"
private const val KEY_ORDER = "order"
private const val KEY_TRANSFER = "transfer"
private const val KEY_LAST_GAME = "last_game"
private const val KEY_LARGE = "large_text"
private const val KEY_AUTOSAVE = "autosave"

fun loadSettings(context: Context): Settings {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return Settings(
        rate = readRate(prefs),
        engine = prefs.getString(KEY_ENGINE, null),
        voice = prefs.getString(KEY_VOICE, null),
        botVoiceDurak = prefs.getString(KEY_BOT_VOICE_DURAK, null),
        botVoiceThousand = prefs.getString(KEY_BOT_VOICE_THOUSAND, null),
        voiceMode = prefs.getString(KEY_VOICE_MODE, null)
            ?.let { name -> runCatching { VoiceMode.valueOf(name) }.getOrNull() }
            ?: VoiceMode.AUTO,
        botTalk = prefs.getBoolean(KEY_BOT_TALK, true),
        sounds = prefs.getBoolean(KEY_SOUNDS, true),
        signals = prefs.getBoolean(KEY_SIGNALS, true),
        vibration = prefs.getBoolean(KEY_VIBRATION, true),
        ownVibration = prefs.getBoolean(KEY_OWN_VIBRATION, true),
        difficulty = prefs.getString(KEY_DIFFICULTY, null)
            ?.let { name -> runCatching { Difficulty.valueOf(name) }.getOrNull() }
            ?: Difficulty.NORMAL,
        order = prefs.getString(KEY_ORDER, null)
            ?.let { name -> runCatching { HandOrder.valueOf(name) }.getOrNull() }
            ?: HandOrder.BY_SUIT,
        transfer = prefs.getBoolean(KEY_TRANSFER, true),
        lastGame = prefs.getString(KEY_LAST_GAME, null) ?: "durak",
        largeText = prefs.getBoolean(KEY_LARGE, false),
        autosave = prefs.getBoolean(KEY_AUTOSAVE, true),
    )
}

/**
 * Скорость из хранилища. В версиях до 0.5 она лежала ступенью-словом
 * («SLOW»), поэтому строку переводим в число, а не падаем на разборе.
 */
private fun readRate(prefs: SharedPreferences): Float =
    when (val raw = prefs.all[KEY_RATE]) {
        is Float -> raw.coerceIn(RATE_MIN, RATE_MAX)
        is String -> LEGACY_RATES[raw] ?: 1.0f
        else -> 1.0f
    }

fun saveSettings(context: Context, settings: Settings) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putFloat(KEY_RATE, settings.rate)
        .putString(KEY_ENGINE, settings.engine)
        .putString(KEY_VOICE, settings.voice)
        .putString(KEY_BOT_VOICE_DURAK, settings.botVoiceDurak)
        .putString(KEY_BOT_VOICE_THOUSAND, settings.botVoiceThousand)
        .putString(KEY_VOICE_MODE, settings.voiceMode.name)
        .putBoolean(KEY_BOT_TALK, settings.botTalk)
        .putBoolean(KEY_SOUNDS, settings.sounds)
        .putBoolean(KEY_SIGNALS, settings.signals)
        .putBoolean(KEY_VIBRATION, settings.vibration)
        .putBoolean(KEY_OWN_VIBRATION, settings.ownVibration)
        .putString(KEY_DIFFICULTY, settings.difficulty.name)
        .putString(KEY_ORDER, settings.order.name)
        .putBoolean(KEY_TRANSFER, settings.transfer)
        .putString(KEY_LAST_GAME, settings.lastGame)
        .putBoolean(KEY_LARGE, settings.largeText)
        .putBoolean(KEY_AUTOSAVE, settings.autosave)
        .apply()
}
