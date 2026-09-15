package games.cardgames.durak

import android.content.Context
import games.cardgames.GAME_DURAK
import java.io.File

/**
 * Незаконченная партия на диске.
 *
 * Отдельным файлом, а не в настройках: настройки — это маленькие ключи,
 * к которым лезут из каждого экрана, а тут целый текст партии. Файл лежит
 * во внутренней папке приложения, то есть переживает и выход, и перезапуск
 * телефона, и не требует никаких разрешений.
 *
 * Файл свой у каждой игры: недоигранный дурак и недоигранная тысяча — это
 * две разные партии, и одна не должна затирать другую.
 *
 * Ошибки чтения и записи глушим: не сохранилась партия — обидно, но игра
 * из-за этого падать не должна.
 */
private const val LEGACY_FILE_NAME = "game.save"

private fun fileOf(context: Context, game: String) = File(context.filesDir, "game-$game.save")

fun saveGame(context: Context, game: String, text: String) {
    runCatching { fileOf(context, game).writeText(text) }
}

/** Текст сохранённой партии или null, если её нет. */
fun loadGameText(context: Context, game: String): String? = runCatching {
    fileOf(context, game).takeIf { it.isFile }?.readText()
        // До 0.8 игра была одна, и партия лежала под общим именем. Читаем
        // её оттуда: иначе обновление молча стирает недоигранный дурак.
        ?: if (game == GAME_DURAK) {
            File(context.filesDir, LEGACY_FILE_NAME).takeIf { it.isFile }?.readText()
        } else {
            null
        }
}.getOrNull()

fun clearGame(context: Context, game: String) {
    runCatching { fileOf(context, game).delete() }
    // Старое имя убираем вместе с новым: иначе брошенная партия воскреснет
    // после «начать новую» — из файла, о котором уже никто не помнит.
    if (game == GAME_DURAK) runCatching { File(context.filesDir, LEGACY_FILE_NAME).delete() }
}
