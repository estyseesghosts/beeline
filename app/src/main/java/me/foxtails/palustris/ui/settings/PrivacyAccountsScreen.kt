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
import me.foxtails.palustris.domain.AccountPostActions
import me.foxtails.palustris.domain.PostFilterAction

/** What happens to posts of muted and blocked accounts, with the callbacks that change it. App-wide. */
data class AccountPostChoices(
    val actions: AccountPostActions = AccountPostActions(),
    val onMuted: (PostFilterAction) -> Unit = {},
    val onBlocked: (PostFilterAction) -> Unit = {},
)

/**
 * The Blocks and mutes page: the choices for posts of muted and blocked accounts, then one section for each
 * account, with one row for each list of that account.
 */
@Composable
fun BlocksAndMutesScreen(
    accounts: List<AccountRef>,
    accountPosts: AccountPostChoices = AccountPostChoices(),
    onSelect: (AccountId, ModerationKind) -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        AccountPostSection(R.string.settings_muted_account_posts, accountPosts.actions.muted, accountPosts.onMuted, "muted_account_posts")
        AccountPostSection(R.string.settings_blocked_account_posts, accountPosts.actions.blocked, accountPosts.onBlocked, "blocked_account_posts")
        if (accounts.isEmpty()) {
            SettingsNote(stringResource(R.string.settings_connect_account_privacy))
        } else {
            PrivacyAccountSections(accounts, onSelect)
        }
    }
}

/** Collapse or remove the posts of one kind of account that the server still sends. */
@Composable
private fun AccountPostSection(title: Int, selected: PostFilterAction, onSelect: (PostFilterAction) -> Unit, tag: String) {
    SettingsSection(stringResource(title)) {
        PostFilterAction.entries.forEach { action ->
            SettingsChoiceRow(
                label = stringResource(if (action == PostFilterAction.Collapse) R.string.settings_filters_action_collapse else R.string.settings_filters_action_remove),
                selected = selected == action,
                onClick = { onSelect(action) },
                testTag = "${tag}_${action.name}",
            )
        }
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
                    summary = stringResource(if (kind == ModerationKind.Filters) R.string.settings_filters_row_summary else R.string.settings_privacy_account_list_summary),
                    onClick = { onSelect(account.accountId, kind) },
                    testTag = "moderation_${account.accountId.localId}_${kind.name}",
                )
            }
        }
    }
}

/** The row label and the page title of each list. */
@Composable
internal fun ModerationKind.label(): String = when (this) {
    ModerationKind.Blocked -> stringResource(R.string.settings_blocked_users)
    ModerationKind.Muted -> stringResource(R.string.settings_muted_users)
    ModerationKind.Hashtags -> stringResource(R.string.settings_muted_hashtags)
    ModerationKind.Filters -> stringResource(R.string.settings_filters)
}
