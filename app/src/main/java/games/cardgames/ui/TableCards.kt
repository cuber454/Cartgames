package games.cardgames.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import games.engine.Card as EngineCard
import games.engine.durak.Battle

/**
 * Карты на столе — картинками.
 *
 * До этого стол был только строчкой слов: «Стол: десятка червей, девять
 * треф». Незрячему этого хватает, а зрячему — нет: он слышит карту, но не
 * видит её, и за столом с детьми («Тысяча» и «Дурак» для всех, не только
 * для незрячих) стол оставался пустым. Поэтому рисуем то же самое, что
 * называем словами.
 *
 * Слова при этом никуда не деваются: подпись «Стол: …» остаётся рядом, и
 * скринридер читает именно её. Картинки молчат ([clearAndSetSemantics]) —
 * иначе карта читалась бы дважды, словами и значками.
 *
 * Карты мельче, чем на руке: стол — это то, что уже случилось, смотреть на
 * него долго не нужно, а рука должна остаться крупной. Если карт больше,
 * чем влезает в ширину экрана, ряд прокручивается вбок.
 */
@Composable
fun TableCards(
    cards: List<EngineCard>,
    cardWidth: Dp,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cards.forEach { CardFace(it, width = cardWidth, height = cardHeight) }
    }
}

/**
 * Стопка закрытых карт — прикуп, который ещё не открывали.
 *
 * Карты в стопке сдвинуты на пару точек: одна рубашка не говорит, сколько
 * под ней лежит, а тут их две, и это единственное, что про прикуп известно,
 * пока его не взяли. Стопка молчит для скринридера: вслух про прикуп
 * говорит кнопка рядом, и говорит ровно то же — сколько и куда.
 */
@Composable
fun CardStack(
    count: Int,
    cardWidth: Dp,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val shift = 6.dp
    val steps = (count - 1).coerceAtLeast(0)
    Box(
        modifier = modifier
            .size(cardWidth + shift * steps, cardHeight + shift * steps)
            .clearAndSetSemantics {},
    ) {
        repeat(count) { index ->
            CardBack(
                width = cardWidth,
                height = cardHeight,
                modifier = Modifier.offset(x = shift * index, y = shift * index),
            )
        }
    }
}

/**
 * Стол «Дурака»: карты парами, как они и лежат — чем ходили и чем отбились.
 *
 * Пара обведена рамкой: без неё двенадцать карт подряд читаются как одна
 * куча, и не видно, какая карта какую покрыла. Отбитая карта стоит рядом с
 * ходом, чуть ниже и с отступом — так же, как она ложится поверх на
 * настоящем столе, только без поворота: повёрнутая карта теряет угловую
 * метку, а по ней карту и узнают.
 *
 * Неотбитый ход — просто карта без пары.
 */
@Composable
fun TableBattles(
    battles: List<Battle>,
    cardWidth: Dp,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        battles.forEach { battle ->
            Row(
                modifier = Modifier
                    .border(1.dp, BattleEdge, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CardFace(battle.attack, width = cardWidth, height = cardHeight)
                battle.defense?.let { defense ->
                    CardFace(
                        defense,
                        modifier = Modifier.offset(y = cardHeight * 0.12f),
                        width = cardWidth,
                        height = cardHeight,
                    )
                }
            }
        }
    }
}

/** Рамка вокруг пары: та же черта, что и у карты, только тоньше. */
private val BattleEdge = Color(0x551B1B1B)
