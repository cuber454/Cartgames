package games.cardgames.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import games.cardgames.settings.loadSettings
import games.cardgames.speech.Speaker
import games.cardgames.speech.sayEvent
import games.cardgames.speech.speech

/**
 * О программе — одной простыней.
 *
 * Текст лежит одним куском, абзацы разделены пустой строкой (слова Катерины,
 * 20.09). Скринридер идёт по такому тексту строками и любую перечитывает на
 * месте; прежний список, где каждый абзац был отдельным элементом, заставлял
 * ходить по кускам и слушать «третий из пяти» там, где человек просто читает.
 *
 * Кнопка «Прочитать» говорит весь текст подряд нашим голосом — иначе при
 * выключенном скринридере экран молчит, а молчащий экран незрячему и есть
 * пустой.
 *
 * Отдельный экран, а не раздел правил: правила — про игру, это — про того, кто
 * её сделал, и заходят сюда один раз. Лежит в настройках, под журналом: там всё
 * про само приложение.
 */
@Composable
fun AboutScreen(onExit: () -> Unit) {
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

    val speech = settings.voiceMode.speech(speaker.screenReaderOn)
    val view = LocalView.current
    val text = ABOUT_TEXT.joinToString("\n\n")

    // Тишина на входе читается как пустой экран: говорим, что здесь лежит и чем
    // это читается. Тем же путём, что и все фразы приложения, — при скринридере
    // скажет он, при «никто» не скажет никто.
    LaunchedEffect(Unit) {
        sayEvent(
            view = view,
            speaker = speaker,
            speech = speech,
            text = "О программе. Текст на экране, кнопка «Прочитать» читает его целиком.",
            whenReady = true,
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("О программе", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        )

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { sayEvent(view, speaker, speech, text) },
                modifier = Modifier.weight(1f),
            ) { Text("Прочитать") }

            Spacer(Modifier.width(8.dp))

            OutlinedButton(onClick = onExit, modifier = Modifier.weight(1f)) { Text("Закрыть") }
        }
    }
}

/**
 * Текст о программе — от первого лица, её же словами (SETTINGS.md, 10).
 *
 * Абзацы короткие намеренно: длинную мысль на слух не удержать. Говорит автор о
 * себе в мужском роде: в женском текст читался не тем голосом, которым писала
 * владелица (её поправка, 20.09). Приложение при этом о том, кто за столом, не
 * догадывается и спрашивает отдельно («Кто играет», настройки) — род речи
 * игрока и лицо автора здесь разные вещи и друг за другом не ходят.
 */
val ABOUT_TEXT: List<String> = listOf(
    "Карточные игры, в которые играют на слух. Я сделал их при помощи нейросетей: " +
        "код, звуки и тексты выросли из разговора с машиной. Это не студия и не команда — " +
        "просто я и моя затея.",
    "Пользуйтесь как есть. Игра бесплатная и взамен не просит ничего: ни регистрации, " +
        "ни отзывов, ни благодарностей.",
    "Дельное предложение — присылайте, ему здесь рады. Остальное — «не понравилось», " +
        "«я бы сделал иначе», «а почему нет того и этого» — лучше оставить при себе: " +
        "я никому ничего не должен.",
    // Куда присылать — отдельной строкой: её слушают на слух, и имя пользователя
    // в середине абзаца пришлось бы разыскивать заново (слова Катерины, 20.09).
    // Ник — тот, под которым она пишет в Telegram (её поправка 20.09): здесь
    // стоял GitHub-овский, и написанное уходило не по адресу.
    "Связаться со мной можно в Telegram: @Gaggia12.",
    "Будет ли игра развиваться — как выйдет. По обстоятельствам: пока есть время и желание, " +
        "делаю; не будет — останется как есть, и это тоже нормально.",
)
