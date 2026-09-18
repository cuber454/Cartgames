package games.cardgames.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import games.engine.tiles.Tile

/**
 * Нарисованная кость домино.
 *
 * Рисуем сами, как и карты, и по тем же двум причинам: рисунок остаётся
 * чётким на любом экране, а контраст и толщину линий задаём мы, а не набор
 * картинок (см. `CardFace`).
 *
 * Кость — две половины и перегородка между ними. Половины задаются числом
 * точек, а не костью: одна и та же кость лежит на руке и на столе по-разному
 * повёрнутой, и рисующему нужно знать, какая половина с какой стороны
 * ([games.engine.kozel.Line.laid]).
 *
 * Кость — только картинка: вслух её называет подпись рядом, поэтому от
 * скринридера разметка закрыта ([clearAndSetSemantics]).
 */
private val TilePaper = Color(0xFFFCFCFA)
private val TileEdge = Color(0xFF1B1B1B)
private val PipInk = Color(0xFF101010)

/** Отступ точек от края половины — доля от высоты кости. */
private const val PAD = 0.16f

@Composable
fun TileFace(
    leftHalf: Int,
    rightHalf: Int,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(height.value * 0.16f)
    Box(
        modifier = modifier
            .size(width, height)
            // Кость лежит на столе, а не нарисована на нём: лёгкая тень
            // отделяет её от фона и от соседней кости. Контраста она не
            // трогает — точки и рамка остаются чёрными по бумаге: за этим
            // столом играют и те, кому видно плохо.
            .shadow(1.5.dp, shape)
            .background(TilePaper, shape)
            .border(2.dp, TileEdge, shape)
            .clearAndSetSemantics {},
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = size.height * PAD
            // Перегородка: она и делит кость на половины, и говорит, что
            // перед тобой кость, а не карта.
            drawLine(
                color = TileEdge,
                start = Offset(size.width / 2f, pad * 0.5f),
                end = Offset(size.width / 2f, size.height - pad * 0.5f),
                strokeWidth = size.height * 0.05f,
            )
            drawHalf(leftHalf, Offset(0f, 0f), Size(size.width / 2f, size.height), pad)
            drawHalf(rightHalf, Offset(size.width / 2f, 0f), Size(size.width / 2f, size.height), pad)
        }
    }
}

/**
 * Кость, какой её называют вслух: [Tile.spoken] произносит старшую половину
 * первой, и на рисунке она стоит слева — слышимое и видимое совпадают.
 */
@Composable
fun TileFace(tile: Tile, width: Dp, height: Dp, modifier: Modifier = Modifier) =
    TileFace(tile.high, tile.low, width, height, modifier)

/** Точки на одной половине кости. */
private fun DrawScope.drawHalf(pips: Int, origin: Offset, cell: Size, pad: Float) {
    val spots = pipSpots(pips)
    if (spots.isEmpty()) return

    val left = origin.x + pad
    val top = origin.y + pad
    val usableWidth = cell.width - pad * 2
    val usableHeight = cell.height - pad * 2
    val radius = minOf(usableWidth, usableHeight) * 0.14f

    spots.forEach { (fx, fy) ->
        drawCircle(PipInk, radius, Offset(left + usableWidth * fx, top + usableHeight * fy))
    }
}

/**
 * Где стоят точки: доли внутри половины. Раскладка та же, что на настоящей
 * кости, — по ней число и узнают, не считая.
 */
private fun pipSpots(pips: Int): List<Pair<Float, Float>> {
    val near = 0f
    val middle = 0.5f
    val far = 1f
    return when (pips) {
        1 -> listOf(middle to middle)
        2 -> listOf(near to near, far to far)
        3 -> listOf(near to near, middle to middle, far to far)
        4 -> CORNERS
        5 -> CORNERS + (middle to middle)
        6 -> listOf(
            near to near, near to middle, near to far,
            far to near, far to middle, far to far,
        )

        else -> emptyList()
    }
}

private val CORNERS = listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)
