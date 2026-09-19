package games.cardgames.settings

import android.speech.tts.Voice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import games.cardgames.BuildConfig
import games.cardgames.GAME_DURAK
import games.cardgames.GAME_KOZEL
import games.cardgames.GAME_THOUSAND
import games.cardgames.diag.Journal
import games.cardgames.diag.JournalShare
import games.cardgames.durak.DURAK_SETTINGS
import games.cardgames.kozel.KOZEL_SETTINGS
import games.cardgames.score.loadScore
import games.cardgames.score.saveScore
import games.cardgames.score.Score
import games.cardgames.thousand.AUTO_PRAISE
import games.cardgames.thousand.THOUSAND_SETTINGS
import games.cardgames.speech.Speaker
import games.cardgames.speech.sayEvent
import games.cardgames.speech.speech
import games.cardgames.update.Update
import games.engine.durak.Difficulty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Образец речи: по нему игрок и выбирает голос — на слух, а не по названию. */
private const val SAMPLE = "Так будет звучать игра. Козырь — пики, у тебя семёрка червей."

/**
 * Чей голос выбирают. Своя строка у каждого, кто за столом говорит.
 *
 * [inherit] — как звучит этот голос, пока своего ему не выбрали. Бот без
 * своего голоса говорит голосом приложения: высоту ему больше не сдвигают,
 * разводить соперников положено синтезаторами (Катерина, 19.09: «убери,
 * чтобы повышение голоса было у каждого бота»).
 */
private enum class VoiceSlot(val title: String, val rateLabel: String, val inherit: String) {
    APP("Голос приложения", "Скорость речи приложения", "системный"),
    DURAK("Голос соперника в дураке", "Скорость речи соперника в дураке", "как у приложения"),
    /**
     * Второй соперник «Дурака» — тот, кто садится за стол, только когда за
     * ним трое. Со своим голосом и скоростью, как в «Тысяче»: за столом на
     * троих двое одним голосом — уже не различить, кто подкинул.
     */
    DURAK_SECOND(
        "Голос второго соперника в дураке",
        "Скорость речи второго соперника в дураке",
        "как у приложения",
    ),
    THOUSAND("Голос соперника в тысяче", "Скорость речи соперника в тысяче", "как у приложения"),
    THOUSAND_SECOND(
        "Голос второго соперника в тысяче",
        "Скорость речи второго соперника в тысяче",
        "как у приложения",
    ),
    KOZEL("Голос соперника в козле", "Скорость речи соперника в козле", "как у приложения"),
}

/**
 * Строка второго соперника этой игры. Второй садится не за всякий стол, и
 * строка у него одна на все игры, где он садится, — но голос и скорость у
 * каждой игры свои: «второй бот» за дураком и за тысячей — разные люди.
 */
private fun secondSlot(game: String): VoiceSlot = when (game) {
    GAME_THOUSAND -> VoiceSlot.THOUSAND_SECOND
    else -> VoiceSlot.DURAK_SECOND
}

/** Второй ли это соперник: у второго и строки в настройках игры другие. */
private fun VoiceSlot.isSecond(): Boolean =
    this == VoiceSlot.DURAK_SECOND || this == VoiceSlot.THOUSAND_SECOND

/**
 * Каким голосом заговорит тот, чью строку настроек правят: у приложения — его
 * собственным, у соперника — его, а не выбрали — голосом приложения.
 */
private fun slotVoice(settings: Settings, slot: VoiceSlot): String? =
    if (slot == VoiceSlot.APP) {
        settings.voice
    } else {
        botVoice(
            own = slotVoiceName(settings, slot),
            appVoice = settings.voice,
            ownEngine = slotEngineOwn(settings, slot),
            appEngine = settings.engine,
        )
    }

/** Что записано в настройках про синтезатор того, чью строку правят. */
private fun slotEngineOwn(settings: Settings, slot: VoiceSlot): String? = when (slot) {
    VoiceSlot.APP -> settings.engine
    VoiceSlot.DURAK -> settings.botEngineDurak
    VoiceSlot.DURAK_SECOND -> settings.botEngineDurakSecond
    VoiceSlot.THOUSAND -> settings.botEngineThousand
    VoiceSlot.THOUSAND_SECOND -> settings.botEngineThousandSecond
    VoiceSlot.KOZEL -> settings.botEngineKozel
}

/**
 * Каким синтезатором тот, чью строку правят, заговорит на самом деле.
 *
 * У приложения это его собственный, у соперника — его же, пока игрок не
 * выбрал боту другой (см. [botEngine]).
 */
private fun slotEngine(settings: Settings, slot: VoiceSlot): String? =
    if (slot == VoiceSlot.APP) settings.engine
    else botEngine(slotEngineOwn(settings, slot), settings.engine)

/**
 * Те же настройки, но с другим синтезатором у того, чья это строка.
 *
 * Выбранный голос при этом стирается: имя голоса живёт внутри движка, и в
 * чужом оно значит не «тот же голос», а «первый подходящий». Оставить его
 * значило бы обещать голос, которого новый движок не знает.
 */
private fun withEngine(settings: Settings, slot: VoiceSlot, engine: String?): Settings = when (slot) {
    VoiceSlot.APP -> settings.copy(engine = engine, voice = null)
    VoiceSlot.DURAK -> settings.copy(botEngineDurak = engine, botVoiceDurak = null)
    VoiceSlot.DURAK_SECOND ->
        settings.copy(botEngineDurakSecond = engine, botVoiceDurakSecond = null)
    VoiceSlot.THOUSAND -> settings.copy(botEngineThousand = engine, botVoiceThousand = null)
    VoiceSlot.THOUSAND_SECOND ->
        settings.copy(botEngineThousandSecond = engine, botVoiceThousandSecond = null)

    VoiceSlot.KOZEL -> settings.copy(botEngineKozel = engine, botVoiceKozel = null)
}

