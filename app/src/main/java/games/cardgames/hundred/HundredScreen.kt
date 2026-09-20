package games.cardgames.hundred

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import games.cardgames.GAME_HUNDRED
import games.cardgames.settings.botEngine
import games.cardgames.settings.botSpeaksAlone
import games.cardgames.settings.botVoice
import games.cardgames.settings.loadSettings
import games.cardgames.settings.seatTitles
import games.cardgames.sound.TableSounds
import games.cardgames.sound.Vibrations
import games.cardgames.speech.BotVoice
import games.cardgames.speech.FIRST_BOT_SEAT
import games.cardgames.speech.PHRASE_GAP_MS
import games.cardgames.speech.Speaker
import games.cardgames.speech.TURN_PHRASE
import games.cardgames.speech.TableVoice
import games.cardgames.speech.sayEvent
import games.cardgames.speech.speech
import games.cardgames.speech.verdictOf
import games.cardgames.speech.withoutTurn
import games.cardgames.ui.HandCard
import games.cardgames.ui.TableCards
import games.cardgames.ui.TableGesture
import games.cardgames.ui.tableGestures
import games.engine.Card
import games.engine.Rank
import games.engine.hundred.Hundred
import games.engine.hundred.HundredBot
import games.engine.hundred.HundredMove
import games.engine.hundred.points
import games.engine.hundred.queenPrice
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Место игрока за столом. Соперники сидят за остальными.
 *
 * Имя короткое, потому что встречается в каждой фразе за столом, а значение
 * берётся у сессии: экран и то, что пишется на диск, должны называть игрока
 * одним и тем же местом.
 */
private const val PLAYER = PLAYER_SEAT

/** Место второго соперника: садится за стол, когда мест трое. */
private const val SECOND_BOT = 2

/** Пауза перед ходом соперника, чтобы не тараторил. */
private const val BOT_DELAY_MS = 700L

/**
 * Сколько ходов подряд позволено сыграть соперникам за один заход. Считается
 * не ходами кона, а предохранителем: без него ошибка в правилах превратилась
 * бы в вечный цикл, из которого игроку не выйти.
 */
private const val BOT_GUARD = 400

/**
 * Размер карты в руке. В крупном режиме он умножается.
 *
 * Тот же, что в «Дураке»: карты за всеми столами одной величины, и рука,
 * привыкшая к одному размеру, не переучивается от игры к игре.
 */
private const val CARD_WIDTH = 64f
private const val CARD_HEIGHT = 92f
private const val LARGE_SCALE = 1.4f

/**
 * Карты на кону мельче рук: на кон смотрят мельком, а место нужно руке.
 * Крупнее, чем в «Дураке», — на кону здесь всегда одна карта, и она же
 * единственное, по чему ходят.
 */
private const val TABLE_SCALE = 0.75f

/**
 * Экран партии в «101».
 *
 * Раскладка та же, что за другими столами: нужное каждый ход не должно
 * уезжать за край. Карты лежат сеткой и прокручиваются только они; панель
 * действий закреплена внизу.
 *
 * Сверху — коротко: чей ход, счёт, сколько карт у кого и что на кону. Кон
 * показан и словами, и картой: слова — скринридеру, карта — тому, кто за
 * столом видит. Картинка молчит (у неё пустая семантика), так что дважды кон
 * не читается.
 *
 * Отличие от других столов одно, и оно из правил: кон кончается **внутри
 * хода**. Движок, приняв последнюю карту, тут же считает очки и раздаёт
 * следующий кон, поэтому от прежнего кона на столе не остаётся ничего — ни
 * рук, ни счёта, по которому видно разницу. Экран поэтому снимает снимок
 * кона **до** хода и рассказывает по нему, что случилось; см. [ConShot].
 */
