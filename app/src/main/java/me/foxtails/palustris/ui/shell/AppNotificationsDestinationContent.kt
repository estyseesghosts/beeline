package me.foxtails.palustris.ui.shell

import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.directmessages.DirectMessageConversationScreen
import me.foxtails.palustris.ui.directmessages.DirectMessageInboxScreen
import me.foxtails.palustris.ui.directmessages.DirectMessageUiState
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.large.CompactWideTabCaretHost
import me.foxtails.palustris.ui.notifications.NotificationsScreen
import me.foxtails.palustris.ui.notifications.NotificationsUiState
import me.foxtails.palustris.ui.posts.LocalContentWarningRules

/**
 * Notification inbox and direct-message destination branches.
 *
 * The shell supplies physical left, physical right, and bottom obstruction clearance. Compact
 * layout ignores every value and keeps its own system-bar and IME clearance. The direct-message
 * empty state clears the floating chrome too, so no reachable wide surface stays uncovered.
 */
@Composable
internal fun AppNotificationsDestinationContent(
    panel: NotificationsPanel,
    account: Account?,
    compactLayout: Boolean,
    compactNavigationVisible: Boolean,
    rightObstructionClearance: Dp = 0.dp,
    leftObstructionClearance: Dp = 0.dp,
    bottomObstructionClearance: Dp = 0.dp,
    bottomNavigationClearance: Dp = 0.dp,
    notificationAccountIdentity: String,
    notificationState: NotificationsUiState,
    onRefreshNotifications: () -> Unit,
    onLoadMoreNotifications: () -> Unit,
    onMarkNotificationSeen: (Notification?) -> Unit,
    onDismissNotification: (Notification) -> Unit,
    onFollowRequest: (Notification, Boolean) -> Unit,
    onOpenNotification: (Notification) -> Unit,
    onSelectQuery: (NotificationQuery) -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenSettings: () -> Unit,
    directMessageState: DirectMessageUiState,
    onRefreshDirectMessages: () -> Unit,
    onLoadMoreDirectMessages: () -> Unit,
    onOpenDirectConversation: (DirectConversation) -> Unit,
    onBackDirectConversation: () -> Unit,
    onEditorTextChange: (String) -> Unit,
    onSendDirectMessage: () -> Unit,
    onContinueDirectThread: () -> Unit,
    onRetryDirectThread: () -> Unit,
    contentWarningRules: ContentWarningRules = LocalContentWarningRules.current,
    useCompactWideCaret: Boolean = false,
    tabCaretHost: CompactWideTabCaretHost? = null,
) {
    val wideLeftClearance = if (compactLayout) 0.dp else leftObstructionClearance
    val wideRightClearance = if (compactLayout) 0.dp else rightObstructionClearance
    AnimatedStatePane(
        stateKey = panel,
        modifier = Modifier.fillMaxSize(),
    ) { selectedPanel ->
        if (selectedPanel == NotificationsPanel.Notifications) {
            NotificationsScreen(
                connected = account != null,
                compactLayout = compactLayout,
                leftObstructionClearance = wideLeftClearance,
                rightObstructionClearance = wideRightClearance,
                bottomObstructionClearance = bottomObstructionClearance,
                accountIdentity = notificationAccountIdentity,
                notificationState = notificationState,
                onRefreshNotifications = onRefreshNotifications,
                onLoadMoreNotifications = onLoadMoreNotifications,
                onMarkNotificationSeen = onMarkNotificationSeen,
                onDismissNotification = onDismissNotification,
                onFollowRequest = onFollowRequest,
                onOpenNotification = onOpenNotification,
                onSelectQuery = onSelectQuery,
                onMarkAllRead = onMarkAllRead,
                onOpenSettings = onOpenSettings,
                contentWarningRules = contentWarningRules,
                useCompactWideCaret = useCompactWideCaret,
                tabCaretHost = tabCaretHost,
            )
        } else if (account == null) {
            EmptyState(
                icon = AppIcons.DirectMessage,
                title = stringResource(R.string.direct_messages_connect_title),
                subtitle = stringResource(R.string.direct_messages_connect_subtitle),
                modifier = Modifier.absolutePadding(
                    left = wideLeftClearance,
                    right = wideRightClearance,
                ),
            )
        } else if (directMessageState.selectedConversationId != null || directMessageState.recipient != null) {
            DirectMessageConversationScreen(
                accountId = account.id,
                state = directMessageState,
                compactLayout = compactLayout,
                compactNavigationVisible = compactNavigationVisible,
                leftObstructionClearance = wideLeftClearance,
                rightObstructionClearance = wideRightClearance,
                bottomObstructionClearance = bottomObstructionClearance,
                onBack = onBackDirectConversation,
                bottomNavigationClearance = bottomNavigationClearance,
                onEditorTextChange = onEditorTextChange,
                onSend = onSendDirectMessage,
                onContinueThread = onContinueDirectThread,
                onRetryThread = onRetryDirectThread,
            )
        } else {
            DirectMessageInboxScreen(
                leftObstructionClearance = wideLeftClearance,
                accountId = account.id,
                state = directMessageState,
                compactLayout = compactLayout,
                compactNavigationVisible = compactNavigationVisible,
                rightObstructionClearance = wideRightClearance,
                bottomObstructionClearance = bottomObstructionClearance,
                onRefresh = onRefreshDirectMessages,
                onLoadMore = onLoadMoreDirectMessages,
                onOpenConversation = onOpenDirectConversation,
            )
        }
    }
}