/**
 * Как звучит тот, чью строку правят, пока своего голоса ему не выбрали.
 *
 * Бот, говорящий синтезатором приложения, говорит и голосом приложения — так
 * и надо сказать. А бот со своим синтезатором берёт голос, который у того
 * движка в телефоне стоит по умолчанию: голос приложения в чужом движке не
 * значит ничего (Катерина, 19.09).
 */
private fun inheritLabel(slot: VoiceSlot, settings: Settings): String =
    if (slotVoice(settings, slot) != null) slot.inherit else "голос этого синтезатора по умолчанию"

/** Скорость речи того, чью строку настроек правят. */
private fun slotRate(settings: Settings, slot: VoiceSlot): Float = when (slot) {
    VoiceSlot.APP -> settings.rate
    VoiceSlot.DURAK -> settings.botRateDurak
    VoiceSlot.DURAK_SECOND -> settings.botRateDurakSecond
    VoiceSlot.THOUSAND -> settings.botRateThousand
    VoiceSlot.THOUSAND_SECOND -> settings.botRateThousandSecond
    VoiceSlot.KOZEL -> settings.botRateKozel
}

/** Те же настройки, но со сменённой скоростью у того, чья это строка. */
private fun withRate(settings: Settings, slot: VoiceSlot, rate: Float): Settings = when (slot) {
    VoiceSlot.APP -> settings.copy(rate = rate)
    VoiceSlot.DURAK -> settings.copy(botRateDurak = rate)
    VoiceSlot.DURAK_SECOND -> settings.copy(botRateDurakSecond = rate)
    VoiceSlot.THOUSAND -> settings.copy(botRateThousand = rate)
    VoiceSlot.THOUSAND_SECOND -> settings.copy(botRateThousandSecond = rate)
    VoiceSlot.KOZEL -> settings.copy(botRateKozel = rate)
}

/** Голос, выбранный вручную: не выбран — null, и за столом звучит чужой. */
private fun slotVoiceName(settings: Settings, slot: VoiceSlot): String? = when (slot) {
    VoiceSlot.APP -> settings.voice
    VoiceSlot.DURAK -> settings.botVoiceDurak
    VoiceSlot.DURAK_SECOND -> settings.botVoiceDurakSecond
    VoiceSlot.THOUSAND -> settings.botVoiceThousand
    VoiceSlot.THOUSAND_SECOND -> settings.botVoiceThousandSecond
    VoiceSlot.KOZEL -> settings.botVoiceKozel
}

/**
 * Как та же строка зовётся в настройках самой игры. [title] называет ещё и
 * игру («голос соперника в тысяче») — это для списка голосов, который
 * открывается поверх и от самой игры оторван. В её же настройках игра названа
 * строкой выше, и повторять её в каждой строке — лишняя остановка для пальца,
 * который идёт по списку на слух.
 */
private fun VoiceSlot.titleInGame(): String =
    if (isSecond()) "Голос второго соперника" else "Голос соперника"

private fun VoiceSlot.rateLabelInGame(): String =
    if (isSecond()) "Скорость речи второго соперника" else "Скорость речи соперника"

/** Чья строка голоса отвечает этой игре. */
private fun gameSlot(game: String): VoiceSlot = when (game) {
    GAME_KOZEL -> VoiceSlot.KOZEL
    GAME_THOUSAND -> VoiceSlot.THOUSAND
    else -> VoiceSlot.DURAK
}

/** Строка выбора голоса: чей это голос и какой сейчас стоит. */
private fun voiceRowTitle(
    name: String?,
    voices: List<Voice>,
    title: String,
    inherit: String,
): String {
    val index = voices.indexOfFirst { it.name == name }
    return "$title: " + if (index < 0) inherit else "${index + 1} из ${voices.size}"
}

/**
 * Образец для пробы. Перед ним — где мы в списке: голосов в телефоне бывает
 * десяток, и без этого не понять, далеко ли ещё листать.
 */
private fun sampleFor(index: Int, total: Int): String =
    (if (index < 0) "Голос по умолчанию" else "Голос ${index + 1} из $total") + ". $SAMPLE"

/**
 * Настройки: всё, что можно включить, выключить или выбрать.
 *
 * Экран один на всё приложение, а игр три, поэтому первым делом он знает,
 * чьи настройки показывает: [game] — игра, из-за стола которой пришли.
 * Её правила и её соперник стоят наверху, а ниже идёт общее — то, что
 * одинаково за любым столом. Пришли из главного меню ([game] равен null) —
 * на экране только общее: правила игры показывают за её столом, в общем
 * списке они только путали (SETTINGS.md, 2).
 *
 * Голоса выбираются на слух: название голоса в системе —
 * «ru-ru-x-ruf-network», человеку оно ничего не говорит. Поэтому выбор
 * открывает список целиком, а каждый выбор сразу звучит образцом.
 *
 * Перебором по кругу это было раньше — и не годилось: у игрока голосов
 * много, а найти среди них нужный перебором нельзя.
 */
