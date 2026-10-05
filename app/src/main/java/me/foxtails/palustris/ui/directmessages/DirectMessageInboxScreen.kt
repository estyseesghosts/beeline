package me.foxtails.palustris.ui.directmessages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.layout.CompactFilterDockHeight
import me.foxtails.palustris.ui.layout.compactScrollEndClearance
import me.foxtails.palustris.ui.emoji.InlineEmojiText

/**
 * Renders the inbox with shell-supplied clearance for wide floating chrome.
 *
 * @param rightObstructionClearance Physical-right clearance for wide inbox content.
 * @param bottomObstructionClearance Bottom clearance added to the wide inbox scroll range.
 */
@Composable
fun DirectMessageInboxScreen(
    accountId: AccountId,
    state: DirectMessageUiState,
    compactLayout: Boolean = true,
    compactNavigationVisible: Boolean = true,
    rightObstructionClearance: Dp = 0.dp,
    bottomObstructionClearance: Dp = 0.dp,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpenConversation: (DirectConversation) -> Unit = {},
) {
    val endClearance = if (compactLayout) {
        compactScrollEndClearance(
            controlStackHeight = CompactFilterDockHeight,
            navigationVisible = compactNavigationVisible,
        )
    } else {
        0.dp
    }
    val wideRightClearance = if (compactLayout) 0.dp else rightObstructionClearance
    val wideBottomClearance = if (compactLayout) 0.dp else bottomObstructionClearance
    Column(Modifier.fillMaxSize().testTag("direct_message_inbox")) {
        Column(
            Modifier.fillMaxWidth()
                .absolutePadding(right = wideRightClearance)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.dm_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onRefresh, enabled = !state.loading && !state.loadingMore) { Text(stringResource(R.string.common_refresh)) }
            }
            Spacer(Modifier.size(6.dp))
            Text(
                stringResource(R.string.dm_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.error != null && state.conversations.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                Text(state.error, color = MaterialTheme.colorScheme.error)
            }
        }
        when {
            state.loading && state.conversations.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            state.error != null && state.conversations.isEmpty() -> EmptyState(
                icon = AppIcons.DirectMessage,
                title = stringResource(R.string.dm_load_error),
                subtitle = state.error,
                modifier = Modifier.fillMaxSize(),
            )
            state.conversations.isEmpty() -> EmptyState(
                icon = AppIcons.DirectMessage,
                title = stringResource(R.string.dm_empty_title),
                subtitle = stringResource(R.string.dm_empty_subtitle),
                modifier = Modifier.fillMaxSize(),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("direct_message_conversation_list"),
                contentPadding = PaddingValues(bottom = endClearance + wideBottomClearance + 24.dp),
            ) {
                items(
                    items = state.conversations,
                    key = { "${it.id.connection}/${it.id.value}" },
                ) { conversation ->
                    DirectConversationRow(
                        accountId = accountId,
                        conversation = conversation,
                        rightObstructionClearance = wideRightClearance,
                        onClick = { onOpenConversation(conversation) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        when {
                            state.loadingMore -> CircularProgressIndicator(Modifier.size(24.dp))
                            state.nextCursor != null -> Text(
                                stringResource(R.string.dm_load_older),
                                modifier = Modifier.clickable(onClick = onLoadMore),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            else -> Text(
                                stringResource(R.string.dm_up_to_date),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectConversationRow(
    accountId: AccountId,
    conversation: DirectConversation,
    rightObstructionClearance: Dp,
    onClick: () -> Unit,
) {
    val people = conversation.participants.filterNot { it.id == accountId }
    val title = people.joinToString(", ") { it.displayName.ifBlank { it.handle } }
        .ifBlank { conversation.lastPost.author.displayName.ifBlank { conversation.lastPost.author.handle } }
    Surface(
        modifier = Modifier.fillMaxWidth()
            .testTag("direct_message_conversation_surface_${conversation.id.value}"),
        color = if (conversation.unread) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .35f)
        else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .absolutePadding(right = rightObstructionClearance)
                .clickable(onClick = onClick)
                .testTag("direct_message_conversation_action_${conversation.id.value}")
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccountAvatar(
                people.firstOrNull() ?: conversation.lastPost.author,
                Modifier.size(52.dp),
                exposeSemantics = false,
            )
            Column(Modifier.weight(1f)) {
                InlineEmojiText(title, people.firstOrNull()?.emoji.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(
                    conversation.lastPost.text.ifBlank { stringResource(R.string.dm_private_post) },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (conversation.unread) {
                Surface(Modifier.size(10.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {}
            }
        }
    }
}
