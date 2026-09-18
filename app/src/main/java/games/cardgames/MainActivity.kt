package games.cardgames

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import games.cardgames.durak.DurakScreen
import games.cardgames.durak.DurakSession
import games.cardgames.durak.durakTransferAllowed
import games.cardgames.rules.RulesScreen
import games.cardgames.rules.durakRules
import games.cardgames.rules.thousandRules
import games.cardgames.thousand.ThousandScreen
import games.cardgames.thousand.ThousandSession
import games.cardgames.score.loadScore
import games.cardgames.settings.SettingsScreen
import games.cardgames.settings.loadSettings
import games.cardgames.settings.saveSettings
import games.cardgames.speech.Speaker
import games.cardgames.speech.appSpeaks
import games.cardgames.speech.sayEvent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf("menu") }

    // Куда вернуться из настроек: их можно открыть и из меню, и прямо
    // из партии — во втором случае возвращаемся за тот же стол.
    var settingsBack by remember { mutableStateOf("menu") }

    // Справка по правилам — из того же места, откуда и настройки: посреди
    // партии в неё тоже заглядывают, и вернуться надо за тот же стол.
    var rulesBack by remember { mutableStateOf("menu") }
    // Чьи правила открыты: у каждой игры своя справка.
    var rulesGame by remember { mutableStateOf(GAME_DURAK) }

    // Партии живут здесь, выше экранов: поход в настройки посреди партии
    // не должен её обнулять. И у каждой игры — своя: недоигранный дурак
    // и недоигранная тысяча друг о друге не знают.
    val session = remember { DurakSession(context) }
    val thousand = remember { ThousandSession(context) }

    fun openSettings(from: String) {
        settingsBack = from
        screen = "settings"
    }

    fun openRules(from: String, game: String) {
        rulesBack = from
        rulesGame = game
        screen = "rules"
    }

    // Системная «Назад» уводит на шаг назад по экранам, а не закрывает
    // приложение: случайно нажал — и вернулся в меню, а не потерял партию.
    // В самом меню кнопку не перехватываем — там выход это выход.
    BackHandler(enabled = screen != "menu") {
        screen = when (screen) {
            "settings" -> settingsBack
            "rules" -> rulesBack
            // Из-за стола выходим в список игр, а не в главное меню: там
            // рядом остальные игры, и за стол возвращаются одним нажатием.
            else -> "games"
        }
    }

    when (screen) {
        "games" -> GamesScreen(
            session = session,
            thousand = thousand,
            onDurak = { screen = "durak" },
            onThousand = { screen = "thousand" },
            onBack = { screen = "menu" },
        )

        "durak" -> DurakScreen(
            session = session,
            onExit = { screen = "games" },
            onSettings = { openSettings("durak") },
            onRules = { openRules("durak", GAME_DURAK) },
        )

        "thousand" -> ThousandScreen(
            session = thousand,
            onExit = { screen = "games" },
            onSettings = { openSettings("thousand") },
            onRules = { openRules("thousand", GAME_THOUSAND) },
        )

        // Чьи настройки открывать: из меню — только общие, из-за стола —
        // с правилами и соперником этой игры (SETTINGS.md, 2).
        "settings" -> SettingsScreen(
            game = settingsBack.takeIf { it != "menu" },
            onExit = { screen = settingsBack },
        )

        "rules" -> if (rulesGame == GAME_THOUSAND) {
            RulesScreen(title = "Тысяча", sections = thousandRules, onExit = { screen = rulesBack })
        } else {
            RulesScreen(title = "Дурак", sections = durakRules, onExit = { screen = rulesBack })
        }

        else -> MenuScreen(
            session = session,
            thousand = thousand,
            onDurak = { screen = "durak" },
            onThousand = { screen = "thousand" },
            onGames = { screen = "games" },
            onSettings = { openSettings("menu") },
        )
    }
}

/**
 * Игры: то, во что здесь можно играть.
 *
 * Отдельным экраном, а не кнопками в главном меню, потому что игры
 * прибывают: «Тысяча» уже на подходе, за ней пойдут следующие. В главном
 * меню такой список рано или поздно вытеснил бы всё остальное.
 */
