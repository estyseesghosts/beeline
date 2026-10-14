package me.foxtails.palustris.ui.filters

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.ContentFilter
import me.foxtails.palustris.domain.ContentFilterKind
import me.foxtails.palustris.domain.FilterContext
import me.foxtails.palustris.domain.FilterPattern
import me.foxtails.palustris.domain.PostFilterAction
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.settings.SettingsChoiceRow
import me.foxtails.palustris.ui.settings.SettingsSection

/** One editor change. The screen hands it to the model, which applies it to the current editor. */
internal typealias EditorChange = ((ContentFilterEditor) -> ContentFilterEditor) -> Unit

/** The title and keywords of a titled filter, or the one phrase of a phrase filter. */
@Composable
internal fun FilterKeywordFields(editor: ContentFilterEditor, onChange: EditorChange) {
    val phrase = editor.kind == ContentFilterKind.Phrase
    if (!phrase) {
        OutlinedTextField(
            value = editor.title,
            onValueChange = { value -> onChange { it.copy(title = value) } },
            label = { Text(stringResource(R.string.settings_filters_title)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).testTag("filter_title"),
        )
    }
    SettingsSection(stringResource(if (phrase) R.string.settings_filters_phrase_section else R.string.settings_filters_keywords_section)) {
        val rows = if (phrase) editor.keywords.take(1) else editor.keywords
        rows.forEachIndexed { index, keyword ->
            KeywordRow(index, keyword, removable = !phrase && editor.keywords.size > 1, onChange)
        }
        if (!phrase) {
            TextButton(onClick = { onChange { it.addKeyword() } }, modifier = Modifier.padding(horizontal = 8.dp).testTag("filter_add_keyword")) {
                Text(stringResource(R.string.settings_filters_add_keyword))
            }
        }
    }
}

@Composable
private fun KeywordRow(index: Int, keyword: KeywordDraft, removable: Boolean, onChange: EditorChange) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = keyword.text,
            onValueChange = { value -> onChange { it.keyword(index, keyword.copy(text = value)) } },
            label = { Text(stringResource(R.string.settings_filters_keyword)) },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("filter_keyword_$index"),
        )
        if (removable) {
            IconButton(onClick = { onChange { it.removeKeyword(index) } }) {
                Icon(AppIcons.Close, stringResource(R.string.settings_filters_remove_keyword))
            }
        }
    }
    CheckboxRow(
        label = stringResource(R.string.settings_filters_whole_word),
        checked = keyword.wholeWord,
        onCheckedChange = { checked -> onChange { it.keyword(index, keyword.copy(wholeWord = checked)) } },
        testTag = "filter_whole_word_$index",
    )
}

/** Collapse behind a notice, or remove from the list. */
@Composable
internal fun FilterActionFields(editor: ContentFilterEditor, onChange: EditorChange) {
    SettingsSection(stringResource(R.string.settings_filters_action_section)) {
        SettingsChoiceRow(
            label = stringResource(R.string.settings_filters_action_collapse),
            selected = editor.action == PostFilterAction.Collapse,
            onClick = { onChange { it.copy(action = PostFilterAction.Collapse) } },
            testTag = "filter_action_collapse",
        )
        SettingsChoiceRow(
            label = stringResource(R.string.settings_filters_action_remove),
            summary = stringResource(R.string.settings_filters_action_remove_summary),
            selected = editor.action == PostFilterAction.Remove,
            onClick = { onChange { it.copy(action = PostFilterAction.Remove) } },
            testTag = "filter_action_remove",
        )
    }
}

/** The places where the filter applies. At least one must stay selected to save. */
@Composable
internal fun FilterContextFields(editor: ContentFilterEditor, onChange: EditorChange) {
    SettingsSection(stringResource(R.string.settings_filters_context_section)) {
        FilterContext.entries.forEach { context ->
            CheckboxRow(
                label = stringResource(context.label()),
                checked = context in editor.contexts,
                onCheckedChange = { onChange { it.toggleContext(context) } },
                testTag = "filter_context_${context.name}",
            )
        }
    }
}

@Composable
internal fun FilterExpiryFields(editor: ContentFilterEditor, onChange: EditorChange) {
    SettingsSection(stringResource(R.string.settings_filters_expiry_section)) {
        editor.expiryChoices.forEach { expiry ->
            SettingsChoiceRow(
                label = stringResource(expiry.label()),
                selected = editor.expiry == expiry,
                onClick = { onChange { it.copy(expiry = expiry) } },
                testTag = "filter_expiry_${expiry.name}",
            )
        }
    }
}

/** One line under a filter in the list: its keywords for a titled filter, what it does, and when it expires. */
@Composable
internal fun filterSummary(kind: ContentFilterKind, filter: ContentFilter): String {
    val context = LocalContext.current
    val parts = buildList {
        if (kind == ContentFilterKind.Titled) {
            filter.patterns.filterIsInstance<FilterPattern.Words>().joinToString(", ") { it.words.joinToString(" ") }.takeIf(String::isNotBlank)?.let(::add)
        }
        add(stringResource(if (filter.action == PostFilterAction.Remove) R.string.settings_filters_summary_remove else R.string.settings_filters_summary_collapse))
        filter.expiresAtEpochMillis?.let { expiry ->
            add(
                if (expiry <= System.currentTimeMillis()) stringResource(R.string.settings_filters_expired)
                else stringResource(R.string.settings_filters_expires_at, DateUtils.formatDateTime(context, expiry, DATE_FLAGS)),
            )
        }
    }
    return parts.joinToString(" · ")
}

/** A row that toggles as one control, so TalkBack reads the label with the state. */
@Composable
private fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, testTag: String) {
    ListItem(
        modifier = Modifier.fillMaxWidth().testTag(testTag).toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        headlineContent = { Text(label) },
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
    )
}

private fun FilterContext.label(): Int = when (this) {
    FilterContext.Home -> R.string.settings_filters_context_home
    FilterContext.Notifications -> R.string.settings_filters_context_notifications
    FilterContext.Public -> R.string.settings_filters_context_public
    FilterContext.Thread -> R.string.settings_filters_context_thread
    FilterContext.Account -> R.string.settings_filters_context_account
}

private fun FilterExpiry.label(): Int = when (this) {
    FilterExpiry.Keep -> R.string.settings_filters_expiry_keep
    FilterExpiry.Never -> R.string.settings_filters_expiry_never
    FilterExpiry.HalfHour -> R.string.settings_filters_expiry_30m
    FilterExpiry.Hour -> R.string.settings_filters_expiry_1h
    FilterExpiry.SixHours -> R.string.settings_filters_expiry_6h
    FilterExpiry.TwelveHours -> R.string.settings_filters_expiry_12h
    FilterExpiry.Day -> R.string.settings_filters_expiry_1d
    FilterExpiry.Week -> R.string.settings_filters_expiry_1w
}

private const val DATE_FLAGS = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL
