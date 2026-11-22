package edu.androidsec.notes.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LikeEscapeTest {

    @Test
    fun plainTextIsUnchanged() = assertEquals("hello мир 123", "hello мир 123".escapeForLike())

    @Test
    fun percentIsEscaped() = assertEquals("100\\%", "100%".escapeForLike())

    @Test
    fun underscoreIsEscaped() = assertEquals("a\\_b", "a_b".escapeForLike())

    @Test
    fun backslashIsEscaped() = assertEquals("C:\\\\temp", "C:\\temp".escapeForLike())

    @Test
    fun backslashIsEscapedFirst() {
        // Если экранировать % до \, слэш, добавленный для %, задвоился бы: "\\\%" вместо "\\%".
        assertEquals("\\\\\\%", "\\%".escapeForLike())
    }

    @Test
    fun sqlQuotesAreLeftAlone() {
        // Кавычки не опасны: строка идёт параметром, а не вклеивается в SQL.
        assertEquals("' OR 1=1 --", "' OR 1=1 --".escapeForLike())
    }

    @Test
    fun emptyStringStaysEmpty() = assertEquals("", "".escapeForLike())
}
