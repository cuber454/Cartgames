package games.cardgames.settings

import android.content.Context
import android.content.SharedPreferences
import games.cardgames.GAME_DURAK
import games.cardgames.GAME_HUNDRED
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
 * Голос бота: свой, а не выбрали — голос приложения.
 *
 * Голос приложения подставляется только тому боту, который говорит его же
 * синтезатором. Имя голоса живёт внутри движка: в чужом оно значит не «тот
 * голос, что у приложения», а «первый подходящий», и обещание «как у
 * приложения» оказалось бы ложью (Катерина, 19.09: «меня всё равно
 * разговаривает всё одним голосом, нет разделения»).
 *
 * [ownEngine] — синтезатор, выбранный боту в настройках игры, [appEngine] —
 * синтезатор приложения. Каким бот заговорит на самом деле, решает
 * [botEngine] — она и зовётся здесь.
 */
fun botVoice(
    own: String?,
    appVoice: String?,
    ownEngine: String? = null,
    appEngine: String? = null,
): String? = when {
    own != null -> own
    // Движки сравниваются приведённые, а не записанные как есть: «системный»
    // у бота и «системный» у приложения — это один и тот же движок, хотя
    // записаны они по-разному (пустой строкой и пустым значением).
    botEngine(ownEngine, appEngine) == appEngine -> appVoice
    else -> null
}

/**
 * Синтезатор бота: свой, а не выбрали — синтезатор приложения.
 *
 * Выбрать боту другой синтезатор — самый надёжный способ развести голоса:
 * голосов у движка бывает и один, а движков в телефоне обычно несколько, и
 * два разных движка не спутать никакой высотой (Катерина, 19.09: «чтобы в
 * голосе каждого бота можно было выбирать и движок, и голос»).
 *
 * Пустая строка — «системный»: это не то же, что «как у приложения», когда
 * у приложения выбран свой движок.
 */
fun botEngine(own: String?, appEngine: String?): String? = when {
    own == null -> appEngine
    own.isEmpty() -> null
    else -> own
}

/**
 * Слышно ли бота отдельно от приложения: выбран свой синтезатор или свой
 * голос — тогда он говорит ими и при работающем скринридере.
 *
 * Так решается, чья речь за столом у скринридера, а чья у приложения.
 * Скринридер говорит одним голосом на всё, и фраза, отданная ему, звучит
 * голосом приложения: выбранный боту голос за столом не слышен вовсе
 * (Катерина, 19.09: «голос с приложения такой же, как и голос первого бота,
 * хотя в настройках выбраны разные голоса»).
 *
 * Бот без своего голоса и движка отличается от приложения только скоростью —
 * за скринридером он и остаётся: голос у него тот же, и своя озвучка здесь
 * означала бы «самоозвучка включилась», ровно то, чего быть не должно.
 */
fun botSpeaksAlone(
    ownVoice: String?,
    appVoice: String?,
    ownEngine: String?,
    appEngine: String?,
): Boolean =
    botEngine(ownEngine, appEngine) != appEngine ||
        botVoice(ownVoice, appVoice, ownEngine, appEngine) != appVoice

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
    GAME_HUNDRED -> settings.botNameHundred
    else -> settings.botNameDurak
}

/** Записать имя соперника той игре, чьи настройки правят. */
fun withBotName(settings: Settings, game: String, value: String): Settings = when (game) {
    GAME_KOZEL -> settings.copy(botNameKozel = value)
    GAME_THOUSAND -> settings.copy(botNameThousand = value)
    GAME_HUNDRED -> settings.copy(botNameHundred = value)
    else -> settings.copy(botNameDurak = value)
}

/**
 * Записать имя второго соперника той игре, чьи настройки правят.
 *
 * У игры без второго места имени нет, и писать его некуда: чужая строка
 * настроек тогда просто ничего не меняет — это тише, чем завести имя
 * сопернику, которого за стол не сажают.
 */
