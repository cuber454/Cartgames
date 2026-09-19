package games.cardgames.settings

import android.content.Context
import android.content.SharedPreferences
import games.cardgames.GAME_KOZEL
import games.cardgames.GAME_THOUSAND
import games.engine.HandOrder
import games.engine.durak.Difficulty
import games.engine.tiles.TileOrder
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Кто говорит — приложение своим синтезатором, скринридер или никто.
 *
 * Правило одно на всё приложение: в каждый момент говорит ровно один. Иначе
 * две речи накладываются и выходит каша. Скринридер говорит то, что видит на
 * экране; приложение — то, чего на экране нет: ход бота, раздачу, итог
 * партии. Когда говорит приложение, скринридеру велено умолкнуть
 * ([Speaker.interruptScreenReader]); когда говорит скринридер, приложение
 * отдаёт свои фразы ему, а не произносит поверх. Играют зрячие — говорит
 * никто: приложение молчит, скринридеру ничего не уходит.
 *
 * [AUTO] — решает по обстановке: раз скринридер работает, говорить ему.
 * [ALWAYS] — говорит приложение.
 * [NEVER] — говорит скринридер.
 * [SILENT] — не говорит никто: за столом тишина. Это для зрячего за игрой
 * (Катерина, 19.09): ему озвучка не нужна, а вслух за столом читалось бы
 * то, что и так видно. Приложение молчит и скринридеру ничего не отдаёт —
 * в отличие от [NEVER], где фраза уходит ему и звучит его голосом.
 */
