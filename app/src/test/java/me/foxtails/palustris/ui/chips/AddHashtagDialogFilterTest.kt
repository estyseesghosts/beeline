package me.foxtails.palustris.ui.chips

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AddHashtagDialogFilterTest {
    @Test
    fun typedMarkAddsNoCharacter() {
        val result = TextFieldValue("ca#", TextRange(3)).withoutHashtagMarks()
        assertEquals("ca", result.text)
        assertEquals(TextRange(2), result.selection)
    }

    @Test
    fun pastedTagKeepsTheCaretAfterTheText() {
        val result = TextFieldValue("#cats", TextRange(5)).withoutHashtagMarks()
        assertEquals("cats", result.text)
        assertEquals(TextRange(4), result.selection)
    }

    @Test
    fun pasteInTheMiddleKeepsTheCaretOnTheSameCharacter() {
        val result = TextFieldValue("a#b#c", TextRange(3)).withoutHashtagMarks()
        assertEquals("abc", result.text)
        assertEquals(TextRange(2), result.selection)
    }

    @Test
    fun valueWithoutMarksIsReturnedUnchanged() {
        val value = TextFieldValue("cats", TextRange(2), TextRange(0, 2))
        assertSame(value, value.withoutHashtagMarks())
    }
}
