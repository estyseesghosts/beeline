package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.HiddenContentPresentation

/**
 * The Content warnings page: the rules for every account, how a hidden post shows, and a row that opens the
 * rules of the active account. [accountLabel] is null when no account is active; the row is then absent.
 */
@Composable
fun ContentWarningSettingsScreen(
    rules: ContentWarningRules,
    onChanged: (ContentWarningRules) -> Unit,
    hiddenPresentation: HiddenContentPresentation = HiddenContentPresentation.Placeholder,
    onHiddenPresentation: (HiddenContentPresentation) -> Unit = {},
    accountLabel: String? = null,
    onAccountRules: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SettingsSection(stringResource(R.string.settings_content_warning_all_accounts)) {
            ContentWarningRulesEditor(rules, onChanged, tagPrefix = "content_warning")
        }
        SettingsSection(stringResource(R.string.settings_content_warning_presentation)) {
            HiddenPresentationChoices(hiddenPresentation, onHiddenPresentation)
        }
        if (accountLabel != null) {
            SettingsSection(stringResource(R.string.settings_content_warning_one_account)) {
                SettingsRow(
                    stringResource(R.string.settings_content_warning_account_rules, accountLabel),
                    stringResource(R.string.settings_content_warning_account_rules_summary),
                    onAccountRules,
                    testTag = "content_warning_account_rules",
                )
            }
        }
    }
}

/** The rules of one account. They add to the rules for every account; they never remove one. */
@Composable
fun AccountContentWarningSettingsScreen(
    rules: ContentWarningRules,
    onChanged: (ContentWarningRules) -> Unit,
    accountLabel: String,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SettingsAccountScope(accountLabel)
        SettingsNote(stringResource(R.string.settings_content_warning_account_adds))
        ContentWarningRulesEditor(rules, onChanged, tagPrefix = "account_content_warning")
    }
}

@Composable
private fun HiddenPresentationChoices(
    selected: HiddenContentPresentation,
    onSelected: (HiddenContentPresentation) -> Unit,
) {
    HiddenContentPresentation.entries.forEach { presentation ->
        val remove = presentation == HiddenContentPresentation.Remove
        SettingsChoiceRow(
            label = stringResource(
                if (remove) R.string.settings_content_warning_remove_entries
                else R.string.settings_content_warning_placeholder_entries,
            ),
            selected = presentation == selected,
            onClick = { onSelected(presentation) },
            testTag = "hidden_presentation_${presentation.name}",
            summary = stringResource(
                if (remove) R.string.settings_content_warning_remove_detail
                else R.string.settings_content_warning_placeholder_detail,
            ),
        )
    }
}
