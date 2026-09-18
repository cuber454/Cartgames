package games.cardgames.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Что игрок попросил движением вбок.
 *
 * Названия — по смыслу, а не по направлению: направление задаётся один раз,
 * в [sideGesture], и переставить стороны местами можно там же, одной строкой.
 */
enum class TableGesture {
    /** Дальше по руке: следующая карта. */
    NEXT_CARD,

    /** Назад по руке: предыдущая карта. */
    PREV_CARD,
}

/**
 * Порог хода. Короче — это не жест, а дрогнувшая рука: палец всегда
 * проезжает несколько точек, пока второй коснётся экрана, и без порога
 * случайное касание читало бы карту или сбивало речь.
 */
private val SWIPE_MIN = 56.dp

/**
 * Сколько отступать от боковых краёв экрана.
 *
 * У самой кромки жест забирает система — там «назад». Поэтому своим считаем
 * только ход, начатый ближе к середине экрана. Верхнего и нижнего отступа
 * здесь нет намеренно: вбок у кромки системы ничего не делает, а отнимать
 * у игрока полосы сверху и снизу незачем.
 */
private val SWIPE_EDGE = 56.dp

/**
 * Движение вбок: вправо — следующая карта, влево — предыдущая.
 *
 * Единственное место, где задано направление. Оба пути к жесту — касание и
 * запрос прокрутки — приводят сюда, так что менять стороны нужно здесь.
 */
private fun sideGesture(dx: Float): TableGesture =
    if (dx > 0) TableGesture.NEXT_CARD else TableGesture.PREV_CARD

/**
 * Движение вбок по руке — вправо и влево.
 *
 * Вверх и вниз жест не наш: рука лежит сеткой и прокручивается сама, и
 * отбирать у неё прокрутку нельзя — иначе незрячий теряет единственный
 * способ добраться до дальних карт. Вбок рука не прокручивается вовсе,
 * так что сторона свободна.
 *
 * Пальцев может быть один или два, и это не наша забота: зрячий ведёт
 * одним, игрок со скринридером — двумя, а ход мы считаем по центру
 * касаний, поэтому число пальцев на него не влияет.
 *
 * Их два пути к приложению, и оба ведут в [onGesture].
 *
 * Первый — обычное касание. Так жест доходит, когда говорит приложение:
 * касания наши, и мы сами считаем, куда поехали пальцы.
 *
 * Второй — запрос прокрутки. Скринридер забирает жест себе и приложению
 * касания может не отдать, но, разобрав его, не глотает молча: он ищет под
 * пальцем узел, который умеет прокручиваться в эту сторону, и просит
 * прокрутить его. Поэтому мы объявляем себя прокручиваемым вбок и просьбу
 * выполняем по-своему: вместо прокрутки листаем руку.
 *
 * Ни один жест не должен быть единственным способом что-то сделать: и
 * «Что можно», и «Повтори» остаются кнопками.
 */
@Composable
fun Modifier.tableGestures(
    /**
     * Где жест вбок не наш — в координатах окна. Так исключается полоса
     * стола: по ней водят пальцем, чтобы ощупать кости (см. [tableFeel]), и
     * листать ею руку заодно нельзя. По умолчанию не исключено ничего.
     */
    skip: (Offset) -> Boolean = { false },
    onGesture: (TableGesture) -> Unit,
): Modifier {
    // Лямбда переживает перерисовку, а её содержимое меняется: держим
    // свежую и читаем в момент жеста, а не в момент подписки.
    val current by rememberUpdatedState(onGesture)
    val skipNow by rememberUpdatedState(skip)

    // Где этот узел на экране: касание приходит в его собственных
    // координатах, а полоса стола задана в оконных, и свести их можно только
    // здесь. Читается в момент жеста, поэтому перерисовку переживать не надо.
    var origin by remember { mutableStateOf(Offset.Zero) }

    return this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .pointerInput(Unit) {
            val min = SWIPE_MIN.toPx()
            val edge = SWIPE_EDGE.toPx()

            awaitEachGesture {
                // Смотрим раньше детей: пока жест наш, карты и кнопки под
                // пальцами не должны считать, что их нажали.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

                // Касание пришлось на полосу стола — там ощупывают кости.
                // Уходим сразу и ничего не гасим: пусть жест доиграет тот,
                // кому он там и адресован.
                if (skipNow(down.position + origin)) return@awaitEachGesture

                var base = down.position
                var fingers = 1
                var ours = false
                var done = false

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break

                    val center = pressed.fold(Offset.Zero) { acc, c -> acc + c.position } /
                        pressed.size.toFloat()

                    // Палец пришёл или ушёл — центр касаний скачет сам по
                    // себе, без хода руки. Считаем ход от новой точки, иначе
                    // касание вторым пальцем читалось бы как рывок вбок.
                    if (pressed.size != fingers) {
                        fingers = pressed.size
                        base = center
                        continue
                    }

                    val dx = center.x - base.x
                    val dy = center.y - base.y

                    if (!ours) {
                        // Поехало вверх или вниз — это рука, а не мы. Уходим
                        // сразу и ничего не гасим: прокрутка должна пройти
                        // до конца, как будто нас тут нет.
                        if (abs(dy) >= min && abs(dy) >= abs(dx)) break
                        if (abs(dx) < min) continue
                        // Ход начат у самой кромки — там его забрала система.
                        if (base.x <= edge || base.x >= size.width - edge) break
                        ours = true
                        // Дальше карту не читаем: один ход — одна карта,
                        // иначе за один взмах рука пролистала бы всю руку.
                        current(sideGesture(dx))
                        done = true
                    }

                    if (!ours) continue

                    // Решение принято, жест наш: гасим касания, чтобы карта
                    // под пальцем не сочла себя нажатой. До этого порога мы
                    // ничего не гасили — обычное нажатие на карту работает.
                    event.changes.forEach { it.consume() }
                    if (done) break
                }
            }
        }
        .semantics {
            // Объявляем себя прокручиваемым вбок: по этой оси скринридер и
            // понимает, что сюда можно слать запрос прокрутки. Своего
            // смещения у экрана нет и не появится, а числа выбраны так,
            // чтобы прошли обе проверки Compose: прямое направление он
            // объявляет, только если value < maxValue, обратное — только
            // если value > 0. С нулём в value экран отдавал бы скринридеру
            // лишь половину направлений — вправо.
            horizontalScrollAxisRange =
                ScrollAxisRange(value = { 1f }, maxValue = { 2f }, reverseScrolling = false)
            this[SemanticsActions.ScrollBy] = AccessibilityAction<(Float, Float) -> Boolean>(null) { x, y ->
                // Вверх и вниз не наше: отказываемся, чтобы скринридер
                // прокручивал руку сам, а не гонял её через нас.
                if (x == 0f || abs(x) <= abs(y)) return@AccessibilityAction false
                // Знаки у прокрутки обратны движению пальца: вбок прокрутка
                // идёт в ту сторону, куда уехало содержимое, а палец едет
                // навстречу. Здесь наоборот — приводим к «куда поехал
                // палец», чтобы оба пути читались одинаково. Если на
                // телефоне стороны окажутся перепутаны, виноват этот минус —
                // и меняется он здесь, одной чертой на оба пути сразу.
                current(sideGesture(-x))
                true
            }
        }
}
