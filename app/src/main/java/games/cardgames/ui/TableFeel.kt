package games.cardgames.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp

/**
 * Ощупывание стола: палец едет вдоль линии, и кость под пальцем называется.
 *
 * Так же, как за настоящим столом: рука сама находит кость, трогает соседнюю,
 * возвращается назад. Пальцем по экрану — то же самое, и для того, кто читает
 * стол на слух, это единственный способ узнать линию по частям: и текст
 * «Стол: три-четыре, шесть-шесть», и кнопка «Что на столе» называют её
 * целиком и сразу, а по одной кости не дают.
 *
 * Считаем не по костям, а по клеткам: линия разложена ровными клетками
 * [tile] шириной с промежутком [gap], и место пальца делится на шаг клетки.
 * Поэтому кость под пальцем — ровно та, что под ним нарисована, а не
 * соседняя. В промежутке между костями называется левая: промахнуться в
 * щель и услышать пустоту — хуже, чем услышать соседнюю.
 *
 * Касания гасим: кость на столе — картинка, нажимать её нечего. А чтобы
 * ощупывание не листало заодно руку, полосу стола исключает из жеста вбок
 * его же `skip` (см. [tableGestures]) — на экране это одна и та же полоса,
 * и жест вбок там не наш.
 *
 * @param count сколько костей лежит на столе.
 * @param onTile кость под пальцем, номер с нуля от левого края.
 */
@Composable
fun Modifier.tableFeel(count: Int, tile: Dp, gap: Dp, onTile: (Int) -> Unit): Modifier {
    // Лямбда переживает перерисовку, а её содержимое меняется вместе с ходом:
    // держим свежую и читаем в момент жеста.
    val current by rememberUpdatedState(onTile)

    return this.pointerInput(count, tile, gap) {
        if (count <= 0) return@pointerInput
        val stride = (tile + gap).toPx()

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

            var felt = -1

            /** Назвать кость под пальцем, если палец перешёл на другую. */
            fun feel(x: Float) {
                val index = (x / stride).toInt().coerceIn(0, count - 1)
                if (index == felt) return
                felt = index
                current(index)
            }

            // Касание стола — уже ощупывание: первая кость называется сразу,
            // не дожидаясь, пока палец куда-то поедет.
            feel(down.position.x)
            down.consume()

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break

                // Пальцев может быть один или два — как и у жеста вбок,
                // считаем по центру касаний: число пальцев на ощупывание
                // не влияет.
                val center = pressed.fold(Offset.Zero) { acc, change -> acc + change.position } /
                    pressed.size.toFloat()
                feel(center.x)
                event.changes.forEach { it.consume() }
            }
        }
    }
}
