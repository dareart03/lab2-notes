package edu.androidsec.notes.ui.note

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import edu.androidsec.notes.R
import edu.androidsec.notes.domain.FieldError

/**
 * Текстовое поле с правилом: состояние ([state]), фильтр ввода ([transformation]) и последняя
 * причина отказа ([error]). Недопустимое изменение не «исправляется», а откатывается целиком.
 */
class ValidatedField(private val check: (CharSequence) -> FieldError?) {
    val state = TextFieldState()

    var error by mutableStateOf<FieldError?>(null)
        private set

    val transformation = InputTransformation {
        val found = check(asCharSequence())
        if (found != null) revertAllChanges()
        error = found
    }

    val text: String get() = state.text.toString()

    /** Программная установка текста (загрузка из базы): фильтр ввода к ней не применяется. */
    fun set(value: String) {
        error = null
        state.setTextAndPlaceCursorAtEnd(value)
    }
}

@StringRes
fun FieldError.messageRes(): Int = when (this) {
    FieldError.TITLE_TOO_LONG -> R.string.error_title_too_long
    FieldError.TITLE_MULTILINE -> R.string.error_title_multiline
    FieldError.BODY_TOO_LONG -> R.string.error_body_too_long
    FieldError.TAGS_TOO_MANY -> R.string.error_tags_too_many
    FieldError.TAG_TOO_LONG -> R.string.error_tag_too_long
    FieldError.TAG_BAD_CHARS -> R.string.error_tag_chars
}

/** Поле ввода с подсказкой под ним: либо причина отказа, либо счётчик символов / подсказка. */
@Composable
fun ValidatedTextField(
    field: ValidatedField,
    @StringRes label: Int,
    maxLength: Int?,
    singleLine: Boolean = false,
) {
    val error = field.error
    OutlinedTextField(
        state = field.state,
        inputTransformation = field.transformation,
        lineLimits = if (singleLine) {
            TextFieldLineLimits.SingleLine
        } else {
            TextFieldLineLimits.MultiLine(minHeightInLines = 4)
        },
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = {
            Text(
                error?.let { stringResource(it.messageRes()) }
                    ?: maxLength?.let { "${field.state.text.length}/$it" }
                    ?: stringResource(R.string.note_tags_hint),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