@Composable
fun HundredScreen(
    session: HundredSession,
    onExit: () -> Unit,
    onSettings: () -> Unit,
    onRules: () -> Unit,
) {
    val context = LocalContext.current

    // Настройки читаем при каждом входе на экран: игрок мог ходить в них
    // прямо посреди партии, и партия от этого не должна пропасть.
    val settings = remember { loadSettings(context) }

    // Как звать сидящих за столом, по местам: место игрока — «ты», у прочих
    // своё имя. Число мест берём у матча, а не у настроек: матч помнит стол,
    // за которым его начали, и смена настройки посреди него имён не
    // переписывает.
    val names = seatTitles(settings, GAME_HUNDRED, session.match.playerCount)

    val speaker = remember(settings.engine, settings.voice, settings.rate) {
        Speaker(
            context = context,
            rate = settings.rate,
            enginePackage = settings.engine,
            voiceName = settings.voice,
        )
    }
    // Голос соперника — своим синтезатором: движок на живом синтезаторе на
    // ходу не поменять.
    val botSpeaker = remember(
        settings.engine,
        settings.botEngineHundred,
        settings.voice,
        settings.botVoiceHundred,
        settings.botRateHundred,
        settings.rate,
    ) {
        Speaker(
            context = context,
            rate = settings.botRateHundred,
            // Имя для журнала: за столом говорят три синтезатора, и запись
            // без имени читается как одна речь.
            title = "соперник",
            enginePackage = botEngine(settings.botEngineHundred, settings.engine),
            voiceName = botVoice(
                settings.botVoiceHundred,
                settings.voice,
                settings.botEngineHundred,
                settings.engine,
            ),
        )
    }
    // Голос второго соперника — только когда за столом трое: на двоих второй
    // синтезатор был бы движком, которого никто не слышит.
    val botSpeakerSecond = remember(
        settings.engine,
        settings.botEngineHundredSecond,
        settings.voice,
        settings.botVoiceHundredSecond,
        settings.botRateHundredSecond,
        settings.rate,
        session.match.playerCount,
    ) {
        if (session.match.playerCount < 3) {
            null
        } else {
            Speaker(
                context = context,
                rate = settings.botRateHundredSecond,
                title = "второй соперник",
                enginePackage = botEngine(settings.botEngineHundredSecond, settings.engine),
                voiceName = botVoice(
                    settings.botVoiceHundredSecond,
                    settings.voice,
                    settings.botEngineHundredSecond,
                    settings.engine,
                ),
            )
        }
    }
    // Соперник со своим синтезатором или своим голосом говорит им и при
    // работающем скринридере: тот озвучивает приложение, а соперник — своим
    // голосом (SETTINGS.md, 8).
    val botApart = botSpeaksAlone(
        settings.botVoiceHundred,
        settings.voice,
        settings.botEngineHundred,
        settings.engine,
    )
    val botSecondApart = botSpeaksAlone(
        settings.botVoiceHundredSecond,
        settings.voice,
        settings.botEngineHundredSecond,
        settings.engine,
    )
    val sounds = remember { TableSounds(context) }
    val vibrations = remember { Vibrations(context) }
    // Гасим каждый синтезатор порознь: второй заводится и пропадает вместе с
    // третьим местом за столом, а общий onDispose на всех погасил бы заодно и
    // те, что остались в работе.
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    DisposableEffect(botSpeaker) { onDispose { botSpeaker.shutdown() } }
    DisposableEffect(botSpeakerSecond) { onDispose { botSpeakerSecond?.shutdown() } }
    DisposableEffect(sounds) { onDispose { sounds.release() } }

    val rng = remember { Random.Default }
    // Открыто ли маленькое меню «Ещё» в углу экрана.
    var menuOpen by remember { mutableStateOf(false) }

    val scale = if (settings.largeText) LARGE_SCALE else 1f
    val cardWidth: Dp = (CARD_WIDTH * scale).dp
    val cardHeight: Dp = (CARD_HEIGHT * scale).dp
    val tableWidth: Dp = (CARD_WIDTH * TABLE_SCALE).dp
    val tableHeight: Dp = (CARD_HEIGHT * TABLE_SCALE).dp

    val speech = settings.voiceMode.speech(speaker.screenReaderOn)
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Вся речь за столом — через общий [TableVoice]: паузы, арбитраж с
    // скринридером и запись фразы для «Повтори» живут там, одни на все игры.
    val speechNow = rememberUpdatedState(speech)
    val rateNow = rememberUpdatedState(settings.rate)
    val voice = remember(speaker, botSpeaker, botSpeakerSecond, botApart, botSecondApart) {
        TableVoice(
            speaker = speaker,
            botSpeakers = buildMap {
                put(FIRST_BOT_SEAT, BotVoice(botSpeaker, botApart))
                botSpeakerSecond?.let { put(SECOND_BOT, BotVoice(it, botSecondApart)) }
            },
            view = view,
            speech = { speechNow.value },
            rate = { rateNow.value },
            scope = scope,
            minWaitMs = BOT_DELAY_MS,
            pauseMs = { settings.phrasePauseMs.toLong() },
            remember = { session.lastPhrase = it },
        )
    }

    /**
     * Ход соперника вслух. Реплики можно выключить, тогда он молча кладёт
     * карты; фразу всё равно запоминаем — её повторит кнопка «Повтори».
     *
     * Пустая фраза значит «говорить нечего»: соперник взял карту, и она
     * подошла — следом он ею и сходит, а называть вслух сам добор незачем
     * (HUNDRED_ONE.md, 2.3: пропуск хода — частое событие, и фраза о нём
     * короткая).
     */
    fun sayTurn(text: String, seat: Int? = null) {
        if (text.isBlank()) return
        session.lastPhrase = text
        if (!settings.botTalk) return
        if (seat == null) voice.say(text) else voice.sayBot(text, seat = seat)
    }

    /**
     * Чья это будет фраза и как её позвать.
     *
     * Имя нужно только там, где фразу читает скринридер, и только на троих: у
     * него один голос на всех, и «кладёт восьмёрку» без имени не говорит, кто
     * кладёт. Своим голосом соперник говорит о себе «я» — двоих различают
     * голоса, а не имя.
     */
    fun botLine(seat: Int, text: String): String =
        if (session.match.playerCount >= 3 && !voice.speaksAlone(seat)) {
            "${names[seat]}, ${text.replaceFirstChar { it.lowercase() }}"
        } else {
            text
        }

    fun soundFor(move: HundredMove) {
        if (!settings.sounds) return
        when (move) {
            is HundredMove.Play -> sounds.card()
            HundredMove.Draw -> sounds.take()
        }
    }

    /** Сколько играет звук этого хода. Ноль — звука нет, и ждать нечего. */
    fun soundGap(move: HundredMove): Long {
        if (!settings.sounds) return 0L
        return when (move) {
            is HundredMove.Play -> TableSounds.CARD_MS
            HundredMove.Draw -> TableSounds.TAKE_MS
        }.toLong()
    }

    /**
     * Ход игрока: фраза, звук, толчок — в одном месте.
     *
     * Снимок кона берётся до хода: после него от прежнего кона не остаётся
     * ничего, и рассказать про него будет уже нечем.
     */
    fun play(move: HundredMove) {
        val game = session.match
        soundFor(move)
        if (settings.ownVibration) vibrations.tap()

        val shot = ConShot.take(game, seat = PLAYER, move = move)
        val turnoversBefore = game.stockTurnovers()
        game.apply(PLAYER, move)

        val text = buildString {
            append(ownMovePhrase(move, game))
            if (shot != null) {
                append(" ")
                append(conPhrase(shot, game, names))
                append(" ")
                append(afterConPhrase(game, names))
            } else if (game.stockTurnovers() > turnoversBefore) {
                append(" ")
                append(TURNOVER_PHRASE)
            }
        }
        // Свой ход звучит так же, как у соперника: сначала карта, потом слово.
        val gap = soundGap(move)
        voice.sayOwnMove(
            text,
            afterMs = if (gap > 0) gap + PHRASE_GAP_MS else 0L,
            // Про колоду скринридер не расскажет: он прочитал нажатую кнопку,
            // а не то, что из колоды пришло.
            aloud = move is HundredMove.Draw,
        )
        session.persist()
        session.tick++
    }

    fun playCard(card: Card) {
        val game = session.match
        val move = game.legalMoves(PLAYER)
            .filterIsInstance<HundredMove.Play>()
            .firstOrNull { it.card == card }
        if (move == null) {
            // Отказ — ответ на нажатие, а не событие за столом: игрок ждёт
            // его сразу, поэтому говорим своим голосом.
            voice.sayRequested(refusalPhrase(game, names))
            return
        }
        play(move)
    }

    fun newMatch() {
        session.restart()
        if (settings.sounds) sounds.deal()
        if (settings.signals) sounds.start()
        val afterMs = if (settings.sounds) TableSounds.DEAL_MS + PHRASE_GAP_MS else 0L
        voice.say(dealPhrase(session.match, names, withScores = true), afterMs = afterMs)
    }

    // За неигровые места играем подряд, пока ход не вернётся к игроку: на
    // троих это два хода подряд, и оба — не его. Кон кончился — считаем его,
    // объявляем итог и играем следующий, пока матч не кончится.
    LaunchedEffect(session.tick) {
        var played = false
        var guard = 0
        // Матч для игрока кончается и тогда, когда он выбыл, — а движок к
        // этому времени ещё играет за оставшихся. Слушать, как двое соперников
        // доигрывают без тебя, игроку незачем (HUNDRED_ONE.md, 2.8).
        while (guard++ < BOT_GUARD && !session.overForPlayer) {
            val game = session.match
            val seat = game.turn
            if (seat == PLAYER) break

            // Ждём не «полсекунды», а пока договорит предыдущая фраза: иначе
            // соперник перебивает сам себя и слышно только последнее слово.
            delay(voice.waitMs())
            // Матч мог кончиться, пока мы ждали: фразу договорили — и хватит.
            if (session.overForPlayer || session.match.turn == PLAYER) break

            val move = HundredBot.chooseMove(
                game,
                seat,
                settings.botDifficultyHundred,
                rng,
            ) ?: break

            val shot = ConShot.take(game, seat, move)
            val turnoversBefore = game.stockTurnovers()
            soundFor(move)
            game.apply(seat, move)
            played = true
            session.persist()

            // Звук хода и реплика стартуют в один момент и налезают друг на
            // друга. Разводим: сначала звук, потом речь.
            val gap = soundGap(move)
            if (gap > 0) delay(gap + PHRASE_GAP_MS)

            val text = buildString {
                append(botMovePhrase(game, seat, move, names))
                if (shot != null) {
                    append(" ")
                    append(conPhrase(shot, game, names))
                    append(" ")
                    append(afterConPhrase(game, names))
                } else if (game.stockTurnovers() > turnoversBefore) {
                    append(" ")
                    append(TURNOVER_PHRASE)
                }
            }
            if (text.isNotBlank()) {
                sayTurn(botLine(seat, text.trim()), seat = seat)
            } else {
                session.lastPhrase = text
            }
        }

        val game = session.match
        val mine = !session.overForPlayer && game.turn == PLAYER

        // Ход вернулся к игроку — короткий толчок и тихая нота: толчок слышно
        // не всегда, а тут понятно без слов, что ждут тебя.
        if (played && mine) {
            if (settings.vibration) vibrations.tap()
            if (settings.signals) sounds.turn()
        }
        if (played) session.tick++

        // Кон перед твоим ходом: соперники называют карты по одной, и по одной
        // они складываются в картину; когда один из них промолчал или взял из
        // колоды — нет.
        //
        // Свежую раздачу не называем: о ней только что сказала [dealPhrase].
        if (played && mine && game.pileSize() > 1) {
            voice.say(turnPhrase(game), afterMs = voice.waitMs())
        }
    }

    LaunchedEffect(Unit) {
        when {
            // Матч поднят с диска: раздачу объявлять не надо, надо сказать,
            // что за столом, и чей ход.
            session.restored -> {
                session.acceptRestored()
                voice.say(resumePhrase(session.match, names), whenReady = true)
            }

            session.lastPhrase.isBlank() -> {
                voice.say(dealPhrase(session.match, names, withScores = true), whenReady = true)
            }

            // Вернулись с другого экрана — напоминаем, на чём остановились.
            // lastPhrase не трогаем: «Продолжаем» — это не фраза для
            // «Повтори», повторять надо ход, а не переход.
            else -> sayEvent(
                view = view,
                speaker = speaker,
                speech = speech,
                text = "Продолжаем. " + session.lastPhrase,
                whenReady = true,
            )
        }
    }

    val game = session.match
    val moves = game.legalMoves(PLAYER)

    val status = buildString {
        append(
            when {
                session.overForPlayer -> "Матч кончен."
                moves.isNotEmpty() -> "Твой ход."
                // «Ход соперника», а не по имени: «ход Пети» — падеж, а
                // склонять произвольное имя программа не умеет; на троих же
                // «соперник» не говорит, кого ждать, поэтому там имя стоит
                // подлежащим — «Ходит Петя» (SETTINGS.md, 8).
                game.playerCount <= 2 -> "Ход соперника."
                else -> "Ходит ${names[game.turn]}."
            },
        )
        append(" У тебя ${game.handSize(PLAYER)}.")
        append(" В колоде ${game.stockSize()}.")
        if (game.playerCount <= 2) {
            append(" У соперника ${game.handSize(FIRST_BOT_SEAT)}.")
        } else {
            append(
                " Соперники: " + (FIRST_BOT_SEAT until game.playerCount).joinToString(", ") {
                    "${names[it]} ${game.handSize(it)}"
                } + ".",
            )
        }
    }

    // Счёт матча. За столом на троих перечисляется по местам: «у соперника»
    // уже не говорит, у кого сколько.
    val scoreLine = "Счёт: " + scorePhrase(game, names) + ". До ${Hundred.HUNDRED_ONE}."

    // Карт на кону всегда одна, и по ней ходят, поэтому называем её и
    // картами, и словами. Про «подходят черви и семёрки» говорим только тогда,
    // когда решать сейчас нам: во время чужого хода это ответ не на вопрос, а
    // шум.
    //
    // Подходят ли — спрашиваем у правил, а не договариваем по карте на кону.
    // Написать «подходят черви и семёрки» при пустой на них руке — это тот же
    // промах, что молчание в «Козле»: игрок идёт щупать руку за ответом,
    // которого за столом нет. Подходящих нет — так и говорим, и сразу
    // подсказываем выход: ход у него ровно один, и он не в руке.
    val top = game.topCard()
    val конLine = when {
        top == null -> "Кон пуст."
        game.turn != PLAYER || session.overForPlayer -> "Кон: ${top.spoken()}."
        game.playable(PLAYER).isEmpty() ->
            "Кон: ${top.spoken()}. Подходящих нет, тянешь из колоды."

        else -> "Кон: ${top.spoken()}. Подходят ${top.suit.spoken} и ${rankName(top.rank)}."
    }

    // Порядок карт — ровно тот, что выбран в настройках, и ничего поверх.
    // В «101» козыря нет, поэтому порядок спрашивается у движка без масти.
    val playable: Set<Card> = moves.filterIsInstance<HundredMove.Play>().map { it.card }.toSet()

    // Молчим о вердикте, только когда решать сейчас не нам.
    val deciding = !session.overForPlayer && game.turn == PLAYER
    fun verdictFor(card: Card): Boolean? = if (deciding) card in playable else null

    val hand = settings.order.sort(game.handOf(PLAYER))
    val columns = if (settings.largeText) 3 else 4

    // Карта, до которой игрок дошёл двумя пальцами. Это не выбор карты, а её
    // чтение: сыграть можно по-прежнему только нажатием. Сбрасывается на
    // каждой раздаче — рука меняется, и старый номер в ней ничего не значит.
    var cursor by remember(hand) { mutableStateOf(-1) }

    /**
     * Шаг по руке вправо-влево. С какого места ни начни — карта называется
     * целиком: тем, кто слушает, номер без названия не говорит ничего, а
     * «подходит или нет» — это и есть ответ на вопрос «чем мне ходить».
     */
    fun walkHand(step: Int) {
        if (hand.isEmpty()) {
            voice.say("Карт на руке нет.")
            return
        }
        val next = if (cursor < 0) {
            if (step > 0) 0 else hand.size - 1
        } else {
            (cursor + step + hand.size) % hand.size
        }
        cursor = next
        val card = hand[next]
        voice.say("${next + 1} из ${hand.size}: ${card.spoken()}${verdictOf(card, playable)}.")
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
                Text(scoreLine, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(
                    конLine,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                // И тот же кон картой: слова — скринридеру, карта — тому, кто
                // за столом видит. Карта молчит, так что дважды кон не читается.
                game.topCard()?.let { top ->
                    Spacer(Modifier.height(6.dp))
                    TableCards(listOf(top), cardWidth = tableWidth, cardHeight = tableHeight)
                }
                Spacer(Modifier.height(4.dp))
                // Без слов о том, чей ход: они уже стоят в строке состояния
                // выше, и повторять их дважды незачем — а сказанное вслух
                // «Твой ход» несёт переход, и его повторяет «Повтори».
                Text(
                    withoutTurn(session.lastPhrase),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
            }
            TextButton(
                onClick = { menuOpen = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("Ещё", style = MaterialTheme.typography.bodySmall)
            }
        }

        // --- Середина: рука сеткой, скроллится только она -----------------

        // Рука лежит на своём фоне, а кон остаётся голым: глазу видно, где
        // кончается стол и начинаются твои карты, и одно от другого не
        // сливается. Порядка и голоса это не трогает — только вид.
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(hand) { index, card ->
                    HandCard(
                        card = card,
                        playable = verdictFor(card),
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        selected = cursor == index,
                        onClick = { playCard(card) },
                    )
                }
            }
        }

        // --- Низ: панель действий, не прокручивается ----------------------

        Spacer(Modifier.height(8.dp))

        if (session.overForPlayer) {
            Button(onClick = { newMatch() }, modifier = Modifier.fillMaxWidth()) { Text("Ещё раз") }
            Spacer(Modifier.height(8.dp))
        }

        // Ход в «101» один из двух, и второй — добор. Кнопка одна и гасится,
        // а не пропадает: исчезающая кнопка сдвигает соседние, и рука каждый
        // раз ищет их заново.
        Button(
            onClick = { play(HundredMove.Draw) },
            enabled = moves.contains(HundredMove.Draw),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Из колоды") }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Обе кнопки — вопрос игрока, а не событие за столом: он нажал и
            // ждёт ответа. Отвечает тот же, кто говорит и за столом.
            //
            // Имя кнопки живёт внутри неё, а не рядом: имя, положенное на
            // кнопку снаружи, до неё не доходит, и скринридер читает «без
            // метки» — проверено на телефоне.
            Button(
                onClick = { voice.sayRequested(allowedPhrase(game, names)) },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    "Что можно",
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "Что можно" },
                )
            }
            Button(
                onClick = { voice.sayRequested(session.lastPhrase) },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    "Повтори",
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "Повтори" },
                )
            }
        }
    }

    // Редкое — под кнопкой «Ещё». Диалогом, а не выпадающим списком:
    // диалог скринридер объявляет целиком и сразу ставит в него фокус,
    // а в выпадашке незрячий может не понять, что вообще что-то открылось.
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
                    TextButton(onClick = { menuOpen = false; newMatch() }) { Text("Новая партия") }
                    TextButton(onClick = { menuOpen = false; onExit() }) { Text("Выйти") }
                }
            },
        )
    }
}

