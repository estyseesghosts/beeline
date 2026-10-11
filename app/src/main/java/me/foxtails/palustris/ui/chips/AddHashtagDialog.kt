package me.foxtails.palustris.ui.chips

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.isExactHashtag
import me.foxtails.palustris.domain.stripHashtagMarks

/**
 * Asks for one exact hashtag to follow or save. The catalog validates and writes it. [onAdd] closes the
 * dialog only when the write succeeds, so a failure keeps the text and shows the error.
 */
@Composable
internal fun AddHashtagDialog(
    busy: Boolean,
    error: FeedChipError?,
    onAdd: (value: String, onDone: () -> Unit) -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val input = field.text
    val valid = isExactHashtag(input)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.photo_grid_add_hashtag_title)) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = {
                    field = it.withoutHashtagMarks()
                    onClearError()
                },
                enabled = !busy,
                label = { Text(stringResource(R.string.photo_grid_hashtag_hint)) },
                singleLine = true,
                isError = input.isNotEmpty() && !valid,
                supportingText = {
                    when {
                        input.isNotEmpty() && !valid -> Text(stringResource(R.string.photo_grid_invalid_hashtag))
                        error == FeedChipError.SaveFailed -> Text(stringResource(R.string.photo_grid_preference_save_failed))
                    }
                },
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid && !busy,
                onClick = { onAdd(input) { onDismiss() } },
            ) { Text(stringResource(R.string.dialog_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

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
