package games.cardgames.kozel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import games.cardgames.GAME_KOZEL
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
import games.cardgames.speech.spokenVerdict
import games.cardgames.speech.withoutTurn
import games.cardgames.ui.HandTile
import games.cardgames.ui.TileFace
import games.cardgames.ui.TableGesture
import games.cardgames.ui.tableFeel
import games.cardgames.ui.tableGestures
import games.engine.kozel.End
import games.engine.kozel.KozelBot
import games.engine.kozel.KozelMove
import games.engine.kozel.KozelRound
import games.engine.kozel.KozelSummary
import games.engine.kozel.KozelView
import games.engine.kozel.laid
import games.engine.tiles.Tile
import games.engine.tiles.pipsName
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Место игрока за столом. Соперники сидят за остальными. */
private const val PLAYER = 0

/** Место второго соперника: садится за стол, когда мест трое. */
private const val SECOND_BOT = 2

/** Пауза перед ходом бота, чтобы не тараторил. */
private const val BOT_DELAY_MS = 700L

/**
 * Сколько ходов подряд позволено сыграть боту за один заход. Считается не
 * ходами раунда, а предохранителем: без него ошибка в правилах превратилась
 * бы в вечный цикл, из которого игроку не выйти.
 */
private const val BOT_GUARD = 200

/**
 * Размер кости в руке. В крупном режиме он умножается.
 *
 * Кость крупнее прежней: по ней щупают пальцем, и чем мельче кость, тем чаще
 * палец попадает не туда, куда шёл (Катерина, 19.09). Ширина и высота растут
 * вместе — отношение 2:1 держим, как у настоящей кости домино.
 */
private const val TILE_WIDTH = 104f
private const val TILE_HEIGHT = 52f
private const val LARGE_SCALE = 1.4f

/**
 * Кости на столе мельче рук, и в крупном тексте они не растут: на линию
 * смотрят мельком, а место нужно руке.
 */
private const val TABLE_SCALE = 0.62f

/**
 * Промежуток между костями на столе. Он же — шаг, по которому палец находит
 * кость под собой, поэтому задан здесь, а не в раскладке: линия и то, что
 * под пальцем, обязаны считать его одинаково (см. `tableFeel`).
 */
private val TABLE_GAP = 4.dp

/** Высота кости к её ширине: у стола кость вдвое длиннее, чем выше. */
private const val TABLE_ASPECT = TILE_HEIGHT / TILE_WIDTH

/**
 * Экран партии в «Козла».
 *
 * Раскладка подчинена тому же правилу, что и в «Дураке»: нужное каждый ход
 * не должно уезжать за край. Кости лежат сеткой в две колонки и прокручиваются
 * только они; панель действий закреплена внизу.
 *
 * Сверху — коротко: чей ход, счёт матча, сколько костей где и линия. Линия
 * показана и словами, и костями: слова — скринридеру, кости — тому, кто за
 * столом видит. Рисунок молчит (у него пустая семантика), так что дважды
 * линия не читается.
 */
