package edu.androidsec.notes.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import edu.androidsec.notes.R
import edu.androidsec.notes.appContainer

/**
 * Ресивер кнопки «Готово» в уведомлении.
 *
 * Объявлен в манифесте с `exported="false"`: его вызывает только наш PendingIntent (система
 * отправляет интент от имени приложения), поэтому другим приложениям и `adb shell am broadcast`
 * он недоступен. Но входящий интент всё равно считается недоверенным вводом: проверяем
 * действие и extras, а не полагаемся на то, что «снаружи не придёт».
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DONE) return

        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, INVALID_ID)
        if (noteId <= 0L) {
            Log.w(TAG, "DONE with invalid note id: $noteId")
            return
        }

        Log.i(TAG, "DONE received for note $noteId")

        // Закрываем уведомление и отменяем напоминание, если оно ещё не сработало.
        NotificationManagerCompat.from(context).cancel(noteId.toInt())
        context.appContainer.reminderScheduler.cancel(noteId)
        Toast.makeText(context, R.string.reminder_done_toast, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val TAG = "ReminderActionReceiver"
        const val ACTION_DONE = "edu.androidsec.notes.action.DONE"
        const val EXTRA_NOTE_ID = "edu.androidsec.notes.extra.NOTE_ID"
        private const val INVALID_ID = -1L
    }
}