// --- Фразы за столом ------------------------------------------------------

/**
 * Снимок кона **до** хода, которым кон может кончиться.
 *
 * Нужен потому, что движок, приняв последнюю карту, тут же считает очки и
 * раздаёт следующий кон. После хода от прежнего кона на столе не остаётся
 * ничего: ни рук, за которые записаны очки, ни прежнего счёта, по которому
 * видно, кому что прибавилось. Поэтому экран снимает всё это заранее — и
 * только по ходу, которым кон и правда кончается: снимок стоит одной руки и
 * одного счёта, но брать их на каждый ход незачем.
 */
private class ConShot(
    /** Кто вышел — за ним и кон. */
    val seat: Int,
    /** Карта, которой вышли. По ней видно даму на выходе. */
    val card: Card,
    /** Руки всех мест до хода: по ним считают очки за оставшиеся карты. */
    val hands: List<List<Card>>,
    /** Счёт до кона: по нему отличают обнуление от первого нуля. */
    val scores: List<Int>,
    /** Кто уже был вне матча до этого кона. */
    val out: List<Int>,
) {
    companion object {
        /**
         * Снимок — или null, если этот ход кон не кончает.
         *
         * Кон кончается ровно одним ходом: картой, которая была в руке
         * последней. Спрашивать об этом движок не о чем — он к тому времени
         * уже раздал следующий, — поэтому признак берём из правил: рука была
         * в одну карту, и карта сыграна.
         */
        fun take(game: Hundred, seat: Int, move: HundredMove): ConShot? {
            val hand = game.handOf(seat)
            if (move !is HundredMove.Play || hand.size != 1) return null
            return ConShot(
                seat = seat,
                card = move.card,
                hands = (0 until game.playerCount).map { game.handOf(it) },
                scores = game.scoresAll(),
                out = game.outSeats(),
            )
        }
    }
}

