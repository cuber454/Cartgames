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
import games.engine.Card as EngineCard

/**
 * Карта на руке: рисунок и подпись под ним.
 *
 * Чем сейчас нельзя сыграть — приглушено. Подпись даёт скринридеру текст,
 * а картинка молчит (у неё своя пустая семантика), поэтому карта читается
 * один раз и словами.
 *
 * Общая на обе игры: подпись «не подходит» — это не оформление, а то, ради
 * чего карта вообще подписана. Разойтись по двум экранам она не должна.
 *
 * [selected] — карта, до которой игрок дошёл жестом и которую сейчас
 * слышит. Отмечена рамкой, а не только голосом: карту называет речь, но
 * тому, кто видит экран плохо, нужно ещё и видеть, где он остановился.
 */
@Composable
fun HandCard(
    card: EngineCard,
    playable: Boolean,
    cardWidth: Dp,
    cardHeight: Dp,
    largeText: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (playable) 1f else 0.45f)
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
            CardFace(card, width = cardWidth, height = cardHeight)
            Spacer(Modifier.height(4.dp))
            Text(
                // Неиграбельную карту помечаем словами, а не только
                // бледным цветом: цветом сыт не будешь, если играешь
                // на слух, а так понятно, что жать нечего.
                text = if (playable) card.spoken() else card.spoken() + " — не подходит",
                textAlign = TextAlign.Center,
                maxLines = 3,
                style = if (largeText) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodySmall
                },
            )
        }
    }
}
