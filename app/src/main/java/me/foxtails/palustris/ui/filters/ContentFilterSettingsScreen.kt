package me.foxtails.palustris.ui.filters

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.ContentFilter
import me.foxtails.palustris.domain.ContentFilterKind
import me.foxtails.palustris.domain.ContentFilterSet
import me.foxtails.palustris.domain.PostFilterAction
import me.foxtails.palustris.ui.settings.SettingsNote
import me.foxtails.palustris.ui.settings.SettingsSection

/**
 * The filters page of one account. It reads the filters each time it opens, and publishes each loaded or
 * saved set to the session owner, so the feeds follow at once.
 */
@Composable
fun ContentFilterSettingsRoute(accountId: AccountId) {
    val model = hiltViewModel<ContentFilterViewModel, ContentFilterViewModel.Factory>(
        key = "content-filters-$accountId",
        creationCallback = { factory -> factory.create(accountId) },
    )
    LaunchedEffect(model) { model.load() }
    val state by model.state.collectAsStateWithLifecycle()
    val sessionOwner = LocalContentFilterOwner.current
    LaunchedEffect(state.set, sessionOwner) {
        state.set?.let { set -> sessionOwner?.replace(accountId, set) }
    }
    ContentFilterSettingsScreen(
        state = state,
        actions = ContentFilterSettingsActions(
            onRetry = model::load,
            onAdd = model::startNew,
            onEdit = model::startEdit,
            onDelete = model::delete,
            onEditor = model::updateEditor,
            onSave = model::save,
            onCancel = model::dismissEditor,
        ),
    )
}

/** The callbacks of [ContentFilterSettingsScreen]. */
data class ContentFilterSettingsActions(
    val onRetry: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onEdit: (ContentFilter) -> Unit = {},
    val onDelete: (ContentFilter) -> Unit = {},
    val onEditor: ((ContentFilterEditor) -> ContentFilterEditor) -> Unit = {},
    val onSave: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

/** The list of filters, or the editor of one filter while [ContentFilterUiState.editor] is set. Back closes the editor. */
@Composable
fun ContentFilterSettingsScreen(state: ContentFilterUiState, actions: ContentFilterSettingsActions) {
    val editor = state.editor
    BackHandler(enabled = editor != null) { actions.onCancel() }
    val set = state.set
    when {
        editor != null -> ContentFilterEditorPage(editor, state, actions)
        set == null && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        set == null && state.unsupported -> SettingsNote(stringResource(R.string.settings_filters_unsupported))
        set == null -> Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = actions.onRetry, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.notifications_retry)) }
        }
        else -> ContentFilterList(set, state, actions)
    }
}

@Composable
private fun ContentFilterList(set: ContentFilterSet, state: ContentFilterUiState, actions: ContentFilterSettingsActions) {
    var confirmDelete by remember { mutableStateOf<ContentFilter?>(null) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SettingsNote(stringResource(set.kind.summary()))
        Button(onClick = actions.onAdd, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).testTag("filters_add")) {
            Text(stringResource(if (set.kind == ContentFilterKind.WordMute) R.string.settings_filters_add_word else R.string.settings_filters_add))
        }
        state.error?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) }
        if (set.filters.isEmpty()) SettingsNote(stringResource(R.string.settings_filters_empty))
        val row: @Composable (ContentFilter) -> Unit = { filter ->
            ContentFilterRow(set.kind, filter, deleting = filter.id in state.deleting, onEdit = actions.onEdit, onDelete = { confirmDelete = it })
        }
        if (set.kind == ContentFilterKind.WordMute) WordMuteSections(set.filters, row) else set.filters.forEach { row(it) }
    }
    confirmDelete?.let { filter ->
        DeleteFilterDialog(
            filter = filter,
            onConfirm = {
                confirmDelete = null
                actions.onDelete(filter)
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun ContentFilterRow(
    kind: ContentFilterKind,
    filter: ContentFilter,
    deleting: Boolean,
    onEdit: (ContentFilter) -> Unit,
    onDelete: (ContentFilter) -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable { onEdit(filter) }.testTag("filter_${filter.id}"),
        headlineContent = { Text(filter.title.ifBlank { stringResource(R.string.post_filtered_untitled) }) },
        supportingContent = { Text(filterSummary(kind, filter)) },
        trailingContent = {
            TextButton(onClick = { onDelete(filter) }, enabled = !deleting, modifier = Modifier.testTag("filter_delete_${filter.id}")) {
                Text(stringResource(R.string.settings_filters_delete))
            }
        },
    )
}

@Composable
private fun DeleteFilterDialog(filter: ContentFilter, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_filters_delete_title)) },
        text = { Text(stringResource(R.string.settings_filters_delete_body, filter.title)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("filter_delete_confirm")) {
                Text(stringResource(R.string.settings_filters_delete))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_filters_cancel)) } },
    )
}

/** Word mutes in two sections: the list that collapses and the list that removes. */
@Composable
private fun WordMuteSections(filters: List<ContentFilter>, row: @Composable (ContentFilter) -> Unit) {
    PostFilterAction.entries.forEach { action ->
        val entries = filters.filter { it.action == action }
        if (entries.isNotEmpty()) {
            SettingsSection(stringResource(if (action == PostFilterAction.Collapse) R.string.settings_filters_soft_section else R.string.settings_filters_hard_section)) {
                entries.forEach { row(it) }
            }
        }
    }
}

private fun ContentFilterKind.summary(): Int = when (this) {
    ContentFilterKind.Titled -> R.string.settings_filters_summary_titled
    ContentFilterKind.Phrase -> R.string.settings_filters_summary_phrase
    ContentFilterKind.WordMute -> R.string.settings_filters_summary_word_mute
}

@Composable
private fun ContentFilterEditorPage(editor: ContentFilterEditor, state: ContentFilterUiState, actions: ContentFilterSettingsActions) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        if (editor.kind == ContentFilterKind.WordMute) {
            WordMuteFields(editor, actions.onEditor)
            FilterActionFields(editor, actions.onEditor)
        } else {
            FilterKeywordFields(editor, actions.onEditor)
            FilterActionFields(editor, actions.onEditor)
            FilterContextFields(editor, actions.onEditor)
            FilterExpiryFields(editor, actions.onEditor)
        }
        state.editorError?.let { error ->
            Text(stringResource(error.message()), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error)
        }
        state.error?.let { Text(it, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = actions.onCancel, enabled = !state.saving) { Text(stringResource(R.string.settings_filters_cancel)) }
            Button(onClick = actions.onSave, enabled = !state.saving && !editor.readOnly, modifier = Modifier.testTag("filters_save")) {
                if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.settings_filters_save))
            }
        }
    }
}

private fun FilterEditorError.message(): Int = when (this) {
    FilterEditorError.MissingTitle -> R.string.settings_filters_error_title
    FilterEditorError.MissingKeyword -> R.string.settings_filters_error_keyword
    FilterEditorError.MissingContext -> R.string.settings_filters_error_context
    FilterEditorError.InvalidExpression -> R.string.settings_filters_error_expression
    FilterEditorError.NotEditable -> R.string.settings_filters_error_read_only
    FilterEditorError.ExpressionRejected -> R.string.settings_filters_error_expression_server
    FilterEditorError.LimitReached -> R.string.settings_filters_error_limit
}
