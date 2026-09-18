package games.cardgames.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import games.cardgames.speech.spokenVerdict
import games.engine.tiles.Tile

/**
 * Кость на руке: рисунок и подпись под ним — то же, что [HandCard] у карт,
 * и по тем же причинам.
 *
 * Подпись даёт скринридеру текст, а картинка молчит (у неё своя пустая
 * семантика), поэтому кость читается один раз и словами. Чем сейчас нельзя
 * сходить — приглушено и помечено словом: цветом сыт не будешь, если играешь
 * на слух. Слова вердикта общие с картами — их даёт [spokenVerdict].
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
    largeText: Boolean,
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
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TileFace(tile, width = tileWidth, height = tileHeight)
            Spacer(Modifier.height(4.dp))
            Text(
                text = tile.spoken() + spokenVerdict(playable),
                textAlign = TextAlign.Center,
                maxLines = 2,
                style = if (largeText) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodySmall
                },
            )
        }
    }
}
