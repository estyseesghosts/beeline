package me.foxtails.palustris.ui.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
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
import me.foxtails.palustris.ui.notifications.NotificationsScreen
import me.foxtails.palustris.ui.notifications.NotificationsUiState
import me.foxtails.palustris.ui.posts.LocalContentWarningRules

@Composable
internal fun AppNotificationsDestinationContent(
    panel: NotificationsPanel,
    account: Account?,
    compactLayout: Boolean,
    compactNavigationVisible: Boolean,
    rightObstructionClearance: Dp,
    bottomObstructionClearance: Dp,
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
) {
    AnimatedStatePane(
        stateKey = panel,
        modifier = Modifier.fillMaxSize(),
    ) { selectedPanel ->
        if (selectedPanel == NotificationsPanel.Notifications) {
            NotificationsScreen(
                connected = account != null,
                compactLayout = compactLayout,
                rightObstructionClearance = rightObstructionClearance,
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
            )
        } else if (account == null) {
            EmptyState(
                AppIcons.DirectMessage,
                androidx.compose.ui.res.stringResource(R.string.direct_messages_connect_title),
                androidx.compose.ui.res.stringResource(R.string.direct_messages_connect_subtitle),
            )
        } else if (directMessageState.selectedConversationId != null || directMessageState.recipient != null) {
            DirectMessageConversationScreen(
                accountId = account.id,
                state = directMessageState,
                compactLayout = compactLayout,
                compactNavigationVisible = compactNavigationVisible,
                rightObstructionClearance = rightObstructionClearance,
                bottomObstructionClearance = bottomObstructionClearance,
                onBack = onBackDirectConversation,
                onEditorTextChange = onEditorTextChange,
                onSend = onSendDirectMessage,
                onContinueThread = onContinueDirectThread,
                onRetryThread = onRetryDirectThread,
            )
        } else {
            DirectMessageInboxScreen(
                accountId = account.id,
                state = directMessageState,
                compactLayout = compactLayout,
                compactNavigationVisible = compactNavigationVisible,
                rightObstructionClearance = rightObstructionClearance,
                bottomObstructionClearance = bottomObstructionClearance,
                onRefresh = onRefreshDirectMessages,
                onLoadMore = onLoadMoreDirectMessages,
                onOpenConversation = onOpenDirectConversation,
            )
        }
    }
}
