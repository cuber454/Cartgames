package games.cardgames.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
 * масть, у валета, дамы и короля — крупная буква. Буквы русские (Т, К, Д,
 * В): приложение называет карты по-русски, и на русской колоде буквы те же.
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

        else -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = card.rank.sign,
                color = ink,
                fontSize = (height.value * 0.30f).sp,
                fontWeight = FontWeight.Bold,
            )
            SuitSign(card.suit, ink, (height.value * 0.22f).dp)
        }
    }
}

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
private fun SuitSign(suit: Suit, ink: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
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
