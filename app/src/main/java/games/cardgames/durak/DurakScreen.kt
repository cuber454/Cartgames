package games.cardgames.durak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import games.cardgames.score.Outcome
import games.cardgames.score.Score
import games.cardgames.score.saveScore
import games.cardgames.settings.BOT_PITCH_DURAK
import games.cardgames.settings.botPitch
import games.cardgames.settings.botVoice
import games.cardgames.settings.loadSettings
import games.cardgames.sound.TableSounds
import games.cardgames.sound.Vibrations
import games.cardgames.speech.PHRASE_GAP_MS
import games.cardgames.speech.Speaker
import games.cardgames.speech.TableVoice
import games.cardgames.speech.appSpeaks
import games.cardgames.speech.sayEvent
import games.cardgames.ui.HandCard
import games.cardgames.ui.TableBattles
import games.cardgames.ui.TableGesture
import games.cardgames.ui.tableGestures
// Карта движка и карточка-контейнер из Material3 зовутся одинаково:
// карту движка берём под своим именем, иначе имена спорят.
import games.engine.Card as EngineCard
import games.engine.HandOrder
import games.engine.durak.BotPlayer
import games.engine.durak.DurakGame
import games.engine.durak.DurakMove
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Место игрока за столом. Бот — второй. */
private const val PLAYER = 0
private const val BOT = 1

/** Пауза перед ходом бота, чтобы не тараторил. */
private const val BOT_DELAY_MS = 700L

/** Размер карты в обычном режиме. В крупном он умножается. */
private const val CARD_WIDTH = 64f
private const val CARD_HEIGHT = 92f
private const val LARGE_SCALE = 1.4f

/**
 * Экран партии.
 *
 * Раскладка подчинена одному правилу: то, что нужно каждый ход, не должно
 * уезжать за край. Карты лежат сеткой в три колонки — шесть карт занимают
 * два ряда вместо шести и в обычной руке прокрутка не нужна вовсе. Панель
 * действий закреплена внизу и не прокручивается: сколько бы карт ни
 * накопилось после «беру», кнопки остаются под пальцем на одном месте.
 *
 * Сверху — только короткое: чей ход, козырь, колода и что сказал бот.
 * Справочные кнопки-дубли («что на руке», «что на столе», «счёт») убраны:
 * это же и так написано на экране, а обход от них только длиннее.
 */