/** Колода кончилась — это событие объявляется вслух обязательно (HUNDRED_ONE.md, 2.4). */
private const val TURNOVER_PHRASE =
    "Колода кончилась — стопку переворачиваем, играем ей дальше."

/**
 * Свой ход — вслух. Короткая живая речь, как за настоящим столом: свой ход
 * повторяет то, что игрок только что сделал сам, поэтому здесь важнее
 * точность, чем разнообразие.
 */
private fun ownMovePhrase(move: HundredMove, game: Hundred): String = when (move) {
    is HundredMove.Play -> "Ты кладёшь ${move.card.spoken()}."

    // Взятую карту называем вслух и всегда: она пришла из закрытой колоды, и
    // кроме этих слов игрок о ней не узнает ничего. Назвать карту мало:
    // игроку в этот момент нужен ответ на один вопрос — нашлась или нет. Без
    // него он идёт щупать руку заново, чтобы выяснить то, что приложение и
    // так знает, — а под палец ему попадутся и старые карты, те, которыми не
    // пройти (HUNDRED_ONE.md, 3.2).
    HundredMove.Draw -> {
        val drawn = game.handOf(PLAYER).lastOrNull()
        val top = game.topCard()
        val fits = drawn != null && top != null && game.fits(drawn, top)
        "Ты берёшь из колоды: ${drawn?.spoken()}." +
            if (fits) " Подошла — ходи ею." else " Не подошла, ход переходит."
    }
}