@Composable
private fun GamesScreen(
    session: DurakSession,
    thousand: ThousandSession,
    onDurak: () -> Unit,
    onThousand: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { loadSettings(context) }
    val speaker = remember(settings.engine, settings.voice, settings.rate) {
        Speaker(
            context = context,
            rate = settings.rate,
            enginePackage = settings.engine,
            voiceName = settings.voice,
        )
    }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }

    // Незаконченная партия: поднята с диска после перезапуска или игрок
    // сам вышел в меню посреди игры. О ней надо сказать вслух, иначе
    // о ней не узнать.
    val paused = session.restored || (session.lastPhrase.isNotBlank() && !session.game.finished)
    val thousandPaused = thousand.restored ||
        (thousand.lastPhrase.isNotBlank() && thousand.match.winner == null)

    val appVoice = settings.voiceMode.appSpeaks(speaker.screenReaderOn)
    val view = LocalView.current

    LaunchedEffect(Unit) {
        if (!appVoice) return@LaunchedEffect
        val tail = buildString {
            if (paused) append(" Партия в дурака не доиграна, можно продолжить.")
            if (thousandPaused) append(" Партия в тысячу не доиграна, можно продолжить.")
        }
        speaker.say("Игры. Дурак, тысяча.$tail")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Игры", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        // Отсюда игру открывают осознанно — значит она и есть последняя,
        // и в главном меню её кнопка встанет первой.
        Button(
            onClick = {
                saveSettings(context, settings.copy(lastGame = GAME_DURAK))
                onDurak()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (paused) "Дурак — продолжить партию" else "Дурак — игра против бота")
        }

        if (paused) {
            Spacer(Modifier.height(8.dp))
            // Раздача берёт режим из правил «Дурака»: перевод можно выключить
            // («подкидной» дурак) — тогда новая партия идёт без него.
            Button(
                onClick = {
                    saveSettings(context, settings.copy(lastGame = GAME_DURAK))
                    session.restart(transferAllowed = durakTransferAllowed(context))
                    onDurak()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Дурак — новая партия")
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                saveSettings(context, settings.copy(lastGame = GAME_THOUSAND))
                onThousand()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (thousandPaused) "Тысяча — продолжить партию" else "Тысяча — игра против бота")
        }

        if (thousandPaused) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    saveSettings(context, settings.copy(lastGame = GAME_THOUSAND))
                    thousand.restart()
                    onThousand()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Тысяча — новая партия")
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Назад") }
    }
}

/**
 * Меню: игры и помощник в одной программе, общий слой озвучки.
 * Раздел «Помощник» появится следом за игрой.
 */
@Composable
private fun MenuScreen(
    session: DurakSession,
    thousand: ThousandSession,
    onDurak: () -> Unit,
    onThousand: () -> Unit,
    onGames: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { loadSettings(context) }
    val speaker = remember(settings.engine, settings.voice, settings.rate) {
        Speaker(
            context = context,
            rate = settings.rate,
            enginePackage = settings.engine,
            voiceName = settings.voice,
        )
    }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }

    // Возвращаемся сюда после партии — счёт читаем заново, он мог измениться.
    val score = remember { loadScore(context) }
    // Незаконченная партия: либо её подняли с диска после перезапуска,
    // либо игрок вышел в меню посреди игры. В обоих случаях её можно
    // продолжить — и об этом надо сказать вслух, иначе о ней не узнать.
    val paused = session.restored || (session.lastPhrase.isNotBlank() && !session.game.finished)
    val thousandPaused = thousand.restored ||
        (thousand.lastPhrase.isNotBlank() && thousand.match.winner == null)
    val anyPaused = paused || thousandPaused

    // Кто говорит: приложение или скринридер. В каждый момент — ровно один.
    val appVoice = settings.voiceMode.appSpeaks(speaker.screenReaderOn)
    val view = LocalView.current

    // Приветствие звучит только тогда, когда говорит приложение. В нём нет
    // ничего, чего нет на экране, — а когда читает скринридер, он и так
    // прочитает и название, и счёт, и кнопки. Наша фраза поверх его чтения
    // была бы ровно той кашей, от которой мы уходим.
    LaunchedEffect(Unit) {
        if (!appVoice) return@LaunchedEffect
        val tail = if (anyPaused) " Партия не доиграна, можно продолжить." else ""
        speaker.say("Карточные игры. Выбери раздел. ${score.spoken()}$tail")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Карточные игры", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(score.spoken(), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        // Последняя игра — первой строкой: за стол возвращаются чаще, чем
        // заглядывают в список игр, и лезть ради этого в подменю ни к чему.
        // Список игр остаётся ниже — им выбирают, когда хочется другой игры.
        if (settings.lastGame == GAME_THOUSAND) {
            Button(onClick = onThousand, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (thousandPaused) "Продолжить партию в тысячу" else "Тысяча — игра против бота",
                )
            }
            Spacer(Modifier.height(8.dp))
        } else {
            Button(onClick = onDurak, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (paused) "Продолжить партию в дурака" else "Дурак — игра против бота",
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // Игры — отдельным разделом: сами игры живут на своём экране,
        // чтобы список рос там, а не в главном меню.
        Button(onClick = onGames, modifier = Modifier.fillMaxWidth()) {
            Text(if (anyPaused) "Игры — партия не доиграна" else "Игры")
        }

        Spacer(Modifier.height(8.dp))

        // Справка по правилам живёт на экране игры, в «Ещё»: её открывают
        // за столом, когда споткнулись о ход, а не из меню. В главном меню
        // лишняя кнопка только удлиняет список.
        Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Настройки — речь, звук, вибрация")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            // Ответ на нажатие: игрок ждёт его, поэтому говорим и тогда,
            // когда за столом говорит скринридер, — но не поверх него,
            // а ему же, чтобы он произнёс это в свою очередь.
            onClick = { sayEvent(view, speaker, appVoice, "Раздел «Помощник» ещё в работе.") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Помощник — скоро")
        }
    }
}
