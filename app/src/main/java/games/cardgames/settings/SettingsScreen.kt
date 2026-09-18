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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import games.cardgames.score.loadScore
import games.cardgames.score.saveScore
import games.cardgames.score.Score
import games.cardgames.speech.Speaker
import games.cardgames.speech.appSpeaks
import games.cardgames.speech.sayEvent

/** Образец речи: по нему игрок и выбирает голос — на слух, а не по названию. */
private const val SAMPLE = "Так будет звучать игра. Козырь — пики, у тебя семёрка червей."

/** Чей голос выбирают. Своя строка у каждого, кто за столом говорит. */
private enum class VoiceSlot(val title: String, val inherit: String) {
    APP("Голос приложения", "системный"),
    DURAK("Голос бота в дураке", "как у приложения"),
    THOUSAND("Голос бота в тысяче", "как у приложения"),
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
 * Голоса выбираются на слух: название голоса в системе —
 * «ru-ru-x-ruf-network», человеку оно ничего не говорит. Поэтому выбор
 * открывает список целиком, а каждый выбор сразу звучит образцом.
 *
 * Перебором по кругу это было раньше — и не годилось: у игрока голосов
 * много, а найти среди них нужный перебором нельзя.
 */
@Composable
fun SettingsScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(loadSettings(context)) }
    var readyTick by remember { mutableIntStateOf(0) }
    var resetAsked by remember { mutableStateOf(false) }

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
    fun auditionVoice(voice: String?, pitch: Float, text: String) {
        if (!appVoice) {
            sayEvent(view, speaker, appVoice, text)
            return
        }
        var probe = audition
        // Синтезатор сменили — прежняя проба говорила чужим движком.
        if (probe == null || auditionEngine != settings.engine) {
            probe = Speaker(context, rate = settings.rate, enginePackage = settings.engine)
            audition = probe
            auditionEngine = settings.engine
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
            },
        )
        when (slot) {
            VoiceSlot.APP -> auditionVoice(name, 1f, sampleFor(index, voices.size, null))
            VoiceSlot.DURAK -> auditionVoice(
                botVoice(name, settings.voice),
                botPitch(name, BOT_PITCH_DURAK),
                sampleFor(index, voices.size, if (name == null) "ниже" else null),
            )
            VoiceSlot.THOUSAND -> auditionVoice(
                botVoice(name, settings.voice),
                botPitch(name, BOT_PITCH_THOUSAND),
                sampleFor(index, voices.size, if (name == null) "выше" else null),
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

        // Голос у каждого из троих свой: за столом говорят приложение и два
        // бота, и на слух их надо различать — «бот сказал» и «приложение
        // сказало» это разные вещи, спутать их значит не понять, чей ход.
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

        SettingButton(voiceRowTitle(VoiceSlot.THOUSAND, settings.botVoiceThousand, voices)) {
            if (voices.isEmpty()) {
                announce("Синтезатор ещё не готов, попробуй ещё раз.")
            } else {
                picking = VoiceSlot.THOUSAND
            }
        }

        SettingButton("Кто говорит: ${settings.voiceMode.title}") {
            val next = settings.voiceMode.next()
            save(settings.copy(voiceMode = next))
            announce(whoSpeaksPhrase(next))
        }

        SettingSwitch("Реплики бота", settings.botTalk) { value ->
            save(settings.copy(botTalk = value))
            announce(if (value) "Реплики бота включены." else "Реплики бота выключены.")
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

        // --- Игра ----------------------------------------------------------

        Text("Игра", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        SettingButton("Соперник: ${settings.difficulty.title}") {
            val next = settings.difficulty.next()
            save(settings.copy(difficulty = next))
            announce("Соперник: ${next.title}.")
        }

        SettingButton("Порядок карт: ${settings.order.title}") {
            val next = settings.order.next()
            save(settings.copy(order = next))
            announce("Порядок карт: ${next.title}.")
        }

        SettingSwitch("Перевод карты — переводной дурак", settings.transfer) { value ->
            save(settings.copy(transfer = value))
            announce(
                if (value) {
                    "Перевод включён: защищающийся может перевести карту соседу. " +
                        "Действует со следующей раздачи."
                } else {
                    "Перевод выключен: подкидной дурак, защищающийся только отбивается или берёт. " +
                        "Действует со следующей раздачи."
                },
            )
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

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
