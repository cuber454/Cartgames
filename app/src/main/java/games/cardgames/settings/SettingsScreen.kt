package games.cardgames.settings

import android.speech.tts.TextToSpeech
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
import games.cardgames.speech.appSpeaks
import games.cardgames.speech.sayEvent
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
 * [inherit] называет не только чей это голос, но и как он звучит, пока
 * своего не выбрали: высота у ботов сдвинута, и «как у приложения» без
 * «выше» или «ниже» обещает ровный голос, которого за столом не будет.
 */
private enum class VoiceSlot(val title: String, val rateLabel: String, val inherit: String) {
    APP("Голос приложения", "Скорость речи приложения", "системный"),
    DURAK("Голос соперника в дураке", "Скорость речи соперника в дураке", "как у приложения, ниже"),
    THOUSAND("Голос соперника в тысяче", "Скорость речи соперника в тысяче", "как у приложения, выше"),
    THOUSAND_SECOND(
        "Голос второго соперника в тысяче",
        "Скорость речи второго соперника в тысяче",
        "как у приложения, ниже",
    ),
    KOZEL("Голос соперника в козле", "Скорость речи соперника в козле", "как у приложения, ещё выше"),
}

/**
 * Голос и высота того, чью строку настроек правят: у приложения — его
 * собственные, у соперника — его голос и его сдвиг высоты.
 *
 * Пара, а не два вызова, потому что берут их всегда вместе: голос без высоты
 * прозвучит не тем, чем говорит за столом.
 */
private fun slotVoice(settings: Settings, slot: VoiceSlot): Pair<String?, Float> = when (slot) {
    VoiceSlot.APP -> settings.voice to 1f
    VoiceSlot.DURAK -> botVoice(settings.botVoiceDurak, settings.voice) to
        botPitch(settings.botVoiceDurak, BOT_PITCH_DURAK)

    VoiceSlot.THOUSAND -> botVoice(settings.botVoiceThousand, settings.voice) to
        botPitch(settings.botVoiceThousand, BOT_PITCH_THOUSAND)

    VoiceSlot.THOUSAND_SECOND -> botVoice(settings.botVoiceThousandSecond, settings.voice) to
        botPitch(settings.botVoiceThousandSecond, BOT_PITCH_THOUSAND_SECOND)

    VoiceSlot.KOZEL -> botVoice(settings.botVoiceKozel, settings.voice) to
        botPitch(settings.botVoiceKozel, BOT_PITCH_KOZEL)
}

/** Скорость речи того, чью строку настроек правят. */
private fun slotRate(settings: Settings, slot: VoiceSlot): Float = when (slot) {
    VoiceSlot.APP -> settings.rate
    VoiceSlot.DURAK -> settings.botRateDurak
    VoiceSlot.THOUSAND -> settings.botRateThousand
    VoiceSlot.THOUSAND_SECOND -> settings.botRateThousandSecond
    VoiceSlot.KOZEL -> settings.botRateKozel
}

/** Те же настройки, но со сменённой скоростью у того, чья это строка. */
private fun withRate(settings: Settings, slot: VoiceSlot, rate: Float): Settings = when (slot) {
    VoiceSlot.APP -> settings.copy(rate = rate)
    VoiceSlot.DURAK -> settings.copy(botRateDurak = rate)
    VoiceSlot.THOUSAND -> settings.copy(botRateThousand = rate)
    VoiceSlot.THOUSAND_SECOND -> settings.copy(botRateThousandSecond = rate)
    VoiceSlot.KOZEL -> settings.copy(botRateKozel = rate)
}

/** Строка выбора голоса: чей это голос и какой сейчас стоит. */
private fun voiceRowTitle(slot: VoiceSlot, name: String?, voices: List<Voice>): String {
    val index = voices.indexOfFirst { it.name == name }
    return "${slot.title}: " + if (index < 0) slot.inherit else "${index + 1} из ${voices.size}"
}

