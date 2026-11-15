package edu.androidsec.notes.deeplink

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

/** Что пришло во входящем интенте Activity. */
sealed interface IncomingNote {
    /** Открыть заметку с корректным id (существует ли она, проверит экран заметки). */
    data class Open(val id: Long) : IncomingNote

    /** Ссылка на заметку есть, но параметр некорректен: показать сообщение, ничего не открывать. */
    data object Invalid : IncomingNote

    /** Обычный запуск без ссылки. */
    data object None : IncomingNote
}

/**
 * Deep link `notes://note/<id>`.
 *
 * Данные интента — недоверенный ввод: любое приложение может отправить VIEW с любым URI.
 * Поэтому принимается только схема `notes`, хост `note` и ровно один сегмент пути,
 * состоящий из цифр и помещающийся в положительное Long. Всё остальное — [IncomingNote.Invalid].
 */
object NoteDeepLink {
    const val SCHEME = "notes"
    const val HOST = "note"

    /** Ссылка на заметку в фиксированном формате `notes://note/<id>`. */
    fun uriFor(noteId: Long): Uri = "$SCHEME://$HOST/$noteId".toUri()

    fun parse(intent: Intent?): IncomingNote {
        // Обычный запуск (MAIN/LAUNCHER) или интент без ссылки — ничего открывать не нужно.
        if (intent == null || intent.action != Intent.ACTION_VIEW) return IncomingNote.None
        val uri = intent.data ?: return IncomingNote.None

        if (uri.scheme != SCHEME || uri.host != HOST) return IncomingNote.Invalid
        // Лишние части ссылки (user@host, порт) не ожидаются: считаем такую ссылку подозрительной.
        if (uri.userInfo != null || uri.port != -1) return IncomingNote.Invalid

        val segments = uri.pathSegments
        if (segments.size != 1) return IncomingNote.Invalid

        val raw = segments[0]
        // Только цифры: toLongOrNull() принял бы и «+5», и «-5».
        if (raw.isEmpty() || !raw.all { it in '0'..'9' }) return IncomingNote.Invalid

        val id = raw.toLongOrNull() ?: return IncomingNote.Invalid // переполнение Long
        return if (id > 0L) IncomingNote.Open(id) else IncomingNote.Invalid
    }
}