/**
 * Ход соперника — вслух. Пусто — говорить нечего: он взял карту, и она
 * подошла, а следом он ею и сходит (HUNDRED_ONE.md, 2.3).
 */
private fun botMovePhrase(
    game: Hundred,
    seat: Int,
    move: HundredMove,
    names: List<String>,
): String = when (move) {
    is HundredMove.Play -> "${names[seat]} кладёт ${move.card.spoken()}."
    HundredMove.Draw -> if (game.turn == seat) "" else "${names[seat]} пропускает ход."
}

/**
 * Кон кончился: кто его взял, что записали, чем вышли.
 *
 * Порядок здесь не случаен и переставлять его нельзя. Сначала — чей кон,
 * потом — за что записали, потом — дама на выходе, потом — обнуление и
 * выбывание, и только в самом конце — счёт. Счёт стоит последним, потому что
 * читается он из движка уже после всех этих правил: назвав его раньше, мы
 * сказали бы число, которое следующей же фразой пришлось бы поправить.
 */
private fun conPhrase(shot: ConShot, game: Hundred, names: List<String>): String {
    val parts = mutableListOf<String>()

    parts += if (shot.seat == PLAYER) {
        "Ты выходишь последней картой и забираешь кон."
    } else {
        "${names[shot.seat]} выходит последней картой и забирает кон."
    }

    // Очки за оставшиеся карты. Свою руку называем по картам, чужую — нет:
    // за чужим столом карт не видно, и перечислять их значило бы рассказать
    // то, чего за столом не рассказывают.
    val mine = shot.hands[PLAYER]
    if (shot.seat != PLAYER && mine.isNotEmpty()) {
        parts += "Считаем: у тебя ${speakList(mine.map { it.spoken() })} — ${pointsOf(mine)}."
    } else if (shot.seat == PLAYER) {
        parts += "Тебе за этот кон не записано."
    }

    if (shot.card.rank == Rank.QUEEN) {
        val price = queenPrice(shot.card.suit)
        parts += if (shot.seat == PLAYER) {
            "Дама на выходе — ${shot.card.spoken()}: минус $price тебе, счёт соперников удваивается."
        } else {
            "Дама на выходе — ${shot.card.spoken()}: минус $price вышедшему, твой счёт удваивается."
        }
    }

    // Ровно 101 — обнуление, больше — выбывание. Разные события, и на слух их
    // надо различать (HUNDRED_ONE.md, 2.7).
    for (seat in 0 until game.playerCount) {
        if (seat in shot.out) continue
        val now = game.scoreOf(seat)
        when {
            game.isOut(seat) -> parts += if (seat == PLAYER) {
                "Тебе $now — ты выбываешь из матча."
            } else {
                "${names[seat]} $now — выбывает из матча."
            }

            // Ноль после кона может значить и «счёт обнулили», и «играл
            // нулевыми картами». Различаем по прежнему счёту: у кого он был не
            // нулевой, а стал нулевым — тому обнулили ровно 101.
            now == 0 && shot.scores[seat] != 0 -> parts += if (seat == PLAYER) {
                "Тебе ровно 101 — счёт обнуляется, играешь дальше."
            } else {
                "${names[seat]} ровно 101 — счёт обнуляется, играет дальше."
            }
        }
    }

    parts += "Счёт: ${scorePhrase(game, names)}."
    return parts.joinToString(" ")
}

