package games.cardgames

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.unit.dp
import games.cardgames.durak.DurakScreen
import games.cardgames.speech.Speaker

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
    var screen by remember { mutableStateOf("menu") }
    when (screen) {
        "durak" -> DurakScreen(onExit = { screen = "menu" })
        else -> MenuScreen(onDurak = { screen = "durak" })
    }
}

/**
 * Меню: игры и помощник в одной программе, общий слой озвучки.
 * Раздел «Помощник» появится следом за игрой.
 */
@Composable
private fun MenuScreen(onDurak: () -> Unit) {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    LaunchedEffect(Unit) { speaker.say("Карточные игры. Выбери раздел.") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Карточные игры", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Button(onClick = onDurak, modifier = Modifier.fillMaxWidth()) {
            Text("Дурак — игра против бота")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { speaker.say("Раздел «Помощник» ещё в работе.") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Помощник — скоро")
        }
    }
}
