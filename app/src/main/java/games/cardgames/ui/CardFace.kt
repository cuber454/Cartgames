package games.cardgames.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import games.engine.Card
import games.engine.Rank
import games.engine.Suit

/**
 * Нарисованная карта.
 *
 * Рисуем её сами, а не берём готовые картинки, и на то две причины.
 * Первая: рисунок остаётся чётким на любом экране и при любом увеличении —
 * готовый набор это набор картинок фиксированного размера, на большом
 * экране он расплывётся. Вторая: контраст и толщину линий мы задаём сами,
 * а для слабовидящего это важнее красоты.
 *
 * Карта устроена как настоящая: метка в двух углах (чтобы читалась и
 * вверх ногами), у номерных карт масти разложены по лицу и в нижней
 * половине перевёрнуты — как в настоящей колоде, у туза одна большая
 * масть, у валета, дамы и короля — фигура. Буквы русские (Т, К, Д, В):
 * приложение называет карты по-русски, и на русской колоде буквы те же.
 *
 * Карта — только картинка: озвучку даёт подпись рядом. Поэтому вся
 * разметка внутри закрыта от скринридера ([clearAndSetSemantics]) —
 * иначе он прочитал бы значки вслух поверх подписи.
 */
private val Face = Color(0xFFFCFCFA)
private val Edge = Color(0xFF1B1B1B)
private val RedSuit = Color(0xFFB00020)
private val BlackSuit = Color(0xFF101010)

/** Рубашка: тёмная спинка и светлый узор по ней — контраст для слабовидящего. */
private val BackFace = Color(0xFF23324F)
private val BackInk = Color(0x88FFFFFF)

/** Раскладка мастей на номерной карте: доли от ширины и высоты. */
private val COLUMN_LEFT = 0.34f
private val COLUMN_RIGHT = 0.66f
private val ROWS_THREE = listOf(0.30f, 0.50f, 0.70f)
private val ROWS_FOUR = listOf(0.28f, 0.42f, 0.58f, 0.72f)

@Composable
fun CardFace(
    card: Card,
    modifier: Modifier = Modifier,
    width: Dp = 64.dp,
    height: Dp = 92.dp,
) {
    val ink = when (card.suit) {
        Suit.HEARTS, Suit.DIAMONDS -> RedSuit
        else -> BlackSuit
    }
    val shape = RoundedCornerShape(6.dp)
    val cornerSize = (height.value * 0.12f).sp
    val cornerSuit = (height.value * 0.14f).dp

    Box(
        modifier = modifier
            .size(width, height)
            .background(Face, shape)
            .border(2.dp, Edge, shape)
            .clearAndSetSemantics {},
    ) {
        FaceOf(card, ink, width, height)

        // Метка в левом верхнем углу — как на настоящей карте.
        CornerMark(
            card = card,
            ink = ink,
            rankSize = cornerSize,
            suitSize = cornerSuit,
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
        )
        // И она же перевёрнутая в правом нижнем: карту видно с любой стороны.
        CornerMark(
            card = card,
            ink = ink,
            rankSize = cornerSize,
            suitSize = cornerSuit,
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).rotate(180f),
        )
    }
}

/**
 * Карта рубашкой вверх — то, что ещё не открыто.
 *
 * Рубашка нарисована, а не залита одним цветом: однотонная спинка на
 * маленькой карте читается как пустое место, а по узору сразу видно, что
 * это карта и что она закрыта. Значков масти тут нет и быть не может: эту
 * карту никто не открывал, и нарисовать на ней что-то значимое значило бы
 * соврать.
 *
 * Для скринридера рубашка молчит ([clearAndSetSemantics]): слова о том, что
 * лежит на столе, говорит подпись рядом, а не картинка. И уж тем более
 * молчит о том, что под ней, — этого не знает и само приложение.
 */
@Composable
fun CardBack(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .size(width, height)
            .background(BackFace, shape)
            .border(2.dp, Edge, shape)
            .clearAndSetSemantics {},
    ) {
        Canvas(Modifier.fillMaxSize().padding(2.dp)) {
            val step = size.minDimension * 0.24f
            val stroke = size.minDimension * 0.05f
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(BackInk, Offset(x, 0f), Offset(x + size.height, size.height), stroke)
                drawLine(BackInk, Offset(x, size.height), Offset(x + size.height, 0f), stroke)
                x += step
            }
        }
    }
}

