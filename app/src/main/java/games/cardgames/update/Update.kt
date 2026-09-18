package games.cardgames.update

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import games.cardgames.diag.Journal
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Обновление приложения: узнать, что вышла новая сборка, скачать её и отдать
 * системному установщику.
 *
 * Сборки собирает и подписывает CI ([.github/workflows/ci.yml]), он же кладёт
 * к релизу файл [MANIFEST_URL] — по нему приложение и понимает, что появилось
 * новое. Приложение тянет сборку само: игрок не должен искать APK в переписке
 * и не должен помнить, какая сборка у него стоит.
 *
 * Тихой установки тут нет и быть не может: Android всегда спрашивает игрока
 * перед установкой. Наша часть — довести до системного экрана «Установить» с
 * уже скачанным и проверенным файлом; последнее слово остаётся за игроком.
 *
 * Файл, скачанный по [download], считается годным, только если сошлась его
 * сумма ([Release.sha256]): обрыв связи на середине дал бы обрезанный APK,
 * который установщик отверг бы непонятной ошибкой. Сумма — единственное, что
 * отличает целую сборку от половины.
 */
object Update {

    /**
     * Где лежит описание последней сборки. Это ссылка на файл в последнем
     * релизе, а не на конкретный релиз: подставлять сюда номер сборки значило
     * бы переписывать приложение на каждой сборке.
     */
    const val MANIFEST_URL =
        "https://github.com/cuber454/Cartgames/releases/latest/download/update.txt"

    /**
     * Подкаталог в кэше, куда ложится скачанная сборка. Кэш, а не память
     * приложения: установщику файл нужен ровно один раз, а после установки
     * эта же сборка больше не понадобится. Имя обязано совпадать с `path` в
     * `res/xml/update_paths.xml` — через него файл уходит установщику.
     */
    private const val DIR_NAME = "updates"

    private const val TIMEOUT_MS = 15000

    /** Предел на размер сборки: обрезанный или подменённый файл ронял бы память. */
    private const val MAX_APK_BYTES = 64L * 1024 * 1024

    /**
     * Скачивание идёт в одном месте на всё приложение: экран меняется,
     * игрок уходит за стол и возвращается, а загрузка в это время живёт своей
     * жизнью и досчитывает сумму. Два скачивания разом писали бы в один файл.
     */
    @Volatile
    private var downloading = false

    /**
     * Последняя сборка из файла обновления.
     *
     * [build] — то, что видит игрок: «0.9.42». [versionCode] — то, по чему
     * приложение сравнивает: он только растёт, а имя версии может и не
     * смениться. [sha256] — сумма файла сборки, [notes] — заголовок коммита,
     * из которого она собрана: по нему видно, что в сборке нового.
     */
    data class Release(
        val versionCode: Int,
        val versionName: String,
        val build: String,
        val apkUrl: String,
        val sha256: String,
        val notes: String,
    ) {
        /** Как сборку называть вслух. */
        val title: String get() = build.ifEmpty { "сборка $versionCode" }
    }

    /** Чем кончилась проверка. */
    sealed interface Check {
        /** Есть сборка новее установленной. */
        data class Fresh(val release: Release) : Check

        /** Установлена последняя: говорить нечего. */
        data object Current : Check

        /** Спросить не вышло — и вот почему. Молчать об этом нельзя. */
        data class Failed(val reason: String) : Check
    }

    /** Чем кончилось скачивание. */
    sealed interface Get {
        /** Сборка на диске и цела. */
        data class Ready(val release: Release, val apk: File) : Get

        /** Скачивание уже идёт: второй раз тянуть то же самое незачем. */
        data object Busy : Get

        data class Failed(val reason: String) : Get
    }

    /** Чем кончился заход к установщику. */
    sealed interface Install {
        /** Намерение готово: осталось показать системный экран установки. */
        data class Ready(val intent: Intent) : Install

        /**
         * Ставить пока нельзя: Android спрашивает разрешение на установку из
         * этого приложения, и спрашивает его на своём экране. Туда и ведём.
         */
        data class NeedsPermission(val intent: Intent) : Install

        data class Failed(val reason: String) : Install
    }

