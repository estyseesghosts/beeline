package me.foxtails.palustris.ui.directmessages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.emoji.InlineEmojiText

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun DirectMessageRecipientFinder(
    state: DirectMessageRecipientFinderState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onAccountSelected: (Account) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.isOpen) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().testTag("dm_recipient_finder").padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.dm_new_conversation), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.dm_recipient_finder_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().testTag("dm_recipient_query"),
                label = { Text(stringResource(R.string.dm_recipient_handle)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onSearch,
                    enabled = state.query.isNotBlank() && !state.loading,
                    modifier = Modifier.testTag("dm_recipient_search"),
                ) {
                    Text(stringResource(R.string.dm_recipient_search))
                }
            }
            when {
                state.loading -> Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.dm_recipient_searching))
                }
                state.error != null -> Text(
                    state.error,
                    Modifier.testTag("dm_recipient_error"),
                    color = MaterialTheme.colorScheme.error,
                )
                state.results.isNotEmpty() -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                        .testTag("dm_recipient_results"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.results, key = { it.id.toString() }) { account ->
                        RecipientResult(account, onClick = { onAccountSelected(account) })
                    }
                }
                state.searched -> Text(
                    stringResource(R.string.dm_recipient_not_found),
                    Modifier.testTag("dm_recipient_empty"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("dm_recipient_cancel")) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun RecipientResult(
    account: Account,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("dm_recipient_result"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountAvatar(account, Modifier.size(44.dp), exposeSemantics = false)
            Column(Modifier.weight(1f)) {
                InlineEmojiText(
                    account.displayName.ifBlank { account.handle },
                    account.emoji,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    account.handle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
