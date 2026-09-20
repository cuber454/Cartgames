package games.cardgames.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import games.cardgames.settings.loadSettings
import games.cardgames.speech.Speaker
import games.cardgames.speech.sayEvent
import games.cardgames.speech.speech

/**
 * О программе — построчно.
 *
 * Устроено как справка по правилам ([games.cardgames.rules.RulesScreen]) и по
 * той же причине: абзац, прочитанный целиком, не остановить и не перечитать.
 * Незрячий слушает скринридером, и каждая строка здесь — отдельный элемент:
 * скринридер читает её как отдельный пункт, а свайп переводит к следующему.
 * Где говорит приложение, те же строки листаются кнопками.
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

    val lines = ABOUT_TEXT
    var cursor by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    /** Сказать абзац: своим голосом или голосом скринридера — как везде. */
    fun say(index: Int) {
        val line = lines.getOrNull(index) ?: return
        cursor = index
        sayEvent(view, speaker, speech, line)
    }

    // Первая строка — приветствие и подсказка, как листать. Без неё экран
    // открывается в тишину, и непонятно, есть ли тут что-то вообще.
    LaunchedEffect(Unit) {
        if (!speech.speaks) return@LaunchedEffect
        speaker.sayWhenReady(
            "О программе. Всего абзацев: ${lines.size}. " +
                "Кнопка «Дальше» читает следующий.",
        )
    }

    // Курсор ведёт список за собой: незрячему экран не видно, и «где я» он
    // понимает только по тому, что прочитано.
    LaunchedEffect(cursor) { listState.animateScrollToItem(cursor) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("О программе", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            itemsIndexed(lines) { index, line ->
                val current = index == cursor
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (current) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        )
                        .clickable { say(index) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Листать можно и кнопками, и свайпом скринридера — оба способа ведут
        // к одному и тому же курсору.
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { say((cursor - 1).coerceAtLeast(0)) },
                modifier = Modifier.weight(1f),
            ) { Text("Назад") }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = {
                    val next = cursor + 1
                    if (next < lines.size) {
                        say(next)
                    } else {
                        sayEvent(view, speaker, speech, "Это всё.")
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Дальше") }
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { say(0) }, modifier = Modifier.weight(1f)) { Text("С начала") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onExit, modifier = Modifier.weight(1f)) { Text("Закрыть") }
        }
    }
}

/**
 * Текст о программе — от первого лица, её же словами (SETTINGS.md, 10).
 *
 * Абзацы короткие намеренно: длинную мысль на слух не удержать. Здесь автор
 * говорит о себе сам, в женском роде, — приложение при этом не догадывается о
 * том, кто за столом, и спрашивает об этом отдельно («Кто играет», настройки).
 */
val ABOUT_TEXT: List<String> = listOf(
    "Карточные игры, в которые играют на слух. Я сделала их при помощи нейросетей: " +
        "код, звуки и тексты выросли из разговора с машиной. Это не студия и не команда — " +
        "просто я и моя затея.",
    "Пользуйтесь как есть. Игра бесплатная и взамен не просит ничего: ни регистрации, " +
        "ни отзывов, ни благодарностей.",
    "Дельное предложение — присылайте, ему здесь рады. Остальное — «не понравилось», " +
        "«я бы сделал иначе», «а почему нет того и этого» — лучше оставить при себе: " +
        "я никому ничего не должна.",
    // Куда присылать — отдельной строкой: её слушают на слух, и имя пользователя
    // в середине абзаца пришлось бы разыскивать заново (слова Катерины, 20.09).
    "Связаться со мной можно в Telegram: @Cuber456.",
    "Будет ли игра развиваться — как выйдет. По обстоятельствам: пока есть время и желание, " +
        "делаю; не будет — останется как есть, и это тоже нормально.",
)
