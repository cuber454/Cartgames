package games.cardgames.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import games.cardgames.speech.spokenVerdict
import games.engine.tiles.Tile

/**
 * Кость на руке: рисунок и имя для скринридера — то же, что [HandCard] у карт,
 * и по тем же причинам.
 *
 * Подписи под костью больше нет: имя кости ушло в описание ([contentDescription]),
 * и наружу при этом ничего не потерялось. Кость — картинка, и она молчит (у неё
 * своя пустая семантика); слова — единственное, что делает её слышимой, — читает
 * та же строка, что и раньше, только теперь она не занимает места на экране.
 * Чем сейчас нельзя сходить — по-прежнему приглушено и сказано словом: цветом
 * сыт не будешь, если играешь на слух. Слова вердикта общие с картами — их даёт
 * [spokenVerdict].
 *
 * Имя кладём внутрь плитки, а не на неё снаружи: имя, положенное снаружи, до
 * нажимаемой плитки не доходит, и скринридер читает «без метки» — те же грабли,
 * что были на кнопках «Что можно» и «Повтори».
 *
 * [selected] — кость, до которой игрок дошёл жестом и которую сейчас слышит.
 *
 * [playable] — не «можно ли сходить этой костью», а вердикт целиком:
 * `null` — решать сейчас не из чего (не наш ход), и тогда ни слова вердикта,
 * ни приглушения нет.
 */
@Composable
fun HandTile(
    tile: Tile,
    playable: Boolean?,
    tileWidth: Dp,
    tileHeight: Dp,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (playable != false) 1f else 0.45f)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp),
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .clearAndSetSemantics {
                    contentDescription = tile.spoken() + spokenVerdict(playable)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TileFace(tile, width = tileWidth, height = tileHeight)
        }
    }
}
