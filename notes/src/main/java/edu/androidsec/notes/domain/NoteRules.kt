package edu.androidsec.notes.domain

enum class FieldError {
    TITLE_TOO_LONG,
    TITLE_MULTILINE,
    BODY_TOO_LONG,
    TAGS_TOO_MANY,
    TAG_TOO_LONG,
    TAG_BAD_CHARS,
}

/** Заметка нарушает правила [NoteRules]. Бросается репозиторием при попытке сохранить такую заметку. */
class InvalidNoteException(val error: FieldError) : IllegalArgumentException("Invalid note: $error")

/**
 * Единственное место, где записаны правила ввода. Их используют и поля на экране,
 * и (позже) репозиторий: экран можно обойти, поэтому проверка нужна в обоих местах.
 *
 * Все проверки работают по принципу белого списка: разрешено только перечисленное,
 * всё остальное отклоняется. Проверки годятся и для промежуточного ввода (например,
 * хвостовой пробел в тегах допустим, пока пользователь печатает следующий тег).
 */
object NoteRules {
    const val TITLE_MAX = 100
    const val BODY_MAX = 5000
    const val TAGS_MAX = 5
    const val TAG_MAX_LEN = 20

    fun checkTitle(value: CharSequence): FieldError? = when {
        value.any { it == '\n' || it == '\r' } -> FieldError.TITLE_MULTILINE
        value.length > TITLE_MAX -> FieldError.TITLE_TOO_LONG
        else -> null
    }

    fun checkBody(value: CharSequence): FieldError? =
        if (value.length > BODY_MAX) FieldError.BODY_TOO_LONG else null

    /** Теги через пробел; в каждом тоже только `a-z`, `0-9` и `-`. */
    fun checkTags(value: CharSequence): FieldError? {
        if (value.any { !(it in 'a'..'z' || it in '0'..'9' || it == '-' || it == ' ') }) {
            return FieldError.TAG_BAD_CHARS
        }
        val tags = splitTags(value)
        return when {
            tags.size > TAGS_MAX -> FieldError.TAGS_TOO_MANY
            tags.any { it.length > TAG_MAX_LEN } -> FieldError.TAG_TOO_LONG
            else -> null
        }
    }

    fun splitTags(value: CharSequence): List<String> = value.split(' ').filter { it.isNotEmpty() }

    /** Лишние пробелы между тегами убираются, теги склеиваются одним пробелом. */
    fun normalizeTags(value: CharSequence): String = splitTags(value).joinToString(" ")

    /** Первая найденная ошибка или `null`, если заметка допустима. */
    fun validate(title: CharSequence, body: CharSequence, tags: CharSequence): FieldError? =
        checkTitle(title) ?: checkBody(body) ?: checkTags(tags)
}
