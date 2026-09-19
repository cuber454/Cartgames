package games.cardgames.thousand

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
import games.cardgames.settings.GameSettingStore
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
import games.cardgames.speech.cardVerdict
import games.cardgames.speech.sayEvent
import games.cardgames.speech.speech
import games.cardgames.speech.speechMs
import games.cardgames.speech.verdictOf
import games.cardgames.speech.withoutTurn
import games.cardgames.ui.HandCard
import games.cardgames.ui.CardStack
import games.cardgames.ui.TableCards
import games.cardgames.ui.TableGesture
import games.cardgames.ui.tableGestures
import games.engine.Card as EngineCard
import games.engine.Rank
import games.engine.thousand.Phase
import games.engine.thousand.RoundSummary
import games.engine.thousand.ThousandBot
import games.engine.thousand.ThousandMatch
import games.engine.thousand.ThousandMove
import games.engine.thousand.ThousandRound
import games.engine.thousand.hasMarriage
import games.engine.thousand.marriagePoints
import games.engine.thousand.orderHand
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Место игрока за столом. Соперники сидят за остальными. */
private const val PLAYER = 0

/** Место второго соперника: садится за стол, когда мест трое. */
private const val SECOND_BOT = 2

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

    // Как звать сидящих за столом, по местам: место игрока — «ты», у прочих
    // своё имя. Число мест берём у партии, а не у настроек: партия помнит
    // стол, за которым её начали, и смена настройки посреди неё имён не
    // переписывает.
    val names = seatTitles(settings, session.match.playerCount)

    // Помощник «хвалить автоматически» — из того же хранилища, что и
    // договорённости, но в правила партии не входит: он про то, кто решает,
    // а не про то, как считают.
    val autoPraise = remember { GameSettingStore(context).value(AUTO_PRAISE) }

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
    // разводить соперников положено синтезаторами, а не высотой (Катерина,
    // 19.09: «убери, чтобы повышение голоса было у каждого бота»).
    val botSpeaker = remember(
        settings.engine,
        settings.botEngineThousand,
        settings.voice,
        settings.botVoiceThousand,
        settings.botRateThousand,
        settings.rate,
    ) {
        Speaker(
            context = context,
            rate = settings.botRateThousand,
            enginePackage = botEngine(settings.botEngineThousand, settings.engine),
            voiceName = botVoice(
                settings.botVoiceThousand,
                settings.voice,
                settings.botEngineThousand,
                settings.engine,
            ),
        )
    }
    // Голос второго соперника — только когда за столом трое: на двоих второй
    // синтезатор был бы движком, которого никто не слышит.
    val botSpeakerSecond = remember(
        settings.engine,
        settings.botEngineThousandSecond,
        settings.voice,
        settings.botVoiceThousandSecond,
        settings.botRateThousandSecond,
        settings.rate,
        session.match.playerCount,
    ) {
        if (session.match.playerCount < 3) {
            null
        } else {
            Speaker(
                context = context,
                rate = settings.botRateThousandSecond,
                enginePackage = botEngine(settings.botEngineThousandSecond, settings.engine),
                voiceName = botVoice(
                    settings.botVoiceThousandSecond,
                    settings.voice,
                    settings.botEngineThousandSecond,
                    settings.engine,
                ),
            )
        }
    }
    // Слышен ли соперник отдельно от приложения: выбран свой синтезатор или
    // свой голос. Такой говорит ими и при работающем скринридере — тот
    // озвучивает приложение, а соперник говорит своим голосом. За скринридером
    // остаётся только тот, кому ничего своего не выбрали: голос у него тот же,
    // что у приложения, и своя озвучка там означала бы включившуюся
    // самоозвучку (SETTINGS.md, 8).
    val botApart = botSpeaksAlone(
        settings.botVoiceThousand,
        settings.voice,
        settings.botEngineThousand,
        settings.engine,
    )
    val botSecondApart = botSpeaksAlone(
        settings.botVoiceThousandSecond,
        settings.voice,
        settings.botEngineThousandSecond,
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
    // Открыт ли выбор марьяжа, который хвалим.
    var praiseOpen by remember { mutableStateOf(false) }
    // Открыт ли вопрос «расписаться?» — роспись кончает кон и спрашивается
    // отдельно: промахнуться по ней пальцем не должно быть накладно.
    var raspisOpen by remember { mutableStateOf(false) }
    // Открыт ли вопрос о золотом коне: он стоит вдвое дороже обычного кона
    // и берётся на всю партию, поэтому спрашивается подтверждением.
    var goldenOpen by remember { mutableStateOf(false) }
    // Карта с марьяжем, по которой нажали. У неё за столом два смысла сразу:
    // ею ходят и ею же хвалят. Молча выбрать за игрока нельзя — сыгранная
    // без похвалы, она марьяж теряет, и теряет без единого слова.
    var cardPraise by remember { mutableStateOf<ThousandMove.Praise?>(null) }
    val scale = if (settings.largeText) LARGE_SCALE else 1f
    val cardWidth: Dp = (CARD_WIDTH * scale).dp
    val cardHeight: Dp = (CARD_HEIGHT * scale).dp
    // Карты на столе — мельче рук: стол смотрят мельком, а рука должна
    // остаться крупной. Масштаб крупного текста тут не удваивается, иначе
    // стол съел бы пол-экрана.
    val tableWidth: Dp = (CARD_WIDTH * 0.55f).dp
    val tableHeight: Dp = (CARD_HEIGHT * 0.55f).dp
    val columns = if (settings.largeText) 2 else 3

    // Кто говорит за столом: приложение, скринридер или никто. В каждый
    // момент — ровно один, иначе две речи накладываются и выходит каша.
    val speech = settings.voiceMode.speech(speaker.screenReaderOn)
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Вся речь за столом — через общий [TableVoice]: паузы, арбитраж со
    // скринридером и запись фразы для «Повтори» живут там, одни на обе игры.
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

    /** Реплика бота: её можно выключить в настройках. Место задаёт голос. */
    fun sayBot(seat: Int, text: String, afterMs: Long = 0L) {
        if (!settings.botTalk) {
            session.lastPhrase = text
            return
        }
        voice.sayBot(text, seat = seat, afterMs = afterMs)
    }

    /**
     * Скорость речи места за столом: у каждого соперника своя (SETTINGS.md, 8).
     * По ней считается, сколько его фраза будет звучать.
     */
    fun seatRate(seat: Int): Float =
        if (seat == SECOND_BOT) settings.botRateThousandSecond else settings.botRateThousand

    /**
     * Разложить ли снос по получателям.
     *
     * На троих снос — это две карты двум разным людям, и одной фразой о нём
     * говорит тот, кто сносил: голосом Пети объявлялось, что Васе досталась
     * семёрка, и кто из них говорит, на слух не понять. Своя карта — своя
     * речь, и говорит её тот, кому карта ушла, своим голосом.
     *
     * Раскладываем, только если каждого получателя слышно отдельно — своим
     * голосом или своим синтезатором (см. [games.cardgames.speech.BotVoice.apart]).
     * Скринридеру снос по-прежнему называют одной фразой: голос у него один на
     * всех, и три реплики подряд он прочитает тем же голосом — только длиннее.
     * Заодно снос не раскладывается, когда реплики соперника выключены:
     * молчащий бот не заговорит и о полученной карте (Катерина, 19.09).
     */
    fun splitDiscard(seat: Int, move: ThousandMove, round: ThousandRound): Boolean {
        if (!settings.botTalk) return false
        if (move !is ThousandMove.Discard || move.cards.size < 2) return false
        // Своя карта игрока говорит «получаешь» — её всегда понятно, чья.
        return discardTargets(round, seat, move).all { (to, _) -> to == PLAYER || voice.speaksAlone(to) }
    }

    /**
     * Снос по получателям: каждый говорит о своей карте сам.
     *
     * Реплики не ждут друг друга по очереди, а ставятся в очередь сразу с
     * накопленной задержкой: [TableVoice] помнит, когда кончится последняя
     * из них, и следующий ход бота начинается после неё, а не поверх.
     * Свою карту игрок слышит голосом приложения: она про его руку, а не
     * про чужую.
     *
     * [afterMs] — сколько звучит фраза того, кто сносил: с неё и начинаем.
     */
    fun sayDiscardReceivers(seat: Int, move: ThousandMove, round: ThousandRound, afterMs: Long) {
        var wait = afterMs
        discardTargets(round, seat, move).forEach { (to, card) ->
            if (to == PLAYER) {
                val line = "Получаешь ${card.spokenAccusative()}."
                voice.say(line, afterMs = wait)
                wait += speechMs(line, settings.rate) + PHRASE_GAP_MS
            } else {
                val line = "Мне дали ${card.spokenAccusative()}."
                sayBot(to, line, afterMs = wait)
                wait += speechMs(line, seatRate(to)) + PHRASE_GAP_MS
            }
        }
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
     *
     * [speaker] — чей ход закрыл взятку. Свою взятку называют «моя», чужую —
     * по имени: «Петя берёт взятку» о самом Пете звучало бы как справка
     * о нём, а не как его собственная речь.
     *
     * [named] — фразу читает скринридер, а не сам бот: у него один голос на
     * всех, и «взятка моя» в его устах не говорит, чья она. Тогда и о своей
     * взятке бот говорит по имени, как о чужой (см. [TableVoice.speaksAlone]).
     */
    fun trickSuffix(round: ThousandRound, tricksBefore: Int, speaker: Int, named: Boolean = false): String {
        val tricks = round.tricksPlayed()
        if (tricks.size <= tricksBefore) return ""
        val trick = tricks.last()
        // Про соперника — в настоящем времени: «взял» требует рода, а имя
        // игрок выбирает любое (SETTINGS.md, 8). Заодно называется тот, кто
        // взял: на троих соперников двое, и «у соперника» уже непонятно
        // про кого.
        val who = when (trick.winner) {
            PLAYER -> "Взятка твоя"
            speaker -> if (named) "Взятку берёт ${names[speaker]}" else "Взятка моя"
            else -> "Взятку берёт ${names[trick.winner]}"
        }
        return " $who, очков ${trick.points}."
    }

    /**
     * Сигнал на исход кона: победа, поражение или болт — что случилось.
     * Играет один сигнал, а не все подряд, и возвращает, сколько он звучит:
     * фразу начинают после него, иначе нота и голос наложатся.
     *
     * Конец матча перекрывает болт в последнем коне: игроку важно, чем
     * кончилась партия, а не то, что последний кон записан в минус, — это и
     * так слышно в счёте. Обратный порядок дал бы две ноты подряд в момент,
     * когда игрок ждёт одной.
     */
    fun finishSignal(summary: RoundSummary): Long {
        if (!settings.signals) return 0L
        val winner = session.match.winner
        return when {
            winner == PLAYER -> { sounds.win(); TableSounds.WIN_MS.toLong() }
            winner != null -> { sounds.lose(); TableSounds.LOSE_MS.toLong() }
            summary.bolted.contains(PLAYER) -> { sounds.bolt(); TableSounds.BOLT_MS.toLong() }
            else -> 0L
        }
    }

    /** Кон доигран — заносим его в матч и говорим итог. */
    fun finishIfOver() {
        val round = session.round
        if (round.phase != Phase.OVER || session.recorded) return
        val summary = session.record()
        if (session.match.winner != null) session.forgetSaved()
        val signalMs = finishSignal(summary)
        voice.say(
            roundPhrase(round, summary, session.match, names),
            afterMs = if (signalMs > 0) signalMs + PHRASE_GAP_MS else 0L,
        )
    }

    fun dealNew() {
        if (settings.sounds) sounds.deal()
        // Сигнал начала идёт вместе с шорохом раздачи, а не после него:
        // ноты и шум не перекрывают друг друга на слух, а разведённые по
        // времени они растянули бы паузу перед фразой вдвое.
        if (settings.signals) sounds.start()
        // Раздача шумит почти семь десятых секунды: скажи мы сразу — голос
        // утонул бы в шорохе карт. Выключенные звуки — ждать нечего.
        val afterMs = if (settings.sounds) TableSounds.DEAL_MS + PHRASE_GAP_MS else 0L
        voice.say(dealPhrase(session.round, names), afterMs = afterMs)
    }

    fun nextRound() {
        session.nextRound()
        dealNew()
    }

    fun newMatch() {
        session.restart()
        dealNew()
    }

    /**
     * Ход игрока: фраза, звук, толчок и передача хода — в одном месте.
     *
     * [note] — приписка к фразе о том, чего ход не сделал. Одной фразой, а не
     * второй речью следом: [TableVoice.sayRequested] перебивает сказанное, и
     * отдельная приписка съела бы сам ход.
     *
     * [aloud] — ход сделан не рукой игрока, а помощником. Такой ход сказать
     * обязательно: скринридеру тут нечего читать, игрок ничего не нажимал, и
     * без этой фразы с руки молча уходит карта.
     */
    fun play(move: ThousandMove, note: String = "", aloud: Boolean = false) {
        val round = session.round
        val tricksBefore = round.tricksPlayed().size
        // Свой прикуп берут вслепую, и в руке он оказывается уже потом:
        // назвать его надо сейчас, иначе две новые карты придётся искать
        // в руке самому и на слух сравнивать с тем, что помнишь.
        val taken = move as? ThousandMove.TakePrikups
        // Снос на троих разложен по получателям: своя фраза называет только
        // снесённые карты, а о том, кому какая ушла, скажет сам получатель
        // (см. [sayDiscardReceivers]).
        val split = splitDiscard(PLAYER, move, round)
        val phrase = ownPhrase(
            move,
            prikup = taken?.let { round.prikup(it.index) }.orEmpty(),
            receivers = if (split) emptyList() else discardReceivers(round, PLAYER, move, names),
        )
        soundFor(move)
        if (settings.ownVibration) vibrations.tap()
        round.apply(PLAYER, move)
        val gap = soundGap(move)
        val afterMs = if (gap > 0) gap + PHRASE_GAP_MS else 0L
        val trick = trickSuffix(round, tricksBefore, speaker = PLAYER)
        val text = phrase + note + trick
        voice.sayOwnMove(
            text,
            afterMs = afterMs,
            // Прикуп и взятка — то, о чём скринридер сам не расскажет: он
            // прочитал карту, которой игрок ходил, а не то, что пришло в руку
            // и не то, чья это взятка. Сказать это вполголоса, «для Повтора»,
            // значит оставить игрока выяснять это самому — а взятку он у себя
            // как раз и не слышит. Поэтому такие фразы звучат всегда.
            //
            // Приписка о марьяже — из того же ряда: она отвечает не на
            // нажатие, а на то, чего ход не сделал, и молча потерять её
            // значит потерять марьяж. Игрок узнал бы о нём только по счёту
            // в конце кона (THOUSAND.md, 2.5).
            aloud = aloud || taken != null || trick.isNotEmpty() || note.isNotEmpty(),
        )
        // Своя фраза сказана — следом получатели сноса говорят каждый о своей
        // карте, своим голосом.
        if (split) {
            sayDiscardReceivers(
                PLAYER,
                move,
                round,
                afterMs = afterMs + speechMs(text, settings.rate) + PHRASE_GAP_MS,
            )
        }
        session.persist()
        finishIfOver()
        session.tick++
    }

    fun allowedPhrase(): String {
        val roundNow = session.round
        val moves = roundNow.legalMoves(PLAYER)
        // По имени, а не «ход соперника»: на троих соперников двое, и «подожди,
        // ход соперника» не говорит, кого ждать. Настоящее время — «ходит
        // Петя» — рода не требует и любого имени подходит (SETTINGS.md, 8).
        if (moves.isEmpty()) {
            return "Сейчас ходит ${names[roundNow.turn]}, подожди."
        }
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
            .forEach { parts += "похвалить ${it.card.suit.title}" }
        if (moves.contains(ThousandMove.Pass)) parts += "пас"
        if (moves.contains(ThousandMove.Raspis)) parts += "расписаться"
        if (moves.contains(ThousandMove.Golden)) parts += "объявить золотой кон"
        return "Можно: " + parts.joinToString(", ") + "."
    }

    /**
     * Отчего карту с марьяжем нельзя похвалить прямо сейчас. Марьяж объявляют
     * за столом, а не на пустой стол: пока не взято ни одной взятки, хвалить
     * нечем. Сказать об этом надо в тот же момент — иначе о потерянном
     * марьяже игрок узнаёт только по счёту в конце кона.
     */
    fun marriageNote(card: EngineCard): String {
        if (card.rank != Rank.KING && card.rank != Rank.QUEEN) return ""
        val round = session.round
        if (!round.handOf(PLAYER).hasMarriage(card.suit)) return ""
        // Хвалить уже можно — значит игрок сам выбрал сыграть без похвалы,
        // а это его решение, а не потеря по незнанию.
        if (round.tricksOf(PLAYER) > 0) return ""
        return " Марьяж ${card.suit.title} похвалить нельзя: взяток ещё нет."
    }

    /** Сыграть карту, если это сейчас можно. */
    fun playCard(card: EngineCard) {
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
        play(move, note = if (move is ThousandMove.Play) marriageNote(card) else "")
    }

    /**
     * Нажатие на карту: в сносе она уходит, в розыгрыше — играется, а если
     * ею же можно похвалить марьяж, то сначала спрашиваем, что имелось в
     * виду. Отвечать за игрока тут нельзя: сыгранный без похвалы король
     * пару уже не соберёт, а марьяж стоит до ста очков.
     */
    fun tapCard(card: EngineCard) {
        val praise = session.round.legalMoves(PLAYER)
            .filterIsInstance<ThousandMove.Praise>()
            .firstOrNull { it.card == card }
        if (praise != null) {
            cardPraise = praise
            return
        }
        playCard(card)
    }

    // Ход бота: играем за него все ходы подряд, пока ход не вернётся к игроку.
    // Эффект перезапускается при возвращении с экрана настроек и продолжает
    // партию с того же места.
    LaunchedEffect(session.tick) {
        val round = session.round
        var guard = 0
        var played = false
        // За неигровые места играем подряд, пока ход не вернётся к игроку:
        // на троих это два хода подряд, и оба — не его.
        while (!round.isFinished(round) && round.turn != PLAYER && guard++ < 60) {
            val seat = round.turn
            // Ждём не «полсекунды», а пока договорит предыдущая фраза: иначе
            // бот перебивает сам себя и слышно только последнее слово.
            delay(voice.waitMs())
            val move = ThousandBot.chooseMove(round, seat, settings.botDifficultyThousand, rng) ?: break
            val tricksBefore = round.tricksPlayed().size
            soundFor(move)
            val split = splitDiscard(seat, move, round)
            // Говорит ли бот о себе по имени. Имя нужно только там, где фразу
            // читает скринридер, и только на троих: у него один голос на всех,
            // и «называет 120» без имени не говорит, кто называет. Своим
            // голосом бот говорит о себе «я» — двоих соперников различают
            // голоса, а не имя. За столом на двоих соперник один и различать
            // нечего: там бот не называет себя даже под скринридером
            // (Катерина, 19.09).
            val named = round.playerCount >= 3 && !voice.speaksAlone(seat)
            val phrase = botPhrase(
                move,
                // Взятый прикуп открывают обоим — иначе игрок так и не узнает,
                // что ушло боту в руку, и это знание потеряно навсегда: в руку
                // соперника не заглядывают. Берёт бот прикуп вслепую, как и
                // игрок, но взятое называют вслух.
                prikup = (move as? ThousandMove.TakePrikups)?.let { round.prikup(it.index) }.orEmpty(),
                bot = if (named) names[seat] else null,
                receivers = if (split) emptyList() else discardReceivers(round, seat, move, names),
            )
            round.apply(seat, move)
            // Звук хода и реплика бота стартуют в один момент и налезают
            // друг на друга. Разводим: сначала звук, потом речь. Молчащему
            // боту ждать незачем.
            val gap = soundGap(move)
            if (settings.botTalk && gap > 0) delay(gap + PHRASE_GAP_MS)
            val line = phrase + trickSuffix(round, tricksBefore, speaker = seat, named = named)
            sayBot(seat, line)
            // Снос на троих: карты ушли двоим, и каждый получатель говорит о
            // своей сам — своим голосом. Одной фразой о сносе говорил тот,
            // кто сносил, и голосом Пети объявлялось, что Васе досталась
            // семёрка: чья это речь, на слух не понять (Катерина, 19.09).
            if (split) {
                sayDiscardReceivers(
                    seat,
                    move,
                    round,
                    afterMs = speechMs(line, seatRate(seat)) + PHRASE_GAP_MS,
                )
            }
            // Пишем после каждого хода: если приложение прибьют посреди
            // серии ходов бота, партия не откатится к её началу.
            session.persist()
            played = true
        }
        // Ход вернулся к игроку — короткий толчок и тихая нота: толчок
        // слышно не всегда, а тут понятно без слов, что ждут тебя.
        // Кон доигран — ход формально ещё за игроком, но ждать его нечего:
        // там скажет своё сигнал исхода, и две ноты подряд тут ни к чему.
        if (played && round.turn == PLAYER && round.phase != Phase.OVER) {
            if (settings.vibration) vibrations.tap()
            if (settings.signals) sounds.turn()
            // И словами. Толчок слышно не всегда, а молчание после хода бота
            // не отличить от «приложение задумалось»: игрок сидит и ждёт,
            // пока заговорит бот, которого уже никто не ждёт. Сначала
            // дослушиваем этого бота — своя фраза его перебивать не должна.
            delay(voice.waitMs())
            voice.say(TURN_PHRASE)
        }

        // Хвалить автоматически: договорённость включена — объявляем марьяж
        // сами, как только он стал возможен, и не переспрашиваем.
        //
        // Ход при этом делает та самая карта пары, которой марьяж и
        // объявляют: другого способа объявить его в «Тысяче» нет, объявление
        // и есть ход этой картой. Поэтому помощник и живёт в настройках, а не
        // зашит: он распоряжается сильнейшим и необратимым решением партии —
        // масть марьяжа становится козырем до конца кона (THOUSAND.md, 7.2).
        if (autoPraise && round.turn == PLAYER && round.phase != Phase.OVER) {
            val praise = round.legalMoves(PLAYER)
                .filterIsInstance<ThousandMove.Praise>()
                .firstOrNull()
            if (praise != null) {
                // Дослушиваем бота: он только что сходил, и его фразу перебивать
                // незачем — своя всё равно пойдёт после паузы.
                delay(voice.waitMs())
                // Пока помощник дослушивал, игрок мог опередить его: нажатая
                // карта уходит на стол его ходом, и объявлять марьяж после
                // этого нечем. Тогда помощник молчит — решение уже принято, и
                // второй ход поверх него был бы ходом за игрока.
                val stillMine = cardPraise == null && praise in round.legalMoves(PLAYER)
                // Вслух обязательно: ход сделал помощник, а не игрок. Скринридеру
                // тут читать нечего — карту никто не нажимал, — и «для Повтора»
                // значит, что с руки молча ушла карта и объявился козырь.
                if (stillMine) play(praise, aloud = true)
            }
        }

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
                    voice.say(resumePhrase(session.round, session.match, names), whenReady = true)
                }
            }

            session.lastPhrase.isBlank() -> voice.say(dealPhrase(session.round, names), whenReady = true)

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
                        if (round.declarer == PLAYER) {
                            "Прикуп твой."
                        } else {
                            "${names[round.declarer ?: PLAYER]} берёт прикуп."
                        }

                    Phase.DISCARD ->
                        if (round.declarer == PLAYER) {
                            "Снос."
                        } else {
                            "${names[round.declarer ?: PLAYER]} сносит карту."
                        }

                    Phase.PLAY -> "Розыгрыш."

                    Phase.OVER -> ""
                },
            )
            // «Ход соперника», а не по имени: «ход Меркурия» — падеж, а
            // склонять произвольное имя программа не умеет (SETTINGS.md, 8).
            append(if (yourTurn) " Твой ход." else " Ход соперника.")
        }
    }

    val info = buildString {
        append("Кон ${match.roundsPlayed + 1}.")
        // Золотой кон выбивается из всего, что игрок привык слышать за
        // столом: без торга, без прикупа, за двойные очки. Молчать об этом
        // нельзя — по одним картам этого не слышно.
        if (round.golden) append(" Золотой кон: заказ 120, очки двойные.")
        // Козырь в «Тысяче» — масть объявленного марьяжа: до первого
        // объявления его попросту нет, и молчать об этом честнее, чем
        // называть козырем что-то одно.
        round.trumpSuit?.let { append(" Козырь — ${it.title}.") }
        append(" Счёт: ${scorePhrase(match, names)}.")
        // Сложить руку в уме до ста двадцати — работа, которой за столом
        // никто не делает: зрячий видит это с одного взгляда, а на слух надо
        // пересчитать всю руку. Поэтому говорим прямо, когда объявить можно.
        if (moves.contains(ThousandMove.Golden)) {
            append(" Рука держит заказ — можно объявить золотой кон.")
        }
        if (match.barrelSeat == PLAYER) append(" Ты на бочке.")
        match.barrelSeat?.takeIf { it != PLAYER }?.let { append(" ${names[it]} на бочке.") }
        if (round.phase == Phase.PLAY) {
            append(
                " Взяток: " + (0 until match.playerCount).joinToString(", ") {
                    seatScore(it, names, "${round.tricksOf(it)}")
                } + ".",
            )
            append(
                " Очков: " + (0 until match.playerCount).joinToString(", ") {
                    seatScore(it, names, "${round.roundPoints(it)}")
                } + ".",
            )
            // Четыре туза на руке зрячий видит сразу, а на слух их надо
            // пересчитать по всей руке — и то лишь пока ни один не сыгран.
            // Про руку бота молчим: это закрытое знание, и выдавать его
            // незачем.
            if (match.rules.aceMarriage && round.hadAllAces(PLAYER)) {
                append(" Все четыре туза на руке — нужна хотя бы одна взятка.")
            }
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
    // Карты, которыми ход и вправду можно сделать. Сноса тут нет намеренно:
    // снести можно любую карту, и «подходит» на сносе — ответ не на тот
    // вопрос. Вердикт отвечает на «чем мне ходить», а не на «что отдать».
    val playable: Set<EngineCard> = moves.mapNotNull { move ->
        when (move) {
            is ThousandMove.Play -> move.card
            is ThousandMove.Praise -> move.card
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

        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = status, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(info, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                Spacer(Modifier.height(4.dp))
                Text(tableLine, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                // Стол ещё и картинками: слова читает скринридер, а зрячий
                // за тем же столом видит, чем ходили. Одно другому не мешает —
                // картинка молчит, подпись над ней говорит.
                if (table.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    TableCards(table, cardWidth = tableWidth, cardHeight = tableHeight)
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
                // Золотой кон стоит рядом с суммами, а не вместо них: выбор
                // «назвать сто или объявить золотой» — один и тот же выбор,
                // и прятать вторую половину в другой экран незачем.
                if (moves.contains(ThousandMove.Golden)) {
                    Button(
                        onClick = { goldenOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Золотой кон") }
                    Spacer(Modifier.height(8.dp))
                }
            }

            prikups.isNotEmpty() -> {
                // Прикупы стоят в ряд, а не один под другим, и каждый
                // нарисован стопкой рубашкой вниз: их выбирают между собой,
                // и «левый или правый» — это и есть выбор. Столбиком кнопки
                // читались как два шага подряд, а не как два равных
                // предложения (Катерина, 18.09).
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    prikups.forEach { take ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CardStack(
                                count = round.prikup(take.index).size,
                                cardWidth = tableWidth,
                                cardHeight = tableHeight,
                            )
                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = { play(take) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Взять прикуп ${take.index + 1}") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Марьяж объявляют картой, которой и ходят, поэтому кнопка одна,
            // а не по кнопке на карту: карт с марьяжем на руке бывает две.
            praises.isNotEmpty() -> {
                Button(
                    onClick = {
                        val only = praises.singleOrNull()
                        // Единственный марьяж — сразу, без диалога; вслух по
                        // той же причине, что и в диалогах ниже: кнопка не
                        // говорит, что ушло на стол и какой козырь объявлен.
                        if (only != null) play(only, aloud = true) else praiseOpen = true
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

        // Золотой кон — тоже вопрос, а не ход: он берётся на всю партию, и
        // промахнуться по нему пальцем дорого. Цифры называем прямо: без них
        // «золотой кон» звучит красиво и не значит ничего.
        if (goldenOpen) {
            AlertDialog(
                onDismissRequest = { goldenOpen = false },
                title = { Text("Золотой кон?") },
                text = {
                    Text(
                        "Торга не будет: ты играешь заказ 120 тем, что сдано, — прикуп не берёшь. " +
                            "Выполнишь — плюс 240, не выполнишь — минус 240. Болт в золотом коне " +
                            "считается за два.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            goldenOpen = false
                            play(ThousandMove.Golden)
                        },
                    ) { Text("Объявляю") }
                },
                dismissButton = {
                    TextButton(onClick = { goldenOpen = false }) { Text("Торгуюсь") }
                },
            )
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
                                "${round.currentBid}, " +
                                names.indices.filter { it != PLAYER }
                                    .joinToString(", ") { "${names[it]} плюс $share" } +
                                ". Карты не доигрываются.",
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
                                // Вслух — как и в диалоге карты: нажатие
                                // скринридер прочитает сам, а чем обошлось —
                                // нет (см. play, aloud).
                                play(praise, aloud = true)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Хвалить: ${praise.card.suit.title}") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { praiseOpen = false }) { Text("Отмена") }
            },
        )
    }

    // Карта, которая годится и на ход, и на похвалу. Один диалог на одну
    // карту: объявляют марьяж той же картой, которой ходят, и выбрать за
    // игрока — значит решить, нужен ли ему марьяж, не спросив его.
    cardPraise?.let { praise ->
        val suit = praise.card.suit
        AlertDialog(
            onDismissRequest = { cardPraise = null },
            title = { Text("Хвалить марьяж?") },
            text = {
                Text(
                    "${praise.card.spoken()}: похвалить — это ${marriagePoints(suit)} очков " +
                        "и козырь ${suit.name}. Или сыграть ею без похвалы.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        cardPraise = null
                        // Вслух: нажатие скринридер прочитает сам («Хвалить»),
                        // а вот чем это обошлось — что ушло на стол и какой
                        // масти теперь козырь — не скажет никто, кроме нас.
                        play(praise, aloud = true)
                    },
                ) { Text("Хвалить") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        cardPraise = null
                        playCard(praise.card)
                    },
                ) { Text("Сыграть без похвалы") }
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
 * называют — и всё. Торг, прикуп и снос названы словами, потому что это
 * не карты, а решения, и о них иначе не догадаться.
 *
 * Прикуп — исключение из краткости: [prikup] называет карты, которые бот
 * забрал. Взятый прикуп показывают обоим, а в открытую руку соперника не
 * заглядывают: не назовём сейчас — игрок не узнает этого никогда.
 *
 * [bot] — имя того, чей это ход. На троих соперников двое, и фразу говорит
 * тот из них, кто ходит: «Петя называет 120» — про Петю, а не про
 * «соперника» вообще. На двоих соперник один, и имени нет вовсе ([bot] ==
 * null): он говорит о себе «я». Так и говорят за столом — имя нужно, чтобы
 * различить двоих; названное без нужды, оно звучит объявлением («бот Петя
 * называет 120») вместо речи.
 *
 * [receivers] — кому достаются карты сноса, по одной каждому: без них на
 * троих не понять, какая карта ушла кому.
 *
 * Все здешние фразы — в настоящем времени, и это не
 * стиль, а условие: прошедшее время требует рода («Меркурий снёс», но «Соня
 * снесла»), а род произвольного имени программа не знает. Настоящее время
 * рода не требует — и имя встаёт на место без ошибки при любом выборе.
 */
private fun botPhrase(
    move: ThousandMove,
    prikup: List<EngineCard> = emptyList(),
    bot: String? = null,
    receivers: List<String> = emptyList(),
): String {
    // Третье лицо для двоих соперников и первое — для одного. Пара пишется
    // целиком: русский глагол в первом и третьем лице разный («называю» /
    // «называет»), и одной формой тут не обойтись.
    fun say(self: String, named: String): String = if (bot == null) self else "$bot $named"

    return when (move) {
        is ThousandMove.Bid -> say("Называю ${move.amount}.", "называет ${move.amount}.")
        ThousandMove.Pass -> say("Пасую.", "пасует.")
        is ThousandMove.TakePrikups ->
            if (prikup.isEmpty()) say("Беру прикуп.", "берёт прикуп.")
            else say(
                "Беру прикуп: ${prikup.joinToString(", ") { it.spoken() }}.",
                "берёт прикуп: ${prikup.joinToString(", ") { it.spoken() }}.",
            )

        // Снос уходит в закрытую, по одной карте каждому сопернику, и в чужой
        // руке эту карту потом не увидеть: не назовём сейчас — игрок найдёт её
        // только перебором руки. Кому какая досталась, называем по имени: на
        // троих карт две, и без имени непонятно, какая ушла кому. Соперник
        // один — получатель всегда игрок, и живой человек сказал бы коротко,
        // «сношу тебе семёрку крести», а не «сношу: ты получаешь семёрку».
        //
        // Пустой [receivers] — снос разложен по получателям, и это уже не
        // фраза сносчика: о своей карте каждый скажет сам (см.
        // [sayDiscardReceivers]). Ему остаётся сам снос.
        is ThousandMove.Discard ->
            when {
                bot == null && move.cards.size == 1 ->
                    "Сношу тебе ${move.cards.first().spokenAccusative()}."
                receivers.size == move.cards.size -> say(
                    "Сношу: ${receivers.joinToString(", ")}.",
                    "сносит: ${receivers.joinToString(", ")}.",
                )
                else -> say("Сношу.", "сносит.")
            }

        is ThousandMove.Praise ->
            say(
                "Хвалю ${move.card.suit.title}. Козырь — ${move.card.suit.title}.",
                "хвалит ${move.card.suit.title}. Козырь — ${move.card.suit.title}.",
            )
        ThousandMove.Golden ->
            say("Объявляю золотой кон: заказ 120, очки двойные.", "объявляет золотой кон: заказ 120, очки двойные.")
        ThousandMove.Raspis -> say("Расписываюсь.", "расписывается.")
        is ThousandMove.Play -> say("Кладу ${move.card.spoken()}.", "кладёт ${move.card.spoken()}.")
    }
}

/** Свой ход — вслух. Не «сыграна карта», а живая речь, короткая. */
private fun ownPhrase(
    move: ThousandMove,
    prikup: List<EngineCard> = emptyList(),
    receivers: List<String> = emptyList(),
): String = when (move) {
    is ThousandMove.Bid -> "Называешь ${move.amount}."
    ThousandMove.Pass -> "Ты пасуешь."
    is ThousandMove.TakePrikups ->
        if (prikup.isEmpty()) "Берёшь прикуп ${move.index + 1}."
        else "Берёшь прикуп ${move.index + 1}: ${prikup.joinToString(", ") { it.spoken() }}."
    // Снос игрока уходит соперникам, по одной карте каждому: на троих это
    // две карты двум разным людям, и кому какая — игрок иначе не узнает.
    //
    // Пустой [receivers] — снос разложен по получателям, и своя фраза
    // называет только снесённые карты: о том, кому какая ушла, скажет сам
    // получатель (см. [sayDiscardReceivers]).
    is ThousandMove.Discard ->
        if (receivers.size == move.cards.size) {
            "Сносишь: ${receivers.joinToString(", ")}."
        } else {
            "Сносишь ${move.cards.joinToString(", ") { it.spokenAccusative() }}."
        }

    // Карта названа намеренно: хвалить можно и королём, и дамой, а на стол
    // уходит ровно одна из них — без имени игрок ищет пропажу перебором руки.
    is ThousandMove.Praise ->
        "Хвалишь ${move.card.suit.title}: на стол уходит ${move.card.spoken()}. " +
            "Козырь — ${move.card.suit.title}."
    ThousandMove.Golden -> "Объявляешь золотой кон: заказ 120, прикуп не берёшь, очки двойные."
    ThousandMove.Raspis -> "Расписываешься."
    is ThousandMove.Play -> "Кладёшь ${move.card.spoken()}."
}

/**
 * Кому достаются карты сноса — «кто что получает», по одной на карту: по
 * одной каждому следующему за столом, так же, как их раздаёт движок
 * (partiesTo). Имя получателя нужно на троих: карт в сносе две, и без имени
 * непонятно, какая ушла кому.
 *
 * Глагол идёт вместе с именем, а не отдельно: получателем бывает и игрок, а
 * «ты получает» — ошибка, которую слышно. Форма глагола зависит от места, и
 * место здесь известно.
 *
 * Карта названа винительным («получаешь семёрку крести», не «семёрка
 * крести»): она тут дополнение к «получаешь», и именительный в этой роли
 * слышен как обрывок чужой фразы.
 */
private fun discardReceivers(
    round: ThousandRound,
    seat: Int,
    move: ThousandMove,
    names: List<String>,
): List<String> = discardTargets(round, seat, move).map { (to, card) ->
    val spoken = card.spokenAccusative()
    if (to == PLAYER) "ты получаешь $spoken" else "${names[to]} получает $spoken"
}

/**
 * Кому и какая карта уходит в сносе — парами «место — карта», в том же
 * порядке, как их раздаёт движок: по одной каждому следующему за столом
 * (`partiesTo`).
 *
 * Место, а не имя: имя нужно там, где карту называют вслух, а здесь решается,
 * кто получатель, — и он же говорит о своей карте сам (см.
 * [sayDiscardReceivers]).
 */
private fun discardTargets(
    round: ThousandRound,
    seat: Int,
    move: ThousandMove,
): List<Pair<Int, EngineCard>> = if (move !is ThousandMove.Discard) {
    emptyList()
} else {
    move.cards.indices.map { step ->
        ((seat + step + 1) % round.playerCount) to move.cards[step]
    }
}

private fun dealPhrase(round: ThousandRound, names: List<String>): String {
    val first = if (round.turn == PLAYER) {
        "Первое слово твоё."
    } else {
        "Первым называет ${names[round.turn]}."
    }
    return "Раздача. Торг: первое слово — сто, дальше по пять. $first"
}

/**
 * Место за столом и число при нём — без падежа: «тебе 120», «Петя 85».
 *
 * «Ты 120» на слух — обрывок, а «у тебя 120» требует падежа и у имени, а его
 * программа не знает. Дательный для игрока известен всегда, поэтому предлог
 * достаётся только ему: имя соперника остаётся названием при числе, как в
 * счёте на экране.
 */
private fun seatScore(seat: Int, names: List<String>, value: String): String =
    if (seat == PLAYER) "тебе $value" else "${names[seat]} $value"

/**
 * Счёт за столом — по всем местам, а не по двум: на троих счёт из двух чисел
 * оставил бы третьего соперника без имени и без очков.
 */
private fun scorePhrase(match: ThousandMatch, names: List<String>): String =
    (0 until match.playerCount).joinToString(", ") { seatScore(it, names, "${match.scores[it]}") }

/**
 * Что сказать, когда партия поднята с диска. Игрок вернулся через час или
 * после случайного выхода и не помнит стол — напоминаем положение дел,
 * а не «продолжаем», за которым ничего не стоит.
 */
private fun resumePhrase(
    round: ThousandRound,
    match: ThousandMatch,
    names: List<String>,
): String {
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
    val trump = round.trumpSuit?.let { " Козырь — ${it.title}." } ?: ""
    // Золотой кон поднятой партии называют первым делом: без торга и без
    // прикупа он и так выбивается из привычного хода кона, но заметить это
    // по одному лишь «розыгрыш» невозможно.
    val golden = if (round.golden) " Золотой кон: заказ 120, очки двойные." else ""
    // Тузы напоминаем и здесь: поднятая партия — та, где игрок помнит о
    // столе меньше всего, а четыре туза на руке он мог и не заметить.
    val aces = if (match.rules.aceMarriage && round.hadAllAces(PLAYER)) {
        " Все четыре туза на руке — нужна хотя бы одна взятка."
    } else {
        ""
    }
    // «Ход соперника», а не по имени: «ход Пети» — падеж, а склонять
    // произвольное имя программа не умеет (SETTINGS.md, 8).
    val turn = if (round.turn == PLAYER) " Твой ход." else " Ход соперника."
    return "Продолжаем партию. $body$golden$trump$aces " +
        "Счёт: ${scorePhrase(match, names)}.$turn"
}

/**
 * Итог кона. Считается по матчу, а не по кону: болт, бочка и победа — это
 * то, что тянется дальше одного кона, и в самом коне их не видно.
 */
private fun roundPhrase(
    round: ThousandRound,
    summary: RoundSummary,
    match: ThousandMatch,
    names: List<String>,
): String {
    val parts = mutableListOf<String>()
    val declarer = round.declarer
    val scribbler = summary.raspised
    if (scribbler != null) {
        // Тут не «набрал столько-то»: при росписи карты не доиграны, и
        // очки кона ни о чём не говорят. Говорим то, что случилось.
        //
        // Про соперника — в настоящем времени: «Меркурий расписался» верно
        // только для мужского имени, а имя игрок выбирает любое. Род
        // прошедшего времени программа угадывать не станет (см. [botPhrase]).
        val who = if (scribbler == PLAYER) "Ты расписываешься" else "${names[scribbler]} расписывается"
        parts += "Кон окончен. $who: заказ ${round.currentBid} не играется."
    } else if (declarer != null) {
        // Очки берём у матча, а не у кона: договорённости сторон добавляют
        // к кону своё уже после розыгрыша, и по взяткам их не видно. Иначе
        // сказали бы «заказ не выполнен» там, где матч только что записал
        // выполнение, — а это худшая из возможных ошибок за столом.
        val points = summary.points.getOrElse(declarer) { round.roundPoints(declarer) }
        // «Ты набрал» — род, которого у игрока никто не спрашивал: за
        // столом сидит и женщина. «У тебя 130» верно при любом.
        val who = if (declarer == PLAYER) "у тебя" else "${names[declarer]} набирает"
        val done = if (points >= round.currentBid) "Заказ выполнен." else "Заказ не выполнен."
        parts += "Кон окончен. Заказ ${round.currentBid}, $who $points. $done"
    } else {
        parts += "Кон окончен."
    }

    // Тузовый марьяж — договорённость сторон, и в очках кона выше он уже
    // сидит. Называем его отдельно: без этого «набрал 300» на ста взятках
    // звучит как ошибка счёта.
    if (summary.golden) parts += "Золотой кон: очки за него двойные."

    // Имя впереди с двоеточием, а не «у соперника»: на троих соперников
    // двое, и «у соперника» уже не говорит, у кого именно. Падежа тут не
    // нужно — имя стоит подлежащим-названием, как в счёте на экране.
    summary.aceMarried.forEach {
        parts += if (it == PLAYER) {
            "Тузовый марьяж: четыре туза и взятка — плюс 200."
        } else {
            "Тузовый марьяж — ${names[it]}: плюс 200."
        }
    }

    val writes = summary.deltas.mapIndexed { seat, delta ->
        seatScore(seat, names, writeWords(delta))
    }
    parts += "Записано: ${writes.joinToString(", ")}."

    // «Болт тебе», а сопернику — в настоящем времени: «Петя ловит болт».
    // Прошедшее («Петя поймал») потребовало бы рода, а имя игрок выбирает
    // любое (SETTINGS.md, 8).
    summary.bolted.forEach {
        parts += if (it == PLAYER) "Болт тебе." else "${names[it]} ловит болт."
    }
    summary.raspisPenalised?.let {
        parts += if (it == PLAYER) {
            "Третья роспись — штраф 120."
        } else {
            "${names[it]} расписывается третий раз — штраф 120."
        }
    }
    summary.samosvaled.forEach {
        parts += if (it == PLAYER) {
            "Самосвал: 555, и счёт сгорел — начинаешь с нуля."
        } else {
            "Самосвал — ${names[it]}: счёт сгорел."
        }
    }
    summary.barrelSat?.let {
        parts += if (it == PLAYER) "Ты садишься на бочку." else "${names[it]} садится на бочку."
    }
    summary.barrelsDropped.forEach {
        parts += if (it == PLAYER) "Ты слетаешь с бочки." else "${names[it]} слетает с бочки."
    }

    parts += "Счёт: ${scorePhrase(match, names)}."
    summary.winner?.let {
        parts += if (it == PLAYER) "Ты выигрываешь партию!" else "${names[it]} выигрывает партию."
    }
    return parts.joinToString(" ")
}

/** «+100» синтезатор читает как бог на душу положит, поэтому пишем словами. */
private fun writeWords(value: Int): String = when {
    value > 0 -> "плюс $value"
    value < 0 -> "минус ${-value}"
    else -> "ничего"
}
