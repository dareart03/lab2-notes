package edu.androidsec.notes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteRulesTest {

    // --- допустимые значения ---

    @Test
    fun validNote() = assertNull(NoteRules.validate("Заголовок", "Текст", "kotlin android-sec 2026"))

    @Test
    fun emptyFieldsAreValid() = assertNull(NoteRules.validate("", "", ""))

    @Test
    fun titleAtLimit() = assertNull(NoteRules.checkTitle("a".repeat(100)))

    @Test
    fun bodyAtLimit() = assertNull(NoteRules.checkBody("a".repeat(5000)))

    @Test
    fun bodyMayContainLineBreaks() = assertNull(NoteRules.checkBody("первая\nвторая\r\nтретья"))

    @Test
    fun tagWithDigitsAndHyphen() = assertNull(NoteRules.checkTags("a-1 b2-c3"))

    // --- превышение длины ---

    @Test
    fun titleTooLong() =
        assertEquals(FieldError.TITLE_TOO_LONG, NoteRules.checkTitle("a".repeat(101)))

    @Test
    fun bodyTooLong() =
        assertEquals(FieldError.BODY_TOO_LONG, NoteRules.checkBody("a".repeat(5001)))

    @Test
    fun titleWithLineBreakIsRejected() {
        assertEquals(FieldError.TITLE_MULTILINE, NoteRules.checkTitle("первая\nвторая"))
        assertEquals(FieldError.TITLE_MULTILINE, NoteRules.checkTitle("первая\rвторая"))
    }

    // --- неверные символы ---

    @Test
    fun tagWithUppercaseIsRejected() = assertEquals(FieldError.TAG_BAD_CHARS, NoteRules.checkTags("Kotlin"))

    @Test
    fun tagWithUnderscoreIsRejected() = assertEquals(FieldError.TAG_BAD_CHARS, NoteRules.checkTags("a_b"))

    @Test
    fun tagWithCyrillicIsRejected() = assertEquals(FieldError.TAG_BAD_CHARS, NoteRules.checkTags("тег"))

    @Test
    fun tagWithTabOrNewlineIsRejected() {
        assertEquals(FieldError.TAG_BAD_CHARS, NoteRules.checkTags("a\tb"))
        assertEquals(FieldError.TAG_BAD_CHARS, NoteRules.checkTags("a\nb"))
    }

    @Test
    fun tagWithSqlCharactersIsRejected() =
        assertEquals(FieldError.TAG_BAD_CHARS, NoteRules.checkTags("a';--"))

    // --- неверный тег (длина) ---

    @Test
    fun tagAtLimit() = assertNull(NoteRules.checkTags("a".repeat(20)))

    @Test
    fun tagTooLong() = assertEquals(FieldError.TAG_TOO_LONG, NoteRules.checkTags("a".repeat(21)))

    @Test
    fun oneLongTagAmongValidOnes() =
        assertEquals(FieldError.TAG_TOO_LONG, NoteRules.checkTags("ok " + "a".repeat(21) + " fine"))

    // --- слишком много тегов ---

    @Test
    fun fiveTagsAreAllowed() = assertNull(NoteRules.checkTags("a b c d e"))

    @Test
    fun sixTagsAreRejected() = assertEquals(FieldError.TAGS_TOO_MANY, NoteRules.checkTags("a b c d e f"))

    @Test
    fun extraSpacesDoNotCountAsTags() = assertNull(NoteRules.checkTags("  a   b  c d  e  "))

    @Test
    fun trailingSpaceWhileTypingIsAllowed() = assertNull(NoteRules.checkTags("a b "))

    // --- нормализация и общий вердикт ---

    @Test
    fun normalizeCollapsesSpaces() = assertEquals("a b c", NoteRules.normalizeTags("  a  b c "))

    @Test
    fun validateReportsFirstError() {
        assertEquals(FieldError.TITLE_TOO_LONG, NoteRules.validate("a".repeat(101), "a".repeat(5001), "A"))
        assertEquals(FieldError.BODY_TOO_LONG, NoteRules.validate("ok", "a".repeat(5001), "A"))
        assertEquals(FieldError.TAG_BAD_CHARS, NoteRules.validate("ok", "ok", "A"))
    }
}
