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
import java.net.UnknownHostException
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
     * Запасной адрес той же новости — ветка `release-info` того же репозитория,
     * отданная через CDN jsDelivr. Ветку переписывает CI на каждой сборке
     * (`ci.yml`, шаг «Опубликовать новость»), а имя ветки здесь и там обязано
     * совпадать.
     */
    const val MANIFEST_URL_CDN =
        "https://cdn.jsdelivr.net/gh/cuber454/Cartgames@release-info/update.txt"

    /**
     * Дороги к новости о сборке, по порядку. Имена нужны журналу: по ним видно,
     * какая дорога сработала, а какая отвалилась.
     *
     * Дорог несколько потому, что одной мало. 19.09 у Катерины телефон перестал
     * разрешать имя `github.com` — «Unable to resolve host» в журнале, — и
     * проверка обновления умерла вместе с именем: сборки у неё на руках
     * остаются, а новости о новой взять неоткуда. Прямой адрес пробуется первым:
     * он единственный, кому не нужен посредник, и на незаблокированной сети
     * работает как раньше. Дальше — зеркала-посредники: они ходят на GitHub со
     * своей стороны, и имя `github.com` телефону разрешать уже не нужно.
     *
     * Ходить через посредника не страшно: сборка принимается только с сошедшейся
     * суммой ([Release.sha256]), а подписан её наш ключ. Подменить сборку
     * посредник не может — Android не примет файл с чужой подписью; испортить
     * ответ он может, но это видно и стоит одной неудачной проверки.
     */
    private val MANIFEST_ROADS: List<Road> = listOf(
        Road("github", MANIFEST_URL),
        Road("jsdelivr", MANIFEST_URL_CDN),
        Road("gh-proxy", "https://gh-proxy.com/$MANIFEST_URL"),
        Road("ghfast", "https://ghfast.top/$MANIFEST_URL"),
    )

    /**
     * Дороги к самой сборке: зеркало приставляется к ссылке из файла обновления.
     * Первой идёт прямая — на ней посредника нет вовсе.
     */
    private val APK_MIRRORS: List<Road> = listOf(
        Road("github", ""),
        Road("gh-proxy", "https://gh-proxy.com/"),
        Road("ghfast", "https://ghfast.top/"),
    )

    /** Дорога: [name] — как звать её в журнале, [url] — куда идти. */
    internal data class Road(val name: String, val url: String)

    /**
     * Ссылки на сборку по порядку: сначала прямая, потом через зеркала.
     * Зеркало не разбирает ссылку, а приставляет себя спереди — так оно работает
     * с любым адресом, и разбирать чужие адреса нам не приходится.
     */
    internal fun apkRoads(url: String): List<Road> =
        APK_MIRRORS.map { Road(it.name, it.url + url) }

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
        /**
         * Намерение готово: осталось показать системный экран установки.
         *
         * [installer] — имя пакета, который этот экран покажет, если телефон
         * его назвал. Это не условие, а след для журнала: по нему видно, ушло
         * ли намерение системному установщику или не ушло никому, — а гадать
         * об этом потом нечем, окно чужое и в наш журнал ничего не пишет.
         */
        data class Ready(val intent: Intent, val installer: String? = null) : Install

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

    /**
     * Спросить, что вышло, — по дорогам по очереди. Ходит в сеть: звать не с
     * главного потока.
     *
     * Игроку при неудаче говорим коротко, а какая дорога и на чём споткнулась —
     * в журнал: вслух это лента, которую не удержать, а в журнале её видно
     * построчно и есть что показать.
     *
     * Отдельная беда — когда ни одно имя не разрешается. Это не «сервер
     * молчит», и починить это в приложении нечем: имена ищет система. 19.09
     * так и вышло — у приложения на телефоне был закрыт доступ к интернету в
     * разрешениях, и все дороги разом отвечали «Unable to resolve host», а
     * игрок видел только «ни одна дорога не ответила» и не знал, куда смотреть.
     * Поэтому такой отказ называется своим именем и говорит, где искать.
     */
    fun check(localVersionCode: Int): Check {
        val trouble = mutableListOf<String>()
        // Все дороги разом не нашли имя — это не дорога виновата, а доступ к
        // сети. Любая другая беда (ответ сервера, разбор файла) снимает флаг.
        var namesUnresolved = true
        for (road in MANIFEST_ROADS) {
            val release = try {
                parse(readText(road.url))
            } catch (e: UnknownHostException) {
                trouble += "${road.name}: имя не разрешилось (${e.message})"
                continue
            } catch (e: Exception) {
                namesUnresolved = false
                trouble += "${road.name}: ${e.message ?: e.javaClass.simpleName}"
                continue
            }
            if (release == null) {
                namesUnresolved = false
                trouble += "${road.name}: файл обновления не разобрался"
                continue
            }
            Journal.note("обновление", "новость о сборке взята дорогой «${road.name}»")
            return if (isNewer(release, localVersionCode)) Check.Fresh(release) else Check.Current
        }
        Journal.note("обновление", "ни одна дорога не ответила: ${trouble.joinToString("; ")}")
        return Check.Failed(
            if (namesUnresolved) {
                "телефон не находит ни одного адреса — проверь, открыт ли приложению интернет " +
                    "в разрешениях телефона"
            } else {
                "ни одна дорога не ответила"
            },
        )
    }

    /**
     * Скачать сборку и проверить её сумму — по дорогам по очереди, пока
     * какая-нибудь не отдаст целый файл.
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
            val trouble = mutableListOf<String>()
            for (road in apkRoads(release.apkUrl)) {
                when (val got = fetch(context, release, road)) {
                    is Fetch.Done -> {
                        Journal.note(
                            "обновление",
                            "сборка ${release.title} скачана и проверена: дорога «${road.name}»",
                        )
                        return Get.Ready(release, got.apk)
                    }

                    is Fetch.Failed -> trouble += "${road.name}: ${got.reason}"
                }
            }
            Journal.note("обновление", "скачать ${release.title} не вышло: ${trouble.joinToString("; ")}")
            Get.Failed("ни одна дорога сборку не отдала")
        } finally {
            downloading = false
        }
    }

    /** Чем кончился один заход за сборкой по одной дороге. */
    private sealed interface Fetch {
        data class Done(val apk: File) : Fetch

        data class Failed(val reason: String) : Fetch
    }

    /**
     * Один заход за сборкой по одной дороге: скачать, посчитать сумму, поставить
     * файл на место.
     *
     * Сумма проверяется здесь же, до того как файл получит имя целого: половина
     * сборки под честным именем — это предложение установить то, что установщик
     * отвергнет ошибкой, которую нечем объяснить. Не сошлась — дорога
     * попробуется ещё раз через зеркало: у посредника ответ мог испортиться в
     * пути.
     */
    private fun fetch(context: Context, release: Release, road: Road): Fetch {
        val target = apkFile(context, release)
        val part = File(target.parentFile, "${target.name}.part")
        return try {
            target.parentFile?.mkdirs()
            part.delete()
            val digest = MessageDigest.getInstance("SHA-256")
            withConnection(road.url) { connection ->
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
                Fetch.Failed("сумма не сошлась")
            } else {
                target.delete()
                if (!part.renameTo(target)) throw IOException("не удалось сохранить сборку")
                Fetch.Done(target)
            }
        } catch (e: Exception) {
            part.delete()
            Fetch.Failed(e.message ?: e.javaClass.simpleName)
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
        if (!canInstall(context)) {
            Journal.note("обновление", "ставим нельзя: разрешение на установку из приложения не выдано")
            return Install.NeedsPermission(permissionIntent(context))
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Install.Ready(intent, installerOf(context, intent))
        }.getOrElse {
            Journal.note("обновление", "намерение установки не собралось: ${it.message ?: it.javaClass.simpleName}")
            Install.Failed(it.message ?: it.javaClass.simpleName)
        }
    }

    /**
     * Кто в телефоне возьмётся показать системное окно установки.
     *
     * Пусто — не приговор и не отказ: на Android 11 и новее приложение видит
     * чужие пакеты, только если спросило о них в `<queries>` (см. манифест).
     * Ответ идёт в журнал: когда окно не появилось, по одной этой строке видно,
     * было ли кому его показывать.
     */
    private fun installerOf(context: Context, intent: Intent): String? = runCatching {
        context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
    }.getOrNull()

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
