package games.cardgames

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import games.cardgames.diag.Journal
import games.cardgames.durak.DurakScreen
import games.cardgames.durak.DurakSession
import games.cardgames.durak.loadDurakRules
import games.cardgames.hundred.HundredScreen
import games.cardgames.hundred.HundredSession
import games.cardgames.kozel.KozelScreen
import games.cardgames.kozel.KozelSession
import games.cardgames.kozel.loadKozelRules
import games.cardgames.rules.RulesScreen
import games.cardgames.rules.durakRules
import games.cardgames.rules.hundredRules
import games.cardgames.rules.kozelRules
import games.cardgames.rules.thousandRules
import games.cardgames.score.loadScore
import games.cardgames.settings.SettingsScreen
import games.cardgames.settings.loadSettings
import games.cardgames.speech.Speaker
import games.cardgames.speech.speech
import games.cardgames.thousand.ThousandScreen
import games.cardgames.thousand.ThousandSession
import games.cardgames.update.Update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Пауза перед записью дерева экрана. Снимаем его не в тот же миг, а когда
 * разметка улеглась: иначе в журнал попадёт предыдущий экран, и искать в нём
 * пропавшую кнопку будет нечего.
 */
private const val TREE_DUMP_DELAY_MS = 600L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Журнал заводим до всего остального: беда, о которой придётся
        // рассказывать, случается с первых секунд — на первом же чтении
        // экрана. Здесь же запоминается контекст, чтобы строку можно было
        // записать откуда угодно.
        Journal.start(this)
        setContent {
            MaterialTheme {
                // Панели системы — статус-строка сверху и полоса навигации
                // снизу — при targetSdk 36 рисуются поверх приложения. Без
                // этого отступа нижние кнопки уезжают под полосу: палец до
                // них доходит, а нажатие забирает система. Отступ внутри
                // Surface, а не на нём: так фон остаётся во всё окно, а
                // содержимое отходит от панелей.
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                        App()
                    }
                }
            }
        }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current

    // Первый экран — список игр: за приложением приходят играть, и выбирать
    // тут нужно игру, а не раздел. Настройки с обновлением стоят строкой внизу
    // списка — они нужны, но не каждый раз.
    var screen by remember { mutableStateOf("games") }

    // Куда вернуться из настроек: их открывают и из списка игр, и прямо из
    // партии — во втором случае возвращаемся за тот же стол.
    var settingsBack by remember { mutableStateOf("games") }

    // Справка по правилам — из того же места, откуда и настройки: посреди
    // партии в неё тоже заглядывают, и вернуться надо за тот же стол.
    var rulesBack by remember { mutableStateOf("games") }
    // Чьи правила открыты: у каждой игры своя справка.
    var rulesGame by remember { mutableStateOf(GAME_DURAK) }
    // Про чью партию спрашивают: не доиграна она или нет — решает игрок, а не
    // приложение, и вопрос этот задаётся при входе за стол.
    var askedGame by remember { mutableStateOf(GAME_DURAK) }

    // Партии живут здесь, выше экранов: поход в настройки посреди партии
    // не должен её обнулять. И у каждой игры — своя: недоигранный дурак
    // и недоигранная тысяча друг о друге не знают.
    val session = remember { DurakSession(context) }
    val thousand = remember { ThousandSession(context) }
    val kozel = remember { KozelSession(context) }
    val hundred = remember { HundredSession(context) }

    // Что видит скринридер на открывшемся экране — в журнал. Это ответ на
    // «кнопка есть, а он её не читает»: в записи видно каждое имя, которое до
    // него дошло, и где узел стоит на экране — а узел за границей экрана и
    // есть та самая пропавшая кнопка.
    val view = LocalView.current
    LaunchedEffect(screen) {
        delay(TREE_DUMP_DELAY_MS)
        Journal.note("экран", "открыт экран «$screen»")
        Journal.dumpTree(view)
    }

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
    // приложение: случайно нажал — и вернулся в список игр, а не потерял
    // партию. В самом списке кнопку не перехватываем — там выход это выход.
    BackHandler(enabled = screen != "games") {
        screen = when (screen) {
            "settings" -> settingsBack
            "rules" -> rulesBack
            // Из-за стола и от вопроса выходим в список игр: там рядом
            // остальные игры, и за стол возвращаются одним нажатием.
            else -> "games"
        }
    }

    when (screen) {
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

        "kozel" -> KozelScreen(
            session = kozel,
            onExit = { screen = "games" },
            onSettings = { openSettings("kozel") },
            onRules = { openRules("kozel", GAME_KOZEL) },
        )

        "hundred" -> HundredScreen(
            session = hundred,
            onExit = { screen = "games" },
            onSettings = { openSettings("hundred") },
            onRules = { openRules("hundred", GAME_HUNDRED) },
        )

        // Чьи настройки открывать: из списка игр — только общие, из-за стола —
        // с правилами и соперником этой игры (SETTINGS.md, 2).
        "settings" -> SettingsScreen(
            game = settingsBack.takeIf { it != "games" },
            onExit = { screen = settingsBack },
        )

        "rules" -> when (rulesGame) {
            GAME_HUNDRED -> RulesScreen(
                title = "101",
                sections = hundredRules,
                onExit = { screen = rulesBack },
            )

            GAME_KOZEL -> RulesScreen(
                title = "Козёл",
                sections = kozelRules,
                onExit = { screen = rulesBack },
            )

            GAME_THOUSAND -> RulesScreen(
                title = "Тысяча",
                sections = thousandRules,
                onExit = { screen = rulesBack },
            )

            else -> RulesScreen(
                title = "Дурак",
                sections = durakRules,
                onExit = { screen = rulesBack },
            )
        }

        // Про неоконченную партию спрашиваем при входе за стол, а не кнопками
        // в списке игр: в списке на каждую неоконченную партию приходилось бы
        // по две кнопки, и выбор игры превращался бы в разбор её состояния.
        "ask" -> AskScreen(
            game = askedGame,
            onContinue = { screen = askedGame },
            onNew = {
                // Новая партия берёт правила из настроек своей игры: раздача
                // идёт по тому, о чём договорились до стола.
                when (askedGame) {
                    GAME_KOZEL -> kozel.restart(rules = loadKozelRules(context))
                    GAME_THOUSAND -> thousand.restart()
                    GAME_HUNDRED -> hundred.restart()
                    else -> session.restart(rules = loadDurakRules(context))
                }
                screen = askedGame
            },
            onBack = { screen = "games" },
        )

        // Всё прочее — список игр: он же и первый экран приложения.
        else -> GamesScreen(
            session = session,
            thousand = thousand,
            kozel = kozel,
            hundred = hundred,
            onGame = { game, paused ->
                if (paused) {
                    askedGame = game
                    screen = "ask"
                } else {
                    screen = game
                }
            },
            onSettings = { openSettings("games") },
        )
    }
}