/**
 * Что сказать после кона: новая раздача или конец матча.
 *
 * Счёт здесь больше не называем: его только что назвала [conPhrase], и второй
 * раз подряд он звучал бы как два разных счёта.
 */
private fun afterConPhrase(game: Hundred, names: List<String>): String = when {
    game.isOut(PLAYER) -> matchPhrase(game, names)
    game.finished -> matchPhrase(game, names)
    else -> dealPhrase(game, names, withScores = false)
}

/** Итог матча прямой фразой. Прибаутка придёт второй, отдельной. */
private fun matchPhrase(game: Hundred, names: List<String>): String = when {
    // Игрок выбыл — матч для него кончен, даже если за столом ещё играют.
    game.isOut(PLAYER) -> "Ты выбываешь из матча. Счёт: ${scorePhrase(game, names)}."
    game.winner == PLAYER -> "Матч кончен. За столом остаёшься ты."
    game.winner != null -> "Матч кончен. За столом остаётся ${names[game.winner!!]}."
    else -> "Матч кончен."
}

/**
 * Раздача: сколько карт, что на кону, чей счёт и кто заходит.
 *
 * [withScores] выключается на раздаче после кона: счёт там только что
 * прозвучал вместе с итогом кона, и повторять его подряд незачем.
 */
private fun dealPhrase(game: Hundred, names: List<String>, withScores: Boolean): String =
    buildString {
        val dealer = game.dealer()
        // Сдатчик раздаёт всем по пять, а себе берёт четыре: пятая его карта
        // ложится на кон и считается его первым ходом (HUNDRED_ONE.md, 2.1).
        // Об этом стоит сказать: у сдатчика рука на карту короче, и без
        // предупреждения это читается как пропажа.
        append(
            if (dealer == PLAYER) {
                "Новая раздача. Ты сдаёшь: у тебя четыре карты, пятая легла на кон."
            } else {
                "Новая раздача. По пять карт, сдаёт ${names[dealer]}."
            },
        )
        game.topCard()?.let { append(" На кону — ${it.spoken()}.") }
        if (withScores) append(" Счёт: ${scorePhrase(game, names)}.")
        append(
            if (game.turn == PLAYER) " Заходишь ты." else " Заходит ${names[game.turn]}.",
        )
    }

