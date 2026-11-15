package edu.androidsec.notes.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.provider.BaseColumns
import android.util.Log
import edu.androidsec.notes.appContainer

/**
 * Провайдер заголовков «для своих».
 *
 * Защита построена на двух уровнях:
 * 1. В манифесте провайдер закрыт `android:permission` с собственным разрешением
 *    [PERMISSION] уровня `signature`: его получает только приложение, подписанное тем же ключом.
 *    Это же разрешение закрывает и запись, поэтому insert/update/delete чужому не доступны.
 * 2. В коде провайдер всё равно минимален: отдаёт только путь `/notes`, только колонки `_id`
 *    и `title`, и только на чтение (insert/update/delete бросают исключение).
 *
 * Параметры selection и sortOrder в запросе к базе не используются, поэтому подставить через
 * них SQL нельзя.
 */
class TitlesProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, PATH_NOTES, CODE_NOTES)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (matcher.match(uri) != CODE_NOTES) throw IllegalArgumentException("Unknown URI: $uri")

        // Кто спрашивает: в logcat видно uid и пакет вызывающего приложения.
        Log.i(TAG, "query from uid=${Binder.getCallingUid()} package=$callingPackage")

        val columns = projection?.takeIf { it.isNotEmpty() }?.map { column ->
            require(column in ALLOWED_COLUMNS) { "Column not allowed: $column" }
            column
        } ?: ALLOWED_COLUMNS

        val context = context ?: return null
        val titles = context.appContainer.database.noteDao().titlesBlocking()

        val cursor = MatrixCursor(columns.toTypedArray(), titles.size)
        for (note in titles) {
            cursor.addRow(columns.map { column -> if (column == BaseColumns._ID) note.id else note.title })
        }
        return cursor
    }

    override fun getType(uri: Uri): String? =
        if (matcher.match(uri) == CODE_NOTES) "vnd.android.cursor.dir/vnd.$AUTHORITY.title" else null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("TitlesProvider is read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("TitlesProvider is read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("TitlesProvider is read-only")

    companion object {
        const val TAG = "TitlesProvider"
        const val AUTHORITY = "edu.androidsec.notes.provider"
        const val PERMISSION = "edu.androidsec.notes.permission.READ_NOTES"
        const val PATH_NOTES = "notes"
        const val COLUMN_TITLE = "title"

        private const val CODE_NOTES = 1
        private val ALLOWED_COLUMNS = listOf(BaseColumns._ID, COLUMN_TITLE)
    }
}