/** Лицо карты без угловых меток: туз, фигура или раскладка мастей. */
@Composable
private fun FaceOf(card: Card, ink: Color, width: Dp, height: Dp) {
    when {
        card.rank == Rank.ACE -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SuitSign(card.suit, ink, (height.value * 0.38f).dp)
        }

        card.rank.value in 6..10 -> PipField(card, ink, width, height)

        else -> FigureFace(card, ink, width, height)
    }
}

/**
 * Валет, дама или король — фигура в пол-карты, повторённая зеркально.
 *
 * Зеркальность здесь не украшение, а то же правило, что у угловой метки:
 * карту кладут на стол как попало, и перевёрнутая картинка не должна
 * означать ничего другого. Поэтому фигура нарисована дважды — вторая
 * половина есть первая, повёрнутая на пол-оборота вокруг центра, — и карта
 * читается с любого конца, как в настоящей колоде.
 *
 * Различаются фигуры силуэтом, а не подписью: у короля корона, у дамы
 * кокошник, у валета шляпа с пером. Силуэт читается на самом мелком
 * размере, где ни лиц, ни узоров всё равно не разглядеть; буквы В, Д и К
 * при этом остаются в углах — там, где их ищут.
 *
 * Знак масти один и стоит посередине, где сходятся половины: на такой
 * карте одной масти довольно, а середина иначе пустовала бы.
 */