    /**
     * Разбор файла обновления.
     *
     * Формат — строки «ключ=значение», а не JSON: файл пишет наш же CI, читает
     * его только это приложение, и тащить на борт разборщик JSON ради шести
     * строк незачем. Режем по первому «=»: заголовок коммита в [Release.notes]
     * вполне может его содержать. Строки «#» — заметки для человека, они не
     * читаются.
     */
    fun parse(text: String): Release? {
        val fields = mutableMapOf<String, String>()
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val at = trimmed.indexOf('=')
            if (at <= 0) return@forEach
            fields[trimmed.substring(0, at).trim()] = trimmed.substring(at + 1).trim()
        }
        val code = fields["versionCode"]?.toIntOrNull() ?: return null
        val url = fields["apkUrl"]?.takeIf { it.isNotEmpty() } ?: return null
        // Сумма — не украшение: без неё целость сборки проверить нечем, и
        // файл без суммы лучше не брать вовсе, чем ставить вслепую.
        val sum = fields["sha256"]?.takeIf { it.length == 64 } ?: return null
        return Release(
            versionCode = code,
            versionName = fields["versionName"].orEmpty(),
            build = fields["build"] ?: fields["versionName"].orEmpty(),
            apkUrl = url,
            sha256 = sum.lowercase(),
            notes = fields["notes"].orEmpty(),
        )
    }

    /** Свежая ли сборка против установленной: номер сборки только растёт. */
    fun isNewer(remote: Release, localVersionCode: Int): Boolean =
        remote.versionCode > localVersionCode

    /** Спросить, что вышло. Ходит в сеть — звать не с главного потока. */
    fun check(localVersionCode: Int): Check = try {
        when (val release = parse(readText(MANIFEST_URL))) {
            null -> Check.Failed("файл обновления не разобрался")
            else -> if (isNewer(release, localVersionCode)) Check.Fresh(release) else Check.Current
        }
    } catch (e: Exception) {
        Check.Failed(e.message ?: e.javaClass.simpleName)
    }

    /**
     * Скачать сборку и проверить её сумму.
     *
     * Пишем сперва в файл с приставкой «.part», а готовый переименовываем:
     * загрузку может прервать уход из приложения или пропажа сети, и половина
     * сборки не должна лежать под именем целой — иначе её предложат
     * установить, и установщик ответит ошибкой, которую нечем объяснить.
     */
    fun download(context: Context, release: Release): Get {
        if (downloading) return Get.Busy
        downloading = true
        return try {
            val target = apkFile(context, release)
            val part = File(target.parentFile, "${target.name}.part")
            target.parentFile?.mkdirs()
            part.delete()
            val digest = MessageDigest.getInstance("SHA-256")
            withConnection(release.apkUrl) { connection ->
                var total = 0L
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_APK_BYTES) throw IOException("сборка больше $MAX_APK_BYTES байт")
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            val sum = digest.digest().joinToString("") { "%02x".format(it) }
            if (sum != release.sha256) {
                part.delete()
                Journal.note("обновление", "сборка ${release.title}: сумма не сошлась")
                return Get.Failed("сборка скачалась битой, сумма не сошлась")
            }
            target.delete()
            if (!part.renameTo(target)) throw IOException("не удалось сохранить сборку")
            Journal.note("обновление", "сборка ${release.title} скачана и проверена")
            Get.Ready(release, target)
        } catch (e: Exception) {
            Journal.note("обновление", "скачать ${release.title} не вышло: ${e.message}")
            Get.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            downloading = false
        }
    }

    /** Где лежит скачанная сборка. Имя по номеру сборки: сборки не путаются. */
    fun apkFile(context: Context, release: Release): File =
        File(File(context.cacheDir, DIR_NAME), "cartgames-${release.title}.apk")

    /** Скачана ли эта сборка целиком — по этому приложение и предлагает установку. */
    fun hasDownloaded(context: Context, release: Release): Boolean =
        apkFile(context, release).let { it.isFile && it.length() > 0L }

    /**
     * Отдать скачанную сборку системному установщику.
     *
     * Дверь наружу — тот же [FileProvider], что и у журнала, но со своим
     * корнем: журнал лежит в памяти приложения, сборка — в кэше, и открывать
     * наружу сразу оба каталога незачем.
     */
    fun install(context: Context, release: Release): Install {
        val apk = apkFile(context, release)
        if (!apk.isFile || apk.length() == 0L) return Install.Failed("сборка ещё не скачана")
        if (!canInstall(context)) return Install.NeedsPermission(permissionIntent(context))
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
            Install.Ready(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.getOrElse { Install.Failed(it.message ?: it.javaClass.simpleName) }
    }

    /**
     * Разрешено ли приложению ставить сборки. С Android 8 это отдельное
     * разрешение, и выдаётся оно на системном экране: без него установщик не
     * откроется вовсе, а игрок остался бы перед кнопкой, которая молчит.
     */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** Системный экран «Установка неизвестных приложений» для нашего пакета. */
    fun permissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )

    /**
     * Идёт ли скачивание именно сейчас. По этому кнопка меняет подпись, а
     * повторный заход не начинает вторую загрузку.
     */
    fun isDownloading(): Boolean = downloading

    /**
     * Есть ли сеть и не мобильная ли она.
     *
     * По мобильной сети сборку не тянем: она весит мегабайты, а игрок про неё
     * не просил. Узнать о новой сборке это не мешает — файл обновления весит
     * граммы, — а скачать её можно кнопкой или по вайфаю.
     */
    fun isUnmetered(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = runCatching {
            manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }
        }.getOrNull() ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /**
     * Прочитать файл по ссылке. Отдельная беда — ответ сервера: «сервер
     * ответил 404» игроку ничего не говорит, но без него не отличить
     * пропавшую сеть от пропавшего файла.
     */
    private fun readText(url: String): String = withConnection(url) { connection ->
        connection.inputStream.bufferedReader().use { it.readText() }
    }

    /**
     * Сходить по ссылке и отдать ответ. Сроки короткие: проверка идёт в
     * открывающемся меню, и ждать её дольше нескольких секунд незачем —
     * не ответили, значит связи нет.
     */
    private fun <T> withConnection(url: String, block: (HttpURLConnection) -> T): T {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("сервер ответил $code")
            return block(connection)
        } finally {
            connection.disconnect()
        }
    }
}
