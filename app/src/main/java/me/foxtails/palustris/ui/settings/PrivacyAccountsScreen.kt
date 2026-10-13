package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.domain.AccountId

/** The Blocks and mutes page: one section for each account, with one row for each list of that account. */
@Composable
fun PrivacyAccountsScreen(accounts: List<AccountRef>, onSelect: (AccountId, ModerationKind) -> Unit) {
    if (accounts.isEmpty()) {
        SettingsNote(stringResource(R.string.settings_connect_account_privacy))
        return
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        PrivacyAccountSections(accounts, onSelect)
    }
}

@Composable
private fun PrivacyAccountSections(accounts: List<AccountRef>, onSelect: (AccountId, ModerationKind) -> Unit) {
    accounts.forEach { account ->
        SettingsSection(account.displayName) {
            Text(account.handle, Modifier.padding(horizontal = 20.dp))
            ModerationKind.entries.forEach { kind ->
                SettingsRow(
                    title = kind.label(),
                    summary = stringResource(R.string.settings_privacy_account_list_summary),
                    onClick = { onSelect(account.accountId, kind) },
                    testTag = "moderation_${account.accountId.localId}_${kind.name}",
                )
            }
        }
    }
}

@Composable
private fun ModerationKind.label(): String = when (this) {
    ModerationKind.Blocked -> stringResource(R.string.settings_blocked_users)
    ModerationKind.Muted -> stringResource(R.string.settings_muted_users)
    ModerationKind.Hashtags -> stringResource(R.string.settings_muted_hashtags)
}
