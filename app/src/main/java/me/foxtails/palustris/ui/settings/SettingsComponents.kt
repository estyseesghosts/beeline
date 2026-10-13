package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SettingsSectionTitle(title)
        content()
    }
}

/** A section heading. It carries heading semantics, so a screen reader can move between sections. */
@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        Modifier.padding(horizontal = 20.dp, vertical = 12.dp).semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun SettingsRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().then(testTag?.let { Modifier.testTag(it) } ?: Modifier).clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
    )
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
