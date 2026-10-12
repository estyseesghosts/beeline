package me.foxtails.palustris.ui.chips

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import me.foxtails.palustris.domain.stripHashtagMarks

/**
 * Removes `#` marks typed, pasted or composed into the field and keeps the caret on the same character.
 * A value without marks returns unchanged, so an IME composition in progress is not disturbed.
 */
internal fun TextFieldValue.withoutHashtagMarks(): TextFieldValue {
    val stripped = stripHashtagMarks(text)
    if (stripped == text) return this
    fun shifted(offset: Int) = stripHashtagMarks(text.substring(0, offset)).length
    return TextFieldValue(
        text = stripped,
        selection = TextRange(shifted(selection.start), shifted(selection.end)),
    )
}
