package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.ContentWarningRules

/**
 * Edits one set of content warning rules: the two switches, the four comma-separated lists, and Save.
 *
 * A switch writes at once. The lists write only on Save, so a half-typed entry is never stored. The global
 * page and the account page both use this editor; [tagPrefix] keeps their test tags apart.
 */
@Composable
internal fun ContentWarningRulesEditor(
    rules: ContentWarningRules,
    onChanged: (ContentWarningRules) -> Unit,
    tagPrefix: String,
) {
    SettingsSwitchRow(
        title = stringResource(R.string.settings_content_warning_hide_all),
        summary = stringResource(R.string.settings_content_warning_hide_all_summary),
        checked = rules.hideAll,
        onCheckedChange = { onChanged(rules.copy(hideAll = it)) },
        testTag = "${tagPrefix}_hide_all",
    )
    SettingsSwitchRow(
        title = stringResource(R.string.settings_content_warning_expand_all),
        summary = stringResource(R.string.settings_content_warning_expand_all_summary),
        checked = rules.expandAll,
        onCheckedChange = { onChanged(rules.copy(expandAll = it)) },
        testTag = "${tagPrefix}_expand_all",
    )
    SettingsNote(stringResource(R.string.settings_content_warning_rules_summary))
    ContentWarningRuleLists(rules, onChanged, tagPrefix)
}

@Composable
private fun ContentWarningRuleLists(
    rules: ContentWarningRules,
    onChanged: (ContentWarningRules) -> Unit,
    tagPrefix: String,
) {
    val draft = remember(rules) { ContentWarningRuleDraft(rules) }
    RuleField(R.string.settings_content_warning_hide_keywords, draft.hideKeywords, "${tagPrefix}_hide_keywords") {
        draft.hideKeywords = it
    }
    RuleField(R.string.settings_content_warning_hide_hashtags, draft.hideHashtags, "${tagPrefix}_hide_hashtags") {
        draft.hideHashtags = it
    }
    RuleField(R.string.settings_content_warning_expand_keywords, draft.expandKeywords, "${tagPrefix}_expand_keywords") {
        draft.expandKeywords = it
    }
    RuleField(R.string.settings_content_warning_expand_hashtags, draft.expandHashtags, "${tagPrefix}_expand_hashtags") {
        draft.expandHashtags = it
    }
    Button(
        onClick = { onChanged(draft.applyTo(rules)) },
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).testTag("${tagPrefix}_save"),
    ) { Text(stringResource(R.string.settings_content_warning_save)) }
}

/** The unsaved text of the four lists. It resets when the stored rules change. */
private class ContentWarningRuleDraft(rules: ContentWarningRules) {
    var hideKeywords by mutableStateOf(rules.hideKeywords.joinToString(", "))
    var hideHashtags by mutableStateOf(rules.hideHashtags.joinToString(", "))
    var expandKeywords by mutableStateOf(rules.expandKeywords.joinToString(", "))
    var expandHashtags by mutableStateOf(rules.expandHashtags.joinToString(", "))

    fun applyTo(rules: ContentWarningRules): ContentWarningRules = rules.copy(
        hideKeywords = hideKeywords.split(","),
        hideHashtags = hideHashtags.split(","),
        expandKeywords = expandKeywords.split(","),
        expandHashtags = expandHashtags.split(","),
    ).normalized()
}

@Composable
private fun RuleField(label: Int, value: String, testTag: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        supportingText = { Text(stringResource(R.string.settings_content_warning_comma_hint)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).testTag(testTag),
        singleLine = true,
    )
}
