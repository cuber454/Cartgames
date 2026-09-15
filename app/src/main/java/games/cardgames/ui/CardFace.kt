package games.cardgames.ui

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
import androidx.compose.ui.graphics.Color
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
 * вверх ногами), у номерных карт масти разложены по лицу, у туза одна
 * большая масть, у валета, дамы и короля — крупная буква.
 *
 * Карта — только картинка: озвучку даёт подпись рядом. Поэтому вся
 * разметка внутри закрыта от скринридера ([clearAndSetSemantics]) —
 * иначе он прочитал бы значки вслух поверх подписи.
 */
private val Face = Color(0xFFFCFCFA)
private val Edge = Color(0xFF1B1B1B)
private val RedSuit = Color(0xFFB00020)
private val BlackSuit = Color(0xFF101010)

/** Раскладка мастей на номерной карте: доли от ширины и высоты. */
private val COLUMN_LEFT = 0.34f
private val COLUMN_RIGHT = 0.66f
private val ROWS_THREE = listOf(0.30f, 0.50f, 0.70f)
private val ROWS_FOUR = listOf(0.30f, 0.43f, 0.57f, 0.70f)

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

    Box(
        modifier = modifier
            .size(width, height)
            .background(Face, shape)
            .border(2.dp, Edge, shape)
            .clearAndSetSemantics {},
    ) {
        FaceOf(card, ink, width, height)

        // Метка в левом верхнем углу — как на настоящей карте.
        CornerMark(card, ink, cornerSize, Modifier.align(Alignment.TopStart).padding(4.dp))
        // И она же перевёрнутая в правом нижнем: карту видно с любой стороны.
        CornerMark(
            card = card,
            ink = ink,
            size = cornerSize,
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).rotate(180f),
        )
    }
}

/** Лицо карты без угловых меток: туз, фигура или раскладка мастей. */
@Composable
private fun FaceOf(card: Card, ink: Color, width: Dp, height: Dp) {
    when {
        card.rank == Rank.ACE -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(card.suit.sign, color = ink, fontSize = (height.value * 0.42f).sp)
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
            Text(card.suit.sign, color = ink, fontSize = (height.value * 0.20f).sp)
        }
    }
}

/** Масти, разложенные по лицу карты, как в настоящей колоде. */
@Composable
private fun PipField(card: Card, ink: Color, width: Dp, height: Dp) {
    val pip = (width.value * 0.24f).dp
    val pipSize = (width.value * 0.19f).sp

    Box(Modifier.fillMaxSize()) {
        pipLayout(card.rank).forEach { (fx, fy) ->
            Box(
                modifier = Modifier
                    .offset(x = width * fx - pip / 2, y = height * fy - pip / 2)
                    .size(pip),
                contentAlignment = Alignment.Center,
            ) {
                Text(card.suit.sign, color = ink, fontSize = pipSize)
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
                listOf(middle to 0.43f, middle to 0.57f)

        else -> emptyList()
    }
}

/** Угловая метка: достоинство над мастью. */
@Composable
private fun CornerMark(card: Card, ink: Color, size: TextUnit, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = card.rank.sign,
            color = ink,
            fontSize = size,
            lineHeight = size,
            fontWeight = FontWeight.Bold,
        )
        Text(text = card.suit.sign, color = ink, fontSize = size, lineHeight = size)
    }
}