@Composable
fun KozelScreen(
    session: KozelSession,
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
    val names = seatTitles(settings, GAME_KOZEL, session.match.seats)

    // Места соперников за этим столом. На троих их двое.
    val botSeats = (FIRST_BOT_SEAT until session.match.seats).toList()

    val speaker = remember(settings.engine, settings.voice, settings.rate) {
        Speaker(
            context = context,
            rate = settings.rate,
            enginePackage = settings.engine,
            voiceName = settings.voice,
        )
    }
    // Голос соперника — своим синтезатором: движок на живом синтезаторе на
    // ходу не поменять. Своего голоса боту не выбрали — говорим голосом
    // приложения: разводить соперника с ним положено синтезатором, а не
    // высотой (Катерина, 19.09: «убери, чтобы повышение голоса было у каждого
    // бота»).
    val botSpeaker = remember(
        settings.engine,
        settings.botEngineKozel,
        settings.voice,
        settings.botVoiceKozel,
        settings.botRateKozel,
        settings.rate,
    ) {
        Speaker(
            context = context,
            rate = settings.botRateKozel,
            enginePackage = botEngine(settings.botEngineKozel, settings.engine),
            voiceName = botVoice(
                settings.botVoiceKozel,
                settings.voice,
                settings.botEngineKozel,
                settings.engine,
            ),
        )
    }
    // Голос второго соперника — только когда за столом трое: на двоих второй
    // синтезатор был бы движком, которого никто не слышит.
    val botSpeakerSecond = remember(
        settings.engine,
        settings.botEngineKozelSecond,
        settings.voice,
        settings.botVoiceKozelSecond,
        settings.botRateKozelSecond,
        settings.rate,
        session.match.seats,
    ) {
        if (session.match.seats < 3) {
            null
        } else {
            Speaker(
                context = context,
                rate = settings.botRateKozelSecond,
                enginePackage = botEngine(settings.botEngineKozelSecond, settings.engine),
                voiceName = botVoice(
                    settings.botVoiceKozelSecond,
                    settings.voice,
                    settings.botEngineKozelSecond,
                    settings.engine,
                ),
            )
        }
    }
    // Бот со своим синтезатором или своим голосом говорит им и при работающем
    // скринридере: тот озвучивает приложение, а соперник — своим голосом
    // (SETTINGS.md, 8).
    val botApart = botSpeaksAlone(
        settings.botVoiceKozel,
        settings.voice,
        settings.botEngineKozel,
        settings.engine,
    )
    val botSecondApart = botSpeaksAlone(
        settings.botVoiceKozelSecond,
        settings.voice,
        settings.botEngineKozelSecond,
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
    // Речь бота живёт выше партии: мешки зачинов должны переживать и
    // перерисовки, и переходы в настройки, иначе повторы вернутся.
    val talker = remember { KozelTalker(rng) }
    // Открыто ли маленькое меню «Ещё» в углу экрана.
    var menuOpen by remember { mutableStateOf(false) }
    // Кость, которую можно положить на любой конец: спрашиваем, на какой.
    var endOpen by remember { mutableStateOf<Tile?>(null) }

    val scale = if (settings.largeText) LARGE_SCALE else 1f
    val tileWidth: Dp = (TILE_WIDTH * scale).dp
    val tileHeight: Dp = (TILE_HEIGHT * scale).dp
    // Кость на столе — предел, а не размер: длинная линия ужимает кости
    // сама, чтобы поместиться на экран (см. раскладку стола).
    val tableWidth: Dp = (TILE_WIDTH * TABLE_SCALE).dp

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
            remember = { session.lastPhrase = it },
        )
    }

    /**
     * Ход бота: реплики можно выключить, тогда он молча кладёт кости.
     * [speak] = false — бот промолчал по своей воле (см. [KozelTalker]); фразу
     * всё равно запоминаем, её повторит кнопка «Повтори».
     */
    fun sayBotMove(seat: Int, text: String, speak: Boolean = true) {
        if (!settings.botTalk || !speak) {
            session.lastPhrase = text
            return
        }
        voice.sayBot(text, seat = seat)
    }

    /**
     * Чья это будет фраза и как её позвать.
     *
     * Имя нужно только там, где фразу читает скринридер, и только на троих: у
     * него один голос на всех, и «кладу три-три» без имени не говорит, кто
     * кладёт (Катерина, 19.09: «чтобы каждому боту можно было своё имя давать,
     * например ходит Петя, ходит Вася»). Своим голосом бот говорит о себе «я»
     * — двоих соперников различают голоса, а не имя. За столом на двоих
     * соперник один и различать нечего.
     */
    fun botLine(seat: Int, text: String): String =
        if (session.match.seats >= 3 && !voice.speaksAlone(seat)) "${names[seat]}, $text" else text

    fun soundFor(move: KozelMove) {
        if (!settings.sounds) return
        when (move) {
            is KozelMove.Place -> sounds.card()
            // Две кости разом — звук тот же: за столом их и слышно как один
            // хлопок, а какая кость легла, скажет фраза.
            is KozelMove.PlaceBoth -> sounds.card()
            KozelMove.Draw -> sounds.take()
            KozelMove.Pass -> sounds.card()
        }
    }

    /** Сколько играет звук этого хода. Ноль — звука нет, и ждать нечего. */
    fun soundGap(move: KozelMove): Long {
        if (!settings.sounds) return 0L
        return when (move) {
            is KozelMove.Place -> TableSounds.CARD_MS
            is KozelMove.PlaceBoth -> TableSounds.CARD_MS
            KozelMove.Draw -> TableSounds.TAKE_MS
            KozelMove.Pass -> TableSounds.CARD_MS
        }.toLong()
    }

    /** Итог раунда и счёт матча — одной фразой. */
    fun sayRound(summary: KozelSummary) {
        // Итог не перебивает ход, которым раунд доигран: ждём, пока договорит
        // сказанное, и только потом считаем вслух. Иначе из двух фраз
        // выживает вторая, и игрок слышит счёт, не услышав своего хода.
        voice.say(
            roundPhrase(summary, session.match.rules.target, names),
            afterMs = voice.waitMs(),
        )
    }

    /** Ход игрока: фраза, звук, толчок — в одном месте. */
    fun play(move: KozelMove) {
        // Про пустой стол спрашиваем до хода: после него он уже не пустой,
        // а «кладёшь шесть-шесть» и «кладёшь шесть-шесть слева» — разные
        // слова, и выбираются они по состоянию до хода.
        val lineWasEmpty = session.match.round.table.isEmpty
        soundFor(move)
        if (settings.ownVibration) vibrations.tap()
        session.match.round.apply(PLAYER, move)
        // А фразу собираем после: какую кость отдаст базар, до хода не знает
        // никто — ни игрок, ни приложение, — и назвать её заранее нечем.
        val phrase = ownMovePhrase(move, session.match.round, lineWasEmpty)
        // Свой ход звучит так же: сначала кость, потом слово.
        val gap = soundGap(move)
        voice.sayOwnMove(
            phrase,
            afterMs = if (gap > 0) gap + PHRASE_GAP_MS else 0L,
            // Про базар скринридер не расскажет: он прочитал нажатую кнопку,
            // а не то, что из базара пришло.
            aloud = move is KozelMove.Draw,
        )
        session.persist()
        session.tick++
    }

    fun playTile(tile: Tile) {
        val round = session.match.round
        val legal = round.legalMoves(PLAYER)
        val moves = legal.filterIsInstance<KozelMove.Place>().filter { it.tile == tile }
        if (moves.isEmpty()) {
            // Отказ — ответ на нажатие, а не событие за столом: игрок ждёт его
            // сразу, поэтому говорим своим голосом, даже когда за столом
            // говорит скринридер.
            voice.sayRequested(refusalPhrase(round, names))
            return
        }
        // Два случая, когда решает не правило, а игрок: кость подходит к
        // обоим концам, и дубль нашёл себе пару по другому концу. Только в
        // них и спрашиваем: лишний вопрос на ровном месте — лишний шаг для
        // того, кто играет на слух.
        val both = bothWith(legal, tile)
        val only = moves.singleOrNull()
        if (only != null && both == null) play(only) else endOpen = tile
    }

    fun newMatch() {
        // Договорённости — из настроек «Козла», а не из общих: сколько стоит
        // пусто-пусто, решается за этим столом (SETTINGS.md, 2).
        session.restart(rules = loadKozelRules(context))
        if (settings.sounds) sounds.deal()
        if (settings.signals) sounds.start()
        val afterMs = if (settings.sounds) TableSounds.DEAL_MS + PHRASE_GAP_MS else 0L
        voice.say(dealPhrase(session.match.round, names), afterMs = afterMs)
    }

    // За неигровые места играем подряд, пока ход не вернётся к игроку: на
    // троих это два хода подряд, и оба — не его. Раунд кончился — считаем
    // его, объявляем итог и играем следующий, пока матч не кончится.
    LaunchedEffect(session.tick) {
        var played = false
        var guard = 0
        while (guard++ < BOT_GUARD) {
            val match = session.match
            // Матч кончен — доигранный раунд остаётся на столе как его итог.
            if (match.over) break

            val round = match.round
            if (round.finished) {
                val summary = match.finishRound()
                // Доигранный матч хранить нечего: он итог, а не пауза.
                if (match.over) session.forgetSaved() else session.persist()
                sayRound(summary)
                played = true
                continue
            }

            val seat = round.turn
            if (seat == PLAYER) break

            // Ждём не «полсекунды», а пока договорит предыдущая фраза: иначе
            // бот перебивает сам себя и слышно только последнее слово.
            delay(voice.waitMs())
            val move = KozelBot.chooseMove(
                KozelView.of(round, seat),
                settings.botDifficultyKozel,
                rng,
            ) ?: break

            // Фразу спрашиваем до хода: «последняя» — это про состояние до
            // него, после хода кости на руке уже нет.
            val line = talker.line(
                move = move,
                lineEmpty = round.table.isEmpty,
                ownHandSize = round.handSize(seat),
            )
            soundFor(move)
            round.apply(seat, move)
            // Звук хода и реплика стартуют в один момент и налезают друг на
            // друга. Разводим: сначала звук, потом речь.
            if (settings.botTalk && line.speak) {
                val gap = soundGap(move)
                if (gap > 0) delay(gap + PHRASE_GAP_MS)
            }
            sayBotMove(seat, botLine(seat, line.text), line.speak)
            session.persist()
            played = true
        }

        val match = session.match
        val turnMine = !match.over && !match.round.finished &&
            match.round.legalMoves(PLAYER).isNotEmpty()

        // Ход вернулся к игроку — короткий толчок и тихая нота: толчок слышно
        // не всегда, а тут понятно без слов, что ждут тебя.
        if (played && turnMine) {
            if (settings.vibration) vibrations.tap()
            if (settings.signals) sounds.turn()
        }
        if (played) session.tick++

        // Линия перед твоим ходом: что лежит и куда можно приставить. Реплики
        // бота называют кости по одной, и по одной они складываются в
        // картину; когда он молчит или положил за раз несколько — нет.
        //
        // Пустой стол не называем: это не сведение, а шум. «Твой ход» нужен
        // всегда — без него игрок ждёт, пока заговорит бот, которого уже
        // никто не ждёт.
        if (played && turnMine) {
            val text = if (session.match.round.table.isEmpty) {
                // Раунд открывают младшим дублем: назвать его — часть хода.
                // Без этого игрок слышит только «твой ход» и перебирает руку
                // вслепую, хотя ход у него ровно один.
                openingPhrase(session.match.round) ?: TURN_PHRASE
            } else {
                endsPhrase(session.match.round)
            }
            voice.say(text, afterMs = voice.waitMs())
        }
    }

    LaunchedEffect(Unit) {
        when {
            // Матч поднят с диска: раздачу объявлять не надо, надо сказать,
            // что за столом, и чей ход.
            session.restored -> {
                session.acceptRestored()
                voice.say(
                    resumePhrase(
                        round = session.match.round,
                        scores = session.match.table,
                        roundNumber = session.match.roundNumber,
                        names = names,
                    ),
                    whenReady = true,
                )
            }

            session.lastPhrase.isBlank() -> {
                voice.say(dealPhrase(session.match.round, names), whenReady = true)
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

    val round = session.match.round
    val match = session.match
    val moves = round.legalMoves(PLAYER)

    val status = buildString {
        append(
            when {
                round.finished -> "Раунд кончился."
                moves.isNotEmpty() -> "Твой ход."
                // «Ход соперника», а не по имени: «ход Пети» — падеж, а
                // склонять произвольное имя программа не умеет; на троих же
                // «соперник» не говорит, кого ждать, поэтому там имя стоит
                // подлежащим — «Ходит Петя» (SETTINGS.md, 8).
                botSeats.size == 1 -> "Ход соперника."
                else -> "Ходит ${names[round.turn]}."
            },
        )
        append(" У тебя ${round.handSize(PLAYER)}.")
        append(" В базаре ${round.bazaarSize}.")
        if (botSeats.size == 1) {
            append(" У соперника ${round.handSize(FIRST_BOT_SEAT)}.")
        } else {
            append(" Соперники: ${botSeats.joinToString(", ") { "${names[it]} ${round.handSize(it)}" }}.")
        }
    }

    // Счёт матча. За столом на троих перечисляется по местам: «у соперника»
    // уже не говорит, у кого сколько.
    val scoreLine = buildString {
        append("Счёт: ")
        append(
            if (match.seats <= 2) {
                "у тебя ${match.table[PLAYER]}, у соперника ${match.table[FIRST_BOT_SEAT]}"
            } else {
                (0 until match.seats).joinToString(", ") {
                    seatScore(it, names, "${match.table[it]}")
                }
            },
        )
        append(". Раунд ${match.roundNumber}. До ${match.rules.target}.")
    }

    // Порядок костей — ровно тот, что выбран в настройках, и ничего поверх:
    // кости, которыми сейчас можно сходить, наверх не поднимаются. Подсказка
    // осталась, но словами: подпись «не подходит» и приглушённая кость.
    val playable: Set<Tile> = moves.filterIsInstance<KozelMove.Place>().map { it.tile }.toSet()

    // Молчим о вердикте, только когда решать сейчас не нам. Пустой набор —
    // это не «решать нечего», а «ни одна не подходит»: во время добора из
    // базара ход у игрока есть, но он один — взять. Отличить одно от другого
    // по набору нельзя, поэтому смотрим на очередь, а не на набор.
    val deciding = !round.finished && round.turn == PLAYER
    fun verdictFor(tile: Tile): Boolean? = if (deciding) tile in playable else null

    val hand = settings.tileOrder.sort(round.handOf(PLAYER))

    // Кость, до которой игрок дошёл жестом. Это не выбор кости, а её чтение:
    // сходить можно по-прежнему только нажатием. Сбрасывается на каждой
    // раздаче — рука меняется, и старый номер в ней ничего не значит.
    var cursor by remember(hand) { mutableStateOf(-1) }

    /**
     * Шаг по руке вправо-влево. С какого места ни начни — кость называется
     * целиком: тем, кто слушает, номер без названия не говорит ничего, а
     * «подходит или нет» — это и есть ответ на вопрос «чем мне ходить».
     */
    fun walkHand(step: Int) {
        if (hand.isEmpty()) {
            voice.say("Костей на руке нет.")
            return
        }
        val next = if (cursor < 0) {
            if (step > 0) 0 else hand.size - 1
        } else {
            (cursor + step + hand.size) % hand.size
        }
        cursor = next
        val tile = hand[next]
        voice.say("${next + 1} из ${hand.size}: ${tile.spoken()}${spokenVerdict(verdictFor(tile))}.")
    }

    // Где на экране лежит полоса стола. Жест вбок её обходит: по столу водят
    // пальцем, чтобы ощупать кости, и листать ею руку заодно нельзя — игрок
    // услышал бы сразу и кость, и чужую карту из своей руки. Пусто, пока
    // стола на экране нет: тогда обходить нечего.
    var tableBand by remember { mutableStateOf(Rect.Zero) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .tableGestures(skip = { point -> tableBand.contains(point) }) { gesture ->
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
                Text(
                    scoreLine,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    // Стол называем коротко: сколько костей и что на концах.
                    // Перечислять кости подряд незачем — их слушают по одной,
                    // ощупывая линию, а лента из десятка названий не
                    // удерживается и глушит то, ради чего игрок сюда смотрит:
                    // чем можно сходить (Катерина, 19.09).
                    if (round.table.isEmpty) {
                        "Стол пуст."
                    } else {
                        "Стол: костей ${round.tableSize}. ${round.spokenEnds()}."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                // И та же линия костями: слова — скринридеру, кости — тому,
                // кто за столом видит, и они же — тому, кто щупает стол
                // пальцем. Кость, повёрнутая как попало, разошлась бы с
                // соседней, поэтому поворот берём у движка (Line.laid).
                val laid = round.table.laid()
                val tableTiles = round.table.tiles
                if (laid.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    // Клетки ровные и на всю ширину: кость не уезжает от
                    // клетки, и палец, разделив ширину на число клеток,
                    // находит ровно ту кость, под которой он лежит. Линия
                    // от этого ещё и всегда помещается на экран целиком —
                    // длинную щупать иначе было бы нечем.
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { tableBand = it.boundsInWindow() },
                    ) {
                        val cell = (maxWidth - TABLE_GAP * (laid.size - 1)) / laid.size
                        val tile = minOf(tableWidth, cell)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tableFeel(
                                    count = laid.size,
                                    tile = cell,
                                    gap = TABLE_GAP,
                                ) { index ->
                                    // Ответ на прикосновение, а не событие за
                                    // столом: игрок сам тронул кость и ждёт
                                    // ответа сейчас же. В «Повтор» такое не
                                    // идёт — повторяют ход, а не ощупывание.
                                    voice.sayRequested(
                                        "${index + 1} из ${laid.size}: " +
                                            "${tableTiles[index].spoken()}.",
                                    )
                                },
                            horizontalArrangement = Arrangement.spacedBy(TABLE_GAP),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            laid.forEachIndexed { index, one ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        // Кость на столе — своя весть для
                                        // скринридера, ровно как кость на
                                        // руке: он читает её под пальцем, одну
                                        // за другой, и называет, которая по
                                        // счёту. Одной подписью на всю линию
                                        // этого не сделать — тогда под пальцем
                                        // звучит вся линия разом, а пощупать
                                        // одну кость нечем. Строка выше
                                        // называет концы и число костей, а
                                        // сами кости читаются только здесь —
                                        // по одной и под пальцем.
                                        .semantics {
                                            contentDescription =
                                                "${index + 1} из ${laid.size}: " +
                                                    "${tableTiles[index].spoken()}"
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TileFace(one.left, one.right, tile, tile * TABLE_ASPECT)
                                }
                            }
                        }
                    }
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

        // Рука лежит на своём фоне, а стол остаётся голым: глазу видно, где
        // кончается стол и начинаются твои кости, и одно от другого не
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
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(hand) { index, tile ->
                    HandTile(
                        tile = tile,
                        playable = verdictFor(tile),
                        tileWidth = tileWidth,
                        tileHeight = tileHeight,
                        selected = cursor == index,
                        onClick = { playTile(tile) },
                    )
                }
            }
        }

        // --- Низ: панель действий, не прокручивается ----------------------

        Spacer(Modifier.height(8.dp))

        if (match.over) {
            Button(onClick = { newMatch() }, modifier = Modifier.fillMaxWidth()) { Text("Ещё раз") }
            Spacer(Modifier.height(8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Кнопки не прячем, а гасим: исчезающая кнопка сдвигает соседние,
            // и рука каждый раз ищет их заново. В каждый момент жива ровно
            // одна из двух: ходить нечем — либо берут из базара, либо
            // пропускают, и решает это правило, а не игрок.
            Button(
                onClick = { play(KozelMove.Draw) },
                enabled = moves.contains(KozelMove.Draw),
                modifier = Modifier.weight(1f),
            ) { Text("Из базара") }
            Button(
                onClick = { play(KozelMove.Pass) },
                enabled = moves.contains(KozelMove.Pass),
                modifier = Modifier.weight(1f),
            ) { Text("Пропустить") }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Обе кнопки — вопрос игрока, а не событие за столом: он нажал и
            // ждёт ответа. Отвечает тот же, кто говорит и за столом.
            //
            // Имя кнопки живёт внутри неё, а не рядом: имя, положенное на
            // кнопку снаружи, до неё не доходит, и скринридер читает «без
            // метки» — проверено на телефоне.
            Button(
                onClick = { voice.sayRequested(allowedPhrase(round, names)) },
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

    // На какой конец положить кость, которая подходит к обоим, и не положить
    // ли заодно парный дубль. Диалогом, а не выпадающим списком: скринридер
    // объявляет диалог целиком и сразу ставит в него фокус.
    val pending = endOpen
    if (pending != null) {
        val places = moves.filterIsInstance<KozelMove.Place>().filter { it.tile == pending }
        val both = bothWith(moves, pending)
        AlertDialog(
            onDismissRequest = { endOpen = null },
            title = { Text(chooseTitle(pending, both)) },
            text = {
                Column {
                    // Обе кости — первым: это и новость (пара нашлась), и
                    // ход, который называет обе кости сразу.
                    if (both != null) {
                        TextButton(
                            onClick = {
                                endOpen = null
                                play(both)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Положить обе: ${both.left.spoken()} и ${both.right.spoken()}")
                        }
                    }
                    places.forEach { place ->
                        TextButton(
                            onClick = {
                                endOpen = null
                                play(place)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(endTitle(round, place.end)) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { endOpen = null }) { Text("Отмена") }
            },
        )
    }

    // Редкое — под кнопкой «Ещё». Диалогом, а не выпадающим списком: диалог
    // скринридер объявляет целиком и сразу ставит в него фокус.
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

/** Куда именно ляжет кость: «влево» само по себе не говорит, к какому числу. */
private fun endTitle(round: KozelRound, end: End): String {
    val side = if (end == End.LEFT) "Влево" else "Вправо"
    val number = round.table.endOf(end) ?: return side
    return "$side — к ${pipsName(number)}"
}

/**
 * Ход двумя дублями, если [tile] в нём участвует.
 *
 * Нужен там, где спрашивают игрока: и когда он ткнул в кость пальцем, и
 * когда для неё открыт диалог. Один ответ на оба вопроса — иначе диалог мог
 * бы предложить не тот ход, которого ждали от нажатия.
 */
private fun bothWith(moves: List<KozelMove>, tile: Tile): KozelMove.PlaceBoth? =
    moves.filterIsInstance<KozelMove.PlaceBoth>()
        .firstOrNull { it.left == tile || it.right == tile }

/**
 * Заголовок выбора. С парным дублем выбор уже не только про сторону: за
 * списком концов стоит ещё и «положить обе», и обещать в заголовке сторону
 * нельзя.
 */
private fun chooseTitle(tile: Tile, both: KozelMove.PlaceBoth?): String =
    if (both == null) "Куда положить ${tile.spoken()}" else "Что положить: ${tile.spoken()}"

/**
 * Итог раунда одной фразой: кто его проиграл, сколько ему записали и что
 * теперь на счёте.
 *
 * Очки в «Козле» штрафные, и записывают их проигравшему раунд. Поэтому про
 * запись говорим в дательном: «тебе записано», а не «ты набрал» — набрал тут
 * как раз тот, кто выиграл.
 *
 * За столом на троих проигравших двое, и «соперник» не говорит, кто именно:
 * вышедший не пишет ничего, а каждый из оставшихся считает своё (Катерина,
 * 19.09), поэтому там запись перечисляется по местам.
 */
private fun roundPhrase(summary: KozelSummary, target: Int, names: List<String>): String = buildString {
    if (summary.written.size <= 2) {
        append(
            when {
                summary.fish && summary.winner == null ->
                    "Рыба. Руки равны, никто ничего не записал."

                summary.fish && summary.winner == PLAYER ->
                    "Рыба. У соперника рука тяжелее: ему записано ${summary.written[FIRST_BOT_SEAT]}."

                summary.fish ->
                    "Рыба. У тебя рука тяжелее: тебе записано ${summary.written[PLAYER]}."

                summary.winner == PLAYER ->
                    "Ты вышел. Сопернику записано ${summary.written[FIRST_BOT_SEAT]}."

                else -> "${names[FIRST_BOT_SEAT]} вышел. Тебе записано ${summary.written[PLAYER]}."
            },
        )
        append(
            " Счёт: у тебя ${summary.scores[PLAYER]}, " +
                "у соперника ${summary.scores[FIRST_BOT_SEAT]}.",
        )
    } else {
        val who = writes(summary, names)
        // Итог раунда берём в местную переменную: свойство движка — чужая
        // для экрана сторона, и умного приведения типов через границу
        // модуля у него нет.
        val winner = summary.winner
        append(
            when {
                winner == PLAYER -> "Ты вышел."

                // Имя здесь подлежащим: падежа оно не требует, а склонять
                // произвольное имя программа не умеет (SETTINGS.md, 8).
                winner != null -> "${names[winner]} вышел."

                who.isEmpty() -> "Рыба. Руки равны."

                else -> "Рыба."
            },
        )
        append(if (who.isEmpty()) " Никому ничего не записано." else " Записано: $who.")
        append(
            " Счёт: " + summary.scores.indices.joinToString(", ") {
                seatScore(it, names, "${summary.scores[it]}")
            } + ".",
        )
    }

    // Козёл — единственный итог матча: за столом на троих до цели не дошёл
    // никто из двоих, и оба они выиграли одинаково.
    val goat = summary.goat
    if (goat == null) {
        append(" Играем до $target.")
    } else if (goat == PLAYER) {
        append(" Ты козёл: набрал ${summary.scores[PLAYER]}.")
    } else {
        append(" ${names[goat]} — козёл, набрал ${summary.scores[goat]}.")
    }
}

/**
 * Кому сколько записали за раунд: «тебе 12, Петя 7». Пусто — не записали
 * никому.
 *
 * Место игрока зовётся «тебе», прочие — по имени и без падежа: имя тут
 * подлежащее-название, как в счёте на экране.
 */
private fun writes(summary: KozelSummary, names: List<String>): String =
    summary.written.indices
        .filter { summary.written[it] > 0 }
        .joinToString(", ") { seatScore(it, names, "${summary.written[it]}") }

/** Место за столом в перечислении: игрок — «тебе», соперники — по имени. */
private fun seatScore(seat: Int, names: List<String>, value: String): String =
    if (seat == PLAYER) "тебе $value" else "${names[seat]} $value"

/**
 * Раздача. Перечислять кости не надо — их игрок слушает свайпом по руке, и
 * вслух они превращаются в длинную ленту, которую он всё равно не удержит;
 * порядок он выбрал сам в настройках. Стол при раздаче пуст — о нём молчим.
 */
private fun dealPhrase(round: KozelRound, names: List<String>): String =
    "Раздача. У тебя ${round.handSize(PLAYER)} костей, в базаре ${round.bazaarSize}. " +
        turnPhrase(round, names)

/**
 * Что сказать, когда матч поднят с диска. Игрок вернулся через час или после
 * случайного выхода и не помнит стол — напоминаем положение дел, а не
 * «продолжаем», за которым ничего не стоит.
 */
private fun resumePhrase(
    round: KozelRound,
    scores: List<Int>,
    roundNumber: Int,
    names: List<String>,
): String {
    val table = if (round.table.isEmpty) "" else " Стол: ${round.spokenEnds()}, костей ${round.tableSize}."
    val others = if (round.seats <= 2) {
        "у соперника ${round.handSize(FIRST_BOT_SEAT)}"
    } else {
        (FIRST_BOT_SEAT until round.seats).joinToString(", ") {
            "${names[it]} ${round.handSize(it)}"
        }
    }
    val score = if (scores.size <= 2) {
        "у тебя ${scores[PLAYER]}, у соперника ${scores[FIRST_BOT_SEAT]}"
    } else {
        scores.indices.joinToString(", ") { seatScore(it, names, "${scores[it]}") }
    }
    return "Продолжаем партию. Раунд $roundNumber. У тебя ${round.handSize(PLAYER)} костей, " +
        "в базаре ${round.bazaarSize}, $others." +
        " Счёт: $score.$table ${turnPhrase(round, names)}"
}

private fun turnPhrase(round: KozelRound, names: List<String>): String = when {
    round.legalMoves(PLAYER).isNotEmpty() -> "Твой ход."
    // «Ход соперника», а не по имени: «ход Пети» — падеж. На троих, где
    // соперников двое, имя стоит подлежащим — «Ходит Петя» (SETTINGS.md, 8).
    round.seats <= 2 -> "Ход соперника."
    else -> "Ходит ${names[round.turn]}."
}

/** Концы линии вслух, когда ход вернулся к игроку. */
private fun endsPhrase(round: KozelRound): String = "${round.spokenEnds()}. $TURN_PHRASE"

/** Почему костью не пройти — ответ на нажатие. */
private fun refusalPhrase(round: KozelRound, names: List<String>): String = when {
    round.finished -> "Раунд кончился."
    round.turn != PLAYER -> waitingPhrase(round, names)
    // Отказ на первом ходу звучал бы как «этой костью не пройти», хотя
    // пройти можно — только другой костью. Поэтому называем ту, которой
    // раунд и открывают.
    openingPhrase(round) != null -> "Раунд открывают младшим дублем. $TURN_PHRASE"
    round.bazaarSize > 0 -> "Этой костью не пройти. Возьми из базара."
    else -> "Этой костью не пройти. Ход придётся пропустить."
}

/**
 * Почему хода сейчас нет: за столом на троих «ход соперника» не говорит,
 * кого ждать.
 */
private fun waitingPhrase(round: KozelRound, names: List<String>): String =
    if (round.seats <= 2) "Сейчас ход соперника, подожди." else "Сейчас ходит ${names[round.turn]}, подожди."

/**
 * Первый ход раунда: младший дубль, которым его открывают, — и больше
 * никакого. Пусто — открывать нечем (дублей нет ни у кого) или раунд уже
 * начат: тогда это не первый ход, и ходят как обычно.
 */
private fun openingPhrase(round: KozelRound): String? {
    if (!round.table.isEmpty) return null
    val opening = round.legalMoves(PLAYER).filterIsInstance<KozelMove.Place>().singleOrNull()
        ?: return null
    return "$TURN_PHRASE Начинаешь младшим дублем: ${opening.tile.spoken()}."
}

/**
 * Что можно сделать прямо сейчас. Про конец линии говорим там, где он есть:
 * в пустую линию кость кладут как угодно, и «влево» там ничего не значит.
 */
private fun allowedPhrase(round: KozelRound, names: List<String>): String {
    val moves = round.legalMoves(PLAYER)
    if (moves.isEmpty()) {
        return if (round.finished) "Раунд кончился." else waitingPhrase(round, names)
    }

    val empty = round.table.isEmpty
    val parts = moves.filterIsInstance<KozelMove.Place>()
        .map { place ->
            if (empty) {
                "положить ${place.tile.spoken()}"
            } else {
                "положить ${place.tile.spoken()} ${place.end.title}"
            }
        }
        .toMutableList()
    moves.filterIsInstance<KozelMove.PlaceBoth>().forEach { both ->
        parts += "положить обе: ${both.left.spoken()} и ${both.right.spoken()}"
    }
    if (moves.contains(KozelMove.Draw)) parts += "взять из базара"
    if (moves.contains(KozelMove.Pass)) parts += "пропустить ход"
    return "Можно: " + parts.joinToString(", ") + "."
}

/**
 * Свой ход — вслух. Короткая живая речь, как за настоящим столом: свой ход
 * повторяет то, что игрок только что сделал сам, поэтому здесь важнее
 * точность, чем разнообразие. Речь бота — в [KozelTalker].
 */
private fun ownMovePhrase(move: KozelMove, round: KozelRound, lineWasEmpty: Boolean): String = when (move) {
    is KozelMove.Place ->
        if (lineWasEmpty) {
            "Кладёшь ${move.tile.spoken()}."
        } else {
            "Кладёшь ${move.tile.spoken()} ${move.end.title}."
        }

    // Оба дубля: концы у них свои и названы самой костью — четвёрка идёт к
    // четвёрке, единица к единице, — поэтому стороны тут не называем, их
    // и без того не с чем спутать.
    is KozelMove.PlaceBoth ->
        "Кладёшь обе: ${move.left.spoken()} и ${move.right.spoken()}."

    // Взятую кость называем вслух и всегда: она пришла из закрытого базара,
    // и кроме этих слов игрок о ней не узнает ничего. Кость, взятая из
    // базара, ложится в руку последней — движок только так её и выдаёт.
    //
    // Назвать кость мало: игроку в этот момент нужен ответ на один вопрос —
    // нашлась или нет. Без него он идёт щупать руку заново, чтобы выяснить
    // то, что приложение и так знает, — а под палец ему попадутся и старые
    // кости, те, которыми не пройти. Отвечаем сразу и одним словом: нашёл
    // значит нашёл, а «не подходит» значит бери ещё, ход остался у тебя.
    KozelMove.Draw -> {
        val drawn = round.handOf(PLAYER).last()
        val fits = round.table.canPlay(drawn).isNotEmpty()
        "Ты берёшь из базара: ${drawn.spoken()}." +
            if (fits) " Нашёл — ходи ею." else " Не подходит, бери ещё."
    }
    KozelMove.Pass -> "Тебе нечем ходить, ход пропущен."
}
