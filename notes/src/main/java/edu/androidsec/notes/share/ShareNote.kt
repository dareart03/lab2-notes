package edu.androidsec.notes.share

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import edu.androidsec.notes.R
import edu.androidsec.notes.domain.Note
import java.io.File

/**
 * Экспорт заметки файлом через FileProvider.
 *
 * `file://`-ссылки на свои файлы другим приложениям не отдаются (с API 24 это
 * `FileUriExposedException`), поэтому наружу уходит `content://`-ссылка от [FileProvider].
 * Получатель не получает доступа к хранилищу приложения: ему выдаётся временное право
 * на чтение только этого URI (`FLAG_GRANT_READ_URI_PERMISSION`), и только пока он обрабатывает
 * интент. Сам провайдер не экспортирован и открывает один каталог (`cache/exports/`,
 * см. `res/xml/file_paths.xml`).
 */
object ShareNote {
    const val AUTHORITY = "edu.androidsec.notes.files"

    fun shareNoteAsFile(context: Context, note: Note): File {
        val file = NoteExporter.export(context, note)
        val uri = FileProvider.getUriForFile(context, AUTHORITY, file)

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            // clipData нужен, чтобы флаг права на чтение сработал для URI в EXTRA_STREAM.
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(send, context.getString(R.string.share_chooser_title))
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        return file
    }
}
