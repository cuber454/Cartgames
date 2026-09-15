package games.cardgames.durak

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import games.cardgames.speech.Speaker
import games.engine.Card
import games.engine.durak.DurakGame
import games.engine.durak.DurakMove
import kotlinx.coroutines.delay

/** Место игрока за столом. Бот — второй. */
private const val PLAYER = 0
private const val BOT = 1

/** Пауза перед ходом бота, чтобы не тараторил. */
private const val BOT_DELAY_MS = 700L

@Composable
fun DurakScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }

    val game = remember { DurakGame.start() }
    var tick by remember { mutableIntStateOf(0) }
    var lastPhrase by remember { mutableStateOf("Раздача. Ходит тот, у кого младший козырь.") }
    var finishSaid by remember { mutableStateOf(false) }

    fun say(text: String) {
        lastPhrase = text
        speaker.say(text)
    }

    // Ход бота: играем за него все ходы подряд, пока ход не вернётся к игроку.
    LaunchedEffect(tick) {
        var guard = 0
        var played = false
        while (!game.finished &&
            game.legalMoves(PLAYER).isEmpty() &&
            game.legalMoves(BOT).isNotEmpty() &&
            guard++ < 60
        ) {
            delay(BOT_DELAY_MS)
            val move = BotPlayer.chooseMove(game, BOT) ?: break
            val phrase = phraseFor(BOT, move)
            game.apply(BOT, move)
            say(phrase)
            played = true
        }
        if (game.finished && !finishSaid) {
            finishSaid = true
            say(finishPhrase(game))
        }
        if (played) tick++
    }

    LaunchedEffect(Unit) { speaker.say(lastPhrase) }

    fun allowedPhrase(): String {
        val moves = game.legalMoves(PLAYER)
        if (moves.isEmpty()) return "Сейчас ход бота, подожди."
        val parts = mutableListOf<String>()
        moves.filterIsInstance<DurakMove.Attack>().forEach { parts += "положить ${it.card.spoken()}" }
        moves.filterIsInstance<DurakMove.Defend>().forEach { parts += "отбиться картой ${it.card.spoken()}" }
        if (moves.contains(DurakMove.Take)) parts += "взять"
        if (moves.contains(DurakMove.Pass)) parts += "сказать бито"
        return "Можно: " + parts.joinToString(", ") + "."
    }

    fun playCard(card: Card) {
        val moves = game.legalMoves(PLAYER)
        val move = moves.filterIsInstance<DurakMove.Defend>().firstOrNull { it.card == card }
            ?: moves.filterIsInstance<DurakMove.Attack>().firstOrNull { it.card == card }
            ?: run {
                say("Этой картой сейчас нельзя.")
                return
            }
        val phrase = phraseFor(PLAYER, move)
        game.apply(PLAYER, move)
        say(phrase)
        tick++
    }

    fun doMove(move: DurakMove) {
        val phrase = phraseFor(PLAYER, move)
        game.apply(PLAYER, move)
        say(phrase)
        tick++
    }

    val status = buildString {
        append(if (game.attacker == PLAYER) "Твой ход." else "Ход бота.")
        append(" Козырь — ${game.trumpSuit.spoken}.")
        append(" В колоде ${game.deckSize()} карт.")
        append(" У бота карт: ${game.handOf(BOT).size}.")
    }

    val moves = game.legalMoves(PLAYER)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { contentDescription = status },
        )

        Spacer(Modifier.height(12.dp))
        Text("Стол: ${game.spokenTable()}")
        Spacer(Modifier.height(12.dp))
        Text(lastPhrase, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (moves.contains(DurakMove.Take)) {
                Button(onClick = { doMove(DurakMove.Take) }) { Text("Взять") }
            }
            if (moves.contains(DurakMove.Pass)) {
                Button(onClick = { doMove(DurakMove.Pass) }) { Text("Бито") }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Твои карты:", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))

        game.handOf(PLAYER).forEach { card ->
            val label = card.spoken()
            Button(
                onClick = { playCard(card) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .semantics { contentDescription = label },
            ) {
                Text(label)
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { say("На руке: " + game.spokenHand(PLAYER) + ".") }) { Text("Что на руке") }
            Button(onClick = { say("На столе: " + game.spokenTable() + ".") }) { Text("Что на столе") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { say(allowedPhrase()) }) { Text("Что можно") }
            Button(onClick = { speaker.say(lastPhrase) }) { Text("Повтори") }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Выйти в меню") }
    }
}

private fun phraseFor(seat: Int, move: DurakMove): String = when (move) {
    is DurakMove.Attack -> if (seat == PLAYER) {
        "Кладёшь ${move.card.spoken()}."
    } else {
        "Бот кладёт ${move.card.spoken()}."
    }

    is DurakMove.Defend -> if (seat == PLAYER) {
        "Отбиваешься картой ${move.card.spoken()}."
    } else {
        "Бот отбивается картой ${move.card.spoken()}."
    }

    DurakMove.Take -> if (seat == PLAYER) {
        "Ты забираешь карты со стола."
    } else {
        "Бот забирает карты со стола."
    }

    DurakMove.Pass -> if (seat == PLAYER) {
        "Ты сказал бито. Стол в отбой."
    } else {
        "Бот сказал бито. Стол в отбой."
    }
}

private fun finishPhrase(game: DurakGame): String = when (game.winner) {
    PLAYER -> "Ты вышел. Бот — дурак."
    BOT -> "Бот вышел, у тебя остались карты. Ты дурак."
    else -> "Партия окончена, оба вышли. Ничья."
}
