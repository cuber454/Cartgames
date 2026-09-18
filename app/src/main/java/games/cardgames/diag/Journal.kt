package games.cardgames.diag

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Журнал: что приложение делало и кто что сказал.
 *
 * Заведён не ради красоты, а потому что иначе о поломке приходится
 * догадываться. За столом говорят трое (приложение и два бота), экран читает
 * не приложение, а скринридер, и словами «слышу не то» это не описать: не
 * видно ни кто говорил, ни что вообще дошло до скринридера. Журнал пишет
 * факты: какая фраза кому ушла, какие кнопки есть на экране и как они
 * названы, куда игрок провёл пальцем.
 *
 * Пишется сам, без настройки: беда, о которой надо рассказать, случается до
 * того, как игрок вспомнит про переключатель. Файл лежит во внутренней
 * памяти приложения, до [MAX_BYTES] — дальше старые строки уходят, и журнал
 * не съедает память. Отправляют его сами: кнопка в настройках открывает
 * системное «Поделиться».
 *
 * Контекст запоминается один раз при запуске ([start]) — так строку журнала
 * можно записать из любого места, где есть что записать, не таща туда
 * контекст через пол-экрана.
 */
object Journal {

    private const val FILE_NAME = "cartgames-journal.txt"
    private const val MAX_BYTES = 512 * 1024L

    /**
     * Сколько строк оставить, когда файл перерос [MAX_BYTES].
     *
     * С запасом: дерево экрана — это сотня строк на один заход, и при тесном
     * пределе журнал превратился бы в одни деревья, вытеснив из него речь и
     * ходы — то, ради чего он и заведён.
     */
    private const val KEEP_LINES = 1000

    /** Сколько строк дерева писать: глубже и больше — уже не читается. */
    private const val TREE_MAX_LINES = 200
    private const val TREE_MAX_DEPTH = 10

    private var appContext: Context? = null

