package me.foxtails.palustris.ui.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.PostLimits
import me.foxtails.palustris.ui.Avatar
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.emoji.ComposerField
import me.foxtails.palustris.ui.motion.ExpandableContent

/**
 * The cursor of one text field. The editor stores plain text, so the cursor lives beside the field
 * and an emoji lands where the user left it.
 */
@Stable
internal class CursorField(length: Int) {
    var selection by mutableStateOf(TextRange(length))

    /** Moves a cursor that points past the end of a shortened text back to the end. */
    fun clamp(length: Int) {
        if (selection.max > length) selection = TextRange(length)
    }

    fun value(text: String): TextFieldValue =
        TextFieldValue(text, TextRange(selection.start.coerceIn(0, text.length), selection.end.coerceIn(0, text.length)))

    /** Replaces the selection of [text] with [insertion], moves the cursor after it, and returns the new text. */
    fun insert(text: String, insertion: String): String {
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(start, text.length)
        selection = TextRange(start + insertion.length)
        return text.replaceRange(start, end, insertion)
    }
}

/** What one entry row reads and reports. The body owns focus and the editor. */
internal class ComposerEntryActions(
    val onTextChange: (String) -> Unit,
    val onWarningChange: (String) -> Unit,
    val onFocus: (ComposerField.Kind) -> Unit,
    val onEmojiInserted: () -> Unit,
)

/**
 * One post of the thread: avatar, optional content warning field, and text field. An emoji for
 * this entry is inserted at the cursor of the field it targets, then reported through
 * [ComposerEntryActions.onEmojiInserted].
 */
@Composable
internal fun ComposerEntryRow(
    entry: ComposerEntryState,
    index: Int,
    account: Account?,
    limits: PostLimits,
    pendingEmojiInsertion: Pair<EmojiChoice, ComposerField>?,
    actions: ComposerEntryActions,
) {
    val first = index == 0
    val text = remember(entry.id) { CursorField(entry.text.length) }
    val warning = remember(entry.id) { CursorField(entry.warning.length) }
    LaunchedEffect(entry.text) { text.clamp(entry.text.length) }
    LaunchedEffect(entry.warning) { warning.clamp(entry.warning.length) }
    LaunchedEffect(pendingEmojiInsertion) {
        val (choice, field) = pendingEmojiInsertion ?: return@LaunchedEffect
        // A null entry id addresses the first entry. Another entry's row leaves the insertion pending.
        if (field.entryId == null && !first || field.entryId != null && field.entryId != entry.id) return@LaunchedEffect
        when (field.kind) {
            ComposerField.Kind.Text -> actions.onTextChange(text.insert(entry.text, choice.submissionValue))
            ComposerField.Kind.Warning -> actions.onWarningChange(warning.insert(entry.warning, choice.submissionValue))
        }
        actions.onEmojiInserted()
    }
    val textDescription = if (first) stringResource(R.string.composer_post_text) else stringResource(R.string.composer_entry_text, index + 1)
    val warningRemaining = entry.warningRemaining(limits)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (account != null) AccountAvatar(account, Modifier.size(40.dp)) else Avatar(Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            ExpandableContent(visible = entry.warningEnabled, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = warning.value(entry.warning),
                    onValueChange = { changed ->
                        actions.onWarningChange(changed.text)
                        warning.selection = changed.selection
                    },
                    label = { Text(stringResource(R.string.composer_content_warning)) },
                    isError = warningRemaining != null && warningRemaining < 0,
                    supportingText = warningRemaining?.let { left ->
                        { Text(stringResource(R.string.composer_warning_remaining, left)) }
                    },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) actions.onFocus(ComposerField.Kind.Warning) },
                )
            }
            BasicTextField(
                value = text.value(entry.text),
                onValueChange = { changed ->
                    actions.onTextChange(changed.text)
                    text.selection = changed.selection
                },
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = if (first) FIRST_ENTRY_MIN_HEIGHT else 48.dp)
                    .padding(vertical = 8.dp)
                    .onFocusChanged { if (it.isFocused) actions.onFocus(ComposerField.Kind.Text) }
                    .semantics { contentDescription = textDescription },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { field ->
                    Box {
                        if (entry.text.isEmpty()) {
                            Text(stringResource(R.string.composer_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        field()
                    }
                },
            )
        }
    }
}

private val FIRST_ENTRY_MIN_HEIGHT = 120.dp
