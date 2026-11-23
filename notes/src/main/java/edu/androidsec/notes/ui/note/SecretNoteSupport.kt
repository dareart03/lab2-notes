package edu.androidsec.notes.ui.note

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import edu.androidsec.notes.R

/**
 * Пока [enabled] (открыта секретная заметка), окно защищено флагом `FLAG_SECURE`:
 * скриншоты и запись экрана не работают, а в списке «Недавние» вместо содержимого пустой превью.
 * Когда заметка не секретная или экран закрыт, флаг снимается, и у обычных заметок скриншоты работают.
 */
@Composable
fun SecureWindowEffect(enabled: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, enabled) {
        if (enabled) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val TAG = "SecretNote"

/**
 * Копирует [text] в буфер обмена. Для секретной заметки ([sensitive]) содержимое помечается как
 * чувствительное: с Android 13 система не показывает его в плашке буфера обмена и в предложениях клавиатуры.
 * На более старых версиях метка безвредна, но и не действует.
 */
fun copyNoteText(context: Context, text: String, sensitive: Boolean) {
    val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ClipDescription.EXTRA_IS_SENSITIVE
    } else {
        "android.content.extra.IS_SENSITIVE"
    }
    val clip = ClipData.newPlainText(context.getString(R.string.app_name), text)
    if (sensitive) {
        clip.description.extras = PersistableBundle().apply { putBoolean(key, true) }
    }
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(clip)

    // Проверка без плашки системы: читаем метку из буфера обратно (сам текст в лог не пишем).
    val storedFlag = clipboard.primaryClipDescription?.extras?.getBoolean(key, false)
    Log.d(TAG, "Clipboard copy: sensitive requested=$sensitive, flag stored in clipboard=$storedFlag")

    // Начиная с Android 13 система сама показывает подтверждение копирования.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.note_copied, Toast.LENGTH_SHORT).show()
    }
}