@Composable
private fun FigureFace(card: Card, ink: Color, width: Dp, height: Dp) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            // Толщину линий задаём от размера карты: на крупной карте тонкая
            // линия слабовидящему не видна, на мелкой — заливает рисунок.
            val pen = size.minDimension * 0.035f
            drawHalf(card.rank, ink, FaceGrid(size.width, size.height, mirror = false), pen)
            drawHalf(card.rank, ink, FaceGrid(size.width, size.height, mirror = true), pen)
        }
        // Знак масти — поверх фигуры и ровно посередине: там сходятся её
        // половины, и там же у настоящей карты стоит масть.
        SuitSign(
            card.suit,
            ink,
            (height.value * 0.18f).dp,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/**
 * Половина фигуры: голова, плечи и головной убор по достоинству.
 *
 * Все доли — от ширины и высоты карты, а не в точках: карта рисуется в
 * разных размерах, и фигура должна меняться вместе с ней. [mirror] —
 * та же фигура, повёрнутая на пол-оборота: её и рисует вторая половина.
 */
private fun DrawScope.drawHalf(rank: Rank, ink: Color, grid: FaceGrid, pen: Float) {
    val head = grid.at(HEAD_X, HEAD_Y)
    val headRadius = HEAD_R * size.width

    // Плечи и шея — контуром, а не заливкой: середина карты остаётся
    // светлой, и знак масти поверх неё читается. Низ срезан по линии сгиба:
    // половины сходятся по ней встык, как у настоящей двуглавой карты, и
    // фигура от этого читается корпусом, а не овалом.
    val bust = Path().apply {
        val fold = grid.at(0.50f, 0.50f)
        moveTo(fold.x, fold.y)
        val leftEdge = grid.at(0.12f, 0.50f)
        lineTo(leftEdge.x, leftEdge.y)
        val leftSlope = grid.at(0.245f, 0.325f)
        lineTo(leftSlope.x, leftSlope.y)
        curveTo(grid, 0.30f to 0.275f, 0.375f to 0.245f, 0.44f to 0.225f)
        // Шея: отрезок под головой, целиком закрытый ею.
        val neckRight = grid.at(0.56f, 0.225f)
        lineTo(neckRight.x, neckRight.y)
        curveTo(grid, 0.625f to 0.245f, 0.70f to 0.275f, 0.755f to 0.325f)
        val rightEdge = grid.at(0.88f, 0.50f)
        lineTo(rightEdge.x, rightEdge.y)
        lineTo(fold.x, fold.y)
    }
    drawPath(bust, ink, style = Stroke(width = pen, join = StrokeJoin.Round, cap = StrokeCap.Round))

    drawCircle(ink, headRadius, head)

    when (rank) {
        // Корона: зубцами вверх, поверх головы.
        Rank.KING -> drawPath(
            filled(
                grid,
                listOf(
                    0.40f to 0.115f, 0.40f to 0.045f, 0.45f to 0.09f,
                    0.50f to 0.038f,
                    0.55f to 0.09f, 0.60f to 0.045f, 0.60f to 0.115f,
                ),
            ),
            ink,
        )

        // Кокошник: широкий веер за головой. Он шире самой головы — по
        // одному силуэту видно, что это дама, а не кто-то ещё.
        Rank.QUEEN -> drawPath(
            Path().apply {
                val start = grid.at(0.33f, 0.185f)
                moveTo(start.x, start.y)
                curveTo(grid, 0.34f to 0.075f, 0.42f to 0.045f, 0.50f to 0.045f)
                curveTo(grid, 0.58f to 0.045f, 0.66f to 0.075f, 0.67f to 0.185f)
                curveTo(grid, 0.62f to 0.14f, 0.56f to 0.125f, 0.50f to 0.125f)
                curveTo(grid, 0.44f to 0.125f, 0.38f to 0.14f, 0.33f to 0.185f)
                close()
            },
            ink,
        )

        // Шляпа с пером: поля, невысокая тулья и перо сбоку.
        Rank.JACK -> {
            drawPath(
                filled(
                    grid,
                    listOf(0.365f to 0.155f, 0.635f to 0.155f, 0.60f to 0.115f, 0.40f to 0.115f),
                ),
                ink,
            )
            drawPath(
                filled(
                    grid,
                    listOf(0.43f to 0.115f, 0.44f to 0.065f, 0.56f to 0.065f, 0.57f to 0.115f),
                ),
                ink,
            )
            drawPath(
                Path().apply {
                    val start = grid.at(0.565f, 0.108f)
                    moveTo(start.x, start.y)
                    curveTo(grid, 0.62f to 0.09f, 0.67f to 0.065f, 0.70f to 0.04f)
                    curveTo(grid, 0.655f to 0.06f, 0.60f to 0.085f, 0.565f to 0.108f)
                    close()
                },
                ink,
            )
        }

        else -> Unit
    }
}

/**
 * Точка на карте по долям её ширины и высоты.
 *
 * При [mirror] точка отражается от центра карты — так вторая половина
 * фигуры получается из первой поворотом на пол-оборота, без разворотов
 * холста: отражение точки по обеим осям есть тот же поворот.
 */
private class FaceGrid(val width: Float, val height: Float, val mirror: Boolean) {
    fun at(x: Float, y: Float): Offset {
        val px = if (mirror) 1f - x else x
        val py = if (mirror) 1f - y else y
        return Offset(px * width, py * height)
    }
}

/** Кривая Безье по долям карты: две опорные точки и конец. */
private fun Path.curveTo(
    grid: FaceGrid,
    control1: Pair<Float, Float>,
    control2: Pair<Float, Float>,
    end: Pair<Float, Float>,
) {
    val c1 = grid.at(control1.first, control1.second)
    val c2 = grid.at(control2.first, control2.second)
    val e = grid.at(end.first, end.second)
    cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
}

/** Замкнутая фигура по долям карты: углы и есть её контур. */
private fun filled(grid: FaceGrid, vararg corners: List<Pair<Float, Float>>): Path {
    val points = corners.flatten()
    return Path().apply {
        points.forEachIndexed { index, (x, y) ->
            val point = grid.at(x, y)
            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
        close()
    }
}

/** Голова фигуры: одна и та же у всех троих, различаются они убором. */
private const val HEAD_X = 0.50f
private const val HEAD_Y = 0.175f

/** Радиус головы — доля ширины карты, а не высоты: иначе на вытянутой карте она вытянется. */
private const val HEAD_R = 0.10f

/** Масти, разложенные по лицу карты, как в настоящей колоде. */
@Composable
private fun PipField(card: Card, ink: Color, width: Dp, height: Dp) {
    val pip = (width.value * 0.19f).dp

    Box(Modifier.fillMaxSize()) {
        pipLayout(card.rank).forEach { (fx, fy) ->
            Box(
                modifier = Modifier
                    .offset(x = width * fx - pip / 2, y = height * fy - pip / 2)
                    .size(pip)
                    // Нижняя половина карты — та же верхняя, но вверх ногами:
                    // так на настоящей карте, и так сразу видно, где у неё низ.
                    .rotate(if (fy > 0.5f) 180f else 0f),
                contentAlignment = Alignment.Center,
            ) {
                SuitSign(card.suit, ink, pip)
            }
        }
    }
}

/** Где стоят масти у шестёрки, семёрки, восьмёрки, девятки и десятки. */
private fun pipLayout(rank: Rank): List<Pair<Float, Float>> {
    val columns = listOf(COLUMN_LEFT, COLUMN_RIGHT)
    val middle = 0.5f
    return when (rank) {
        Rank.SIX -> ROWS_THREE.flatMap { y -> columns.map { x -> x to y } }

        Rank.SEVEN ->
            ROWS_THREE.flatMap { y -> columns.map { x -> x to y } } + (middle to 0.40f)

        Rank.EIGHT ->
            ROWS_THREE.flatMap { y -> columns.map { x -> x to y } } +
                listOf(middle to 0.36f, middle to 0.64f)

        Rank.NINE ->
            ROWS_FOUR.flatMap { y -> columns.map { x -> x to y } } + (middle to middle)

        Rank.TEN ->
            ROWS_FOUR.flatMap { y -> columns.map { x -> x to y } } +
                listOf(middle to 0.42f, middle to 0.58f)

        else -> emptyList()
    }
}

/** Угловая метка: достоинство над мастью. */
@Composable
private fun CornerMark(card: Card, ink: Color, rankSize: TextUnit, suitSize: Dp, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = card.rank.sign,
            color = ink,
            fontSize = rankSize,
            lineHeight = rankSize,
            fontWeight = FontWeight.Bold,
        )
        SuitSign(card.suit, ink, suitSize)
    }
}

