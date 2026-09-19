package games.cardgames.rules

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
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
 * Справка по правилам — построчно.
 *
 * Зачем отдельный экран, а не абзац текста. Зрячий читает правила глазами и
 * находит нужное место взглядом. Незрячий слушает их скринридером, и если
 * правила лежат одной простыней, он слышит «правила дурака колода тридцать
 * шесть карт от шестёрки до туза играют двое ты и бот» — без возможности
 * остановиться, вернуться на строку назад или пропустить знакомое.
 *
 * Поэтому каждая строка справки — отдельный элемент: скринридер читает её
 * как отдельный пункт, а свайп переводит к следующему. В режиме, когда
 * говорит приложение, те же строки листаются кнопками «Дальше» и «Назад»:
 * игрок не держит в голове, сколько он уже прослушал, — за него это помнит
 * курсор.
 */
@Composable
fun RulesScreen(
    title: String,
    sections: List<RuleSection>,
    onExit: () -> Unit,
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

    val speech = settings.voiceMode.speech(speaker.screenReaderOn)
    val view = LocalView.current

    // Строки справки: заголовки разделов и правила, все — плоским списком.
    // Правила нумеруются сквозь всю справку, чтобы на слух было за что
    // зацепиться: «правило двенадцать» — это место, куда можно вернуться.
    val lines = remember(sections) {
        val result = mutableListOf<RuleLine>()
        var number = 0
        sections.forEach { section ->
            result += RuleLine(section.title, isHeader = true, number = 0)
            section.rules.forEach { rule ->
                number++
                result += RuleLine(rule, isHeader = false, number = number)
            }
        }
        result
    }

    var cursor by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    fun lineAt(index: Int): RuleLine? = lines.getOrNull(index)

    /** Сказать строку: своим голосом или голосом скринридера — как везде. */
    fun say(index: Int) {
        val line = lineAt(index) ?: return
        cursor = index
        val text = if (line.isHeader) "Раздел. ${line.text}" else "Правило ${line.number}. ${line.text}"
        sayEvent(view, speaker, speech, text)
    }

    // Первая строка — приветствие и подсказка, как листать. Без неё экран
    // открывается в тишину, и непонятно, есть ли тут что-то вообще.
    LaunchedEffect(Unit) {
        if (!speech.speaks) return@LaunchedEffect
        speaker.sayWhenReady(
            "Правила «$title». Всего пунктов: ${lines.count { !it.isHeader }}. " +
                "Кнопка «Дальше» читает следующий пункт.",
        )
    }

    // Курсор ведёт список за собой: незрячему экран не видно, и «где я» он
    // понимает только по тому, что прочитано.
    LaunchedEffect(cursor) { listState.animateScrollToItem(cursor) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Правила: $title", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            itemsIndexed(lines) { index, line ->
                val current = index == cursor
                Text(
                    text = if (line.isHeader) line.text else "${line.number}. ${line.text}",
                    style = if (line.isHeader) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
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

        // Листать можно и кнопками, и свайпом скринридера — оба способа
        // ведут к одному и тому же курсору.
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { say((cursor - 1).coerceAtLeast(0)) },
                modifier = Modifier.weight(1f),
            ) { Text("Назад") }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = {
                    val next = cursor + 1
                    if (next < lines.size) say(next) else sayEvent(view, speaker, speech, "Это конец справки.")
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

/** Строка справки: заголовок раздела или правило с номером. */
private data class RuleLine(val text: String, val isHeader: Boolean, val number: Int)
