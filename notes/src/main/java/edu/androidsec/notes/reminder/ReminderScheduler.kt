package edu.androidsec.notes.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import edu.androidsec.notes.MainActivity
import edu.androidsec.notes.R
import edu.androidsec.notes.deeplink.NoteDeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.annotation.SuppressLint

/**
 * Планировщик напоминаний. Для лабораторной достаточно задержки в корутине уровня процесса:
 * настоящие напоминания делаются через AlarmManager/WorkManager, это тема другой работы.
 *
 * Уведомление содержит два PendingIntent. Оба создаются с `FLAG_IMMUTABLE` и ЯВНЫМ интентом
 * на собственный компонент (компонент задан классом), поэтому получатель PendingIntent
 * (система, другое приложение) не может подменить ни компонент, ни действие, ни extras:
 * - нажатие на уведомление: VIEW `notes://note/<id>` на [MainActivity];
 * - кнопка «Готово»: broadcast с действием DONE на [ReminderActionReceiver], который не экспортирован.
 */
class ReminderScheduler(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val jobs = HashMap<Long, Job>()

    fun schedule(noteId: Long, title: String, delayMillis: Long = DEFAULT_DELAY_MILLIS) {
        val text = title.ifBlank { context.getString(R.string.note_untitled) }
        synchronized(jobs) {
            jobs.remove(noteId)?.cancel()
            jobs[noteId] = scope.launch {
                delay(delayMillis)
                showNotification(noteId, text)
            }
        }
    }

    fun cancel(noteId: Long) {
        synchronized(jobs) { jobs.remove(noteId)?.cancel() }
    }
    // Разрешение проверяется ниже через hasNotificationPermission(); Lint эту обёртку не распознаёт.
    @SuppressLint("MissingPermission")
    private fun showNotification(noteId: Long, title: String) {
        if (!context.hasNotificationPermission()) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted, reminder for note $noteId dropped")
            return
        }

        // Явный интент: компонент указан классом MainActivity, а не только действием и данными.
        val openIntent = Intent(Intent.ACTION_VIEW, NoteDeepLink.uriFor(noteId), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val contentIntent = PendingIntent.getActivity(
            context,
            noteId.toInt(),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Явный интент на ресивер, закрытый для других приложений.
        val doneIntent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(ReminderActionReceiver.ACTION_DONE)
            .putExtra(ReminderActionReceiver.EXTRA_NOTE_ID, noteId)
        val doneAction = PendingIntent.getBroadcast(
            context,
            noteId.toInt(),
            doneIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_notification, context.getString(R.string.reminder_action_done), doneAction)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(noteId.toInt(), notification)
    }

    companion object {
        const val TAG = "ReminderScheduler"
        /** Id канала фиксирован заданием. */
        const val CHANNEL_ID = "reminders"
        const val DEFAULT_DELAY_MILLIS = 10_000L
    }
}
