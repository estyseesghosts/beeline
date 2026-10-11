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
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.isExactHashtag

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
    var input by remember { mutableStateOf("") }
    val valid = isExactHashtag(input)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.photo_grid_add_hashtag_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
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
