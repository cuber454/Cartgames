package games.cardgames.thousand

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
import games.cardgames.settings.BOT_PITCH_THOUSAND
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
import games.cardgames.ui.TableGesture
import games.cardgames.ui.tableGestures
import games.engine.Card as EngineCard
import games.engine.thousand.Phase
import games.engine.thousand.RoundSummary
import games.engine.thousand.ThousandBot
import games.engine.thousand.ThousandMatch
import games.engine.thousand.ThousandMove
import games.engine.thousand.ThousandRound
import games.engine.thousand.orderHand
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
 * Экран партии в «Тысячу».
 *
 * От «Дурака» отличается тем, что ходов за кон несколько и они разного рода:
 * торг, прикуп, снос, розыгрыш. Панель внизу поэтому меняется по фазе —
 * в торге это суммы, в прикупе выбор прикупа, в сносе и розыгрыше сама рука.
 * Одна кнопка «сходить» тут не годится: ход не один и не одинаковый.
 *
 * Наверху — короткое: фаза, чей ход, счёт и бочка. Счёт в «Тысяче» не
 * украшение, а то, ради чего играют: кон за коном он и есть положение дел.
 */
@Composable
fun ThousandScreen(
    session: ThousandSession,
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
    // голосом приложения, но выше: за столом говорят двое, и спутать их
    // значит не понять, чей ход.
    val botSpeaker = remember(
        settings.engine,
        settings.voice,
        settings.botVoiceThousand,
        settings.rate,
    ) {
        Speaker(
            context = context,
            rate = settings.rate,
            enginePackage = settings.engine,
            voiceName = botVoice(settings.botVoiceThousand, settings.voice),
            pitch = botPitch(settings.botVoiceThousand, BOT_PITCH_THOUSAND),
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
    // Открыто ли маленькое меню «Ещё» в углу экрана.
    var menuOpen by remember { mutableStateOf(false) }
    // Открыт ли выбор марьяжа, который хвалим.
    var praiseOpen by remember { mutableStateOf(false) }
    // Открыт ли вопрос «расписаться?» — роспись кончает кон и спрашивается
    // отдельно: промахнуться по ней пальцем не должно быть накладно.
    var raspisOpen by remember { mutableStateOf(false) }
    val scale = if (settings.largeText) LARGE_SCALE else 1f
    val cardWidth: Dp = (CARD_WIDTH * scale).dp
    val cardHeight: Dp = (CARD_HEIGHT * scale).dp
    val columns = if (settings.largeText) 2 else 3

    // Кто говорит за столом: приложение или скринридер. В каждый момент —
    // ровно один, иначе две речи накладываются и выходит каша.
    val appVoice = settings.voiceMode.appSpeaks(speaker.screenReaderOn)
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Вся речь за столом — через общий [TableVoice]: паузы, арбитраж со
    // скринридером и запись фразы для «Повтори» живут там, одни на обе игры.
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

    /** Реплика бота: её можно выключить в настройках. */
    fun sayBot(text: String) {
        if (!settings.botTalk) {
            session.lastPhrase = text
            return
        }
        voice.sayBot(text)
    }

    fun soundFor(move: ThousandMove) {
        if (!settings.sounds) return
        when (move) {
            is ThousandMove.Play, is ThousandMove.Praise -> sounds.card()
            is ThousandMove.Discard, is ThousandMove.TakePrikups -> sounds.take()
            else -> Unit
        }
    }

    /** Сколько звучит звук этого хода. Ноль — звука нет, и ждать нечего. */
    fun soundGap(move: ThousandMove): Long {
        if (!settings.sounds) return 0L
        return when (move) {
            is ThousandMove.Play, is ThousandMove.Praise -> TableSounds.CARD_MS.toLong()
            is ThousandMove.Discard, is ThousandMove.TakePrikups -> TableSounds.TAKE_MS.toLong()
            else -> 0L
        }
    }

    /**
     * Приписка про взятку, если ход её закрыл. Одной фразой с самим ходом,
     * а не отдельной: карта и её исход — это одно событие за столом, и
     * слушать их порознь значит ловить две речи вместо одной мысли.
     */
    fun trickSuffix(round: ThousandRound, tricksBefore: Int): String {
        val tricks = round.tricksPlayed()
        if (tricks.size <= tricksBefore) return ""
        val trick = tricks.last()
        val who = if (trick.winner == PLAYER) "Взятка твоя" else "Взятку взял бот"
        return " $who, очков ${trick.points}."
    }

    /** Кон доигран — заносим его в матч и говорим итог. */
    fun finishIfOver() {
        val round = session.round
        if (round.phase != Phase.OVER || session.recorded) return
        val summary = session.record()
        if (session.match.winner != null) session.forgetSaved()
        voice.say(roundPhrase(round, summary, session.match))
    }

    fun dealNew() {
        if (settings.sounds) sounds.deal()
        // Раздача шумит почти семь десятых секунды: скажи мы сразу — голос
        // утонул бы в шорохе карт. Выключенные звуки — ждать нечего.
        val afterMs = if (settings.sounds) TableSounds.DEAL_MS + PHRASE_GAP_MS else 0L
        voice.say(dealPhrase(session.round), afterMs = afterMs)
    }

    fun nextRound() {
        session.nextRound()
        dealNew()
    }

    fun newMatch() {
        session.restart()
        dealNew()
    }

    /** Ход игрока: фраза, звук, толчок и передача хода — в одном месте. */
    fun play(move: ThousandMove) {
        val round = session.round
        val tricksBefore = round.tricksPlayed().size
        val phrase = ownPhrase(
            move,
            // Свой прикуп берут вслепую, и в руке он оказывается уже потом:
            // назвать его надо сейчас, иначе две новые карты придётся искать
            // в руке самому и на слух сравнивать с тем, что помнишь.
            prikup = (move as? ThousandMove.TakePrikups)?.let { round.prikup(it.index) }.orEmpty(),
        )
        soundFor(move)
        if (settings.ownVibration) vibrations.tap()
        round.apply(PLAYER, move)
        val gap = soundGap(move)
        voice.sayOwnMove(
            phrase + trickSuffix(round, tricksBefore),
            afterMs = if (gap > 0) gap + PHRASE_GAP_MS else 0L,
        )
        session.persist()
        finishIfOver()
        session.tick++
    }

    fun allowedPhrase(): String {
        val moves = session.round.legalMoves(PLAYER)
        if (moves.isEmpty()) return "Сейчас ход бота, подожди."
        // Снос — это «любая карта», а не список: перечислять двенадцать карт,
        // чтобы сказать «можно любую», значит читать руку второй раз.
        if (moves.all { it is ThousandMove.Discard }) {
            return "Можно снести любую карту. Нажми ту, которую сносишь."
        }
        val parts = mutableListOf<String>()
        moves.filterIsInstance<ThousandMove.Bid>().forEach { parts += "назвать ${it.amount}" }
        moves.filterIsInstance<ThousandMove.TakePrikups>()
            .forEach { parts += "взять прикуп ${it.index + 1}" }
        moves.filterIsInstance<ThousandMove.Play>().forEach { parts += "положить ${it.card.spoken()}" }
        moves.filterIsInstance<ThousandMove.Praise>()
            .forEach { parts += "похвалить ${it.card.suit.spoken}" }
        if (moves.contains(ThousandMove.Pass)) parts += "пас"
        if (moves.contains(ThousandMove.Raspis)) parts += "расписаться"
        return "Можно: " + parts.joinToString(", ") + "."
    }

    /** Нажатие на карту: в сносе она уходит, в розыгрыше — играется. */
    fun tapCard(card: EngineCard) {
        val moves = session.round.legalMoves(PLAYER)
        val move = moves.filterIsInstance<ThousandMove.Play>().firstOrNull { it.card == card }
            ?: moves.filterIsInstance<ThousandMove.Discard>().firstOrNull { card in it.cards }
            ?: run {
                // Отказ — ответ на нажатие, а не событие за столом: игрок
                // ждёт его сразу, поэтому говорим своим голосом, даже когда
                // за столом говорит скринридер.
                voice.sayRequested("Сейчас этой картой нельзя.")
                return
            }
        play(move)
    }

    // Ход бота: играем за него все ходы подряд, пока ход не вернётся к игроку.
    // Эффект перезапускается при возвращении с экрана настроек и продолжает
    // партию с того же места.
    LaunchedEffect(session.tick) {
        val round = session.round
        var guard = 0
        var played = false
        while (!round.isFinished(round) && round.turn == BOT && guard++ < 60) {
            // Ждём не «полсекунды», а пока договорит предыдущая фраза: иначе
            // бот перебивает сам себя и слышно только последнее слово.
            delay(voice.waitMs())
            val move = ThousandBot.chooseMove(round, BOT, settings.difficulty, rng) ?: break
            val tricksBefore = round.tricksPlayed().size
            soundFor(move)
            val phrase = botPhrase(
                move,
                // Взятый прикуп открывают обоим — иначе игрок так и не узнает,
                // что ушло боту в руку, и это знание потеряно навсегда: в руку
                // соперника не заглядывают. Берёт бот прикуп вслепую, как и
                // игрок, но взятое называют вслух.
                prikup = (move as? ThousandMove.TakePrikups)?.let { round.prikup(it.index) }.orEmpty(),
            )
            round.apply(BOT, move)
            // Звук хода и реплика бота стартуют в один момент и налезают
            // друг на друга. Разводим: сначала звук, потом речь. Молчащему
            // боту ждать незачем.
            val gap = soundGap(move)
            if (settings.botTalk && gap > 0) delay(gap + PHRASE_GAP_MS)
            sayBot(phrase + trickSuffix(round, tricksBefore))
            // Пишем после каждого хода: если приложение прибьют посреди
            // серии ходов бота, партия не откатится к её началу.
            session.persist()
            played = true
        }
        // Ход вернулся к игроку — короткий толчок: слышно не всегда, а тут
        // понятно без звука, что ждут тебя.
        if (played && settings.vibration && round.turn == PLAYER) vibrations.tap()
        finishIfOver()
        if (played) session.tick++
    }

    LaunchedEffect(Unit) {
        when {
            // Партия поднята с диска: раздачу объявлять не надо, надо
            // сказать, что за столом. Доигранный кон не объявляем — о нём
            // скажет итог, который вот-вот посчитается.
            session.restored -> {
                session.acceptRestored()
                if (session.round.phase != Phase.OVER) {
                    voice.say(resumePhrase(session.round, session.match), whenReady = true)
                }
            }

            session.lastPhrase.isBlank() -> voice.say(dealPhrase(session.round), whenReady = true)

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

    val round = session.round
    val match = session.match
    val moves = round.legalMoves(PLAYER)
    val yourTurn = moves.isNotEmpty()
    val bids = moves.filterIsInstance<ThousandMove.Bid>()
    val praises = moves.filterIsInstance<ThousandMove.Praise>()
    val prikups = moves.filterIsInstance<ThousandMove.TakePrikups>()

    val status = if (round.phase == Phase.OVER) {
        if (match.winner != null) "Партия окончена." else "Кон окончен."
    } else {
        buildString {
            append(
                when (round.phase) {
                    Phase.BIDDING ->
                        if (round.currentBid == 0) "Торг. Ставок нет." else "Торг. Ставка ${round.currentBid}."

                    Phase.PRIKUP ->
                        if (round.declarer == PLAYER) "Прикуп твой." else "Бот берёт прикуп."

                    Phase.DISCARD ->
                        if (round.declarer == PLAYER) "Снос." else "Бот сносит карту."

                    Phase.PLAY -> "Розыгрыш."

                    Phase.OVER -> ""
                },
            )
            append(if (yourTurn) " Твой ход." else " Ход бота.")
        }
    }

    val info = buildString {
        append("Кон ${match.roundsPlayed + 1}.")
        // Козырь в «Тысяче» — масть объявленного марьяжа: до первого
        // объявления его попросту нет, и молчать об этом честнее, чем
        // называть козырем что-то одно.
        round.trumpSuit?.let { append(" Козырь — ${it.spoken}.") }
        append(" Счёт: ты ${match.scores[PLAYER]}, бот ${match.scores[BOT]}.")
        if (match.barrelSeat == PLAYER) append(" Ты на бочке.")
        if (match.barrelSeat == BOT) append(" Бот на бочке.")
        if (round.phase == Phase.PLAY) {
            append(" Взяток: у тебя ${round.tricksOf(PLAYER)}, у бота ${round.tricksOf(BOT)}.")
            append(" Очков: ${round.roundPoints(PLAYER)} и ${round.roundPoints(BOT)}.")
        }
    }

    val table = round.tableNow()
    val tableLine = when {
        table.isNotEmpty() -> "Стол: " + table.joinToString(", ") { it.spoken() }
        round.phase == Phase.DISCARD && round.declarer == PLAYER -> "Нажми карту, которую снести."
        round.phase == Phase.PRIKUP && round.declarer == PLAYER -> "Возьми один прикуп из двух."
        round.phase == Phase.BIDDING -> "Торг: первое слово — сто, дальше по пять."
        else -> "Стол пуст."
    }

    // Порядок карт — тот, что выбран в настройках; старшинство тут своё
    // (десятка выше короля), поэтому и сортировка своя, из движка.
    val hand = orderHand(round.handOf(PLAYER), settings.order, round.trumpSuit)
    val playable: Set<EngineCard> = moves.mapNotNull { move ->
        when (move) {
            is ThousandMove.Play -> move.card
            is ThousandMove.Discard -> move.cards.firstOrNull()
            else -> null
        }
    }.toSet()

    // Карта, до которой игрок дошёл двумя пальцами. Это не выбор карты,
    // а её чтение: сыграть можно по-прежнему только нажатием. Сбрасывается
    // на каждом ходу — рука меняется, и старый номер в ней ничего не значит.
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

        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = status, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(info, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                Spacer(Modifier.height(4.dp))
                Text(tableLine, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
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
                        onClick = { tapCard(card) },
                    )
                }
            }
        }

        // --- Низ: панель по фазе, не прокручивается -----------------------

        Spacer(Modifier.height(8.dp))

        when {
            round.phase == Phase.OVER -> {
                Button(
                    onClick = { if (match.winner != null) newMatch() else nextRound() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (match.winner != null) "Новая партия" else "Дальше")
                }
                Spacer(Modifier.height(8.dp))
            }

            // Торг: каждая сумма — своя кнопка. Их не больше пяти, и все они
            // на виду: спрашивать «на сколько?» диалогом с ползунком значит
            // заставлять незрячего крутить то, что можно просто нажать.
            bids.isNotEmpty() -> {
                bids.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { bid ->
                            Button(
                                onClick = { play(bid) },
                                modifier = Modifier.weight(1f),
                            ) { Text("Назвать ${bid.amount}") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            prikups.isNotEmpty() -> {
                prikups.forEach { take ->
                    Button(
                        onClick = { play(take) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Взять прикуп ${take.index + 1}") }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Марьяж объявляют картой, которой и ходят, поэтому кнопка одна,
            // а не по кнопке на карту: карт с марьяжем на руке бывает две.
            praises.isNotEmpty() -> {
                Button(
                    onClick = {
                        val only = praises.singleOrNull()
                        if (only != null) play(only) else praiseOpen = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Хвалить марьяж") }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (moves.contains(ThousandMove.Pass)) {
            Button(onClick = { play(ThousandMove.Pass) }, modifier = Modifier.fillMaxWidth()) {
                Text("Пас")
            }
            Spacer(Modifier.height(8.dp))
        }

        // Роспись стоит отдельно от «Паса» и спрашивается подтверждением:
        // это не ход, а конец кона, и стоит он заказчику всего заказа.
        if (moves.contains(ThousandMove.Raspis)) {
            val share = ThousandRound.raspisShare(round.currentBid)
            Button(onClick = { raspisOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Расписаться")
            }
            Spacer(Modifier.height(8.dp))

            if (raspisOpen) {
                AlertDialog(
                    onDismissRequest = { raspisOpen = false },
                    title = { Text("Расписаться?") },
                    text = {
                        Text(
                            "Заказ ${round.currentBid} не играется: ты пишешь минус " +
                                "${round.currentBid}, бот плюс $share. Карты не доигрываются.",
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                raspisOpen = false
                                play(ThousandMove.Raspis)
                            },
                        ) { Text("Расписаться") }
                    },
                    dismissButton = {
                        TextButton(onClick = { raspisOpen = false }) { Text("Играю") }
                    },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Обе кнопки — вопрос игрока, а не событие за столом: он нажал
            // и ждёт ответа. Отвечаем своим голосом и в том случае, когда
            // за столом говорит скринридер.
            Button(
                onClick = { voice.sayRequested(allowedPhrase()) },
                modifier = Modifier.weight(1f),
            ) { Text("Что можно") }
            Button(
                onClick = { voice.sayRequested(session.lastPhrase) },
                modifier = Modifier.weight(1f),
            ) { Text("Повтори") }
        }
    }

    // Какой марьяж хвалим. Диалогом, а не выпадающим списком: скринридер
    // объявляет диалог целиком и сразу ставит в него фокус.
    if (praiseOpen) {
        AlertDialog(
            onDismissRequest = { praiseOpen = false },
            title = { Text("Хвалить марьяж") },
            text = {
                Column {
                    praises.forEach { praise ->
                        TextButton(
                            onClick = {
                                praiseOpen = false
                                play(praise)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Хвалить: ${praise.card.suit.spoken}") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { praiseOpen = false }) { Text("Отмена") }
            },
        )
    }

    // Редкое — под кнопкой «Ещё». Диалогом, а не выпадающим списком:
    // диалог скринридер объявляет целиком и сразу ставит в него фокус.
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
                    TextButton(onClick = { menuOpen = false; onRules() }) { Text("Правила") }
                    TextButton(onClick = { menuOpen = false; onSettings() }) { Text("Настройки") }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { menuOpen = false; newMatch() }) { Text("Новая партия") }
                    TextButton(onClick = { menuOpen = false; onExit() }) { Text("Выйти") }
                }
            },
        )
    }
}

/**
 * Ход бота вслух. Коротко: за столом не комментируют каждую карту, её
 * называют — и всё. Торг, прикуп и снос названы по имени, потому что это
 * не карты, а решения, и о них иначе не догадаться.
 *
 * Прикуп — исключение из краткости: [prikup] называет карты, которые бот
 * забрал. Взятый прикуп показывают обоим, а в открытую руку соперника не
 * заглядывают: не назовём сейчас — игрок не узнает этого никогда.
 */
private fun botPhrase(move: ThousandMove, prikup: List<EngineCard> = emptyList()): String = when (move) {
    is ThousandMove.Bid -> "Бот называет ${move.amount}."
    ThousandMove.Pass -> "Бот пасует."
    is ThousandMove.TakePrikups ->
        if (prikup.isEmpty()) "Бот берёт прикуп."
        else "Бот берёт прикуп: ${prikup.joinToString(", ") { it.spoken() }}."
    is ThousandMove.Discard -> "Бот снёс карту."
    is ThousandMove.Praise -> "Бот хвалит ${move.card.suit.spoken}. Козырь — ${move.card.suit.spoken}."
    ThousandMove.Raspis -> "Бот расписывается."
    is ThousandMove.Play -> "Бот кладёт ${move.card.spoken()}."
}

/** Свой ход — вслух. Не «сыграна карта», а живая речь, короткая. */
private fun ownPhrase(move: ThousandMove, prikup: List<EngineCard> = emptyList()): String = when (move) {
    is ThousandMove.Bid -> "Называешь ${move.amount}."
    ThousandMove.Pass -> "Ты пасуешь."
    is ThousandMove.TakePrikups ->
        if (prikup.isEmpty()) "Берёшь прикуп ${move.index + 1}."
        else "Берёшь прикуп ${move.index + 1}: ${prikup.joinToString(", ") { it.spoken() }}."
    is ThousandMove.Discard -> "Сносишь ${move.cards.joinToString(", ") { it.spoken() }}."
    is ThousandMove.Praise -> "Хвалишь ${move.card.suit.spoken}. Козырь — ${move.card.suit.spoken}."
    ThousandMove.Raspis -> "Расписываешься."
    is ThousandMove.Play -> "Кладёшь ${move.card.spoken()}."
}

private fun dealPhrase(round: ThousandRound): String {
    val first = if (round.turn == PLAYER) "Первое слово твоё." else "Первым называет бот."
    return "Раздача. Торг: первое слово — сто, дальше по пять. $first"
}

/**
 * Что сказать, когда партия поднята с диска. Игрок вернулся через час или
 * после случайного выхода и не помнит стол — напоминаем положение дел,
 * а не «продолжаем», за которым ничего не стоит.
 */
private fun resumePhrase(round: ThousandRound, match: ThousandMatch): String {
    val body = when (round.phase) {
        Phase.BIDDING ->
            if (round.currentBid == 0) "Торг, ставок нет." else "Торг, ставка ${round.currentBid}."

        Phase.PRIKUP -> "Прикуп за заказчиком."
        Phase.DISCARD -> "Заказчик сносит карту."
        Phase.PLAY ->
            "Розыгрыш. Взяток у тебя ${round.tricksOf(PLAYER)}, " +
                "очков ${round.roundPoints(PLAYER)}."

        Phase.OVER -> "Кон окончен."
    }
    val trump = round.trumpSuit?.let { " Козырь — ${it.spoken}." } ?: ""
    val turn = if (round.turn == PLAYER) " Твой ход." else " Ход бота."
    return "Продолжаем партию. $body$trump " +
        "Счёт: у тебя ${match.scores[PLAYER]}, у бота ${match.scores[BOT]}.$turn"
}

/**
 * Итог кона. Считается по матчу, а не по кону: болт, бочка и победа — это
 * то, что тянется дальше одного кона, и в самом коне их не видно.
 */
private fun roundPhrase(
    round: ThousandRound,
    summary: RoundSummary,
    match: ThousandMatch,
): String {
    val parts = mutableListOf<String>()
    val declarer = round.declarer
    val scribbler = summary.raspised
    if (scribbler != null) {
        // Тут не «набрал столько-то»: при росписи карты не доиграны, и
        // очки кона ни о чём не говорят. Говорим то, что случилось.
        val who = if (scribbler == PLAYER) "Ты расписался" else "Бот расписался"
        parts += "Кон окончен. $who: заказ ${round.currentBid} не играется."
    } else if (declarer != null) {
        val points = round.roundPoints(declarer)
        val who = if (declarer == PLAYER) "ты" else "бот"
        val done = if (points >= round.currentBid) "Заказ выполнен." else "Заказ не выполнен."
        parts += "Кон окончен. Заказ ${round.currentBid}, $who набрал $points. $done"
    } else {
        parts += "Кон окончен."
    }

    val writes = summary.deltas.mapIndexed { seat, delta ->
        val who = if (seat == PLAYER) "ты" else "бот"
        "$who ${writeWords(delta)}"
    }
    parts += "Записано: ${writes.joinToString(", ")}."

    summary.bolted.forEach { parts += if (it == PLAYER) "Болт тебе." else "Болт боту." }
    summary.barrelSat?.let {
        parts += if (it == PLAYER) "Ты садишься на бочку." else "Бот садится на бочку."
    }
    summary.barrelsDropped.forEach {
        parts += if (it == PLAYER) "Ты слетел с бочки." else "Бот слетел с бочки."
    }

    parts += "Счёт: у тебя ${match.scores[PLAYER]}, у бота ${match.scores[BOT]}."
    summary.winner?.let {
        parts += if (it == PLAYER) "Ты выиграл партию!" else "Бот выиграл партию."
    }
    return parts.joinToString(" ")
}

/** «+100» синтезатор читает как бог на душу положит, поэтому пишем словами. */
private fun writeWords(value: Int): String = when {
    value > 0 -> "плюс $value"
    value < 0 -> "минус ${-value}"
    else -> "ничего"
}
