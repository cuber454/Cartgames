package games.cardgames.sound

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Вибрация: короткий толчок, когда соперник сходил и ждёт ответа.
 *
 * В шумном месте звук не слышен, а наушники не всегда надеты — вибрация
 * остаётся единственным способом понять, что ход перешёл к тебе. Разрешение
 * VIBRATE объявлено в манифесте.
 */
class Vibrations(context: Context) {

    private val vibrator: Vibrator? =
        runCatching { context.getSystemService(Vibrator::class.java) }.getOrNull()

    /** Ход соперника: короткий и тихий. */
    fun tap() = buzz(40L)

    /** Что-то важное: чуть заметнее. */
    fun alert() = buzz(120L)

    @Suppress("DEPRECATION")
    private fun buzz(milliseconds: Long) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            device.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            device.vibrate(milliseconds)
        }
    }
}
