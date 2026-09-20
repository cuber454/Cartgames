package games.cardgames.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import games.cardgames.diag.Journal

/**
 * Ответ системы на установку, которую приложение начало и отдало ей.
 *
 * Тихая установка кончается не там, где начинается: сборку принимает система,
 * и она же решает, чем дело кончилось. Узнать об этом можно только здесь —
 * приложению она присылает ответ на намерение, которым сессия закрывалась
 * ([Update.install]).
 *
 * Говорить отсюда нечем: у приёмника нет ни экрана, ни синтезатора, а живёт он
 * считаные мгновения. Поэтому итог ложится в память ([Update.rememberOutcome])
 * и прозвучит при следующем входе в игру. Единственное, что делается на месте,
 * — открыть системное окно, если система просит действия игрока: молча мы его
 * пропустить не имеем права, установка тогда не состоится вовсе.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val session = intent.getIntExtra(EXTRA_SESSION, -1)
        Journal.note(
            "обновление",
            "итог установки: сессия $session, статус $status" +
                if (message.isBlank()) "" else ", сказано: ${message.take(200)}",
        )

        when (status) {
            // Встало. Приложение система перезапустит уже новым, и первая же
            // его фраза — эта.
            PackageInstaller.STATUS_SUCCESS ->
                Update.rememberOutcome(context, "Обновление встало, приложение уже новое.")

            PackageInstaller.STATUS_PENDING_USER_ACTION -> askPlayer(context, intent)

            else -> Update.rememberOutcome(
                context,
                "Обновление не встало: " +
                    if (message.isBlank()) "телефон не сказал, почему." else "$message.",
            )
        }
    }

    /**
     * Система просит игрока подтвердить установку — значит, тихая дорога ему
     * недоступна (или он на старом Android, или разрешения не хватило).
     *
     * Окно берём не своё, а то, которое система положила в ответ
     * (`EXTRA_INTENT`): это её установщик, и ведёт он себя так же, как если бы
     * открылся сразу.
     */
    private fun askPlayer(context: Context, intent: Intent) {
        val window = intent.extraIntent() ?: run {
            Journal.note("обновление", "система просит подтверждения, а окна в ответе нет")
            return
        }
        window.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val shown = runCatching { context.startActivity(window) }
        if (shown.isFailure) {
            Journal.note(
                "обновление",
                "системное окно установки не открылось: " +
                    "${shown.exceptionOrNull()?.message ?: "причина неизвестна"}",
            )
            Update.rememberOutcome(
                context,
                "Обновление скачано, но окно установки открыть не вышло. " +
                    "Пришли журнал — по нему видно, что помешало.",
            )
        }
    }

    /**
     * Намерение установщика из ответа системы.
     *
     * На Android 13 и новее у этого чтения своя форма, до неё — своя: старая
     * помечена устаревшей, но работает, а новая на старых системах не
     * существует. Обе ветки нужны, и обе здесь — вместо того чтобы растить
     * из-за одной строки зависимость от новой библиотеки.
     */
    @Suppress("DEPRECATION")
    private fun Intent.extraIntent(): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    } else {
        getParcelableExtra(Intent.EXTRA_INTENT)
    }

    companion object {
        /** Действие нашего намерения: система дописывает в него итог. */
        const val ACTION_RESULT = "games.cardgames.action.INSTALL_RESULT"

        /** Номер сессии — по нему в журнале видно, о какой установке речь. */
        const val EXTRA_SESSION = "session"
    }
}
