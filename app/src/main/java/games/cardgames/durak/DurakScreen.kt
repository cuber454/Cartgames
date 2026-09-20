package games.cardgames.durak

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
import games.cardgames.GAME_DURAK
import games.cardgames.score.Outcome
import games.cardgames.score.Score
import games.cardgames.score.saveScore
import games.cardgames.settings.Gender
import games.cardgames.settings.botEngine
import games.cardgames.settings.botSpeaksAlone
import games.cardgames.settings.botVoice
import games.cardgames.settings.loadSettings
import games.cardgames.settings.seatTitles
import games.cardgames.sound.TableSounds
import games.cardgames.sound.Vibrations
import games.cardgames.speech.BotVoice
import games.cardgames.speech.Chatter
import games.cardgames.speech.FIRST_BOT_SEAT
import games.cardgames.speech.Jokes
import games.cardgames.speech.PHRASE_GAP_MS
import games.cardgames.speech.Speaker
import games.cardgames.speech.TURN_PHRASE
import games.cardgames.speech.TableEvent
import games.cardgames.speech.TableVoice
import games.cardgames.speech.cardVerdict
import games.cardgames.speech.sayEvent
import games.cardgames.speech.speech
import games.cardgames.speech.verdictOf
import games.cardgames.speech.withoutTurn
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

/**
 * Место игрока за столом. Соперники — все прочие места по кругу.
 *
 * Игрок всегда нулевой, а соперников бывает один или двое: «Дурак» садится и
 * на двоих, и на троих (GAMES.md). Поэтому места здесь не два числа, а
 * список — кто именно ходит, спрашивается у партии, а не выводится из
 * числа мест на экране.
 */
private const val PLAYER = 0

/** Больше трёх за стол не садится — столько и синтезаторов заводим. */
private const val MAX_SEATS = 3

/** Пауза перед ходом бота, чтобы не тараторил. */
private const val BOT_DELAY_MS = 700L

/**
 * Сколько карт на руке — уже ворох, а не рука.
 *
 * Раздача кладёт по шесть; восемь и больше набирается только у того, кто
 * забирал со стола, и это за столом заметно: такую руку держат, а не
 * раскладывают. По этому числу разговор о взятых картах меняется на разговор
 * о полной руке ([games.cardgames.speech.TableEvent]).
 */