/**
 * Игры: то, во что здесь можно играть. Он же — первый экран приложения.
 *
 * Список игр стоит первым, а не кнопками в главном меню рядом с настройками:
 * игры прибывают, и раздел, которого ещё нет, читается как поломка — «нажимаю,
 * а он молчит» (Катерина, 19.09: «переделай главное меню, чтобы там были
 * только игры»).
 */
@Composable
private fun GamesScreen(
    session: DurakSession,
    thousand: ThousandSession,
    kozel: KozelSession,
    hundred: HundredSession,
    onGame: (String, Boolean) -> Unit,
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

    // Незаконченная партия: поднята с диска после перезапуска или игрок
    // сам вышел в список посреди игры. О ней надо сказать вслух, иначе о ней
    // не узнать — и по ней же приложение спрашивает при входе за стол.
    val paused = session.restored || (session.lastPhrase.isNotBlank() && !session.game.finished)
    val thousandPaused = thousand.restored ||
        (thousand.lastPhrase.isNotBlank() && thousand.match.winner == null)
    // В «Козле» на диске лежит матч, а не раунд: не доигран он, пока в нём
    // никто не набрал до цели.
    val kozelPaused = kozel.restored ||
        (kozel.lastPhrase.isNotBlank() && !kozel.match.over)
    // А в «101» игрок может выбыть, и матч от этого кончается только для
    // него: движок доигрывает за оставшихся, но спрашивать «продолжить
    // партию?» про матч, в котором тебя больше нет, нечего.
    val hundredPaused = hundred.restored ||
        (hundred.lastPhrase.isNotBlank() && !hundred.overForPlayer)

    // Счёт партий: за ним сюда и заходят чаще, чем за настройками.
    val score = remember { loadScore(context) }

    val speech = settings.voiceMode.speech(speaker.screenReaderOn)

    LaunchedEffect(Unit) {
        if (!speech.speaks) return@LaunchedEffect
        val tail = buildString {
            if (paused) append(" Партия в дурака не доиграна.")
            if (thousandPaused) append(" Партия в тысячу не доиграна.")
            if (kozelPaused) append(" Партия в козла не доиграна.")
            if (hundredPaused) append(" Партия в сто одну не доиграна.")
        }
        speaker.say("Игры. Дурак, тысяча, козёл, сто одна. ${score.spoken()}$tail")
    }

    // Итог установки, которую доводила система, и новость о новой сборке —
    // одним заходом, а не двумя: две фразы подряд перебивают друг друга, и
    // первая осталась бы недослушанной.
    LaunchedEffect(Unit) {
        if (!speech.speaks) return@LaunchedEffect

        // Итог прошлой установки — первым: игрок в этот момент смотрит, что
        // стало с приложением, и о неудаче узнать больше неоткуда. Сказанное
        // здесь стирается: сказанное дважды — не сказанное.
        Update.takeOutcome(context)?.let { speaker.say(it, interrupt = false) }

        // Разрешения спрашиваем при входе, а не тогда, когда они понадобились:
        // на свежей установке их нет ни одного, и первое же обновление упёрлось
        // бы в строку, которая молчит (Катерина, 20.09). Спрашиваем то, что
        // вообще можно спросить: разрешение установщика выдаётся на системном
        // экране, и без него система не примет сборку.
        if (!Update.canInstall(context)) {
            speaker.say(
                "Приложению нужно разрешение ставить обновления — сейчас открою экран, " +
                    "где оно выдаётся.",
                interrupt = false,
            )
            runCatching { context.startActivity(Update.permissionIntent(context)) }
        }

        if (!settings.autoUpdate) return@LaunchedEffect
        when (val found = withContext(Dispatchers.IO) { Update.check(BuildConfig.VERSION_CODE) }) {
            is Update.Check.Fresh -> speaker.say(
                "Вышла новая версия ${found.release.title}. " +
                    if (Update.canInstall(context)) {
                        "В настройках строка «проверить обновление» поставит её сама."
                    } else {
                        "Без разрешения на установку она не встанет."
                    },
                interrupt = false,
            )

            // Дорога до сети — единственный отказ, который чинится не в
            // приложении: имена не разрешаются, когда ему закрыт интернет.
            // Говорим об этом при входе и ведём на экран разрешений: сам
            // попросить интернет программа не может.
            is Update.Check.Failed -> if (found.network) {
                // Сеть у телефона есть, а имён нет — значит закрыт доступ
                // приложению, и чинится это на экране его разрешений. Если
                // сети нет вовсе, открывать нечего: там её и не выдадут.
                val blocked = Update.networkIsUp(context)
                speaker.say(
                    "Нет связи с сервером обновлений: ${found.reason}." +
                        if (blocked) " Сейчас открою настройки приложения." else "",
                    interrupt = false,
                )
                if (blocked) runCatching { context.startActivity(Update.appSettingsIntent(context)) }
            }

            Update.Check.Current -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Игры", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(score.spoken(), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        // Игра — одна кнопка. Не доиграна партия или нет, спрашивают при входе
        // за стол ([AskScreen]): в списке на это уходило бы по две кнопки на
        // игру, и выбор игры читался бы как разбор её состояния.
        Button(
            onClick = { onGame(GAME_DURAK, paused) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Дурак")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { onGame(GAME_THOUSAND, thousandPaused) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Тысяча")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { onGame(GAME_KOZEL, kozelPaused) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Козёл")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { onGame(GAME_HUNDRED, hundredPaused) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("101")
        }

        Spacer(Modifier.height(16.dp))

        // Настройки и обновление — одной строкой внизу списка. Обновление
        // живёт внутри настроек, и оттуда до него одна остановка: без этой
        // строки за свежей сборкой пришлось бы сперва сесть за стол.
        Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Настройки — речь, звук, обновление, журнал")
        }
    }
}

/**
 * Вопрос перед неоконченной партией: продолжать её или начинать новую.
 *
 * Отдельным экраном, а не кнопками в списке игр: вопрос звучит один раз и
 * ровно про ту игру, за которую игрок собирается сесть. Партию хранит
 * приложение, но решает тут игрок — продолжение и новая раздача расходятся
 * с этого нажатия, и отменять его потом нечем.
 */
@Composable
private fun AskScreen(
    game: String,
    onContinue: () -> Unit,
    onNew: () -> Unit,
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

    // Название игры в винительном падеже: «партия в дурака», «в тысячу»,
    // «в козла». Подставить сюда имя из кнопки — значит сказать «партия в
    // Козёл».
    val what = when (game) {
        GAME_KOZEL -> "козла"
        GAME_THOUSAND -> "тысячу"
        GAME_HUNDRED -> "сто одну"
        else -> "дурака"
    }

    val speech = settings.voiceMode.speech(speaker.screenReaderOn)
    LaunchedEffect(game) {
        if (speech.speaks) {
            speaker.say("Партия в $what не доиграна. Продолжить её или начать новую?")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Партия в $what не доиграна", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Продолжить партию")
        }

        Spacer(Modifier.height(8.dp))

        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
            Text("Начать новую партию")
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Назад")
        }
    }
}