/**
 * Образец для пробы. Перед ним — где мы в списке: голосов в телефоне бывает
 * десяток, и без этого не понять, далеко ли ещё листать.
 *
 * [hint] — про высоту: у бота без своего голоса она сдвинута, и слышать надо
 * то же, что будет за столом, а не ровный голос приложения.
 */
private fun sampleFor(index: Int, total: Int, hint: String?): String = buildString {
    append(if (index < 0) "Голос по умолчанию" else "Голос ${index + 1} из $total")
    if (hint != null) append(", $hint")
    append(". $SAMPLE")
}

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
    // настроек озвучивают оба голоса сразу.
    val appVoice = settings.voiceMode.appSpeaks(speaker.screenReaderOn)
    val view = LocalView.current

    // Список голосов открыт для одного из троих. null — закрыт.
    var picking by remember { mutableStateOf<VoiceSlot?>(null) }

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
    fun auditionVoice(voice: String?, pitch: Float, text: String, rate: Float = settings.rate) {
        if (!appVoice) {
            sayEvent(view, speaker, appVoice, text)
            return
        }
        var probe = audition
        // Синтезатор сменили — прежняя проба говорила чужим движком.
        if (probe == null || auditionEngine != settings.engine) {
            probe = Speaker(context, rate = rate, enginePackage = settings.engine)
            audition = probe
            auditionEngine = settings.engine
        } else {
            // Скорость ставим на живом движке: у пробы своя скорость на
            // каждый образец, и прежняя к новому не относится.
            probe.setRate(rate)
        }
        probe.previewVoice(voice, text, pitch)
    }

    // readyTick в ключе: пока синтезатор не поднялся, списки пустые,
    // и пересобрать их надо ровно тогда, когда он ответил.
    val engines = remember(readyTick, settings.engine) { speaker.engines() }
    val voices = remember(readyTick, settings.engine) { speaker.voices() }

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
        sayEvent(view, speaker, appVoice, text)
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
     * ничего. Проба идёт тем же голосом и той же высотой, какими этот
     * собеседник заговорит за столом: у приложения высота своя, у бота без
     * своего голоса — сдвинутая, и слышать надо ровно то, что будет.
     */
    fun pick(slot: VoiceSlot, name: String?, index: Int) {
        save(
            when (slot) {
                VoiceSlot.APP -> settings.copy(voice = name)
                VoiceSlot.DURAK -> settings.copy(botVoiceDurak = name)
                VoiceSlot.THOUSAND -> settings.copy(botVoiceThousand = name)
                VoiceSlot.THOUSAND_SECOND -> settings.copy(botVoiceThousandSecond = name)
                VoiceSlot.KOZEL -> settings.copy(botVoiceKozel = name)
            },
        )
        when (slot) {
            VoiceSlot.APP -> auditionVoice(name, 1f, sampleFor(index, voices.size, null))
            VoiceSlot.DURAK -> auditionVoice(
                botVoice(name, settings.voice),
                botPitch(name, BOT_PITCH_DURAK),
                sampleFor(index, voices.size, if (name == null) "ниже" else null),
            )
            VoiceSlot.THOUSAND_SECOND -> auditionVoice(
                name,
                botPitch(name, BOT_PITCH_THOUSAND_SECOND),
                sampleFor(index, voices.size, "ниже первого соперника"),
            )

            VoiceSlot.THOUSAND -> auditionVoice(
                botVoice(name, settings.voice),
                botPitch(name, BOT_PITCH_THOUSAND),
                sampleFor(index, voices.size, if (name == null) "выше" else null),
            )
            VoiceSlot.KOZEL -> auditionVoice(
                botVoice(name, settings.voice),
                botPitch(name, BOT_PITCH_KOZEL),
                sampleFor(index, voices.size, if (name == null) "ещё выше" else null),
            )
        }
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
    fun rateRow(slot: VoiceSlot) {
        val value = slotRate(settings, slot)
        SettingButton("${slot.rateLabel}: ${botRateTitle(value)}") {
            val next = nextBotRate(value)
            save(withRate(settings, slot, next))
            val (voice, pitch) = slotVoice(settings, slot)
            auditionVoice(
                voice,
                pitch,
                "${slot.rateLabel}: ${botRateTitle(next)}. $SAMPLE",
                rate = next,
            )
        }
    }

    fun applyRate(value: Float) {
        val next = snapRate(value)
        rateDraft = next
        speaker.setRate(next)
        if (next != settings.rate) save(settings.copy(rate = next))
        sayEvent(view, speaker, appVoice, SAMPLE, whenReady = true)
    }

    val activeEngine: TextToSpeech.EngineInfo? = engines.firstOrNull { it.name == settings.engine }
    val systemEngine = remember(engines) {
        speaker.defaultEngine()?.let { name -> engines.firstOrNull { it.name == name }?.label ?: name }
    }
    // Имя движка показываем и для «системного»: иначе системный и названный
    // по имени — это один и тот же движок, а выглядят как два разных.
    val engineLabel = when {
        settings.engine != null -> activeEngine?.label ?: settings.engine
        systemEngine != null -> "системный — $systemEngine"
        else -> "системный"
    }
    // «2 из 3» прямо отвечает на вопрос, сколько их всего: раньше казалось,
    // что движок в телефоне один.
    val engineTotal = engines.size + 1
    val enginePosition = if (activeEngine == null) 1 else engines.indexOf(activeEngine) + 2

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
            // с кем играть. В «Тысячу» играют и вдвоём, и втроём, и выбор
            // тут не про правила, а про стол (THOUSAND.md, 1.2).
            if (game == GAME_THOUSAND) {
                Spacer(Modifier.height(8.dp))
                val seats = settings.thousandSeats.coerceIn(2, 3)
                SettingButton("За столом: ${seatsTitle(seats)}") {
                    val next = if (seats >= 3) 2 else 3
                    save(settings.copy(thousandSeats = next))
                    announce(seatsPhrase(next))
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

            SettingButton("Синтезатор: $engineLabel ($enginePosition из $engineTotal)") {
                if (engines.isEmpty()) {
                    announce("Список синтезаторов ещё не готов, нажми ещё раз.")
                } else {
                    // Первый в списке — системный, дальше установленные в телефоне.
                    val currentIndex = if (activeEngine == null) 0 else engines.indexOf(activeEngine) + 1
                    val nextIndex = (currentIndex + 1) % (engines.size + 1)
                    val nextName = if (nextIndex == 0) null else engines[nextIndex - 1].name
                    val nextLabel = if (nextIndex == 0) "системный" else engines[nextIndex - 1].label
                    // Голос у нового синтезатора свой, прежний ему не принадлежит:
                    // поэтому выбор голоса начинается заново, с его умолчания.
                    save(settings.copy(engine = nextName, voice = null))
                    auditionVoice(null, 1f, "Синтезатор: $nextLabel. $SAMPLE")
                }
            }

            // Голос у каждого из четверых свой: за столом говорят приложение и
            // три бота, и на слух их надо различать — «бот сказал» и
            // «приложение сказало» это разные вещи, спутать их значит не
            // понять, чей ход. Строка скорости стоит сразу под голосом: это
            // вторая примета того же бота, и искать её в другом месте списка
            // значит не найти (Катерина, 19.09).
            SettingButton(voiceRowTitle(VoiceSlot.APP, settings.voice, voices)) {
                if (voices.isEmpty()) {
                    announce("Синтезатор ещё не готов, попробуй ещё раз.")
                } else {
                    picking = VoiceSlot.APP
                }
            }

            SettingButton(voiceRowTitle(VoiceSlot.DURAK, settings.botVoiceDurak, voices)) {
                if (voices.isEmpty()) {
                    announce("Синтезатор ещё не готов, попробуй ещё раз.")
                } else {
                    picking = VoiceSlot.DURAK
                }
            }
            rateRow(VoiceSlot.DURAK)

            SettingButton(voiceRowTitle(VoiceSlot.THOUSAND, settings.botVoiceThousand, voices)) {
                if (voices.isEmpty()) {
                    announce("Синтезатор ещё не готов, попробуй ещё раз.")
                } else {
                    picking = VoiceSlot.THOUSAND
                }
            }
            rateRow(VoiceSlot.THOUSAND)

            // Голос второго соперника показываем только за столом на троих:
            // на двоих второго бота нет, и строка была бы строкой ни о чём.
            if (settings.thousandSeats >= 3) {
                SettingButton(
                    voiceRowTitle(VoiceSlot.THOUSAND_SECOND, settings.botVoiceThousandSecond, voices),
                ) {
                    if (voices.isEmpty()) {
                        announce("Синтезатор ещё не готов, попробуй ещё раз.")
                    } else {
                        picking = VoiceSlot.THOUSAND_SECOND
                    }
                }
                rateRow(VoiceSlot.THOUSAND_SECOND)
            }

            SettingButton(voiceRowTitle(VoiceSlot.KOZEL, settings.botVoiceKozel, voices)) {
                if (voices.isEmpty()) {
                    announce("Синтезатор ещё не готов, попробуй ещё раз.")
                } else {
                    picking = VoiceSlot.KOZEL
                }
            }
            rateRow(VoiceSlot.KOZEL)

            SettingButton("Кто говорит: ${settings.voiceMode.title}") {
                val next = settings.voiceMode.next()
                save(settings.copy(voiceMode = next))
                announce(whoSpeaksPhrase(next))
            }

            // Имя соперника — единственная строка настроек, которую не
            // переключают, а набирают: имя из готовых не выбрать. Стоит
            // рядом с его голосом и репликами — здесь всё про того, кто
            // сидит напротив.
            //
            // Кнопки «Сохранить» нет намеренно: за столом имя не правят, а
            // лишняя кнопка после поля — ещё одна остановка для пальца на
            // пути к «Репликам бота». Пишем на каждую букву.
            //
            // Склонять имя программа не станет: «у Меркурия» из «Меркурий»
            // не вывести. Поэтому имя звучит там, где соперник действует
            // («Меркурий берёт прикуп», «Взятку берёт Меркурий»), а падежные
            // фразы говорят «соперник» (SETTINGS.md, 8).
            //
            // Бот себя по имени не называет: свою речь он ведёт от первого
            // лица («Называю 120» вместо «Меркурий называет 120») — имя
            // возвращается только там, где фразу читает скринридер. Строка от
            // этого не лишняя: счёт, взятки и исход кона зовут его по имени
            // всё равно (Катерина, 19.09).
            Text("Имя соперника", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = settings.botName,
                onValueChange = { name -> save(settings.copy(botName = name)) },
                singleLine = true,
                placeholder = { Text("Бот") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text("Пусто — «Бот».", style = MaterialTheme.typography.bodyMedium)

            // Имя второго соперника — за столом на троих: там ботов двое, и
            // без второго имени обоих звали бы «Бот», а различить их на слух
            // тогда нечем (Катерина, 19.09).
            if (settings.thousandSeats >= 3) {
                Spacer(Modifier.height(8.dp))
                Text("Имя второго соперника", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = settings.botNameSecond,
                    onValueChange = { name -> save(settings.copy(botNameSecond = name)) },
                    singleLine = true,
                    placeholder = { Text("Второй бот") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text("Пусто — «Второй бот».", style = MaterialTheme.typography.bodyMedium)
            }

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
    val openSlot = picking
    if (openSlot != null) {
        VoicePickerDialog(
            slot = openSlot,
            voices = voices,
            current = when (openSlot) {
                VoiceSlot.APP -> settings.voice
                VoiceSlot.DURAK -> settings.botVoiceDurak
                VoiceSlot.THOUSAND -> settings.botVoiceThousand
                VoiceSlot.THOUSAND_SECOND -> settings.botVoiceThousandSecond
                VoiceSlot.KOZEL -> settings.botVoiceKozel
            },
            onPick = { name, index -> pick(openSlot, name, index) },
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
                    label = slot.inherit,
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