@Composable
fun DurakScreen(
    session: DurakSession,
    onExit: () -> Unit,
    onSettings: () -> Unit,
    onRules: () -> Unit,
) {
    val context = LocalContext.current

    // Настройки читаем при каждом входе на экран: игрок мог ходить в них
    // прямо посреди партии, и партия от этого не должна пропасть.
    val settings = remember { loadSettings(context) }

    val speaker = remember(settings.engine, settings.voice, settings.rate) {
        Speaker(
            context = context,
            rate = settings.rate,
            enginePackage = settings.engine,
            voiceName = settings.voice,
        )
    }
    // Голос бота — своим синтезатором: высота у него своя, а её на живом
    // движке на ходу не поменять. Своего голоса боту не выбрали — говорим
    // голосом приложения, но ниже: за столом говорят двое, и спутать их
    // значит не понять, чей ход.
    val botSpeaker = remember(
        settings.engine,
        settings.voice,
        settings.botVoiceDurak,
        settings.rate,
    ) {
        Speaker(
            context = context,
            rate = settings.rate,
            enginePackage = settings.engine,
            voiceName = botVoice(settings.botVoiceDurak, settings.voice),
            pitch = botPitch(settings.botVoiceDurak, BOT_PITCH_DURAK),
        )
    }
    val sounds = remember { TableSounds(context) }
    val vibrations = remember { Vibrations(context) }
    DisposableEffect(speaker, botSpeaker) {
        onDispose {
            speaker.shutdown()
            botSpeaker.shutdown()
            sounds.release()
        }
    }

    val rng = remember { Random.Default }
    // Речь бота живёт выше партии: мешки зачинов должны переживать и
    // перерисовки, и переходы в настройки, иначе повторы вернутся.
    val talker = remember { BotTalker(rng) }
    // Открыто ли маленькое меню «Ещё» в углу экрана.
    var menuOpen by remember { mutableStateOf(false) }
    // Открыт ли выбор карты для перевода.
    var transferOpen by remember { mutableStateOf(false) }
    val scale = if (settings.largeText) LARGE_SCALE else 1f
    val cardWidth: Dp = (CARD_WIDTH * scale).dp
    val cardHeight: Dp = (CARD_HEIGHT * scale).dp
    // Карты на столе мельче рук и в крупном тексте не растут: стол смотрят
    // мельком, а место нужно руке. Пар на столе бывает до шести, и ряд
    // прокручивается вбок.
    val tableWidth: Dp = (CARD_WIDTH * 0.5f).dp
    val tableHeight: Dp = (CARD_HEIGHT * 0.5f).dp
    // Крупным картам втроём тесно — тогда по две в ряд.
    val columns = if (settings.largeText) 2 else 3

    // Кто говорит за столом: приложение или скринридер. В каждый момент —
    // ровно один, иначе две речи накладываются и выходит каша. Значение
    // живое: скринридер включают и выключают прямо посреди партии.
    val appVoice = settings.voiceMode.appSpeaks(speaker.screenReaderOn)
    val view = LocalView.current
    // Фраза после звука откладывается на полсекунды — на это время нужен
    // свой корутин: переживёт перерисовку и умрёт вместе с экраном.
    val scope = rememberCoroutineScope()

    // Вся речь за столом — через общий [TableVoice]: паузы, арбитраж с
    // скринридером и запись фразы для «Повтори» живут там, одни на обе игры.
    //
    // Кто говорит и с какой скоростью — через rememberUpdatedState: объект
    // речи переживает перерисовку, а эти два значения меняются на ходу
    // (скринридер включают посреди партии, скорость крутят в настройках).
    val appVoiceNow = rememberUpdatedState(appVoice)
    val rateNow = rememberUpdatedState(settings.rate)
    val voice = remember(speaker, botSpeaker) {
        TableVoice(
            speaker = speaker,
            botSpeaker = botSpeaker,
            view = view,
            appVoice = { appVoiceNow.value },
            rate = { rateNow.value },
            scope = scope,
            minWaitMs = BOT_DELAY_MS,
            remember = { session.lastPhrase = it },
        )
    }

    /**
     * Ход бота: реплики можно выключить, тогда он молча кладёт карты.
     * [speak] = false — бот промолчал по своей воле (см. [BotTalker]); фразу
     * всё равно запоминаем, её повторит кнопка «Повтори».
     */
    fun sayBotMove(text: String, speak: Boolean = true) {
        if (!settings.botTalk || !speak) {
            session.lastPhrase = text
            return
        }
        voice.sayBot(text)
    }

    fun soundFor(move: DurakMove) {
        if (!settings.sounds) return
        when (move) {
            is DurakMove.Attack, is DurakMove.Defend, is DurakMove.Transfer -> sounds.card()
            DurakMove.Take -> sounds.take()
            DurakMove.Pass -> sounds.card()
        }
    }

    /**
     * Сколько играет звук этого хода, в миллисекундах. Ноль — звука нет
     * (выключен в настройках), и ждать нечего. SoundPool длину семпла не
     * сообщает, поэтому числа живут рядом с самими звуками
     * ([TableSounds.CARD_MS] и соседние).
     */
    fun soundGap(move: DurakMove): Long {
        if (!settings.sounds) return 0L
        return when (move) {
            is DurakMove.Attack, is DurakMove.Defend, is DurakMove.Transfer -> TableSounds.CARD_MS
            DurakMove.Take -> TableSounds.TAKE_MS
            DurakMove.Pass -> TableSounds.CARD_MS
        }.toLong()
    }

    /** Партия кончилась — считаем счёт и говорим итог. */
    fun finishIfOver() {
        val game = session.game
        if (!game.finished || session.finishSaid) return
        session.finishSaid = true

        val result = when (game.winner) {
            PLAYER -> Outcome.WIN
            BOT -> Outcome.LOSS
            else -> Outcome.DRAW
        }
        session.outcome = result
        val next = session.score.plus(result)
        session.score = next
        saveScore(context, next)
        // Партия доиграна — продолжать нечего, сохранение убираем.
        session.forgetSaved()
        // Сигнал исхода играет один: ничья остаётся без него, у неё нет
        // своей ноты, а придумывать ей звук «ни то ни сё» незачем.
        val signalMs = if (!settings.signals) {
            0L
        } else {
            when (result) {
                Outcome.WIN -> { sounds.win(); TableSounds.WIN_MS.toLong() }
                Outcome.LOSS -> { sounds.lose(); TableSounds.LOSE_MS.toLong() }
                else -> 0L
            }
        }
        voice.say(
            finishPhrase(game, next),
            afterMs = if (signalMs > 0) signalMs + PHRASE_GAP_MS else 0L,
        )
    }

    fun newGame() {
        session.restart(transferAllowed = settings.transfer)
        if (settings.sounds) sounds.deal()
        // Сигнал начала идёт вместе с шорохом раздачи, а не после него:
        // ноты и шум не перекрывают друг друга на слух, а разведённые по
        // времени они растянули бы паузу перед фразой вдвое.
        if (settings.signals) sounds.start()
        // Раздача шумит почти семь десятых секунды: скажи мы сразу — голос
        // утонул бы в шорохе карт. Выключенные звуки — ждать нечего.
        val afterMs = if (settings.sounds) TableSounds.DEAL_MS + PHRASE_GAP_MS else 0L
        voice.say(dealPhrase(session.game), afterMs = afterMs)
    }

    // Ход бота: играем за него все ходы подряд, пока ход не вернётся к игроку.
    // Эффект перезапускается при возвращении с экрана настроек и продолжает
    // партию с того же места.
    LaunchedEffect(session.tick) {
        val game = session.game
        var guard = 0
        var played = false
        // Сколько карт бот положил за эту серию. По одной его реплики
        // складываются в картину, по нескольким — уже нет.
        var botCards = 0
        while (!game.finished &&
            game.legalMoves(PLAYER).isEmpty() &&
            game.legalMoves(BOT).isNotEmpty() &&
            guard++ < 60
        ) {
            // Ждём не «полсекунды», а пока договорит предыдущая фраза:
            // иначе бот перебивает сам себя и слышно только последнее слово.
            delay(voice.waitMs())
            val move = BotPlayer.chooseMove(game, BOT, settings.difficulty, rng) ?: break
            soundFor(move)
            // Фразу спрашиваем до хода: «последняя карта» и «колода вышла» —
            // это про состояние до него, после хода карта уже не последняя.
            val line = talker.line(
                move = move,
                difficulty = settings.difficulty,
                trumpSuit = game.trumpSuit,
                tableEmpty = game.table.isEmpty(),
                ownHandSize = game.handOf(BOT).size,
                deckSize = game.deckSize(),
            )
            game.apply(BOT, move)
            // Звук хода и реплика бота стартуют в один момент и налезают
            // друг на друга. Разводим: сначала звук, потом речь. Молчащему
            // боту ждать незачем.
            if (settings.botTalk && line.speak) {
                val gap = soundGap(move)
                if (gap > 0) delay(gap + PHRASE_GAP_MS)
            }
            sayBotMove(line.text, line.speak)
            // Пишем после каждого хода: если приложение прибьют посреди
            // серии ходов бота, партия не откатится к её началу.
            session.persist()
            played = true
            botCards++
        }
        // Ход вернулся к игроку — короткий толчок и тихая нота: толчок
        // слышно не всегда, а тут понятно без слов, что ждут тебя.
        if (played && game.legalMoves(PLAYER).isNotEmpty()) {
            if (settings.vibration) vibrations.tap()
            if (settings.signals) sounds.turn()
        }
        finishIfOver()
        // Стол перед твоим ходом: что лежит и что из этого отбито. Реплики
        // бота называют карты по одной, и по одной они складываются в
        // картину; когда он молчит или положил за раз несколько — нет.
        // Пустой стол не называем: это не сведение, а шум.
        if (played &&
            !game.finished &&
            game.legalMoves(PLAYER).isNotEmpty() &&
            game.table.isNotEmpty() &&
            (!settings.botTalk || botCards > 1)
        ) {
            voice.say("На столе: ${game.spokenTable()}. Твой ход.")
        }
        if (played) session.tick++
    }

    LaunchedEffect(Unit) {
        when {
            // Партия поднята с диска: раздачу объявлять не надо, надо
            // сказать, что за столом, и чей ход.
            session.restored -> {
                session.acceptRestored()
                voice.say(resumePhrase(session.game), whenReady = true)
            }

            session.lastPhrase.isBlank() -> voice.say(dealPhrase(session.game), whenReady = true)

            // Вернулись с другого экрана — напоминаем, на чём остановились.
            // lastPhrase не трогаем: «Продолжаем» — это не фраза для
            // «Повтори», повторять надо ход, а не переход.
            else -> sayEvent(
                view = view,
                speaker = speaker,
                appVoice = appVoice,
                text = "Продолжаем. " + session.lastPhrase,
                whenReady = true,
            )
        }
    }

    /** Ход игрока: фраза, звук, толчок и передача хода — в одном месте. */
    fun play(move: DurakMove) {
        val phrase = ownMovePhrase(move)
        soundFor(move)
        if (settings.ownVibration) vibrations.tap()
        session.game.apply(PLAYER, move)
        // Свой ход звучит так же: сначала карта, потом слово.
        val gap = soundGap(move)
        voice.sayOwnMove(phrase, afterMs = if (gap > 0) gap + PHRASE_GAP_MS else 0L)
        session.persist()
        finishIfOver()
        session.tick++
    }

    fun allowedPhrase(): String {
        val moves = session.game.legalMoves(PLAYER)
        if (moves.isEmpty()) return "Сейчас ход бота, подожди."
        val parts = mutableListOf<String>()
        moves.filterIsInstance<DurakMove.Attack>().forEach { parts += "положить ${it.card.spoken()}" }
        moves.filterIsInstance<DurakMove.Defend>().forEach { parts += "отбиться картой ${it.card.spoken()}" }
        moves.filterIsInstance<DurakMove.Transfer>().forEach { parts += "перевести картой ${it.card.spoken()}" }
        if (moves.contains(DurakMove.Take)) parts += "взять"
        if (moves.contains(DurakMove.Pass)) parts += "сказать бито"
        return "Можно: " + parts.joinToString(", ") + "."
    }

    fun playCard(card: EngineCard) {
        val moves = session.game.legalMoves(PLAYER)
        // Отбиться и перевести может одна и та же карта — той же масти, что
        // карта на столе, но старше её. Первым в цепочке стоит отбой: это
        // ход по умолчанию, а перевод — решение, для него есть кнопка.
        val move = moves.filterIsInstance<DurakMove.Defend>().firstOrNull { it.card == card }
            ?: moves.filterIsInstance<DurakMove.Attack>().firstOrNull { it.card == card }
            ?: moves.filterIsInstance<DurakMove.Transfer>().firstOrNull { it.card == card }
            ?: run {
                // Отказ — ответ на нажатие, а не событие за столом: игрок
                // ждёт его сразу, поэтому говорим своим голосом, даже когда
                // за столом говорит скринридер.
                voice.sayRequested("Этой картой сейчас нельзя.")
                return
            }
        play(move)
    }

    val game = session.game
    val moves = game.legalMoves(PLAYER)
    val transfers = moves.filterIsInstance<DurakMove.Transfer>()

    val status = buildString {
        // Чей ход — по допустимым ходам, а не по тому, кто атакует. Пока
        // стол не отбит, атакующий формально игрок, но играть ему нечем:
        // ход защищающегося. По «attacker == PLAYER» строка врала ровно в
        // этот момент — обещала ход там, где игрок ничего сделать не мог.
        append(if (moves.isNotEmpty()) "Твой ход." else "Ход бота.")
        append(" Козырь — ${game.trumpSuit.spoken}.")
        append(" В колоде ${game.deckSize()}.")
        append(" У бота ${game.handOf(BOT).size}.")
    }

    // Порядок карт — ровно тот, что выбран в настройках, и ничего поверх:
    // раньше карты, которыми сейчас можно сыграть, поднимались наверх, и
    // выбранный порядок от этого ломался — «по масти» превращалось в
    // «как получится». Подсказка осталась, но словами: подпись «не подходит»
    // и бледная карта. Порядок, который игрок задал сам, игра не переиначивает.
    val playable: Set<EngineCard> = moves.mapNotNull { move ->
        when (move) {
            is DurakMove.Attack -> move.card
            is DurakMove.Defend -> move.card
            is DurakMove.Transfer -> move.card
            else -> null
        }
    }.toSet()
    val hand = settings.order.sort(game.handOf(PLAYER), game.trumpSuit)

    // Карта, до которой игрок дошёл двумя пальцами. Это не выбор карты,
    // а её чтение: сыграть можно по-прежнему только нажатием. Сбрасывается
    // на каждой раздаче — рука меняется, и старый номер в ней ничего не
    // значит.
    var cursor by remember(hand) { mutableStateOf(-1) }

    /**
     * Шаг по руке вправо-влево. С какого места ни начни — карта называется
     * целиком: тем, кто слушает, номер без названия не говорит ничего, а
     * «подходит или нет» — это и есть ответ на вопрос «чем мне ходить».
     */
    fun walkHand(step: Int) {
        if (hand.isEmpty()) {
            voice.sayRequested("Карт на руке нет.")
            return
        }
        val next = if (cursor < 0) {
            if (step > 0) 0 else hand.size - 1
        } else {
            (cursor + step + hand.size) % hand.size
        }
        cursor = next
        val card = hand[next]
        val fits = if (card in playable) "подходит" else "не подходит"
        voice.sayRequested("${next + 1} из ${hand.size}: ${card.spoken()}, $fits.")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Жесты — поверх всего экрана и до отступов: считаем ход пальцев
            // от краёв экрана, а не от краёв отрисованной области.
            .tableGestures { gesture ->
                when (gesture) {
                    TableGesture.NEXT_CARD -> walkHand(1)
                    TableGesture.PREV_CARD -> walkHand(-1)
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // --- Верх: только короткое ----------------------------------------

        // Верхние строки подрезаны по высоте: они короткие по смыслу,
        // и разрастаться им за счёт руки незачем. Сбоку — маленькая
        // кнопка «Ещё»: настройки и выход нужны редко, держать их
        // внизу широкой полосой значит отнимать место у карт.
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = status, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Стол: ${game.spokenTable()}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                // И те же карты картинками: слова — скринридеру, картинки —
                // тому, кто за столом видит. Картинка молчит, так что
                // дважды карта не читается.
                if (game.table.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    TableBattles(game.table, cardWidth = tableWidth, cardHeight = tableHeight)
                }
                Spacer(Modifier.height(4.dp))
                Text(session.lastPhrase, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            TextButton(
                onClick = { menuOpen = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("Ещё", style = MaterialTheme.typography.bodySmall)
            }
        }

        // --- Середина: рука сеткой, скроллится только она -----------------

        Box(modifier = Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(hand) { index, card ->
                    HandCard(
                        card = card,
                        playable = card in playable,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        largeText = settings.largeText,
                        selected = cursor == index,
                        onClick = { playCard(card) },
                    )
                }
            }
        }

        // --- Низ: панель действий, не прокручивается ----------------------

        Spacer(Modifier.height(8.dp))

        if (session.outcome != null) {
            Button(onClick = { newGame() }, modifier = Modifier.fillMaxWidth()) { Text("Ещё раз") }
            Spacer(Modifier.height(8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Кнопки не прячем, а гасим: исчезающая кнопка сдвигает
            // соседние, и рука каждый раз ищет их заново.
            Button(
                onClick = { play(DurakMove.Take) },
                enabled = moves.contains(DurakMove.Take),
                modifier = Modifier.weight(1f),
            ) { Text("Взять") }
            Button(
                onClick = { play(DurakMove.Pass) },
                enabled = moves.contains(DurakMove.Pass),
                modifier = Modifier.weight(1f),
            ) { Text("Бито") }
            // В подкидной партии кнопки нет вовсе: серая кнопка, которая не
            // оживает никогда, — это вопрос без ответа. Режим меняется только
            // вместе с раздачей, так что соседние кнопки на месте.
            if (game.transferAllowed) {
                Button(
                    // Перевести можно несколькими картами — тогда спрашиваем
                    // какой, и только потом переводим. Единственную карту
                    // переводим сразу: лишний вопрос на ровном месте — это
                    // лишний шаг для того, кто играет на слух.
                    onClick = {
                        val only = transfers.singleOrNull()
                        if (only != null) play(only) else transferOpen = true
                    },
                    enabled = transfers.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Перевести") }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Обе кнопки — вопрос игрока, а не событие за столом: он нажал
            // и ждёт ответа. Отвечаем своим голосом и в том случае, когда
            // за столом говорит скринридер.
            Button(onClick = { voice.sayRequested(allowedPhrase()) }, modifier = Modifier.weight(1f)) {
                Text("Что можно")
            }
            Button(
                onClick = { voice.sayRequested(session.lastPhrase) },
                modifier = Modifier.weight(1f),
            ) { Text("Повтори") }
        }

    }

    // Какой картой переводить. Диалогом, а не выпадающим списком: скринридер
    // объявляет диалог целиком и сразу ставит в него фокус.
    if (transferOpen) {
        AlertDialog(
            onDismissRequest = { transferOpen = false },
            title = { Text("Перевести") },
            text = {
                Column {
                    transfers.forEach { transfer ->
                        TextButton(
                            onClick = {
                                transferOpen = false
                                play(transfer)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Перевести: ${transfer.card.spoken()}") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { transferOpen = false }) { Text("Отмена") }
            },
        )
    }

    // Редкое — под кнопкой «Ещё». Диалогом, а не выпадающим списком:
    // диалог скринридер объявляет целиком и сразу ставит в него фокус,
    // а в выпадашке незрячий может не понять, что вообще что-то открылось.
    //
    // Пояснительной строки в диалоге нет намеренно: она перечисляла то же,
    // что и так написано на кнопках, а скринридер читал её вслух целиком —
    // лишняя речь перед каждым выбором. «Отмены» тоже нет: диалог
    // закрывается и тапом мимо, и системной «Назад».
    if (menuOpen) {
        AlertDialog(
            onDismissRequest = { menuOpen = false },
            title = { Text("Ещё") },
            confirmButton = {
                Row {
                    // Правила держим рядом с «Настройками»: если за столом
                    // что-то пошло не по-понятному, справка нужна на месте,
                    // а не после выхода в меню.
                    TextButton(onClick = { menuOpen = false; onRules() }) { Text("Правила") }
                    TextButton(onClick = { menuOpen = false; onSettings() }) { Text("Настройки") }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { menuOpen = false; newGame() }) { Text("Новая партия") }
                    TextButton(onClick = { menuOpen = false; onExit() }) { Text("Выйти") }
                }
            },
        )
    }
}

private fun dealPhrase(game: DurakGame): String {
    // Коротко: козырь и чей ход. Перечислять карты и порядок не надо —
    // карты игрок слушает свайпом по руке, и вслух они превращаются в
    // длинную ленту, которую он всё равно не удержит; порядок он выбрал
    // сам в настройках. Стол при раздаче пуст — о нём молчим.
    val turn = if (game.legalMoves(PLAYER).isNotEmpty()) "Твой ход." else "Ход бота."
    return "Раздача. Козырь — ${game.trumpSuit.spoken}. $turn"
}

/**
 * Что сказать, когда партия поднята с диска. Игрок вернулся через час или
 * после случайного выхода и не помнит стол — напоминаем положение дел,
 * а не «продолжаем», за которым ничего не стоит.
 */
private fun resumePhrase(game: DurakGame): String {
    val turn = if (game.legalMoves(PLAYER).isNotEmpty()) "Ход твой." else "Ход бота."
    // Стол пуст — о нём молчим: «на столе пусто» это не сведение, а шум.
    val table = if (game.table.isEmpty()) "" else " На столе: ${game.spokenTable()}."
    return "Продолжаем партию. Карт у тебя: ${game.handOf(PLAYER).size}, " +
        "в колоде: ${game.deckSize()}, козырь — ${game.trumpSuit.spoken}.$table $turn"
}

/**
 * Свой ход — вслух. Это не «сыграна карта», а живая речь: короткая, как за
 * настоящим столом, — иначе партия превращается в перечисление. Свой ход
 * повторяет то, что игрок только что сделал сам, поэтому здесь важнее
 * точность, чем разнообразие: за столом человек и не комментирует свои
 * карты, он просто кладёт их. Речь бота — в [BotTalker].
 */
private fun ownMovePhrase(move: DurakMove): String = when (move) {
    is DurakMove.Attack -> "Кладёшь ${move.card.spoken()}."
    is DurakMove.Defend -> "Отбиваешься картой ${move.card.spoken()}."
    is DurakMove.Transfer -> "Переводишь: ${move.card.spoken()}. Отбиваться боту."
    DurakMove.Take -> "Ты забираешь карты со стола."
    DurakMove.Pass -> "Ты сказал бито. Стол в отбой."
}

private fun finishPhrase(game: DurakGame, score: Score): String {
    val result = when (game.winner) {
        PLAYER -> "Ты вышел. Бот — дурак."
        BOT -> "Бот вышел, у тебя остались карты. Ты дурак."
        else -> "Партия окончена, оба вышли. Ничья."
    }
    return "$result ${score.spoken()}"
}