@Composable
fun SettingsScreen(game: String?, onExit: () -> Unit) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(loadSettings(context)) }
    var readyTick by remember { mutableIntStateOf(0) }
    var resetAsked by remember { mutableStateOf(false) }
    var clearAsked by remember { mutableStateOf(false) }

    // Проверка обновления идёт в сети: пока она идёт, кнопка говорит об этом
    // сама — молчащая кнопка читается как «нажал, и ничего не случилось».
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Что открыто: настройки своей игры или общие для всей программы.
    // Из главного меню общие — единственное, что есть, и переключателя там
    // не нужно. Из-за стола их двое, и по умолчанию открыта игра: за ней и
    // лезут в настройки посреди партии.
    var part by remember { mutableStateOf(if (game == null) SettingsPart.COMMON else SettingsPart.GAME) }

    // Размер журнала читаем при входе: по нему видно, есть ли что отправлять,
    // не открывая файл. За время на экране он растёт — но это уже неважно,
    // важно, что было до.
    var journalSize by remember { mutableStateOf(Journal.sizeTitle(context)) }

    // Черновик скорости: пока палец на ползунке, настройку не трогаем —
    // иначе Speaker пересобирался бы на каждый пиксель движения.
    var rateDraft by remember { mutableStateOf(settings.rate) }

    // Смена синтезатора или голоса — это новый Speaker: у уже созданного
    // движка их на ходу не поменять. Скорости в ключе нет намеренно: её
    // меняют на живом синтезаторе (см. applyRate), иначе каждое нажатие
    // «Быстрее» пересобирало бы движок с полусекундной задержкой.
    val speaker = remember(settings.engine, settings.voice) {
        Speaker(
            context = context,
            rate = settings.rate,
            enginePackage = settings.engine,
            voiceName = settings.voice,
        ).also { it.onReady = { readyTick++ } }
    }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }

    // Кто говорит прямо сейчас — то же правило, что и за столом: в каждый
    // момент ровно один. Нужно и здесь, в настройках: иначе переключение
    // настроек озвучивают оба голоса сразу. «Никто» молчит и здесь.
    val speech = settings.voiceMode.speech(speaker.screenReaderOn)
    val view = LocalView.current

    // Список голосов открыт для одного из троих. null — закрыт. Голоса
    // захвачены в момент открытия: их берут у того синтезатора, которым этот
    // собеседник говорит, а он у каждой строки свой.
    var picking by remember { mutableStateOf<Pair<VoiceSlot, List<Voice>>?>(null) }

    // Проба голоса идёт своим синтезатором, а не тем, которым говорят сами
    // настройки: голос у них разный, и подменять голос живого синтезатора
    // значило бы, что после пробы настройки заговорят не своим голосом.
    // Заводим его лениво — пока голос не выбирали, второго движка нет.
    var audition by remember { mutableStateOf<Speaker?>(null) }
    var auditionEngine by remember { mutableStateOf(settings.engine) }
    // Значение захватываем: к закрытию эффекта в audition лежит уже
    // следующий синтезатор, и гасить его нельзя — только отработавший.
    DisposableEffect(audition) {
        val probe = audition
        onDispose { probe?.shutdown() }
    }

    /**
     * Проба голоса: включаем его и говорим образец. Настройку это не меняет —
     * её сохраняет тот, кто пробу заказал.
     *
     * Образец звучит не при входе в настройки, а когда игрок сам сменил
     * синтезатор или голос: при входе он только мешает — скринридер в это
     * время читает экран, и две речи накладываются.
     *
     * Когда за экраном скринридер, образец уходит ему: своего синтезатора в
     * этот момент не слышно, и подбирать голос на слух всё равно придётся с
     * ним — для этого есть режим «Кто говорит: приложение».
     */
    fun auditionVoice(engine: String?, voice: String?, text: String, rate: Float = settings.rate) {
        if (!speech.speaks) {
            sayEvent(view, speaker, speech, text)
            return
        }
        var probe = audition
        // Синтезатор сменили — прежняя проба говорила чужим движком.
        if (probe == null || auditionEngine != engine) {
            probe = Speaker(context, rate = rate, enginePackage = engine)
            audition = probe
            auditionEngine = engine
        } else {
            // Скорость ставим на живом движке: у пробы своя скорость на
            // каждый образец, и прежняя к новому не относится.
            probe.setRate(rate)
        }
        probe.previewVoice(voice, text)
    }

    // readyTick в ключе: пока синтезатор не поднялся, списки пустые,
    // и пересобрать их надо ровно тогда, когда он ответил.
    val engines = remember(readyTick, settings.engine) { speaker.engines() }
    val voices = remember(readyTick, settings.engine) { speaker.voices() }

    // Системный синтезатор называем по имени: иначе системный и названный по
    // имени — это один и тот же движок, а выглядят они как два разных.
    val systemEngine = remember(engines) {
        speaker.defaultEngine()?.let { name -> engines.firstOrNull { it.name == name }?.label ?: name }
    }
    val systemTitle = systemEngine?.let { "системный — $it" } ?: "системный"

    /**
     * Синтезатор соперника — свой, если игрок его ему выбрал.
     *
     * Заводим лениво: пока движок у бота тот же, что у приложения, второго
     * синтезатора в телефоне нет, и поднимать его ради списка голосов,
     * который, может, и не откроют, значит держать лишний движок.
     */
    @Composable
    fun botSpeaker(slot: VoiceSlot): Speaker? {
        val engine = slotEngine(settings, slot)
        return if (engine == settings.engine) {
            null
        } else {
            val bot = remember(engine) {
                Speaker(
                    context = context,
                    rate = settings.rate,
                    enginePackage = engine,
                ).also { it.onReady = { readyTick++ } }
            }
            DisposableEffect(bot) { onDispose { bot.shutdown() } }
            bot
        }
    }

    /** Голоса того, чью строку правят, — из его синтезатора, а не из чужого. */
    @Composable
    fun slotVoices(slot: VoiceSlot): List<Voice> {
        val bot = botSpeaker(slot)
        return if (bot == null) voices else remember(bot, readyTick) { bot.voices() }
    }

    fun save(next: Settings) {
        settings = next
        saveSettings(context, next)
    }

    /**
     * Отозваться на переключение. Когда говорит скринридер, фразу отдаём ему:
     * он прочитает её своим голосом следом за тем, что читал, а не поверх —
     * иначе два голоса накладываются и выходит каша.
     *
     * Так отвечают все кнопки и переключатели настроек без исключения. Раньше
     * часть из них звала синтезатор напрямую, и при включённом скринридере
     * выбранный порядок карт произносили оба разом.
     */
    fun announce(text: String) {
        sayEvent(view, speaker, speech, text)
    }

    /**
     * Проверить обновление по кнопке.
     *
     * Не то же, что проверка при входе в меню: здесь игрок сам об этом
     * попросил, поэтому и скачиваем сразу, не глядя на сеть, — спрашивать про
     * мобильную сеть у того, кто сам нажал, значит отвечать отказом на
     * просьбу. При входе в меню наоборот: там про сборку игрока не спрашивали.
     */
    fun checkUpdate() {
        if (checking) return
        checking = true
        scope.launch {
            val found = withContext(Dispatchers.IO) { Update.check(BuildConfig.VERSION_CODE) }
            val said = when (found) {
                is Update.Check.Fresh -> {
                    val release = found.release
                    when (val got = withContext(Dispatchers.IO) { Update.download(context, release) }) {
                        is Update.Get.Ready ->
                            "Есть новая версия ${release.title}, она уже скачана. " +
                                "Открой главное меню — там кнопка «установить обновление»."

                        Update.Get.Busy -> "Новая версия ${release.title} уже скачивается."

                        is Update.Get.Failed ->
                            "Новая версия ${release.title} есть, но скачать не вышло: ${got.reason}."
                    }
                }

                Update.Check.Current ->
                    "Установлена последняя версия, сборка ${BuildConfig.VERSION_CODE}."

                is Update.Check.Failed -> "Не вышло проверить обновление: ${found.reason}."
            }
            checking = false
            announce(said)
        }
    }

    /**
     * Записать новую скорость. Скорость ставим живому синтезатору и тут же
     * проговариваем образец — по нему и слышно, что получилось. Образец
     * перебивается на каждом нажатии намеренно: важно последнее значение,
     * а не то, что успело прозвучать до него.
     */
    /**
     * Выбрать голос одному из троих. Выбор сразу и звучит: голос подбирают
     * на слух, а не по названию — «ru-ru-x-ruf-network» человеку не говорит
     * ничего. Проба идёт тем же голосом и тем же синтезатором, какими этот
     * собеседник заговорит за столом: слышать надо ровно то, что будет.
     */
    fun pick(slot: VoiceSlot, name: String?, index: Int, total: Int) {
        save(
            when (slot) {
                VoiceSlot.APP -> settings.copy(voice = name)
                VoiceSlot.DURAK -> settings.copy(botVoiceDurak = name)
                VoiceSlot.DURAK_SECOND -> settings.copy(botVoiceDurakSecond = name)
                VoiceSlot.THOUSAND -> settings.copy(botVoiceThousand = name)
                VoiceSlot.THOUSAND_SECOND -> settings.copy(botVoiceThousandSecond = name)
                VoiceSlot.KOZEL -> settings.copy(botVoiceKozel = name)
            },
        )
        // Голос берём из уже сохранённых настроек, а не считаем заново по
        // имени: чем бот заговорит — правило одно, и записанное здесь второй
        // раз оно однажды разошлось бы с первым.
        auditionVoice(
            engine = slotEngine(settings, slot),
            voice = slotVoice(settings, slot),
            text = sampleFor(index, total),
            rate = slotRate(settings, slot),
        )
    }

    /**
     * Строка скорости речи того, кто за столом говорит.
     *
     * Скорость выбирают на слух, как и голос, поэтому образец звучит его
     * голосом и с его скоростью: «1.4» человеку ни о чём не говорит, а
     * услышать её надо там же, где она будет звучать, — за столом.
     *
     * Кнопкой по кругу, а не ползунком: ступеней мало ([BOT_RATES]), и
     * скорость тут — примета, по которой бота узнают, а не разборчивость,
     * которую подгоняют под себя.
     */
    @Composable
    fun rateRow(slot: VoiceSlot, label: String = slot.rateLabel) {
        val value = slotRate(settings, slot)
        SettingButton("$label: ${botRateTitle(value)}") {
            val next = nextBotRate(value)
            save(withRate(settings, slot, next))
            auditionVoice(
                engine = slotEngine(settings, slot),
                voice = slotVoice(settings, slot),
                text = "$label: ${botRateTitle(next)}. $SAMPLE",
                rate = next,
            )
        }
    }

    /**
     * Строка выбора голоса. Открывает тот же список, что и в общих настройках:
     * голос подбирают на слух, а название голоса в системе —
     * «ru-ru-x-ruf-network» — человеку не говорит ничего.
     */
    @Composable
    fun voiceRow(slot: VoiceSlot, label: String = slot.title) {
        val list = slotVoices(slot)
        SettingButton(
            voiceRowTitle(
                name = slotVoiceName(settings, slot),
                voices = list,
                title = label,
                inherit = inheritLabel(slot, settings),
            ),
        ) {
            if (list.isEmpty()) {
                announce("Синтезатор ещё не готов, попробуй ещё раз.")
            } else {
                picking = slot to list
            }
        }
    }

    /**
     * Строка синтезатора: чьим движком говорит тот, чья это строка.
     *
     * У соперника движок свой не от роскоши: голосов у движка бывает и один,
     * и тогда бот без своего движка говорит ровно тем же голосом, что и
     * приложение, — за столом это один и тот же человек (Катерина, 19.09:
     * «меня всё равно разговаривает всё одним голосом»). Другой движок
     * слышно сразу.
     *
     * «Как у приложения» — не то же, что «системный»: там бот говорит движком
     * приложения и голосом приложения тоже, а тут берёт движок телефона — тот
     * самый, которым говорит скринридер.
     */
    @Composable
    fun engineRow(slot: VoiceSlot, label: String) {
        val own = slotEngineOwn(settings, slot)
        val options: List<Pair<String?, String>> = buildList {
            add(null to if (slot == VoiceSlot.APP) systemTitle else slot.inherit)
            if (slot != VoiceSlot.APP) add("" to systemTitle)
            engines.forEach { add(it.name to it.label) }
        }
        val found = options.indexOfFirst { it.first == own }
        val index = if (found < 0) 0 else found
        SettingButton("$label: ${options[index].second} (${index + 1} из ${options.size})") {
            if (engines.isEmpty()) {
                announce("Список синтезаторов ещё не готов, нажми ещё раз.")
            } else {
                val (value, shown) = options[(index + 1) % options.size]
                save(withEngine(settings, slot, value))
                auditionVoice(
                    engine = slotEngine(settings, slot),
                    voice = slotVoice(settings, slot),
                    text = "Синтезатор: $shown. $SAMPLE",
                    // Скорость — его же: у бота она своя, и слышать её надо
                    // там же, где он ею заговорит.
                    rate = slotRate(settings, slot),
                )
            }
        }
    }

    /**
     * Строка имени соперника — единственная на экране, которую не переключают,
     * а набирают: имя из готовых не выбрать. Стоит рядом с его голосом и его
     * скоростью — там всё про того, кто сидит напротив.
     *
     * Кнопки «Сохранить» нет намеренно: за столом имя не правят, а лишняя
     * кнопка после поля — ещё одна остановка для пальца. Пишем на каждую букву.
     */
    @Composable
    fun nameRow(label: String, value: String, empty: String, onSave: (String) -> Unit) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onSave,
            singleLine = true,
            placeholder = { Text(empty) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text("Пусто — «$empty».", style = MaterialTheme.typography.bodyMedium)
    }

    fun applyRate(value: Float) {
        val next = snapRate(value)
        rateDraft = next
        speaker.setRate(next)
        if (next != settings.rate) save(settings.copy(rate = next))
        sayEvent(view, speaker, speech, SAMPLE, whenReady = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        // --- Какие настройки -----------------------------------------------

        // Общие не прячутся за прокруткой. За столом их так и не находили:
        // список начинается с игры, до общих нужно листать вслепую — а
        // сколько там ещё, не слышно (Катерина, 18.09: «я их когда играю в
        // тысячу не вижу, их нету»). Кнопка называет то, что откроется, и
        // стоит первой, до всякой прокрутки.
        if (game != null) {
            if (part == SettingsPart.GAME) {
                SettingButton("Общие настройки: речь, звук, порядок карт, журнал") {
                    part = SettingsPart.COMMON
                    announce("Общие настройки для всей программы.")
                }
            } else {
                SettingButton("Настройки игры: ${gameTitle(game)}") {
                    part = SettingsPart.GAME
                    announce("Настройки игры ${gameTitle(game)}.")
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // --- Эта игра ------------------------------------------------------

        // Первым — то, ради чего за столом и лезут в настройки: с кем играть
        // и по каким правилам. Строки правил приходят от самой игры, а не
        // написаны здесь: экран один на всё приложение, и третья игра принесёт
        // сюда свой список, а не ещё один экран (SETTINGS.md, 2).
        if (game != null && part == SettingsPart.GAME) {
            Text(gameTitle(game), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            SettingButton("Соперник: ${botDifficulty(game, settings).title}") {
                val next = botDifficulty(game, settings).next()
                save(withBotDifficulty(game, settings, next))
                announce("Соперник: ${next.title}.")
            }

            // Сколько мест за столом — рядом с соперником: это тоже про то,
            // с кем играть. И в «Тысячу», и в «Дурака» играют и вдвоём, и
            // втроём, и выбор тут не про правила, а про стол (THOUSAND.md,
            // 1.2; в «Дураке» — круг подкидывающих, GAMES.md).
            if (game == GAME_THOUSAND || game == GAME_DURAK) {
                Spacer(Modifier.height(8.dp))
                val seats = seatsAtTable(settings, game)
                SettingButton("За столом: ${seatsTitle(seats)}") {
                    val next = if (seats >= 3) 2 else 3
                    save(withSeats(settings, game, next))
                    announce(seatsPhrase(next, game))
                }
            }

            // Порядок костей — рядом с соперником и до договорённостей: это
            // то, чего игрок хочет от своей руки, а не то, о чём сговариваются
            // за столом. Стоит здесь, а не в общих: у карт свой порядок, и
            // общий список от этого только путался бы (Катерина, 18.09).
            if (game == GAME_KOZEL) {
                Spacer(Modifier.height(8.dp))
                SettingButton("Порядок костей: ${settings.tileOrder.title}") {
                    val next = settings.tileOrder.next()
                    save(settings.copy(tileOrder = next))
                    announce("Порядок костей: ${next.title}.")
                }
            }

            // --- Соперник этой игры ---------------------------------------
            // Имя, голос и скорость — в настройках той игры, за столом
            // которой соперник сидит (Катерина, 19.09: «чтобы боты были в
            // настройках с игрой, и чтобы там всё настраивалось»). В общих
            // они лежали одной кучей на все три игры — и в дураке рядом с
            // голосом козла стояло имя тысячного соперника.
            Spacer(Modifier.height(8.dp))

            nameRow(
                "Имя соперника",
                botName(settings, game),
                "Бот",
            ) { name -> save(withBotName(settings, game, name)) }

            Spacer(Modifier.height(8.dp))

            val slot = gameSlot(game)
            engineRow(slot, "Синтезатор соперника")
            voiceRow(slot, slot.titleInGame())
            rateRow(slot, slot.rateLabelInGame())

            // Второй соперник садится за стол, только когда за ним трое: на
            // двоих его строки — строки ни о ком.
            if (seatsAtTable(settings, game) >= 3) {
                Spacer(Modifier.height(8.dp))
                nameRow(
                    "Имя второго соперника",
                    botNameSecond(settings, game),
                    "Второй бот",
                ) { name -> save(withBotNameSecond(settings, game, name)) }
                Spacer(Modifier.height(8.dp))
                val second = secondSlot(game)
                engineRow(second, "Синтезатор второго соперника")
                voiceRow(second, second.titleInGame())
                rateRow(second, second.rateLabelInGame())
            }

            Spacer(Modifier.height(8.dp))

            Text(
                // В «Дураке» за столом сговариваются только о переводе, и
                // «правила стола» там — точное слово. В «Тысяче» и в «Козле»
                // договариваются о том, что считают по-разному, — там это
                // договорённости сторон.
                if (game == GAME_DURAK) "Правила стола" else "Договорённости сторон",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))

            val gameSettings = remember { GameSettingStore(context) }
            val rules = when (game) {
                GAME_KOZEL -> KOZEL_SETTINGS
                GAME_THOUSAND -> THOUSAND_SETTINGS
                else -> DURAK_SETTINGS
            }
            rules.forEach { setting ->
                GameSettingRow(setting, gameSettings) { announce(it) }
            }

            // Помощник стоит отдельно от договорённостей: там то, о чём
            // сговариваются до стола, а тут — чего игрок хочет от приложения.
            // Свалить их в один список значило бы сказать, что это одно и то же.
            if (game == GAME_THOUSAND) {
                Spacer(Modifier.height(8.dp))
                Text("Помощь за столом", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                GameSettingRow(AUTO_PRAISE, gameSettings) { announce(it) }
            }

            Spacer(Modifier.height(16.dp))
        }

        // --- Общие для всей программы --------------------------------------
        // Своя половина настроек: из-за стола сюда переходят кнопкой наверху,
        // а из главного меню эти настройки — единственные, и половина одна.
        if (game == null || part == SettingsPart.COMMON) {
            // --- Речь ---------------------------------------------------------

            Text("Речь", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            // Скорость: ползунок и две кнопки. Ползунок хорош на глаз, но
            // незрячему он неудобен — пальцем в него не попасть, а «сорок
            // процентов» ему ничего не говорят. Поэтому точную подстройку
            // ведут кнопками, а ползунок показывает, где мы находимся.
            // Число рядом со словом — для зрячих помощников: незрячему оно
            // ничего не добавляет, а зрячему сразу видно, куда сдвинулся ползунок.
            Text(
                "Скорость речи: ${rateTitle(rateDraft)} (${"%.1f".format(rateDraft)})",
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = rateDraft,
                onValueChange = { rateDraft = it },
                onValueChangeFinished = { applyRate(rateDraft) },
                valueRange = RATE_MIN..RATE_MAX,
                steps = RATE_STEPS,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Скорость речи" },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { applyRate(rateDraft - RATE_STEP) },
                    enabled = rateDraft > RATE_MIN + 1e-4f,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Медленнее")
                }
                OutlinedButton(
                    onClick = { applyRate(rateDraft + RATE_STEP) },
                    enabled = rateDraft < RATE_MAX - 1e-4f,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Быстрее")
                }
            }

            Spacer(Modifier.height(8.dp))

            engineRow(VoiceSlot.APP, "Синтезатор")

            // Здесь остаётся голос самого приложения. Голоса соперников ушли
            // в настройки их игр: за столом говорят приложение и три бота, и
            // на слух их надо различать — но настраивают бота там, где за его
            // столом сидят, а не в общем списке, где рядом с голосом козла
            // стояло имя тысячного соперника (Катерина, 19.09).
            voiceRow(VoiceSlot.APP)

            SettingButton("Кто говорит: ${settings.voiceMode.title}") {
                val next = settings.voiceMode.next()
                save(settings.copy(voiceMode = next))
                announce(whoSpeaksPhrase(next))
            }

            // Имя соперника отсюда ушло: оно у каждой игры своё и стоит в её
            // настройках, рядом с голосом и скоростью того же соперника. Само
            // правило про имя не изменилось — не склонять произвольное имя и
            // возвращать его из первого лица только скринридеру (SETTINGS.md, 8).
            Spacer(Modifier.height(8.dp))

            SettingSwitch("Реплики соперника", settings.botTalk) { value ->
                save(settings.copy(botTalk = value))
                announce(if (value) "Реплики соперника включены." else "Реплики соперника выключены.")
            }

            Spacer(Modifier.height(16.dp))

            // --- Звук и вибрация ----------------------------------------------

            Text("Звук и вибрация", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            SettingSwitch("Звуки стола", settings.sounds) { value ->
                save(settings.copy(sounds = value))
                announce(if (value) "Звуки стола включены." else "Звуки стола выключены.")
            }

            SettingSwitch("Сигналы", settings.signals) { value ->
                save(settings.copy(signals = value))
                announce(if (value) "Сигналы включены." else "Сигналы выключены.")
            }

            SettingSwitch("Вибрация на ход соперника", settings.vibration) { value ->
                save(settings.copy(vibration = value))
                announce(if (value) "Вибрация на ход соперника включена." else "Вибрация на ход соперника выключена.")
            }

            SettingSwitch("Вибрация на мои ходы", settings.ownVibration) { value ->
                save(settings.copy(ownVibration = value))
                announce(if (value) "Вибрация на мои ходы включена." else "Вибрация на мои ходы выключена.")
            }

            Spacer(Modifier.height(16.dp))

            // --- Общее ---------------------------------------------------------

            // Общее — про приложение, а не про игру: за любым столом оно одно и
            // то же, поэтому стоит ниже игрового и ни у одной игры не повторяется.
            // Соперник и правила живут выше, в блоке своей игры, а из главного
            // меню их не видно совсем: там про приложение (SETTINGS.md, 2).

            Text("Общее", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            SettingButton("Порядок карт: ${settings.order.title}") {
                val next = settings.order.next()
                save(settings.copy(order = next))
                announce("Порядок карт: ${next.title}.")
            }

            SettingSwitch("Крупные карты и шрифт", settings.largeText) { value ->
                save(settings.copy(largeText = value))
                announce(if (value) "Крупный размер включён." else "Крупный размер выключен.")
            }

            SettingSwitch("Автосохранение партии", settings.autosave) { value ->
                save(settings.copy(autosave = value))
                announce(
                    if (value) {
                        "Автосохранение включено. Незаконченную партию можно будет продолжить."
                    } else {
                        "Автосохранение выключено. Выйдешь посреди партии — начнёшь заново."
                    },
                )
            }

            Spacer(Modifier.height(16.dp))

            // --- Обновление -----------------------------------------------------

            // Обновление — про приложение, а не про игру: оно одно за любым
            // столом, поэтому живёт в общем блоке. Сборка стоит на телефоне у
            // игрока, а выходят они на стороне: сам он о новой иначе не узнает.
            Text("Обновление", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Установлена версия ${BuildConfig.VERSION_NAME}, сборка ${BuildConfig.VERSION_CODE}. " +
                    "Новая сборка приходит сама: при входе приложение спрашивает, не вышла ли она.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))

            SettingSwitch("Проверять обновление при входе", settings.autoUpdate) { value ->
                save(settings.copy(autoUpdate = value))
                announce(
                    if (value) {
                        "Проверка обновления включена. При входе приложение скажет, если вышла новая сборка."
                    } else {
                        "Проверка обновления выключена. Новые сборки приложение искать не будет."
                    },
                )
            }

            SettingButton(
                if (checking) "Проверяю обновление…" else "Проверить обновление",
            ) {
                if (!checking) {
                    announce("Проверяю обновление.")
                    checkUpdate()
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Счёт ----------------------------------------------------------

            SettingButton(if (resetAsked) "Нажми ещё раз — счёт обнулится" else "Сбросить счёт партий") {
                if (resetAsked) {
                    saveScore(context, Score())
                    resetAsked = false
                    announce("Счёт обнулён.")
                } else {
                    resetAsked = true
                    announce("Нажми ещё раз, и счёт обнулится. Побед было ${loadScore(context).wins}.")
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Журнал ---------------------------------------------------------

            // Журнал — про то, что случилось и требует рассказа: «кнопку не
            // читает», «говорит не тем голосом». Он пишется сам, поэтому стоит в
            // самом низу: пока всё звучит как надо, сюда не заглядывают.
            Text("Журнал", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Что приложение делало, кто что сказал и какие кнопки были на экране. " +
                    "Если что-то звучит не так — отправь журнал: по нему видно, " +
                    "до чего дошло дело.",
                style = MaterialTheme.typography.bodyMedium,
            )

            SettingButton("Отправить журнал: $journalSize") {
                when (val share = Journal.shareIntent(context)) {
                    is JournalShare.Ready -> {
                        val shown = runCatching { context.startActivity(share.intent) }
                        if (shown.isSuccess) {
                            announce("Выбирай, куда отправить журнал.")
                        } else {
                            // Системный выбор не открылся — это тоже поломка, и
                            // назвать её надо вслух, а не молчанием.
                            val why = shown.exceptionOrNull()?.message ?: "причина неизвестна"
                            announce("Выбор «Поделиться» не открылся: $why.")
                        }
                    }

                    JournalShare.Empty -> announce("Журнал пуст, отправлять нечего.")

                    is JournalShare.Failed ->
                        announce("Журнал не удалось отдать наружу: ${share.reason}.")
                }
            }

            SettingButton(if (clearAsked) "Нажми ещё раз — журнал очистится" else "Очистить журнал") {
                if (clearAsked) {
                    Journal.clear()
                    journalSize = "пусто"
                    clearAsked = false
                    announce("Журнал очищен.")
                } else {
                    clearAsked = true
                    announce("Нажми ещё раз, и журнал очистится. Сейчас в нём $journalSize.")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Назад") }
    }

    // Список голосов. Открывается поверх настроек и не закрывается после
    // выбора: голоса сравнивают на слух, перебирая несколько подряд, — а
    // закрыть его можно кнопкой «Готово», тапом мимо и системной «Назад».
    val open = picking
    if (open != null) {
        val (openSlot, openVoices) = open
        VoicePickerDialog(
            slot = openSlot,
            voices = openVoices,
            inherit = inheritLabel(openSlot, settings),
            current = slotVoiceName(settings, openSlot),
            onPick = { name, index -> pick(openSlot, name, index, openVoices.size) },
            onClose = { picking = null },
        )
    }
}

/**
 * Список голосов синтезатора. Списком, а не перебором по кругу: голосов в
 * телефоне бывает десяток, и найти среди них нужный перебором нельзя —
 * приходится раз за разом слушать все подряд.
 *
 * Названия голосов оставлены системными: «ru-ru-x-ruf-network» — это всё,
 * чем голос себя называет, и придумывать ему человеческое имя значит
 * показывать имя, которого в телефоне нет. Выбирают всё равно на слух, а
 * номер перед названием говорит, где мы в списке.
 */
@Composable
private fun VoicePickerDialog(
    slot: VoiceSlot,
    voices: List<Voice>,
    inherit: String,
    current: String?,
    onPick: (name: String?, index: Int) -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(slot.title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                VoiceOption(
                    label = inherit,
                    selected = current == null,
                    onClick = { onPick(null, -1) },
                )
                voices.forEachIndexed { index, voice ->
                    VoiceOption(
                        label = "${index + 1}. ${voice.name}",
                        selected = voice.name == current,
                        onClick = { onPick(voice.name, index) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Готово") }
        },
    )
}

@Composable
private fun VoiceOption(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            // Выбранный помечен словом, а не птичкой: птичку скринридер не
            // читает, а слово читает.
            text = if (selected) "$label — выбран" else label,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Название игры в шапке её блока: то же, что на экране самой игры. */
/** Какая половина настроек открыта. Из главного меню половина всегда одна — [COMMON]. */
private enum class SettingsPart { GAME, COMMON }

private fun gameTitle(game: String): String = when (game) {
    GAME_KOZEL -> "Козёл"
    GAME_THOUSAND -> "Тысяча"
    else -> "Дурак"
}

/** Соперник той игры, чьи настройки открыты: у каждой игры он свой. */
private fun botDifficulty(game: String, settings: Settings): Difficulty = when (game) {
    GAME_KOZEL -> settings.botDifficultyKozel
    GAME_THOUSAND -> settings.botDifficultyThousand
    else -> settings.botDifficultyDurak
}

/** Записать силу соперника той игре, чьи настройки открыты. */
private fun withBotDifficulty(game: String, settings: Settings, value: Difficulty): Settings =
    when (game) {
        GAME_KOZEL -> settings.copy(botDifficultyKozel = value)
        GAME_THOUSAND -> settings.copy(botDifficultyThousand = value)
        else -> settings.copy(botDifficultyDurak = value)
    }

/** Объяснить выбранный режим словами: «авто» само по себе ничего не говорит. */
private fun whoSpeaksPhrase(mode: VoiceMode): String = when (mode) {
    VoiceMode.AUTO -> "Авто: работает скринридер — говорит он, выключен — говорит приложение."
    VoiceMode.ALWAYS -> "Говорит приложение, скринридеру велено умолкать."
    VoiceMode.NEVER -> "Говорит скринридер, приложение отдаёт свои фразы ему."
    VoiceMode.SILENT ->
        "Игра молчит: ни приложение, ни скринридер не говорят. Звуки стола и вибрация остаются — их выключают отдельно."
}

@Composable
private fun SettingButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(label)
    }
}

/**
 * Строка игровой настройки. Пояснение стоит под названием, а не прячется в
 * подсказку: скринридер читает его вместе с названием и значением, и игрок
 * слышит не «Самосвал, переключатель», а что это вообще такое.
 */
@Composable
private fun GameSettingRow(
    setting: GameSetting,
    store: GameSettingStore,
    onAnnounce: (String) -> Unit,
) {
    var value by remember(setting.key) { mutableStateOf(store.value(setting)) }
    Row(
        // Переключатель и подпись — один элемент для скринридера, а не два.
        // Без этого TalkBack читает название с пояснением, а следом отдельно
        // «переключатель, выключено» без имени: два разных объекта там, где
        // для игрока за столом один. toggleable на самой строке заодно делает
        // переключателем всю строку целиком, а не маленький ромб справа, —
        // незрячему попасть по строке во всю ширину заметно проще.
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = value,
                role = Role.Switch,
                onValueChange = { next ->
                    value = next
                    store.set(setting, next)
                    onAnnounce(gameSettingPhrase(setting, next))
                },
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(setting.title, style = MaterialTheme.typography.bodyLarge)
            Text(setting.about, style = MaterialTheme.typography.bodyMedium)
        }
        // onCheckedChange = null: переключает строка, а сам ромб — только
        // картинка. Иначе он забирает нажатие себе и снова становится
        // вторым элементом в дереве доступности.
        Switch(checked = value, onCheckedChange = null)
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        // То же, что в строке игровой настройки: подпись и переключатель —
        // один элемент, и переключает вся строка. Здесь это заметнее: у
        // общей настройки подписи в две-три строки хватает, а рядом с ней
        // ромб в четыре миллиметра.
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
