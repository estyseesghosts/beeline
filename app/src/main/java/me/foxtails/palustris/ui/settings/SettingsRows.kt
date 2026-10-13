package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R

/** One choice of a single-selection group. The whole row selects, and the radio button only shows the state. */
@Composable
internal fun SettingsChoiceRow(label: String, selected: Boolean, onClick: () -> Unit, testTag: String? = null) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        headlineContent = { Text(label) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
    )
}

/** A short explanation under a section title or a setting. */
@Composable
internal fun SettingsNote(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Names the account that an account-scoped page edits. */
@Composable
internal fun SettingsAccountScope(accountLabel: String) {
    SettingsNote(stringResource(R.string.settings_account_scope, accountLabel))
}

/**
 * One on/off setting. The whole row toggles, and the switch only shows the state, so TalkBack reads one
 * control with the title, the summary and the state.
 */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        headlineContent = { Text(title) },
        supportingContent = summary?.let { text -> { Text(text) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}