fun withBotNameSecond(settings: Settings, game: String, value: String): Settings = when (game) {
    GAME_THOUSAND -> settings.copy(botNameThousandSecond = value)
    GAME_DURAK -> settings.copy(botNameDurakSecond = value)
    GAME_KOZEL -> settings.copy(botNameKozelSecond = value)
    GAME_HUNDRED -> settings.copy(botNameHundredSecond = value)
    else -> settings
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
 * Имя второго соперника этой игры. Пусто — имени нет.
 *
 * За столом на троих соперников двое, и у второго своё имя: «Петя берёт
 * прикуп» — это про Петю, а не про «второго бота» (Катерина, 19.09: «чтобы
 * каждому боту можно было своё имя давать, например ходит Петя, ходит Вася»).
 * У игры без второго места имени нет вовсе — не потому, что оно пустое, а
 * потому, что садиться за тот стол второму некуда.
 */
fun botNameSecond(settings: Settings, game: String): String = when (game) {
    GAME_THOUSAND -> settings.botNameThousandSecond
    GAME_DURAK -> settings.botNameDurakSecond
    GAME_KOZEL -> settings.botNameKozelSecond
    GAME_HUNDRED -> settings.botNameHundredSecond
    else -> ""
}

/** Как звать второго соперника за столом. Пусто — «Второй бот». */
fun botTitleSecond(settings: Settings, game: String): String =
    botNameSecond(settings, game).trim().ifEmpty { "Второй бот" }

/**
 * Сколько мест за столом игры — сколько их в настройках этой игры.
 *
 * Движок держит ровно двоих и троих и другого числа не примет, но он же и
 * отвергнет что попало: чужая правка в хранилище — не повод упасть за
 * столом, поэтому число здесь зажато теми же рамками.
 *
 * Своё у каждой игры, а не одно на приложение: за «Тысячей» втроём играют
 * чаще, чем вдвоём, за «Дураком» — наоборот, и общий выключатель переставлял
 * бы чужой стол заодно со своим.
 */
fun seatsAtTable(settings: Settings, game: String): Int = when (game) {
    GAME_THOUSAND -> settings.thousandSeats
    GAME_KOZEL -> settings.kozelSeats
    GAME_HUNDRED -> settings.hundredSeats
    else -> settings.durakSeats
}.coerceIn(2, 3)

/** Те же настройки, но со сменённым числом мест за столом этой игры. */
fun withSeats(settings: Settings, game: String, seats: Int): Settings = when (game) {
    GAME_THOUSAND -> settings.copy(thousandSeats = seats.coerceIn(2, 3))
    GAME_KOZEL -> settings.copy(kozelSeats = seats.coerceIn(2, 3))
    GAME_HUNDRED -> settings.copy(hundredSeats = seats.coerceIn(2, 3))
    else -> settings.copy(durakSeats = seats.coerceIn(2, 3))
}

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
fun seatTitles(settings: Settings, game: String, seats: Int): List<String> = List(seats) { seat ->
    when (seat) {
        0 -> "ты"
        // Имя зовётся там, где о сопернике говорит само приложение: счёт,
        // взятка, снос, исход кона. Свою же речь бот ведёт от первого лица —
        // но только за столом на двоих, где различать нечего: там имя звучит
        // не речью, а справкой о себе («Петя называет 120» вместо «называю
        // 120»). Это решает экран, а не место за столом (Катерина, 19.09).
        1 -> botTitle(settings, game)
        else -> botTitleSecond(settings, game)
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
fun seatsPhrase(seats: Int, game: String): String = when {
    game == GAME_THOUSAND && seats >= 3 ->
        "За столом трое: соперников двое, по семь карт и прикуп из трёх. Со следующей партии."

    game == GAME_THOUSAND ->
        "За столом двое: соперник один, по десять карт и два прикупа по две. Со следующей партии."

    game == GAME_KOZEL && seats >= 3 ->
        "За столом трое: соперников двое, по семь костей и семь в базаре. Со следующей партии."

    game == GAME_KOZEL ->
        "За столом двое: соперник один, по семь костей и четырнадцать в базаре. Со следующей партии."

    game == GAME_HUNDRED && seats >= 3 ->
        "За столом трое: соперников двое, по пять карт и одна на кон. Со следующей партии."

    game == GAME_HUNDRED ->
        "За столом двое: соперник один, по пять карт и одна на кон. Со следующей партии."

    seats >= 3 -> "За столом трое: соперников двое, по шесть карт каждому. Со следующей партии."

    else -> "За столом двое: соперник один, по шесть карт каждому. Со следующей партии."
}

/**
 * Ступени скорости речи соперника: короткий шаг вокруг обычной.
 *
 * Скорость — ещё одна примета, по которой бота узнают: голос и синтезатор
 * разводят его с приложением, но два бота за одним столом могут говорить
 * похоже, а быстрый говор и медленный не спутать. Ступеней намеренно мало:
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

/**
 * Пауза между репликами за столом — ступени, которыми её перебирают.
 *
 * Ноль — пауза выключена, всё как было. Дальше по секунде: реплики идут
 * вплотную, и незрячий игрок не успевает сообразить, кто из двоих что сказал
 * (Катерина, 19.09: «они быстро друг за другом идут, я физически не успеваю
 * сообразить, кто там что говорит»). Ступеней намеренно мало, и перебирают их
 * кнопкой, а не ползунком: ползунок незрячему неудобен, а «две тысячи
 * миллисекунд» ничего не говорят — то же правило, что у скорости речи.
 */
val PHRASE_PAUSES: List<Int> = listOf(0, 1000, 2000, 3000)

/** Пауза словами: «2000» человеку ни о чём не говорит, «две секунды» — говорит. */
fun phrasePauseTitle(ms: Int): String = when {
    ms <= 0 -> "выключена"
    ms == 1000 -> "1 секунда"
    else -> "${ms / 1000} секунды"
}

/**
 * Следующая ступень паузы — по кругу. Ближайшая ступень, а не точное
 * совпадение: пауза приходит из хранилища, и кнопка, которая на чужом числе
 * просто молчит, хуже кнопки, которая с него начинает.
 */
fun nextPhrasePause(ms: Int): Int {
    val at = PHRASE_PAUSES.indices.minByOrNull { kotlin.math.abs(PHRASE_PAUSES[it] - ms) } ?: 0
    return PHRASE_PAUSES[(at + 1) % PHRASE_PAUSES.size]
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
     * Голос бота — свой у каждой игры. null — «как у приложения»: бот говорит
     * и голосом приложения тоже, а разводит их синтезатор (см. [botEngine]).
     *
     * Игра — не украшение: за столом говорят трое, и два бота одним голосом
     * звучали бы как один собеседник, переходящий из игры в игру.
     */
    val botVoiceDurak: String? = null,
    /**
     * Голос второго соперника в «Дураке» — того, кто садится за стол, только
     * когда за ним трое. null — голос приложения, как и у первого бота.
     *
     * Свой у каждого, а не один на двоих: за столом на троих двое говорят
     * одним голосом — уже не различить, кто из них подкинул (Катерина,
     * 19.09; в «Тысяче» то же самое, и там это уже сделано).
     */
    val botVoiceDurakSecond: String? = null,
    val botVoiceThousand: String? = null,
    /**
     * Голос второго соперника в «Тысяче» — того, кто садится за стол, только
     * когда за ним трое. null — голос приложения, как и у первого бота.
     *
     * Свой голос у каждого, а не один на двоих: за столом на троих двое
     * говорят одним голосом — уже не различить, кто из них назвал сумму
     * (Катерина, 19.09).
     */
    val botVoiceThousandSecond: String? = null,
    val botVoiceKozel: String? = null,
    /**
     * Голос второго соперника в «Козле» — того, кто садится за стол, только
     * когда за ним трое. null — голос приложения, как и у первого бота.
     *
     * Свой у каждого: за столом на троих двое говорят одним голосом — уже не
     * различить, кто из них выложил кость (Катерина, 19.09; в «Тысяче» и
     * «Дураке» то же самое, и там это уже сделано).
     */
    val botVoiceKozelSecond: String? = null,
    val botVoiceHundred: String? = null,
    /**
     * Голос второго соперника в «101» — того, кто садится за стол, только
     * когда за ним трое. null — голос приложения, как и у первого бота.
     */
    val botVoiceHundredSecond: String? = null,
    /**
     * Синтезатор соперника — свой у каждой игры, как и голос. null — «как у
     * приложения», пусто — «системный».
     *
     * Голос выбирают внутри движка, и голосов у движка бывает один: тогда
     * бот, которому движок не сменили, говорит ровно тем же голосом, что и
     * приложение, и различить их за столом нечем. Другой движок — различие,
     * которое ни с чем не спутать (Катерина, 19.09: «чтобы в голосе каждого
     * бота можно было выбирать и движок, и голос»).
     */
    val botEngineDurak: String? = null,
    /** Синтезатор второго соперника в «Дураке» — у него свой, как и голос. */
    val botEngineDurakSecond: String? = null,
    val botEngineThousand: String? = null,
    /** Синтезатор второго соперника в «Тысяче» — у него свой, как и голос. */
    val botEngineThousandSecond: String? = null,
    val botEngineKozel: String? = null,
    /** Синтезатор второго соперника в «Козле» — у него свой, как и голос. */
    val botEngineKozelSecond: String? = null,
    val botEngineHundred: String? = null,
    /** Синтезатор второго соперника в «101» — у него свой, как и голос. */
    val botEngineHundredSecond: String? = null,
    /**
     * Скорость речи соперника — своя у каждой игры, как и голос. 1.0 — как
     * говорит приложение.
     *
     * Своя у игры, а не одна на всех: голоса у ботов разные, и одна скорость
     * на четверых заставляла бы подгонять всех под того, кого слушаешь чаще.
     * Ступени — [BOT_RATES].
     */
    val botRateDurak: Float = 1.0f,
    /**
     * Скорость второго соперника в «Дураке» — ещё одна примета вдобавок к
     * голосу и синтезатору: за столом на троих говорят трое, и различать их
     * надо всех (Катерина, 19.09).
     */
    val botRateDurakSecond: Float = 1.0f,
    val botRateThousand: Float = 1.0f,
    /**
     * Скорость второго соперника в «Тысяче» — ещё одна примета вдобавок к
     * голосу и синтезатору: за столом на троих говорят трое, и различать их
     * надо всех (Катерина, 19.09).
     */
    val botRateThousandSecond: Float = 1.0f,
    val botRateKozel: Float = 1.0f,
    /**
     * Скорость второго соперника в «Козле» — ещё одна примета вдобавок к
     * голосу и синтезатору: за столом на троих говорят трое, и различать их
     * надо всех (Катерина, 19.09).
     */
    val botRateKozelSecond: Float = 1.0f,
    val botRateHundred: Float = 1.0f,
    /**
     * Скорость второго соперника в «101» — ещё одна примета вдобавок к голосу
     * и синтезатору: за столом на троих говорят трое, и различать их надо всех.
     */
    val botRateHundredSecond: Float = 1.0f,
    val voiceMode: VoiceMode = VoiceMode.AUTO,
    val botTalk: Boolean = true,
    /**
     * Пауза между репликами за столом, в миллисекундах. Ноль — выключена.
     *
     * Тишина не между звуком и речью и не между картой и ходом, а между двумя
     * фразами: игроку нужно время не услышать, а сообразить, кто сказал и что
     * именно. Ответы на собственные нажатия пауза не задерживает — там ждать
     * нечего.
     */
    val phrasePauseMs: Int = 0,
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
    /**
     * Имя второго соперника «Дурака» — того, кто садится за стол, только
     * когда за ним трое. Пусто — «Второй бот».
     *
     * Своё у игры, как и у первого: за одним столом Петя, за другим Вася —
     * и переставлять их имена одной строкой значило бы звать за столом не
     * того, кого игрок назвал (Катерина, 19.09).
     */
    val botNameDurakSecond: String = "",
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
     * Имя второго соперника «Козла» — того, кто садится за стол, только
     * когда за ним трое. Пусто — «Второй бот».
     *
     * Своё у игры, как и у первого: за одним столом Петя, за другим Вася —
     * и переставлять их имена одной строкой значило бы звать за столом не
     * того, кого игрок назвал (Катерина, 19.09).
     */
    val botNameKozelSecond: String = "",
    val botNameHundred: String = "",
    /**
     * Имя второго соперника «101» — того, кто садится за стол, только когда
     * за ним трое. Пусто — «Второй бот».
     */
    val botNameHundredSecond: String = "",
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
    /**
     * Сколько мест за столом «Дурака»: двое или трое.
     *
     * В «Дурака» садятся и вдвоём, и втроём — движок держит оба стола (круг
     * подкидывающих на троих, см. DurakSeatsTest), поэтому выбор здесь, а не
     * в правилах: игрок сам решает, за какой стол садиться.
     *
     * По умолчанию двое: так партия шла до сих пор, и менять её задним числом
     * нельзя — открытая партия продолжается за тем столом, за каким её начали
     * (число мест записано в самой партии, см. DurakSave).
     */
    val durakSeats: Int = 2,
    /**
     * Сколько мест за столом «Козла»: двое или трое.
     *
     * Третье место в «Козле» — не ещё один бот, а другой расклад: набор
     * «дубль-шесть» — 28 костей, и по семь на руке двоим и троим, значит
     * втроём базар сжимается с четырнадцати костей до семи. Движок держит
     * оба стола, поэтому выбор здесь, а не в правилах.
     *
     * По умолчанию двое: так партия шла до сих пор, и менять её задним
     * числом нельзя — открытая партия продолжается за тем столом, за каким
     * её начали (число мест записано в самой партии, см. KozelSave).
     */
    val kozelSeats: Int = 2,
    /**
     * Сколько мест за столом «101»: двое или трое.
     *
     * Движок держит оба стола — на троих карт на руки приходит столько же,
     * меняется лишь число соперников, — поэтому выбор здесь, а не в правилах.
     *
     * По умолчанию трое: «101» складывалась как игра на троих, и вдвоём в неё
     * садятся реже. Открытая партия продолжается за тем столом, за каким её
     * начали: число мест помнит сама запись (HundredSave).
     */
    val hundredSeats: Int = 3,
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
    val botDifficultyHundred: Difficulty = Difficulty.NORMAL,
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
private const val KEY_BOT_VOICE_DURAK_SECOND = "bot_voice_durak_second"
private const val KEY_BOT_VOICE_THOUSAND = "bot_voice_thousand"
private const val KEY_BOT_VOICE_THOUSAND_SECOND = "bot_voice_thousand_second"
private const val KEY_BOT_VOICE_KOZEL = "bot_voice_kozel"
private const val KEY_BOT_VOICE_KOZEL_SECOND = "bot_voice_kozel_second"
private const val KEY_BOT_VOICE_HUNDRED = "bot_voice_hundred"
private const val KEY_BOT_VOICE_HUNDRED_SECOND = "bot_voice_hundred_second"

/**
 * Синтезатор соперника. Пустая строка — «системный»: она значит «движок по
 * умолчанию», а не «как у приложения», и путать эти два состояния нельзя.
 */
private const val KEY_BOT_ENGINE_DURAK = "bot_engine_durak"
private const val KEY_BOT_ENGINE_DURAK_SECOND = "bot_engine_durak_second"
private const val KEY_BOT_ENGINE_THOUSAND = "bot_engine_thousand"
private const val KEY_BOT_ENGINE_THOUSAND_SECOND = "bot_engine_thousand_second"
private const val KEY_BOT_ENGINE_KOZEL = "bot_engine_kozel"
private const val KEY_BOT_ENGINE_KOZEL_SECOND = "bot_engine_kozel_second"
private const val KEY_BOT_ENGINE_HUNDRED = "bot_engine_hundred"
private const val KEY_BOT_ENGINE_HUNDRED_SECOND = "bot_engine_hundred_second"
private const val KEY_BOT_RATE_DURAK = "bot_rate_durak"
private const val KEY_BOT_RATE_DURAK_SECOND = "bot_rate_durak_second"
private const val KEY_BOT_RATE_THOUSAND = "bot_rate_thousand"
private const val KEY_BOT_RATE_THOUSAND_SECOND = "bot_rate_thousand_second"
private const val KEY_BOT_RATE_KOZEL = "bot_rate_kozel"
private const val KEY_BOT_RATE_KOZEL_SECOND = "bot_rate_kozel_second"
private const val KEY_BOT_RATE_HUNDRED = "bot_rate_hundred"
private const val KEY_BOT_RATE_HUNDRED_SECOND = "bot_rate_hundred_second"
private const val KEY_VOICE_MODE = "voice_mode"
private const val KEY_BOT_TALK = "bot_talk"
private const val KEY_PHRASE_PAUSE = "phrase_pause"
private const val KEY_BOT_NAME_DURAK = "bot_name_durak"
private const val KEY_BOT_NAME_DURAK_SECOND = "bot_name_durak_second"
private const val KEY_BOT_NAME_THOUSAND = "bot_name_thousand"
private const val KEY_BOT_NAME_THOUSAND_SECOND = "bot_name_thousand_second"
private const val KEY_BOT_NAME_KOZEL = "bot_name_kozel"
private const val KEY_BOT_NAME_KOZEL_SECOND = "bot_name_kozel_second"
private const val KEY_BOT_NAME_HUNDRED = "bot_name_hundred"
private const val KEY_BOT_NAME_HUNDRED_SECOND = "bot_name_hundred_second"
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
private const val KEY_DURAK_SEATS = "durak.seats"
private const val KEY_KOZEL_SEATS = "kozel.seats"
private const val KEY_HUNDRED_SEATS = "hundred.seats"
private const val KEY_SOUNDS = "sounds"
private const val KEY_SIGNALS = "signals"
private const val KEY_VIBRATION = "vibration"
private const val KEY_OWN_VIBRATION = "own_vibration"
private const val KEY_BOT_DIFFICULTY_DURAK = "durak.difficulty"
private const val KEY_BOT_DIFFICULTY_THOUSAND = "thousand.difficulty"
private const val KEY_BOT_DIFFICULTY_KOZEL = "kozel.difficulty"
private const val KEY_BOT_DIFFICULTY_HUNDRED = "hundred.difficulty"
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
        botVoiceDurakSecond = prefs.getString(KEY_BOT_VOICE_DURAK_SECOND, null),
        botVoiceThousand = prefs.getString(KEY_BOT_VOICE_THOUSAND, null),
        botVoiceThousandSecond = prefs.getString(KEY_BOT_VOICE_THOUSAND_SECOND, null),
        botVoiceKozel = prefs.getString(KEY_BOT_VOICE_KOZEL, null),
        botVoiceKozelSecond = prefs.getString(KEY_BOT_VOICE_KOZEL_SECOND, null),
        botVoiceHundred = prefs.getString(KEY_BOT_VOICE_HUNDRED, null),
        botVoiceHundredSecond = prefs.getString(KEY_BOT_VOICE_HUNDRED_SECOND, null),
        botEngineDurak = prefs.getString(KEY_BOT_ENGINE_DURAK, null),
        botEngineDurakSecond = prefs.getString(KEY_BOT_ENGINE_DURAK_SECOND, null),
        botEngineThousand = prefs.getString(KEY_BOT_ENGINE_THOUSAND, null),
        botEngineThousandSecond = prefs.getString(KEY_BOT_ENGINE_THOUSAND_SECOND, null),
        botEngineKozel = prefs.getString(KEY_BOT_ENGINE_KOZEL, null),
        botEngineKozelSecond = prefs.getString(KEY_BOT_ENGINE_KOZEL_SECOND, null),
        botEngineHundred = prefs.getString(KEY_BOT_ENGINE_HUNDRED, null),
        botEngineHundredSecond = prefs.getString(KEY_BOT_ENGINE_HUNDRED_SECOND, null),
        botRateDurak = readBotRate(prefs, KEY_BOT_RATE_DURAK),
        botRateDurakSecond = readBotRate(prefs, KEY_BOT_RATE_DURAK_SECOND),
        botRateThousand = readBotRate(prefs, KEY_BOT_RATE_THOUSAND),
        botRateThousandSecond = readBotRate(prefs, KEY_BOT_RATE_THOUSAND_SECOND),
        botRateKozel = readBotRate(prefs, KEY_BOT_RATE_KOZEL),
        botRateKozelSecond = readBotRate(prefs, KEY_BOT_RATE_KOZEL_SECOND),
        botRateHundred = readBotRate(prefs, KEY_BOT_RATE_HUNDRED),
        botRateHundredSecond = readBotRate(prefs, KEY_BOT_RATE_HUNDRED_SECOND),
        voiceMode = prefs.getString(KEY_VOICE_MODE, null)
            ?.let { name -> runCatching { VoiceMode.valueOf(name) }.getOrNull() }
            ?: VoiceMode.AUTO,
        botTalk = prefs.getBoolean(KEY_BOT_TALK, true),
        phrasePauseMs = readPhrasePause(prefs),
        botNameDurak = readBotName(prefs, KEY_BOT_NAME_DURAK, KEY_BOT_NAME),
        botNameDurakSecond = readBotName(prefs, KEY_BOT_NAME_DURAK_SECOND, KEY_BOT_NAME_SECOND),
        botNameThousand = readBotName(prefs, KEY_BOT_NAME_THOUSAND, KEY_BOT_NAME),
        botNameThousandSecond = readBotName(prefs, KEY_BOT_NAME_THOUSAND_SECOND, KEY_BOT_NAME_SECOND),
        botNameKozel = readBotName(prefs, KEY_BOT_NAME_KOZEL, KEY_BOT_NAME),
        botNameKozelSecond = readBotName(prefs, KEY_BOT_NAME_KOZEL_SECOND, KEY_BOT_NAME_SECOND),
        botNameHundred = readBotName(prefs, KEY_BOT_NAME_HUNDRED, KEY_BOT_NAME),
        botNameHundredSecond = readBotName(prefs, KEY_BOT_NAME_HUNDRED_SECOND, KEY_BOT_NAME_SECOND),
        thousandSeats = readSeats(prefs, KEY_THOUSAND_SEATS),
        durakSeats = readSeats(prefs, KEY_DURAK_SEATS),
        kozelSeats = readSeats(prefs, KEY_KOZEL_SEATS),
        hundredSeats = readSeats(prefs, KEY_HUNDRED_SEATS, default = 3),
        sounds = prefs.getBoolean(KEY_SOUNDS, true),
        signals = prefs.getBoolean(KEY_SIGNALS, true),
        vibration = prefs.getBoolean(KEY_VIBRATION, true),
        ownVibration = prefs.getBoolean(KEY_OWN_VIBRATION, true),
        botDifficultyDurak = readDifficulty(prefs, KEY_BOT_DIFFICULTY_DURAK),
        botDifficultyThousand = readDifficulty(prefs, KEY_BOT_DIFFICULTY_THOUSAND),
        botDifficultyKozel = readDifficulty(prefs, KEY_BOT_DIFFICULTY_KOZEL),
        botDifficultyHundred = readDifficulty(prefs, KEY_BOT_DIFFICULTY_HUNDRED),
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
 * Скорость речи соперника из хранилища. Ключа нет — обычная: бот, которого
 * не настраивали, говорит как приложение, и это то, к чему игрок привык.
 */
private fun readBotRate(prefs: SharedPreferences, key: String): Float =
    (prefs.all[key] as? Float)?.coerceIn(RATE_MIN, RATE_MAX) ?: 1.0f

/**
 * Пауза между репликами из хранилища. Ключа нет — выключена: игра, к которой
 * игрок привык, не должна меняться от обновления.
 *
 * Число приводим к ближайшей ступени: в хранилище его мог положить кто угодно,
 * а ступени — единственное, что умеет кнопка настройки.
 */
private fun readPhrasePause(prefs: SharedPreferences): Int {
    val stored = (prefs.all[KEY_PHRASE_PAUSE] as? Int) ?: return 0
    return PHRASE_PAUSES.minByOrNull { kotlin.math.abs(it - stored) } ?: 0
}

/**
 * Имя соперника из хранилища. Своего у игры ещё нет — берём прежнее общее:
 * так имя, набранное до переезда, находится за каждым из трёх столов, а не
 * пропадает у того, кто набирал его в общих настройках.
 */
private fun readBotName(prefs: SharedPreferences, key: String, legacy: String): String =
    (prefs.getString(key, null) ?: prefs.getString(legacy, null)).orEmpty()

/**
 * Число мест за столом игры из хранилища. Ключа нет — двое, как играли до
 * того, как появился выбор: партия, начатая до обновления, продолжается за
 * тем же столом, за каким шла.
 */
private fun readSeats(prefs: SharedPreferences, key: String, default: Int = 2): Int =
    (prefs.all[key] as? Int)?.coerceIn(2, 3) ?: default

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
        .putString(KEY_BOT_VOICE_DURAK_SECOND, settings.botVoiceDurakSecond)
        .putString(KEY_BOT_VOICE_THOUSAND, settings.botVoiceThousand)
        .putString(KEY_BOT_VOICE_THOUSAND_SECOND, settings.botVoiceThousandSecond)
        .putString(KEY_BOT_VOICE_KOZEL, settings.botVoiceKozel)
        .putString(KEY_BOT_VOICE_KOZEL_SECOND, settings.botVoiceKozelSecond)
        .putString(KEY_BOT_VOICE_HUNDRED, settings.botVoiceHundred)
        .putString(KEY_BOT_VOICE_HUNDRED_SECOND, settings.botVoiceHundredSecond)
        .putString(KEY_BOT_ENGINE_DURAK, settings.botEngineDurak)
        .putString(KEY_BOT_ENGINE_DURAK_SECOND, settings.botEngineDurakSecond)
        .putString(KEY_BOT_ENGINE_THOUSAND, settings.botEngineThousand)
        .putString(KEY_BOT_ENGINE_THOUSAND_SECOND, settings.botEngineThousandSecond)
        .putString(KEY_BOT_ENGINE_KOZEL, settings.botEngineKozel)
        .putString(KEY_BOT_ENGINE_KOZEL_SECOND, settings.botEngineKozelSecond)
        .putString(KEY_BOT_ENGINE_HUNDRED, settings.botEngineHundred)
        .putString(KEY_BOT_ENGINE_HUNDRED_SECOND, settings.botEngineHundredSecond)
        .putFloat(KEY_BOT_RATE_DURAK, settings.botRateDurak)
        .putFloat(KEY_BOT_RATE_DURAK_SECOND, settings.botRateDurakSecond)
        .putFloat(KEY_BOT_RATE_THOUSAND, settings.botRateThousand)
        .putFloat(KEY_BOT_RATE_THOUSAND_SECOND, settings.botRateThousandSecond)
        .putFloat(KEY_BOT_RATE_KOZEL, settings.botRateKozel)
        .putFloat(KEY_BOT_RATE_KOZEL_SECOND, settings.botRateKozelSecond)
        .putFloat(KEY_BOT_RATE_HUNDRED, settings.botRateHundred)
        .putFloat(KEY_BOT_RATE_HUNDRED_SECOND, settings.botRateHundredSecond)
        .putString(KEY_VOICE_MODE, settings.voiceMode.name)
        .putBoolean(KEY_BOT_TALK, settings.botTalk)
        .putInt(KEY_PHRASE_PAUSE, settings.phrasePauseMs)
        .putString(KEY_BOT_NAME_DURAK, settings.botNameDurak)
        .putString(KEY_BOT_NAME_DURAK_SECOND, settings.botNameDurakSecond)
        .putString(KEY_BOT_NAME_THOUSAND, settings.botNameThousand)
        .putString(KEY_BOT_NAME_THOUSAND_SECOND, settings.botNameThousandSecond)
        .putString(KEY_BOT_NAME_KOZEL, settings.botNameKozel)
        .putString(KEY_BOT_NAME_KOZEL_SECOND, settings.botNameKozelSecond)
        .putString(KEY_BOT_NAME_HUNDRED, settings.botNameHundred)
        .putString(KEY_BOT_NAME_HUNDRED_SECOND, settings.botNameHundredSecond)
        .putInt(KEY_THOUSAND_SEATS, settings.thousandSeats.coerceIn(2, 3))
        .putInt(KEY_DURAK_SEATS, settings.durakSeats.coerceIn(2, 3))
        .putInt(KEY_KOZEL_SEATS, settings.kozelSeats.coerceIn(2, 3))
        .putInt(KEY_HUNDRED_SEATS, settings.hundredSeats.coerceIn(2, 3))
        .putBoolean(KEY_SOUNDS, settings.sounds)
        .putBoolean(KEY_SIGNALS, settings.signals)
        .putBoolean(KEY_VIBRATION, settings.vibration)
        .putBoolean(KEY_OWN_VIBRATION, settings.ownVibration)
        .putString(KEY_BOT_DIFFICULTY_DURAK, settings.botDifficultyDurak.name)
        .putString(KEY_BOT_DIFFICULTY_THOUSAND, settings.botDifficultyThousand.name)
        .putString(KEY_BOT_DIFFICULTY_KOZEL, settings.botDifficultyKozel.name)
        .putString(KEY_BOT_DIFFICULTY_HUNDRED, settings.botDifficultyHundred.name)
        .putString(KEY_ORDER, settings.order.name)
        .putString(KEY_TILE_ORDER, settings.tileOrder.name)
        .putString(KEY_LAST_GAME, settings.lastGame)
        .putBoolean(KEY_LARGE, settings.largeText)
        .putBoolean(KEY_AUTOSAVE, settings.autosave)
        .putBoolean(KEY_AUTO_UPDATE, settings.autoUpdate)
        .apply()
}
