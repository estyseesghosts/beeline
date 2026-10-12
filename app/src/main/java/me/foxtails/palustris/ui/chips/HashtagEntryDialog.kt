package me.foxtails.palustris.ui.chips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.hashtagIdentity
import me.foxtails.palustris.domain.isExactHashtag
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.shell.FeedChipsContract

/** A group needs at least this many tags. */
private const val MIN_GROUP_TAGS = 2

/**
 * Asks for one exact hashtag to follow or save. Where the source can search several tags at once, a second
 * button turns the same dialog into a local hashtag group: more tag fields, an Add more button, a remove
 * button on each extra field and an optional group name. The catalog validates and writes. The dialog
 * closes only when the write succeeds, so a failure keeps the text and shows the error. [onSelect]
 * receives the new chip.
 */
@Composable
internal fun HashtagEntryDialog(
    chips: FeedChipsContract,
    onSelect: (FeedSource) -> Unit,
    onDismiss: () -> Unit,
) {
    val fields = remember { mutableStateListOf(TextFieldValue("")) }
    var name by remember { mutableStateOf("") }
    var grouping by remember { mutableStateOf(false) }
    val busy = chips.state.busy
    val tags = fields.map { it.text }
    val filled = tags.filter { it.isNotEmpty() }
    val valid = if (grouping) {
        filled.all(::isExactHashtag) && filled.distinctBy(::hashtagIdentity).size >= MIN_GROUP_TAGS
    } else {
        isExactHashtag(tags.first())
    }
    val maxTags = chips.state.maxGroupTags
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.photo_grid_add_hashtag_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEachIndexed { index, field ->
                    HashtagEntryField(
                        value = field,
                        onValueChange = {
                            fields[index] = it.withoutHashtagMarks()
                            chips.actions.clearError()
                        },
                        enabled = !busy,
                        showError = chips.state.error == FeedChipError.SaveFailed && index == 0,
                        onRemove = if (grouping && fields.size > MIN_GROUP_TAGS) ({ fields.removeAt(index) }) else null,
                        modifier = Modifier.testTag("hashtag_entry_field_$index"),
                    )
                }
                if (grouping) {
                    if (fields.size < maxTags) {
                        TextButton(onClick = { fields.add(TextFieldValue("")) }, enabled = !busy) {
                            Text(stringResource(R.string.hashtag_group_add_more))
                        }
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        enabled = !busy,
                        label = { Text(stringResource(R.string.hashtag_group_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("hashtag_group_name"),
                    )
                } else if (maxTags >= MIN_GROUP_TAGS) {
                    TextButton(
                        onClick = {
                            grouping = true
                            fields.add(TextFieldValue(""))
                        },
                        enabled = !busy,
                        modifier = Modifier.testTag("hashtag_group_create"),
                    ) { Text(stringResource(R.string.hashtag_group_create)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !busy,
                onClick = {
                    if (grouping) {
                        chips.actions.addHashtagGroup(name, filled) { added ->
                            onSelect(added)
                            onDismiss()
                        }
                    } else {
                        chips.actions.addHashtag(tags.first()) { added ->
                            onSelect(added)
                            onDismiss()
                        }
                    }
                },
            ) { Text(stringResource(R.string.dialog_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

@Composable
private fun HashtagEntryField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    enabled: Boolean,
    showError: Boolean,
    onRemove: (() -> Unit)?,
    modifier: Modifier,
) {
    val invalid = value.text.isNotEmpty() && !isExactHashtag(value.text)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            label = { Text(stringResource(R.string.photo_grid_hashtag_hint)) },
            singleLine = true,
            isError = invalid,
            supportingText = {
                when {
                    invalid -> Text(stringResource(R.string.photo_grid_invalid_hashtag))
                    showError -> Text(stringResource(R.string.photo_grid_preference_save_failed))
                }
            },
            modifier = modifier.weight(1f),
        )
        if (onRemove != null) {
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(AppIcons.Close, stringResource(R.string.hashtag_group_remove_tag))
            }
        }
    }
}