    /**
     * Запись — в отдельном потоке: строка журнала появляется на каждом ходу
     * и каждой фразе, и держать её на главном потоке значит платить за
     * диагностику задержкой в игре. Порядок строк сохраняется: поток один.
     */
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "журнал").apply { isDaemon = true }
    }

    private val clock = SimpleDateFormat("dd.MM HH:mm:ss", Locale.ROOT)

    fun start(context: Context) {
        appContext = context.applicationContext
        // Размер экрана — рядом с деревьями: по нему видно, попал ли узел за
        // нижний край или за полосу навигации, а без него координаты в дереве
        // говорят только «где-то там».
        val metrics = context.resources.displayMetrics
        note(
            "жизнь",
            "приложение запущено, экран ${metrics.widthPixels}x${metrics.heightPixels} px, " +
                "плотность ${metrics.density}",
        )
    }

    /** Записать строку. Без [start] молчит — до запуска писать некуда. */
    fun note(tag: String, text: String) {
        val context = appContext ?: return
        val line = "${clock.format(Date())} $tag · $text"
        writer.execute {
            runCatching {
                val file = file(context)
                file.appendText(line + "\n")
                trim(file)
            }
        }
    }

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /**
     * Отдать журнал наружу — системным «Поделиться». null, если отправлять
     * нечего.
     *
     * Файл лежит во внутренней памяти приложения, куда снаружи доступа нет.
     * [FileProvider] открывает на него одну дверь с разрешением на время
     * отправки: без него мессенджеру нечего было бы взять, а открывать всю
     * память наружу незачем.
     */
    fun shareIntent(context: Context): Intent? = runCatching {
        val file = file(context)
        if (!file.exists() || file.length() == 0L) return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.journal", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intent.createChooser(send, "Отправить журнал")
    }.getOrNull()

    /** Последние строки журнала — их читают на экране. */
    fun tail(context: Context, limit: Int = 200): List<String> = runCatching {
        val file = file(context)
        if (!file.exists()) emptyList() else file.readLines().takeLast(limit)
    }.getOrDefault(emptyList())

    /** Размер журнала словами: «12 КБ, 340 строк» — по нему видно, есть ли что слать. */
    fun sizeTitle(context: Context): String = runCatching {
        val file = file(context)
        if (!file.exists()) return@runCatching "пусто"
        val kilobytes = (file.length() + 1023) / 1024
        "$kilobytes КБ, ${file.readLines().size} строк"
    }.getOrDefault("пусто")

    fun clear() {
        val context = appContext ?: return
        writer.execute { runCatching { file(context).delete() } }
    }

    /**
     * Записать дерево доступности: то, что видит скринридер.
     *
     * Это ответ на «кнопка есть, а скринридер её не читает»: в дереве видно
     * каждое имя, которое до него дошло, и где узел стоит на экране. Узел за
     * границей экрана — отдельная беда, и по координатам она видна сразу.
     *
     * Разбор идёт через системный провайдер доступности, потому что он и есть
     * тот самый источник, из которого скринридер берёт имена, — то есть это
     * не наше представление о дереве, а само дерево.
     */
    fun dumpTree(view: View?) {
        val lines = runCatching { treeLines(view) }.getOrElse { listOf("сбой разбора: $it") }
        note(
            "экран",
            "дерево доступности (${lines.size} строк), ${hostTitle(view)}:\n" +
                lines.joinToString("\n"),
        )
    }

    /**
     * Где сам экран приложения на дисплее и сколько занимают панели системы.
     *
     * Без этого дерево отвечает «кнопка на 2280-й строке», а экран кончается
     * на 2200-й: число есть, а беды не видно. Здесь же видно и отступ, который
     * приложение себе оставило.
     */
    private fun hostTitle(view: View?): String {
        if (view == null) return "вид не найден"
        val place = IntArray(2)
        runCatching { view.getLocationOnScreen(place) }
        val insets = runCatching { ViewCompat.getRootWindowInsets(view) }.getOrNull()
        val bars = if (insets == null) {
            "панели неизвестны"
        } else {
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            "панели: сверху ${bars.top} px, снизу ${bars.bottom} px"
        }
        return "хост ${view.width}x${view.height} в точке ${place[0]},${place[1]}, $bars"
    }

    private fun treeLines(view: View?): List<String> {
        if (view == null) return listOf("вид не найден")
        val provider: AccessibilityNodeProvider =
            view.accessibilityNodeProvider ?: return listOf("провайдер доступности недоступен")
        val root = provider.createAccessibilityNodeInfo(AccessibilityNodeProvider.HOST_VIEW_ID)
            ?: return listOf("корень дерева недоступен")
        val out = mutableListOf<String>()
        if (walk(root, 0, out)) out += "…дальше дерево не записано"
        return out
    }

    /** true — упёрлись в предел и часть дерева осталась за бортом. */
    private fun walk(node: AccessibilityNodeInfo, depth: Int, out: MutableList<String>): Boolean {
        if (depth > TREE_MAX_DEPTH) return false
        if (out.size >= TREE_MAX_LINES) return true

        val parts = mutableListOf<String>()
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { parts += "имя=«$it»" }
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { parts += "текст=«$it»" }
        node.className?.toString()?.substringAfterLast('.')?.let { parts += it }
        if (node.isClickable) parts += "нажимаемая"
        if (!node.isVisibleToUser) parts += "не видна"

        val bounds = Rect().also { node.getBoundsInScreen(it) }
        out += "  ".repeat(depth) + parts.joinToString(", ") + " $bounds"

        for (index in 0 until node.childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            if (walk(child, depth + 1, out)) return true
        }
        return false
    }

    /** Файл перерос предел — оставляем хвост: свежая поломка важнее старой. */
    private fun trim(file: File) {
        if (file.length() <= MAX_BYTES) return
        val tail = file.readLines().takeLast(KEEP_LINES)
        file.writeText(tail.joinToString("\n", postfix = "\n"))
    }
}