/**
 * Кон перед твоим ходом. Свежую раздачу так не объявляют: о ней сказала
 * [dealPhrase], а «твой ход» без подходящих карт — это тупик, из которого
 * игроку надо подсказать выход.
 */
private fun turnPhrase(game: Hundred): String {
    val top = game.topCard() ?: return TURN_PHRASE
    val fitting = game.playable(PLAYER)
    return if (fitting.isEmpty()) {
        "Твой ход. На кону ${top.spoken()}. Подходящих нет, тянешь карту."
    } else {
        "Твой ход. На кону ${top.spoken()}."
    }
}

/**
 * Что можно сделать прямо сейчас — ответ на кнопку «Что можно».
 *
 * Больше трёх карт подряд не называем: лента из пяти названий не
 * удерживается, а рука всё равно слушается по одной (HUNDRED_ONE.md, 3.2).
 */
private fun allowedPhrase(game: Hundred, names: List<String>): String {
    if (game.isOut(PLAYER) || game.finished) return "Матч кончен."
    if (game.turn != PLAYER) {
        return if (game.playerCount <= 2) {
            "Сейчас ход соперника, подожди."
        } else {
            "Сейчас ходит ${names[game.turn]}, подожди."
        }
    }

    val fitting = game.playable(PLAYER)
    if (fitting.isEmpty()) return "Подходящих нет, тянешь карту."

    val named = fitting.take(3)
    val tail = if (fitting.size <= 3) {
        ""
    } else {
        ", и ещё ${countWord(fitting.size - 3)}, пройди по руке"
    }
    return "Можно: ${speakList(named.map { it.spoken() })}$tail."
}

