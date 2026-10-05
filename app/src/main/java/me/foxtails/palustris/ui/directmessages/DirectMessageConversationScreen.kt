package me.foxtails.palustris.ui.directmessages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.ActionIcon
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.Avatar
import me.foxtails.palustris.ui.layout.compactContextualControlsPositioningInsets
import me.foxtails.palustris.ui.layout.compactGlobalNavigationPositioningInsets
import me.foxtails.palustris.ui.emoji.InlineEmojiText

/**
 * Keeps wide content clear of shell-supplied floating chrome without insetting the viewport.
 *
 * @param rightObstructionClearance Physical-right clearance for wide conversation and editor content.
 * @param bottomObstructionClearance Wide transcript scroll clearance; never an editor positioning inset.
 * Compact layout ignores both values and retains its contextual-control inset policy.
 */
@Composable
fun DirectMessageConversationScreen(
    accountId: AccountId,
    state: DirectMessageUiState,
    compactLayout: Boolean = true,
    compactNavigationVisible: Boolean = true,
    rightObstructionClearance: Dp = 0.dp,
    bottomObstructionClearance: Dp = 0.dp,
    onBack: () -> Unit = {},
    onEditorTextChange: (String) -> Unit = {},
    onSend: () -> Unit = {},
    onContinueThread: () -> Unit = {},
    onRetryThread: () -> Unit = {},
) {
    val wideRightClearance = if (compactLayout) 0.dp else rightObstructionClearance
    val wideBottomClearance = if (compactLayout) 0.dp else bottomObstructionClearance
    val recipient = state.recipient
    val title = state.selectedConversation?.participants
        ?.filterNot { it.id == accountId }
        ?.joinToString(", ") { it.displayName.ifBlank { it.handle } }
        ?.ifBlank { null }
        ?: recipient?.displayName?.ifBlank { recipient.handle }
        ?: stringResource(R.string.dm_private_message)

    Column(Modifier.fillMaxSize().testTag("direct_message_conversation")) {
        Row(
            Modifier.fillMaxWidth().absolutePadding(right = wideRightClearance)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIcon(AppIcons.Back, stringResource(R.string.dm_back), onBack)
            Text(title, Modifier.weight(1f).testTag("direct_message_title"), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            stringResource(R.string.dm_not_encrypted),
            Modifier.fillMaxWidth().absolutePadding(right = wideRightClearance)
                .padding(horizontal = 20.dp, vertical = 4.dp).testTag("direct_message_notice"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.error != null) {
            Text(
                state.error,
                Modifier.absolutePadding(right = wideRightClearance).padding(20.dp)
                    .testTag("direct_message_error"),
                color = MaterialTheme.colorScheme.error,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("direct_message_thread"),
            // Editor positioning already clears navigation and the IME. Only wide
            // floating chrome adds scroll range, not another editor positioning inset.
            contentPadding = PaddingValues.Absolute(
                left = 16.dp,
                top = 12.dp,
                right = 16.dp + wideRightClearance,
                bottom = 12.dp + wideBottomClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.thread, key = { "${it.id.connection}/${it.id.value}" }) { post ->
                DirectMessageRow(accountId, post)
            }
            if (state.loadingThread || state.threadContinuing) item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp).testTag("direct_message_thread_loading"))
                }
            } else if (state.threadError != null) item {
                // Thread acquisition failed. Prior posts stay. Retry reuses the
                // stored cursor, or reloads fresh when no cursor remains.
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        state.threadError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = onRetryThread,
                        modifier = Modifier.testTag("direct_message_thread_retry"),
                    ) {
                        Text(stringResource(R.string.notifications_retry))
                    }
                }
            } else if (state.threadCursor != null) item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = onContinueThread,
                        modifier = Modifier.testTag("direct_message_thread_continue"),
                    ) {
                        Text(stringResource(R.string.dm_thread_continue))
                    }
                }
            } else if (state.threadLimitations.isNotEmpty()) item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.dm_thread_partial),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .absolutePadding(right = wideRightClearance)
                .then(
                    if (compactLayout) Modifier.windowInsetsPadding(
                        compactContextualControlsPositioningInsets(compactNavigationVisible),
                    ) else Modifier.imePadding().windowInsetsPadding(
                        compactGlobalNavigationPositioningInsets(WindowInsets(bottom = 0.dp)),
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.editorText,
                onValueChange = onEditorTextChange,
                modifier = Modifier.weight(1f).testTag("direct_message_input"),
                placeholder = { Text(stringResource(R.string.dm_compose_placeholder)) },
                minLines = 1,
                maxLines = 4,
            )
            IconButton(
                onClick = onSend,
                enabled = state.editorText.isNotBlank() && !state.sending && (recipient != null || state.selectedConversation != null),
                modifier = Modifier.size(52.dp).testTag("direct_message_send"),
            ) {
                if (state.sending) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(AppIcons.Compose, stringResource(R.string.dm_send))
            }
        }
    }
}

@Composable
private fun DirectMessageRow(accountId: AccountId, post: Post) {
    val mine = post.author.id == accountId
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            AccountAvatar(post.author, Modifier.size(36.dp), exposeSemantics = false)
            Spacer(Modifier.size(8.dp))
        }
        Surface(
            modifier = Modifier.testTag("direct_message_bubble_${post.id.value}"),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(.82f)) {
                if (!mine) InlineEmojiText(post.author.displayName, post.author.emoji, style = MaterialTheme.typography.labelLarge)
                Text(post.text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    post.publishedAtEpochMillis.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (mine) {
            Spacer(Modifier.size(8.dp))
            Avatar(Modifier.size(36.dp), description = null)
        }
    }
}
