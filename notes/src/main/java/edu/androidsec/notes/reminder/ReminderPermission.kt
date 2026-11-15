package edu.androidsec.notes.reminder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import edu.androidsec.notes.R

private enum class PermissionDialog { None, Rationale, Settings }

/**
 * Разрешение на уведомления запрашивается в момент нажатия «Напомнить», а не при старте.
 *
 * - есть разрешение (или API < 33) -> [onGranted];
 * - система советует объяснить -> диалог с пояснением, затем системный запрос;
 * - первый отказ -> сообщение, приложение работает дальше;
 * - повторный отказ (система больше не спрашивает) -> предложение открыть настройки.
 */
@Composable
fun rememberNotificationPermission(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnGranted by rememberUpdatedState(onGranted)
    var dialog by remember { mutableStateOf(PermissionDialog.None) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            granted -> currentOnGranted()
            // Первый отказ: система ещё готова спросить снова, просто сообщаем.
            context.shouldExplainNotifications() ->
                Toast.makeText(context, R.string.permission_denied_toast, Toast.LENGTH_SHORT).show()
            // Система больше не покажет запрос: единственный путь — настройки.
            else -> dialog = PermissionDialog.Settings
        }
    }

    fun launchSystemDialog() {
        context.markNotificationsAsked()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    when (dialog) {
        PermissionDialog.Rationale -> AlertDialog(
            onDismissRequest = { dialog = PermissionDialog.None },
            title = { Text(stringResource(R.string.permission_rationale_title)) },
            text = { Text(stringResource(R.string.permission_rationale_text)) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = PermissionDialog.None
                    launchSystemDialog()
                }) { Text(stringResource(R.string.permission_rationale_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = PermissionDialog.None }) {
                    Text(stringResource(R.string.permission_rationale_cancel))
                }
            },
        )

        PermissionDialog.Settings -> AlertDialog(
            onDismissRequest = { dialog = PermissionDialog.None },
            title = { Text(stringResource(R.string.permission_settings_title)) },
            text = { Text(stringResource(R.string.permission_settings_text)) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = PermissionDialog.None
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }) { Text(stringResource(R.string.permission_settings_open)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = PermissionDialog.None }) {
                    Text(stringResource(R.string.permission_settings_cancel))
                }
            },
        )

        PermissionDialog.None -> Unit
    }

    return {
        when {
            context.hasNotificationPermission() -> currentOnGranted()
            context.shouldExplainNotifications() -> dialog = PermissionDialog.Rationale
            // Уже спрашивали, а пояснять нечего: отказ окончательный.
            context.wasNotificationsAsked() -> dialog = PermissionDialog.Settings
            else -> launchSystemDialog()
        }
    }
}

/** Есть ли у приложения право показывать уведомления (до API 33 — всегда). */
fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

// Флаг «системный запрос уже показывали»: без него после окончательного отказа нельзя отличить
// от ситуации «ещё ни разу не спрашивали» (в обоих случаях shouldShowRequestPermissionRationale = false).
private const val PREFS = "notification_permission"
private const val KEY_ASKED = "asked"

private fun Context.wasNotificationsAsked(): Boolean =
    getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

private fun Context.markNotificationsAsked() {
    getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
}

private fun Context.shouldExplainNotifications(): Boolean {
    val activity = findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
}

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
