package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.domain.AccountId

/** The notification account picker. The main page skips it when exactly one account is signed in. */
@Composable
fun NotificationAccountsScreen(accounts: List<AccountRef>, onSelect: (AccountId) -> Unit) {
    if (accounts.isEmpty()) {
        SettingsNote(stringResource(R.string.settings_connect_account_notifications))
        return
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        NotificationAccountRows(accounts, onSelect)
    }
}

@Composable
private fun NotificationAccountRows(accounts: List<AccountRef>, onSelect: (AccountId) -> Unit) {
    accounts.forEach { account ->
        SettingsRow(
            title = account.displayName,
            summary = account.handle,
            onClick = { onSelect(account.accountId) },
            testTag = "notification_account_${account.accountId.localId}",
        )
    }
}