/** Почему картой не сыграть — ответ на нажатие. */
private fun refusalPhrase(game: Hundred, names: List<String>): String = when {
    game.isOut(PLAYER) || game.finished -> "Матч кончен."
    game.turn != PLAYER -> if (game.playerCount <= 2) {
        "Сейчас ход соперника, подожди."
    } else {
        "Сейчас ходит ${names[game.turn]}, подожди."
    }

    // Подходящей карты нет вовсе — тогда это не «эта не подходит», а «нечем
    // ходить», и выход у игрока один.
    game.playable(PLAYER).isEmpty() -> "Подходящих нет, тянешь карту."

    else -> "Этой картой не пройти: нужна та же масть или то же достоинство."
}

/** Что сказать, когда матч поднят с диска. */
private fun resumePhrase(game: Hundred, names: List<String>): String {
    val top = game.topCard()
    val table = if (top == null) "" else " На кону — ${top.spoken()}."
    return "Продолжаем партию. Счёт: ${scorePhrase(game, names)}." +
        " У тебя ${game.handSize(PLAYER)} карт, в колоде ${game.stockSize()}.$table " +
        if (game.turn == PLAYER) TURN_PHRASE else "Ходит ${names[game.turn]}."
}

// --- Мелочи речи ----------------------------------------------------------

/**
 * Место за столом и число при нём — без падежа: «тебе 34», «Бот 57».
 *
 * Как в «Тысяче» и «Козле»: «у тебя 34» требует падежа и у имени, а его
 * программа не знает. Дательный для игрока известен всегда, поэтому предлог
 * достаётся только ему: имя соперника остаётся названием при числе.
 */
private fun seatScore(seat: Int, names: List<String>, value: String): String =
    if (seat == PLAYER) "тебе $value" else "${names[seat]} $value"

/** Счёт за столом — по всем местам, а не по двум. */
private fun scorePhrase(game: Hundred, names: List<String>): String =
    (0 until game.playerCount).joinToString(", ") {
        seatScore(it, names, "${game.scoreOf(it)}")
    }

/** Очки за карты, оставшиеся на руке: «3 очка», «1 очко», «7 очков». */
private fun pointsOf(cards: List<Card>): String {
    val value = points(cards)
    return "$value ${pointWord(value)}"
}

private fun pointWord(value: Int): String {
    val ten = value % 100
    if (ten in 11..14) return "очков"
    return when (value % 10) {
        1 -> "очко"
        2, 3, 4 -> "очка"
        else -> "очков"
    }
}

/** Название карты числом: «ещё две», «ещё пять». До пяти — словами. */
private fun countWord(count: Int): String = when (count) {
    1 -> "одна"
    2 -> "две"
    3 -> "три"
    4 -> "четыре"
    5 -> "пять"
    else -> "$count"
}

/** Достоинство во множественном числе: «подходят черви и семёрки». */
private fun rankName(rank: Rank): String = when (rank) {
    Rank.SIX -> "шестёрки"
    Rank.SEVEN -> "семёрки"
    Rank.EIGHT -> "восьмёрки"
    Rank.NINE -> "девятки"
    Rank.TEN -> "десятки"
    Rank.JACK -> "валеты"
    Rank.QUEEN -> "дамы"
    Rank.KING -> "короли"
    Rank.ACE -> "тузы"
}

/** Перечисление вслух: «дама бубён, девятка пик и семёрка червей». */
private fun speakList(parts: List<String>): String = when (parts.size) {
    0 -> ""
    1 -> parts[0]
    else -> parts.dropLast(1).joinToString(", ") + " и " + parts.last()
}
