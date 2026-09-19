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
import games.engine.Card as EngineCard

/**
 * Карта на руке: рисунок и имя для скринридера.
 *
 * Чем сейчас нельзя сыграть — приглушено, и об этом же говорит слово в имени
 * карты ([spokenVerdict]). Картинка молчит (у неё своя пустая семантика),
 * поэтому карта читается один раз и словами.
 *
 * Подписи под картой больше нет: имя ушло в описание ([contentDescription]),
 * и видно его теперь не на экране, а на слух (Катерина, 19.09). Слово «не
 * подходит» при этом остаётся тем, ради чего карта вообще названа: без него
 * приглушённая карта молчала бы о том, что жать её нечем.
 *
 * Имя кладём внутрь карты, а не на неё снаружи: имя, положенное снаружи, до
 * нажимаемой карты не доходит, и скринридер читает «без метки» — те же грабли,
 * что были на кнопках «Что можно» и «Повтори».
 *
 * Общая на все игры: имя карты — это не оформление, а то, ради чего карта
 * вообще подписана. Разойтись по экранам оно не должно.
 *
 * [selected] — карта, до которой игрок дошёл жестом и которую сейчас
 * слышит. Отмечена рамкой, а не только голосом: карту называет речь, но
 * тому, кто видит экран плохо, нужно ещё и видеть, где он остановился.
 *
 * [playable] — не «можно ли сыграть эту карту», а вердикт целиком:
 * `null` — решать сейчас не из чего (нет игры, не наш ход, торг), и тогда
 * ни слова вердикта, ни приглушения нет. Раньше на экране без партии все
 * карты разом читались как «не подходит», хотя не подходить там было нечему
 * (Катерина, 18.09).
 */
@Composable
fun HandCard(
    card: EngineCard,
    playable: Boolean?,
    cardWidth: Dp,
    cardHeight: Dp,
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
                    contentDescription = card.spoken() + spokenVerdict(playable)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CardFace(card, width = cardWidth, height = cardHeight)
        }
    }
}