/**
 * Значок масти, нарисованный нами, а не взятый из шрифта.
 *
 * В шрифте ♠♥♦♣ — символы Юникода с двумя начертаниями, текстовым и
 * эмодзи, и Android нередко выбирает эмодзи. Цветной эмодзи перекрасить
 * нельзя: наш красный и чёрный к нему не применяются, и на карте вместо
 * масти оказывается глянцевое сердечко из мессенджера. Нарисованный
 * значок ведёт себя как всё остальное на карте: наш цвет, наша толщина,
 * чёткий на любом размере.
 */
@Composable
private fun SuitSign(suit: Suit, ink: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        drawPath(suitPath(suit, this.size.minDimension), ink)
    }
}

/** Контур масти в квадрате со стороной [s]: доли от 0 до 1. */
private fun suitPath(suit: Suit, s: Float): Path {
    fun x(v: Float) = v * s
    fun y(v: Float) = v * s
    return Path().apply {
        when (suit) {
            // Сердце: две доли сверху, острие снизу.
            Suit.HEARTS -> {
                moveTo(x(0.50f), y(0.98f))
                cubicTo(x(0.06f), y(0.62f), x(0.02f), y(0.34f), x(0.24f), y(0.17f))
                cubicTo(x(0.40f), y(0.05f), x(0.50f), y(0.16f), x(0.50f), y(0.30f))
                cubicTo(x(0.50f), y(0.16f), x(0.60f), y(0.05f), x(0.76f), y(0.17f))
                cubicTo(x(0.98f), y(0.34f), x(0.94f), y(0.62f), x(0.50f), y(0.98f))
            }

            // Бубна: ромб.
            Suit.DIAMONDS -> {
                moveTo(x(0.50f), y(0.02f))
                lineTo(x(0.96f), y(0.50f))
                lineTo(x(0.50f), y(0.98f))
                lineTo(x(0.04f), y(0.50f))
            }

            // Пика: перевёрнутое сердце с ножкой.
            Suit.SPADES -> {
                moveTo(x(0.50f), y(0.02f))
                cubicTo(x(0.50f), y(0.30f), x(0.04f), y(0.48f), x(0.04f), y(0.70f))
                cubicTo(x(0.04f), y(0.86f), x(0.20f), y(0.92f), x(0.34f), y(0.84f))
                lineTo(x(0.27f), y(1.00f))
                lineTo(x(0.73f), y(1.00f))
                lineTo(x(0.66f), y(0.84f))
                cubicTo(x(0.80f), y(0.92f), x(0.96f), y(0.86f), x(0.96f), y(0.70f))
                cubicTo(x(0.96f), y(0.48f), x(0.50f), y(0.30f), x(0.50f), y(0.02f))
            }

            // Трефа: три круга и ножка.
            Suit.CLUBS -> {
                addOval(Rect(center = Offset(x(0.50f), y(0.28f)), radius = 0.24f * s))
                addOval(Rect(center = Offset(x(0.26f), y(0.60f)), radius = 0.24f * s))
                addOval(Rect(center = Offset(x(0.74f), y(0.60f)), radius = 0.24f * s))
                moveTo(x(0.40f), y(0.62f))
                lineTo(x(0.60f), y(0.62f))
                lineTo(x(0.67f), y(1.00f))
                lineTo(x(0.33f), y(1.00f))
            }
        }
        close()
    }
}