enum class VoiceMode(val title: String) {
    AUTO("авто"),
    ALWAYS("приложение"),
    NEVER("скринридер"),
    SILENT("никто"),
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
 * За столом говорят вчетвером: приложение, бот в дураке, бот в тысяче и бот
 * в «Козле». Голосов в телефоне у игрока бывает и один, а различать их надо
 * всех: «бот сказал» и «приложение сказало» — разные вещи, и спутать их
 * значит не понять, чей ход. Поэтому бот без своего голоса говорит голосом
 * приложения, сдвинутым по высоте: в дураке ниже, в тысяче выше, в «Козле»
 * ещё выше. Сдвиг небольшой — голос должен остаться разборчивым, а на слух
 * и четверти тона хватает.
 */
const val BOT_PITCH_DURAK = 0.85f
const val BOT_PITCH_THOUSAND = 1.2f
/**
 * Высота второго соперника в «Тысяче» — ниже приложения.
 *
 * За столом на троих один голос выше, другой ниже: два бота в одной
 * тональности звучали бы как один, и кто назвал сумму, не различить
 * (Катерина, 19.09). Своего голоса второму не выбрали — говорим голосом
 * приложения, сдвинутым вниз.
 */
const val BOT_PITCH_THOUSAND_SECOND = 0.85f
const val BOT_PITCH_KOZEL = 1.4f

/** Голос бота: свой, а не выбрали — голос приложения. */
fun botVoice(own: String?, appVoice: String?): String? = own ?: appVoice

/**
 * Имя соперника этой игры. Пусто — имени нет, и за столом его зовут «Бот».
 *
 * Имя у каждой игры своё (Катерина, 19.09: «чтобы боты были в настройках с
 * игрой, и чтобы там всё настраивалось»). За дураком и за тысячей сидит как
 * будто один и тот же соперник, но игры — три разных стола: за одним играют
 * с Петей, за другим с Васей, и общее имя заставляло бы звать Петю за чужим
 * столом. Прежнее общее имя при этом не теряется — оно переезжает в каждую
 * игру при первом чтении настроек (см. loadSettings).
 */
fun botName(settings: Settings, game: String): String = when (game) {
    GAME_KOZEL -> settings.botNameKozel
    GAME_THOUSAND -> settings.botNameThousand
    else -> settings.botNameDurak
}

/** Записать имя соперника той игре, чьи настройки правят. */
fun withBotName(settings: Settings, game: String, value: String): Settings = when (game) {
    GAME_KOZEL -> settings.copy(botNameKozel = value)
    GAME_THOUSAND -> settings.copy(botNameThousand = value)
    else -> settings.copy(botNameDurak = value)
}

/**
 * Как звать соперника за столом. Пусто — «Бот», как было до имени.
 *
 * Имя звучит только там, где соперник — действующий: «Меркурий берёт
 * прикуп». Где фраза требует падежа («у бота марьяж»), имя не подставить:
 * склонять произвольное имя программа не умеет, а «у Меркурий» хуже, чем
 * вовсе без имени. Такие фразы говорят «соперник» — см. SETTINGS.md, 8.
 */
fun botTitle(settings: Settings, game: String): String =
    botName(settings, game).trim().ifEmpty { "Бот" }

/**
 * Сколько мест за столом «Тысячи» — сколько их в настройках.
 *
 * Движок держит ровно двоих и троих и другого числа не примет, но он же и
 * отвергнет что попало: чужая правка в хранилище — не повод упасть за
 * столом, поэтому число здесь зажато теми же рамками.
 */
fun seatsAtTable(settings: Settings): Int = settings.thousandSeats.coerceIn(2, 3)

/**
 * Как звать сидящих за столом, по местам: место игрока — «ты», у прочих своё
 * имя, а не назвали — «Бот» и «Второй бот».
 *
 * Список, а не одно имя, потому что за «Тысячей» на троих соперников двое,
 * и фраза про любое из мест должна называть своё: «Петя называет 120» — это
 * про Петю, а не про «соперника» вообще.
 *
 * Имя из настроек остаётся и за столом на двоих: фразы приложения («Взятку
 * берёт Петя», «Счёт: тебе 120, Петя 90») называют человека по имени, сколько
 * бы их ни сидело. Иначе звучит не речь о человеке, а «соперник» — слово,
 * которого за столом не говорят. Сам же бот на двоих имени не называет, а
 * говорит о себе «я»: это дело его собственной речи, а не списка мест. Подробнее
 * — в самой функции.
 *
 * Место игрока тоже в списке: так у фразы один способ позвать кого угодно, и
 * нет ветки «а если это я» в каждом месте, где называют место за столом.
 */
fun seatTitles(settings: Settings, seats: Int): List<String> = List(seats) { seat ->
    when {
        seat == 0 -> "ты"
        // Имя зовётся там, где о сопернике говорит само приложение: счёт,
        // взятка, снос, исход кона. Свою же речь бот ведёт от первого лица —
        // но только за столом на двоих, где различать нечего: там имя звучит
        // не речью, а справкой о себе («Петя называет 120» вместо «называю
        // 120»). Это решает экран, а не место за столом (Катерина, 19.09).
        seat == 1 -> settings.botNameThousand.trim().ifEmpty { "Бот" }
        else -> settings.botNameThousandSecond.trim().ifEmpty { "Второй бот" }
    }
}

/** Сколько мест за столом — словами: «двое» или «трое». */
fun seatsTitle(seats: Int): String = if (seats >= 3) "трое" else "двое"

/**
 * Что сказать, когда мест за столом стало больше или меньше.
 *
 * Про следующую партию, а не про текущую: за столом посреди кона число
 * игроков не меняется — сдача уже разошлась, и прикуп взят по старому
 * столу. Обещать смену «сейчас» значило бы обещать пересдачу, которой не
 * будет.
 */
fun seatsPhrase(seats: Int): String =
    if (seats >= 3) {
        "За столом трое: соперников двое, по семь карт и прикуп из трёх. Со следующей партии."
    } else {
        "За столом двое: соперник один, по десять карт и два прикупа по две. Со следующей партии."
    }

/** Высота голоса бота: своя только у бота без собственного голоса. */
fun botPitch(own: String?, fallback: Float): Float = if (own == null) fallback else 1f

/**
 * Ступени скорости речи соперника: короткий шаг вокруг обычной.
 *
 * Скорость — вторая примета, по которой бота узнают, когда голос у него тот
 * же, что у приложения: высота их уже разводит, но «выше» и «ниже» на слух
 * путаются, а быстрый говор и медленный — нет. Ступеней намеренно мало:
 * скорость — это разборчивость, и на предельных её значениях речь бота
 * перестаёт быть речью. Перебирают их одной кнопкой, а не ползунком
 * (Катерина, 19.09: «вместе с синтезатором для каждого бота надо сделать
 * регулировку скорости»).
 */
val BOT_RATES: List<Float> = listOf(0.75f, 0.9f, 1.0f, 1.15f, 1.4f)

/**
 * Скорость соперника словами. 1.0 — не «обычно», а «как приложение»:
 * обычная скорость у них общая, и слово на этом месте ничего не говорит.
 */
fun botRateTitle(value: Float): String =
    if (value == 1f) "как приложение" else rateTitle(value)

/**
 * Следующая ступень скорости соперника — по кругу.
 *
 * Ближайшая ступень, а не точное совпадение: скорость приходит из хранилища,
 * куда её мог положить кто угодно, и кнопка, которая на чужом числе просто
 * молчит, — хуже, чем кнопка, которая с него начинает.
 */
fun nextBotRate(value: Float): Float {
    val at = BOT_RATES.indices.minByOrNull { kotlin.math.abs(BOT_RATES[it] - value) } ?: 0
    return BOT_RATES[(at + 1) % BOT_RATES.size]
}

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
    /**
     * Голос второго соперника в «Тысяче» — того, кто садится за стол, только
     * когда за ним трое. null — голос приложения, но ниже первого бота.
     *
     * Свой голос у каждого, а не один на двоих: за столом на троих двое
     * говорят одним голосом — уже не различить, кто из них назвал сумму
     * (Катерина, 19.09).
     */
    val botVoiceThousandSecond: String? = null,
    val botVoiceKozel: String? = null,
    /**
     * Скорость речи соперника — своя у каждой игры, как и голос. 1.0 — как
     * говорит приложение.
     *
     * Своя у игры, а не одна на всех: голоса у ботов разные, и одна скорость
     * на четверых заставляла бы подгонять всех под того, кого слушаешь чаще.
     * Ступени — [BOT_RATES].
     */
    val botRateDurak: Float = 1.0f,
    val botRateThousand: Float = 1.0f,
    /**
     * Скорость второго соперника в «Тысяче» — третья примета вдобавок к
     * голосу и высоте: за столом на троих говорят трое, и различать их надо
     * всех (Катерина, 19.09).
     */
    val botRateThousandSecond: Float = 1.0f,
    val botRateKozel: Float = 1.0f,
    val voiceMode: VoiceMode = VoiceMode.AUTO,
    val botTalk: Boolean = true,
    /**
     * Как звать соперника — у каждой игры своё имя.
     *
     * Имя переехало сюда из общих настроек вслед за голосом и скоростью
     * (Катерина, 19.09): всё про соперника настраивается в настройках той
     * игры, за столом которой он сидит, а не в общей куче, где имя дурака
     * стояло рядом с голосом козла. Имя при этом одно и то же на все столы
     * дурака — их не два.
     */
    val botNameDurak: String = "",
    val botNameThousand: String = "",
    /**
     * Имя второго соперника — того, кто садится за стол, только когда за ним
     * трое. Пусто — «Второй бот» (Катерина, 19.09: «чтобы каждому боту можно
     * было своё имя давать, например ходит Петя, ходит Вася»).
     *
     * Отдельным полем, а не списком: имена набирают пальцем в поле настроек,
     * и список из двух строк — тот же список ровно до тех пор, пока в нём
     * ровно две строки, а дальше за столом всё равно больше не садится.
     *
     * Имя звучит там, где бот действует: «Петя называет 120». Где фраза
     * требует падежа, имени нет — склонять произвольное имя программа не
     * умеет (SETTINGS.md, 8).
     */
    val botNameThousandSecond: String = "",
    val botNameKozel: String = "",
    /**
     * Сколько мест за столом «Тысячи»: двое или трое.
     *
     * Втроём в неё играют чаще, чем вдвоём, — на троих она и сложилась: на
     * двоих прикуп берёт один из двух и торг идёт один на один. Движок
     * держит оба стола (прикуп 2+2 на двоих и 1+3 на троих), поэтому выбор
     * здесь, а не в правилах: игрок сам решает, за какой стол садиться.
     *
     * По умолчанию двое: так партия шла до сих пор, и менять её задним
     * числом нельзя — открытая партия продолжается за тем столом, за каким
     * её начали (число мест записано в самой партии).
     */
    val thousandSeats: Int = 2,
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
    /**
     * Соперник — свой у каждой игры.
     *
     * Раньше он был один на приложение, и это оказалось неверно: в «Дураке»
     * и в «Тысяче» сила бота значит разное, и, поставив сложного в одной
     * игре, игрок получал его же во второй, где заказывать труднее. Теперь
     * настройка у каждой игры своя — как голос бота.
     */
    val botDifficultyDurak: Difficulty = Difficulty.NORMAL,
    val botDifficultyThousand: Difficulty = Difficulty.NORMAL,
    val botDifficultyKozel: Difficulty = Difficulty.NORMAL,
    /**
     * Порядок карт на руке: по масти или по старшинству.
     *
     * Он у карт, а не у игр: рука листается вслепую, и порядок в ней — способ
     * найти нужную карту, а не договорённость за столом. Карты одни и те же
     * в дураке и в тысяче, значит и порядок у них один.
     */
    val order: HandOrder = HandOrder.BY_SUIT,
    /**
     * Порядок костей на руке — своё, а не [order]: карты и кости листаются
     * по-разному, и порядок, удобный для масти, ничего не значит для кости.
     *
     * По умолчанию [TileOrder.BY_NUMBERS]: за столом чаще всего спрашивают
     * «чем ответить на шестёрку», и рядом оказываются все шестёрки сразу
     * (Катерина, 18.09: «сделай в настройках Домино настройку сортировки и
     * пусть по умолчанию будет как ты рекомендуешь»).
     */
    val tileOrder: TileOrder = TileOrder.BY_NUMBERS,
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
    /**
     * Спрашивать при входе, не вышла ли новая сборка.
     *
     * Включено по умолчанию: приложение живёт на телефоне у игрока, а сборки
     * выходят на стороне — сам он о них иначе не узнает. Выключить стоит,
     * только если обновления мешают: проверка ходит в сеть и говорит вслух,
     * когда есть что сказать.
     */
    val autoUpdate: Boolean = true,
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
private const val KEY_BOT_VOICE_THOUSAND_SECOND = "bot_voice_thousand_second"
private const val KEY_BOT_VOICE_KOZEL = "bot_voice_kozel"
private const val KEY_BOT_RATE_DURAK = "bot_rate_durak"
private const val KEY_BOT_RATE_THOUSAND = "bot_rate_thousand"
private const val KEY_BOT_RATE_THOUSAND_SECOND = "bot_rate_thousand_second"
private const val KEY_BOT_RATE_KOZEL = "bot_rate_kozel"
private const val KEY_VOICE_MODE = "voice_mode"
private const val KEY_BOT_TALK = "bot_talk"
private const val KEY_BOT_NAME_DURAK = "bot_name_durak"
private const val KEY_BOT_NAME_THOUSAND = "bot_name_thousand"
private const val KEY_BOT_NAME_THOUSAND_SECOND = "bot_name_thousand_second"
private const val KEY_BOT_NAME_KOZEL = "bot_name_kozel"
/**
 * Прежние ключи имени — одно на все игры и имя второго соперника в тысяче.
 *
 * Читаются только как запасной вариант, когда своего имени у игры ещё нет:
 * у кого настройки уже стояли, тот своё имя не потеряет — оно подставится во
 * все три игры. Пишем только новые ключи: иначе имя дурака возвращалось бы
 * в тысячу при каждой правке.
 */
private const val KEY_BOT_NAME = "bot_name"
private const val KEY_BOT_NAME_SECOND = "bot_name_second"
private const val KEY_THOUSAND_SEATS = "thousand.seats"
private const val KEY_SOUNDS = "sounds"
private const val KEY_SIGNALS = "signals"
private const val KEY_VIBRATION = "vibration"
private const val KEY_OWN_VIBRATION = "own_vibration"
private const val KEY_BOT_DIFFICULTY_DURAK = "durak.difficulty"
private const val KEY_BOT_DIFFICULTY_THOUSAND = "thousand.difficulty"
private const val KEY_BOT_DIFFICULTY_KOZEL = "kozel.difficulty"
private const val KEY_ORDER = "order"
private const val KEY_TILE_ORDER = "tile_order"

/**
 * Ключи из версий до 0.9: тогда и соперник, и перевод были одни на всё
 * приложение. Настройки переехали в игровой слой, а старые ключи остались
 * читаться — иначе игрок, поставивший сложного соперника или выключивший
 * перевод, после обновления нашёл бы их сброшенными в умолчание. Молча.
 */
private const val LEGACY_KEY_DIFFICULTY = "difficulty"
internal const val LEGACY_KEY_TRANSFER = "transfer"
private const val KEY_LAST_GAME = "last_game"
private const val KEY_LARGE = "large_text"
private const val KEY_AUTOSAVE = "autosave"
private const val KEY_AUTO_UPDATE = "auto_update"

fun loadSettings(context: Context): Settings {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return Settings(
        rate = readRate(prefs),
        engine = prefs.getString(KEY_ENGINE, null),
        voice = prefs.getString(KEY_VOICE, null),
        botVoiceDurak = prefs.getString(KEY_BOT_VOICE_DURAK, null),
        botVoiceThousand = prefs.getString(KEY_BOT_VOICE_THOUSAND, null),
        botVoiceThousandSecond = prefs.getString(KEY_BOT_VOICE_THOUSAND_SECOND, null),
        botVoiceKozel = prefs.getString(KEY_BOT_VOICE_KOZEL, null),
        botRateDurak = readBotRate(prefs, KEY_BOT_RATE_DURAK),
        botRateThousand = readBotRate(prefs, KEY_BOT_RATE_THOUSAND),
        botRateThousandSecond = readBotRate(prefs, KEY_BOT_RATE_THOUSAND_SECOND),
        botRateKozel = readBotRate(prefs, KEY_BOT_RATE_KOZEL),
        voiceMode = prefs.getString(KEY_VOICE_MODE, null)
            ?.let { name -> runCatching { VoiceMode.valueOf(name) }.getOrNull() }
            ?: VoiceMode.AUTO,
        botTalk = prefs.getBoolean(KEY_BOT_TALK, true),
        botNameDurak = readBotName(prefs, KEY_BOT_NAME_DURAK, KEY_BOT_NAME),
        botNameThousand = readBotName(prefs, KEY_BOT_NAME_THOUSAND, KEY_BOT_NAME),
        botNameThousandSecond = readBotName(prefs, KEY_BOT_NAME_THOUSAND_SECOND, KEY_BOT_NAME_SECOND),
        botNameKozel = readBotName(prefs, KEY_BOT_NAME_KOZEL, KEY_BOT_NAME),
        thousandSeats = readSeats(prefs),
        sounds = prefs.getBoolean(KEY_SOUNDS, true),
        signals = prefs.getBoolean(KEY_SIGNALS, true),
        vibration = prefs.getBoolean(KEY_VIBRATION, true),
        ownVibration = prefs.getBoolean(KEY_OWN_VIBRATION, true),
        botDifficultyDurak = readDifficulty(prefs, KEY_BOT_DIFFICULTY_DURAK),
        botDifficultyThousand = readDifficulty(prefs, KEY_BOT_DIFFICULTY_THOUSAND),
        botDifficultyKozel = readDifficulty(prefs, KEY_BOT_DIFFICULTY_KOZEL),
        order = prefs.getString(KEY_ORDER, null)
            ?.let { name -> runCatching { HandOrder.valueOf(name) }.getOrNull() }
            ?: HandOrder.BY_SUIT,
        tileOrder = prefs.getString(KEY_TILE_ORDER, null)
            ?.let { name -> runCatching { TileOrder.valueOf(name) }.getOrNull() }
            ?: TileOrder.BY_NUMBERS,
        lastGame = prefs.getString(KEY_LAST_GAME, null) ?: "durak",
        largeText = prefs.getBoolean(KEY_LARGE, false),
        autosave = prefs.getBoolean(KEY_AUTOSAVE, true),
        autoUpdate = prefs.getBoolean(KEY_AUTO_UPDATE, true),
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

/**
 * Число мест за столом «Тысячи» из хранилища. Ключа нет — двое, как играли
 * до того, как появился выбор: партия, начатая до обновления, продолжается
 * за тем же столом, за каким шла.
 */
/**
 * Скорость речи соперника из хранилища. Ключа нет — обычная: бот, которого
 * не настраивали, говорит как приложение, и это то, к чему игрок привык.
 */
private fun readBotRate(prefs: SharedPreferences, key: String): Float =
    (prefs.all[key] as? Float)?.coerceIn(RATE_MIN, RATE_MAX) ?: 1.0f

/**
 * Имя соперника из хранилища. Своего у игры ещё нет — берём прежнее общее:
 * так имя, набранное до переезда, находится за каждым из трёх столов, а не
 * пропадает у того, кто набирал его в общих настройках.
 */
private fun readBotName(prefs: SharedPreferences, key: String, legacy: String): String =
    (prefs.getString(key, null) ?: prefs.getString(legacy, null)).orEmpty()

private fun readSeats(prefs: SharedPreferences): Int =
    (prefs.all[KEY_THOUSAND_SEATS] as? Int)?.coerceIn(2, 3) ?: 2

/**
 * Соперник из хранилища: сперва свой ключ игры, а если его ещё нет — общий
 * ключ версий до 0.9. Обе игры получают из него то же значение, которое
 * было у игрока до обновления, и выбор не теряется.
 */
private fun readDifficulty(prefs: SharedPreferences, key: String): Difficulty =
    (prefs.getString(key, null) ?: prefs.getString(LEGACY_KEY_DIFFICULTY, null))
        ?.let { name -> runCatching { Difficulty.valueOf(name) }.getOrNull() }
        ?: Difficulty.NORMAL

fun saveSettings(context: Context, settings: Settings) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putFloat(KEY_RATE, settings.rate)
        .putString(KEY_ENGINE, settings.engine)
        .putString(KEY_VOICE, settings.voice)
        .putString(KEY_BOT_VOICE_DURAK, settings.botVoiceDurak)
        .putString(KEY_BOT_VOICE_THOUSAND, settings.botVoiceThousand)
        .putString(KEY_BOT_VOICE_THOUSAND_SECOND, settings.botVoiceThousandSecond)
        .putString(KEY_BOT_VOICE_KOZEL, settings.botVoiceKozel)
        .putFloat(KEY_BOT_RATE_DURAK, settings.botRateDurak)
        .putFloat(KEY_BOT_RATE_THOUSAND, settings.botRateThousand)
        .putFloat(KEY_BOT_RATE_THOUSAND_SECOND, settings.botRateThousandSecond)
        .putFloat(KEY_BOT_RATE_KOZEL, settings.botRateKozel)
        .putString(KEY_VOICE_MODE, settings.voiceMode.name)
        .putBoolean(KEY_BOT_TALK, settings.botTalk)
        .putString(KEY_BOT_NAME_DURAK, settings.botNameDurak)
        .putString(KEY_BOT_NAME_THOUSAND, settings.botNameThousand)
        .putString(KEY_BOT_NAME_THOUSAND_SECOND, settings.botNameThousandSecond)
        .putString(KEY_BOT_NAME_KOZEL, settings.botNameKozel)
        .putInt(KEY_THOUSAND_SEATS, settings.thousandSeats.coerceIn(2, 3))
        .putBoolean(KEY_SOUNDS, settings.sounds)
        .putBoolean(KEY_SIGNALS, settings.signals)
        .putBoolean(KEY_VIBRATION, settings.vibration)
        .putBoolean(KEY_OWN_VIBRATION, settings.ownVibration)
        .putString(KEY_BOT_DIFFICULTY_DURAK, settings.botDifficultyDurak.name)
        .putString(KEY_BOT_DIFFICULTY_THOUSAND, settings.botDifficultyThousand.name)
        .putString(KEY_BOT_DIFFICULTY_KOZEL, settings.botDifficultyKozel.name)
        .putString(KEY_ORDER, settings.order.name)
        .putString(KEY_TILE_ORDER, settings.tileOrder.name)
        .putString(KEY_LAST_GAME, settings.lastGame)
        .putBoolean(KEY_LARGE, settings.largeText)
        .putBoolean(KEY_AUTOSAVE, settings.autosave)
        .putBoolean(KEY_AUTO_UPDATE, settings.autoUpdate)
        .apply()
}