private const val FULL_HAND_SIZE = 8

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

    val game = session.game

    // Как звать соперников по местам: место игрока — «ты», у прочих своё имя.
    // Число мест берём у партии, а не у настроек: партия помнит стол, за
    // которым её начали, и смена настройки посреди неё имён не переписывает.
    val names = seatTitles(settings, GAME_DURAK, game.playerCount)
    // Соперники по местам. На двоих он один, на троих — двое.
    val bots = (1 until game.playerCount).toList()

    val speaker = remember(settings.engine, settings.voice, settings.rate) {
        Speaker(
            context = context,
            rate = settings.rate,
            enginePackage = settings.engine,
            voiceName = settings.voice,
        )
    }
    // Голос бота — своим синтезатором: движок на живом синтезаторе на ходу не
    // поменять. Своего голоса боту не выбрали — говорим голосом приложения:
    // разводить соперника с ним положено синтезатором, а не высотой
    // (Катерина, 19.09: «убери, чтобы повышение голоса было у каждого бота»).
    val botSpeaker = remember(
        settings.engine,
        settings.botEngineDurak,
        settings.voice,
        settings.botVoiceDurak,
        settings.botRateDurak,
        settings.rate,
    ) {
        Speaker(
            context = context,
            rate = settings.botRateDurak,
            // Имя для журнала: за столом говорят три синтезатора, и запись
            // без имени читается как одна речь.
            title = "соперник",
            enginePackage = botEngine(settings.botEngineDurak, settings.engine),
            voiceName = botVoice(
                settings.botVoiceDurak,
                settings.voice,
                settings.botEngineDurak,
                settings.engine,
            ),
        )
    }
    // Второй соперник заводится, только когда за столом трое: на двоих его
    // синтезатор был бы движком, которого никто не слышит.
    val botSpeakerSecond = remember(
        settings.engine,
        settings.botEngineDurakSecond,
        settings.voice,
        settings.botVoiceDurakSecond,
        settings.botRateDurakSecond,
        settings.rate,
        game.playerCount,
    ) {
        if (game.playerCount < 3) {
            null
        } else {
            Speaker(
                context = context,
                rate = settings.botRateDurakSecond,
                title = "второй соперник",
                enginePackage = botEngine(settings.botEngineDurakSecond, settings.engine),
                voiceName = botVoice(
                    settings.botVoiceDurakSecond,
                    settings.voice,
                    settings.botEngineDurakSecond,
                    settings.engine,
                ),
            )
        }
    }
    // Бот со своим синтезатором или своим голосом говорит им и при работающем
    // скринридере: тот озвучивает приложение, а соперник — своим голосом
    // (SETTINGS.md, 8).
    val botApart = botSpeaksAlone(
        settings.botVoiceDurak,
        settings.voice,
        settings.botEngineDurak,
        settings.engine,
    )
    val botSecondApart = botSpeaksAlone(
        settings.botVoiceDurakSecond,
        settings.voice,
        settings.botEngineDurakSecond,
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
    // Речь соперников живёт выше партии: мешки зачинов должны переживать и
    // перерисовки, и переходы в настройки, иначе повторы вернутся.
    //
    // Свой рассказчик каждому месту, а не один на стол: мешок зачинов общий
    // на всех — и двое соперников тянули бы из него по очереди, повторяя
    // друг за другом. За столом это слышно как один человек, говорящий
    // попеременно то одним, то другим голосом.
    val talkers = remember { List(MAX_SEATS) { BotTalker(rng) } }
    // Разговоры за столом и прибаутки — общий слой на все игры (HUNDRED_ONE.md,
    // 4.2). Живут выше партии, как и мешки зачинов: реплика, сказанная до
    // ухода в настройки, не должна прозвучать второй раз после возвращения.
    val chatter = remember { Chatter(rng) }
    val jokes = remember { Jokes(rng) }
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

    // Кто говорит за столом: приложение, скринридер или никто. В каждый
    // момент — ровно один, иначе две речи накладываются и выходит каша.
    // Значение живое: скринридер включают и выключают прямо посреди партии.
    val speech = settings.voiceMode.speech(speaker.screenReaderOn)
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
    val speechNow = rememberUpdatedState(speech)
    val rateNow = rememberUpdatedState(settings.rate)
    val voice = remember(speaker, botSpeaker, botSpeakerSecond, botApart, botSecondApart) {
        TableVoice(
            speaker = speaker,
            botSpeakers = buildMap {
                put(FIRST_BOT_SEAT, BotVoice(botSpeaker, botApart))
                botSpeakerSecond?.let { put(2, BotVoice(it, botSecondApart)) }
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
     * Ход бота: реплики можно выключить, тогда он молча кладёт карты.
     * [speak] = false — бот промолчал по своей воле (см. [BotTalker]); фразу
     * всё равно запоминаем, её повторит кнопка «Повтори».
     *
     * Место задаёт голос: за столом на троих соперников двое, и каждый
     * говорит своим (SETTINGS.md, 8).
     */
    fun sayBotMove(seat: Int, text: String, speak: Boolean = true) {
        if (!settings.botTalk || !speak) {
            session.lastPhrase = text
            return
        }
        voice.sayBot(text, seat = seat)
    }

    /**
     * Разговор за столом — о том, что случилось у игрока.
     *
     * Реплику говорит соперник, и всегда со стороны ([TableEvent]): о своём
     * ходе он уже сказал сам ([BotTalker]: «беру», «бито»), и второй раз про то
     * же — два голоса об одном (HUNDRED_ONE.md, 4.2). За игрока же не говорит
     * никто, кроме приложения, которое называет ход, — и вот тут за столом
     * есть что сказать.
     *
     * Реплика идёт за фразой о ходе, а не вместо неё: [TableVoice.waitMs]
     * отсчитывает, сколько той фразе ещё звучать. Слота хватает одной реплике
     * на партию — [Chatter.newRound] здесь не зовётся.
     *
     * [Chatter] тянет фразу из мешка и сам решает промолчать, поэтому `null`
     * здесь — обычное дело, а не сбой.
     */
    fun talk(event: TableEvent, seat: Int = FIRST_BOT_SEAT) {
        if (!settings.botTalk || !settings.tableTalk) return
        val line = chatter.line(event, own = false) ?: return
        // lastPhrase не трогаем: «Повтори» повторяет ход, а не разговор.
        voice.sayBot(line, seat = seat, afterMs = voice.waitMs())
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
        if (!game.finished || session.finishSaid) return
        session.finishSaid = true

        // На троих исходов не два, а три: первый вышедший выиграл, последний
        // с картами — дурак, а середина не выиграла и не проиграла. Ничьей
        // это и называем: засчитать её победой значило бы записать в счёт
        // то, чего за столом не было.
        val result = when {
            game.winner == PLAYER -> Outcome.WIN
            game.loser == PLAYER -> Outcome.LOSS
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
            finishPhrase(game, next, names, settings.playerGender),
            afterMs = if (signalMs > 0) signalMs + PHRASE_GAP_MS else 0L,
        )
        // Прибаутка — второй фразой, тем же голосом: стола уже нет, и говорить
        // о партии, кроме приложения, некому ([Jokes]). Ничья её не получает:
        // прибаутка бывает про победу или про проигрыш, а ничья — ни то ни сё,
        // и любая из двух прозвучала бы враньём.
        if (settings.matchJokes && result != Outcome.DRAW) {
            val joke = jokes.afterMatch(won = result == Outcome.WIN, gender = settings.playerGender)
            voice.say(joke, afterMs = voice.waitMs())
        }
    }

    fun newGame() {
        // Правила раздачи — из игровых настроек «Дурака», а не из общих:
        // переводной или подкидной решается за этим столом (SETTINGS.md, 2).
        session.restart(rules = loadDurakRules(context))
        if (settings.sounds) sounds.deal()
        // Сигнал начала идёт вместе с шорохом раздачи, а не после него:
        // ноты и шум не перекрывают друг друга на слух, а разведённые по
        // времени они растянули бы паузу перед фразой вдвое.
        if (settings.signals) sounds.start()
        // Раздача шумит почти семь десятых секунды: скажи мы сразу — голос
        // утонул бы в шорохе карт. Выключенные звуки — ждать нечего.
        val afterMs = if (settings.sounds) TableSounds.DEAL_MS + PHRASE_GAP_MS else 0L
        // Имена берём у новой партии, а не у прежней: смена «за столом трое»
        // пересдаёт партию здесь же, и старое число мест назвало бы за
        // столом не тех, кто за ним сел.
        val fresh = session.game
        voice.say(
            dealPhrase(fresh, seatTitles(settings, GAME_DURAK, fresh.playerCount)),
            afterMs = afterMs,
        )
    }

    // Ход бота: играем за него все ходы подряд, пока ход не вернётся к игроку.
    // Эффект перезапускается при возвращении с экрана настроек и продолжает
    // партию с того же места.
    LaunchedEffect(session.tick) {
        var guard = 0
        var played = false
        // Сколько карт бот положил за эту серию. По одной его реплики
        // складываются в картину, по нескольким — уже нет.
        var botCards = 0
        // Чей ход — спрашиваем у партии, а не выводим из числа мест: на троих
        // очередь идёт по кругу, и ход принадлежит то одному сопернику, то
        // другому (DurakSeatsTest). Пока ход не у игрока — играем за того,
        // чей он.
        while (!game.finished && game.turn != PLAYER && guard++ < 300) {
            val seat = game.turn
            // Ждём не «полсекунды», а пока договорит предыдущая фраза:
            // иначе бот перебивает сам себя и слышно только последнее слово.
            delay(voice.waitMs())
            val move = BotPlayer.chooseMove(game, seat, settings.botDifficultyDurak, rng) ?: break
            soundFor(move)
            // Фразу спрашиваем до хода: «последняя карта» и «колода вышла» —
            // это про состояние до него, после хода карта уже не последняя.
            val line = talkers[seat].line(
                move = move,
                difficulty = settings.botDifficultyDurak,
                trumpSuit = game.trumpSuit,
                tableEmpty = game.table.isEmpty(),
                ownHandSize = game.handOf(seat).size,
                deckSize = game.deckSize(),
            )
            game.apply(seat, move)
            // Звук хода и реплика бота стартуют в один момент и налезают
            // друг на друга. Разводим: сначала звук, потом речь. Молчащему
            // боту ждать незачем.
            if (settings.botTalk && line.speak) {
                val gap = soundGap(move)
                if (gap > 0) delay(gap + PHRASE_GAP_MS)
            }
            sayBotMove(seat, line.text, line.speak)
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
        // Дослушиваем бота, прежде чем сказать своё: и итог партии, и сведение
        // стола, и «твой ход» — это фразы поверх его реплики, а поверх неё
        // слышно одно последнее слово. В «Тысяче» и «Козле» эта пауза стоит с
        // самого начала, а в «Дураке» её не было — «На столе…» накрывало
        // последнее слово бота, и это ровно та каша, из-за которой не понять,
        // кто что сказал.
        if (played) delay(voice.waitMs())
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
        } else if (played && !game.finished && game.legalMoves(PLAYER).isNotEmpty()) {
            // Стол пуст или бот положил одну карту и про неё уже сказал —
            // называть нечего, а «твой ход» нужен всегда: без него игрок
            // ждёт, пока заговорит бот, которого уже никто не ждёт.
            voice.say(TURN_PHRASE)
        }
        if (played) session.tick++
    }

    LaunchedEffect(Unit) {
        when {
            // Партия поднята с диска: раздачу объявлять не надо, надо
            // сказать, что за столом, и чей ход.
            session.restored -> {
                session.acceptRestored()
                voice.say(resumePhrase(game, names), whenReady = true)
            }

            session.lastPhrase.isBlank() -> voice.say(dealPhrase(game, names), whenReady = true)

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

    /** Ход игрока: фраза, звук, толчок и передача хода — в одном месте. */
    fun play(move: DurakMove) {
        val phrase = ownMovePhrase(move, settings.playerGender)
        soundFor(move)
        if (settings.ownVibration) vibrations.tap()
        session.game.apply(PLAYER, move)
        // Свой ход звучит так же: сначала карта, потом слово.
        val gap = soundGap(move)
        voice.sayOwnMove(phrase, afterMs = if (gap > 0) gap + PHRASE_GAP_MS else 0L)
        // Забрал со стола — единственное, о чём за игрока не сказал никто:
        // приложение назвало ход, а соперник молчит. Про свой «беру» он
        // говорит сам, и повторять за ним нечего (HUNDRED_ONE.md, 4.2).
        if (move == DurakMove.Take) {
            val full = game.handOf(PLAYER).size >= FULL_HAND_SIZE
            talk(if (full) TableEvent.FULL_HAND else TableEvent.TOOK)
        }
        session.persist()
        finishIfOver()
        session.tick++
    }

    fun allowedPhrase(): String {
        val moves = game.legalMoves(PLAYER)
        if (moves.isEmpty()) {
            // Партия кончена — ждать некого: ходов нет ни у кого, и «сейчас
            // ходит» назвало бы того, кто уже вышел.
            return if (game.finished) "Партия окончена." else "Сейчас ходит ${names[game.turn]}, подожди."
        }
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

    val moves = game.legalMoves(PLAYER)
    val transfers = moves.filterIsInstance<DurakMove.Transfer>()

    val status = buildString {
        // Чей ход — по допустимым ходам, а не по тому, кто атакует. Пока
        // стол не отбит, атакующий формально игрок, но играть ему нечем:
        // ход защищающегося. По «attacker == PLAYER» строка врала ровно в
        // этот момент — обещала ход там, где игрок ничего сделать не мог.
        append(
            when {
                game.finished -> "Партия окончена."
                moves.isNotEmpty() -> "Твой ход."
                // Соперник один — «ход соперника» и есть его имя; на троих
                // их двое, и «соперник» перестаёт что-либо называть, поэтому
                // место зовётся своим именем (SETTINGS.md, 8).
                bots.size == 1 -> "Ход соперника."
                else -> "Ходит ${names[game.turn]}."
            },
        )
        append(" Козырь — ${game.trumpSuit.title}.")
        append(" В колоде ${game.deckSize()}.")
        // Карты соперников: на двоих — одно число, на троих — по местам. За
        // столом на троих важно, у кого сколько осталось, а имя при числе
        // звучит справкой, а не речью, и падежа не требует.
        append(
            if (bots.size == 1) {
                " У соперника ${game.handOf(bots.first()).size}."
            } else {
                " У соперников: " +
                    bots.joinToString(", ") { "${names[it]} — ${game.handOf(it).size}" } + "."
            },
        )
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
     *
     * Говорит тот, кто читает экран вообще: работает скринридер — он, выключен
     * — приложение. Через [TableVoice.say], а не [TableVoice.sayRequested]:
     * свайп по руке — это чтение экрана, и голос приложения на нём перебивал
     * скринридер, из-за чего игрок слышал чужую речь вместо своей и терял
     * место, на котором остановился.
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
        voice.say("${next + 1} из ${hand.size}: ${card.spoken()}${cardVerdict(card, playable)}.")
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
                        playable = verdictOf(card, playable),
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
            if (game.rules.transfer) {
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
            //
            // Имя кнопки живёт внутри неё, а не рядом с ней.
            //
            // Так его собирает сам Compose: имя, положенное на кнопку снаружи,
            // до неё не доходит, и скринридер читает «без метки» — проверено
            // на телефоне. Внутри — доходит и склеивается с кнопкой; ровно так
            // подписаны кнопки-иконки во всём Compose. Надпись для глаза при
            // этом закрыта от скринридера, иначе он прочитает её дважды.
            Button(
                onClick = { voice.sayRequested(allowedPhrase()) },
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
                    // «Настройки» стоят вплотную перед «Выйти» (Катерина,
                    // 20.09): их и ищут подряд — сперва поправить, потом уйти.
                    // Свайп с настроек ведёт прямо на выход.
                    TextButton(onClick = { menuOpen = false; onSettings() }) { Text("Настройки") }
                    TextButton(onClick = { menuOpen = false; onExit() }) { Text("Выйти") }
                }
            },
            dismissButton = {
                Row {
                    // Правила держим рядом с «Новой партией»: если за столом
                    // что-то пошло не по-понятному, справка нужна на месте,
                    // а не после выхода в меню.
                    TextButton(onClick = { menuOpen = false; newGame() }) { Text("Новая партия") }
                    TextButton(onClick = { menuOpen = false; onRules() }) { Text("Правила") }
                }
            },
        )
    }
}

/**
 * Чей ход — словами: «Твой ход» или имя того, кто ходит.
 *
 * На двоих соперник один, и «ход соперника» называет его целиком — так было
 * до третьего места, и так остаётся. На троих их двое, и «соперник» уже
 * ничего не называет: место зовётся своим именем. Имя стоит в именительном
 * рядом с глаголом («Ходит Петя») — склонять произвольное имя программа не
 * умеет (SETTINGS.md, 8).
 */
private fun turnPhrase(game: DurakGame, names: List<String>): String = when {
    game.finished -> "Партия окончена."
    game.legalMoves(PLAYER).isNotEmpty() -> "Твой ход."
    names.size <= 2 -> "Ход соперника."
    else -> "Ходит ${names[game.turn]}."
}

private fun dealPhrase(game: DurakGame, names: List<String>): String {
    // Коротко: козырь и чей ход. Перечислять карты и порядок не надо —
    // карты игрок слушает свайпом по руке, и вслух они превращаются в
    // длинную ленту, которую он всё равно не удержит; порядок он выбрал
    // сам в настройках. Стол при раздаче пуст — о нём молчим.
    return "Раздача. Козырь — ${game.trumpSuit.title}. ${turnPhrase(game, names)}"
}

/**
 * Что сказать, когда партия поднята с диска. Игрок вернулся через час или
 * после случайного выхода и не помнит стол — напоминаем положение дел,
 * а не «продолжаем», за которым ничего не стоит.
 */
private fun resumePhrase(game: DurakGame, names: List<String>): String {
    // Стол пуст — о нём молчим: «на столе пусто» это не сведение, а шум.
    val table = if (game.table.isEmpty()) "" else " На столе: ${game.spokenTable()}."
    return "Продолжаем партию. Карт у тебя: ${game.handOf(PLAYER).size}, " +
        "в колоде: ${game.deckSize()}, козырь — ${game.trumpSuit.title}.$table " +
        turnPhrase(game, names)
}

/**
 * Свой ход — вслух. Это не «сыграна карта», а живая речь: короткая, как за
 * настоящим столом, — иначе партия превращается в перечисление. Свой ход
 * повторяет то, что игрок только что сделал сам, поэтому здесь важнее
 * точность, чем разнообразие: за столом человек и не комментирует свои
 * карты, он просто кладёт их. Речь бота — в [BotTalker].
 */
private fun ownMovePhrase(move: DurakMove, gender: Gender): String = when (move) {
    is DurakMove.Attack -> "Кладёшь ${move.card.spoken()}."
    is DurakMove.Defend -> "Отбиваешься картой ${move.card.spoken()}."
    // Не «отбиваться боту»: дательный падеж имени программа не выведет.
    // «Соседу» — потому что на троих соперников двое, и перевод уходит
    // следующему за столом, а не «сопернику» вообще.
    is DurakMove.Transfer -> "Переводишь: ${move.card.spoken()}. Отбиваться соседу."
    DurakMove.Take -> "Ты забираешь карты со стола."
    // Единственное место в фразе о ходе, где слышен род: остальные — настоящее
    // время, и в нём «ты» звучит одинаково для обоих (SETTINGS.md, 8).
    DurakMove.Pass -> "Ты ${gender.past("сказал")} бито. Стол в отбой."
}

private fun finishPhrase(
    game: DurakGame,
    score: Score,
    names: List<String>,
    gender: Gender,
): String {
    // Про соперника — в настоящем времени: «Меркурий вышел» верно только
    // для мужского имени, а имя игрок выбирает любое (SETTINGS.md, 8).
    val winner = game.winner
    val loser = game.loser
    val result = when {
        winner == null -> "Партия окончена, карт ни у кого не осталось. Ничья."
        winner == PLAYER && loser != null -> "Ты ${gender.past("вышел")}. ${names[loser]} — дурак."
        loser == PLAYER && winner != null ->
            "${names[winner]} выходит, у тебя остались карты. Ты " +
                gender.noun("дурак", "дура") + "."

        // На троих игрок может быть и ни тем, ни другим: вышел вторым —
        // значит не выиграл и не проиграл, и называть это надо как есть.
        winner != null && loser != null -> "${names[winner]} вышел, дурак — ${names[loser]}."

        else -> "Партия окончена."
    }
    return "$result ${score.spoken()}"
}
