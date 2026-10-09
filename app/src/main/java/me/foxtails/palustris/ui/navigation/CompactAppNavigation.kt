@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.foxtails.palustris.ui.navigation

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.Avatar
import me.foxtails.palustris.ui.shell.Destination
import me.foxtails.palustris.ui.shell.NotificationsPanel
import me.foxtails.palustris.ui.shell.SearchPanel
import me.foxtails.palustris.ui.large.ContextualTabCaretButton
import me.foxtails.palustris.ui.large.TabCaretUiState
import me.foxtails.palustris.ui.layout.CompactNavigationCapsuleWidth
import me.foxtails.palustris.ui.layout.CompactNavigationControlSpacing
import me.foxtails.palustris.ui.layout.CompactNavigationHeight
import me.foxtails.palustris.ui.motion.TriggerSurfaceSource
import me.foxtails.palustris.ui.profile.ProfileUiState

internal fun Modifier.bubblePressLayer(
    pressed: Boolean,
    color: androidx.compose.ui.graphics.Color,
    shape: androidx.compose.ui.graphics.Shape,
): Modifier = clip(shape).drawWithContent {
    drawContent()
    if (pressed) {
        drawRect(color)
    }
}

/**
 * Resolves the compact contextual action for one destination.
 *
 * The compact bar keeps four grouped positions, so a remembered child panel stays a separate
 * contextual action. The panel, destination, and profile relationship own the label and callback.
 * The callback itself stays with the feature contract or the shell.
 */
@Composable
internal fun contextualActionFor(
    destination: Destination,
    searchPanel: SearchPanel,
    notificationsPanel: NotificationsPanel,
    profileTarget: Account?,
    authenticatedAccountId: AccountId?,
    profileState: ProfileUiState,
    onCompose: () -> Unit,
    onSearchToggle: () -> Unit,
    onNotificationsToggle: () -> Unit,
    onEditProfile: () -> Unit,
    onFollowProfile: () -> Unit,
    onUnfollowProfile: () -> Unit,
    composeTrigger: TriggerSurfaceSource? = null,
    editProfileTrigger: TriggerSurfaceSource? = null,
): ContextualNavigationAction? = when (destination) {
    Destination.Home -> ContextualNavigationAction(AppIcons.Compose, stringResource(R.string.nav_compose), true, onCompose, composeTrigger)
    Destination.Search -> if (searchPanel == SearchPanel.Search) {
        ContextualNavigationAction(AppIcons.PhotoGrid, stringResource(R.string.nav_photo_grid), true, onSearchToggle)
    } else {
        ContextualNavigationAction(AppIcons.SearchBeeline, stringResource(R.string.nav_search), true, onSearchToggle)
    }
    Destination.Notifications -> if (notificationsPanel == NotificationsPanel.Notifications) {
        ContextualNavigationAction(AppIcons.DirectMessage, stringResource(R.string.nav_direct_messages), true, onNotificationsToggle)
    } else {
        ContextualNavigationAction(AppIcons.Mail, stringResource(R.string.nav_notifications), true, onNotificationsToggle)
    }
    Destination.Profile -> profileContextualAction(
        profileTarget = profileTarget,
        authenticatedAccountId = authenticatedAccountId,
        profileState = profileState,
        onEditProfile = onEditProfile,
        onFollowProfile = onFollowProfile,
        onUnfollowProfile = onUnfollowProfile,
        editProfileTrigger = editProfileTrigger,
    )
}

/**
 * Resolves the shared contextual action for a profile target.
 *
 * Compact and vertical presentation both call this policy, so a moved profile, the signed-in
 * account, and a known relationship keep the same label and callback in either presentation.
 */
@Composable
internal fun profileContextualAction(
    profileTarget: Account?,
    authenticatedAccountId: AccountId?,
    profileState: ProfileUiState,
    onEditProfile: () -> Unit,
    onFollowProfile: () -> Unit,
    onUnfollowProfile: () -> Unit,
    editProfileTrigger: TriggerSurfaceSource? = null,
): ContextualNavigationAction? = when {
    profileTarget?.movedTo != null -> null
    profileTarget?.id == authenticatedAccountId && authenticatedAccountId != null ->
        ContextualNavigationAction(
            AppIcons.PersonEdit,
            stringResource(R.string.profile_edit),
            profileState.editableSupported,
            onEditProfile,
            editProfileTrigger,
        )
    profileState.relationshipSupported == true && profileState.relationship != null -> {
        val relationship = profileState.relationship
        val following = relationship.following || relationship.requested
        ContextualNavigationAction(
            icon = if (following) AppIcons.Unfollow else AppIcons.Follow,
            contentDescription = when {
                relationship.following -> stringResource(R.string.profile_unfollow_action)
                relationship.requested -> stringResource(R.string.profile_cancel_request_action)
                else -> stringResource(R.string.profile_follow_action)
            },
            enabled = !profileState.relationshipMutation,
            onClick = if (following) onUnfollowProfile else onFollowProfile,
            // A pending request cancels directly; only a real follow or unfollow asks first.
            followChoice = if (relationship.requested && !relationship.following) {
                null
            } else {
                ContextualFollowChoice(
                    following = relationship.following,
                    onConfirm = if (relationship.following) onUnfollowProfile else onFollowProfile,
                )
            },
        )
    }
    else -> null
}

/**
 * Resolves the contextual action for one of the six direct vertical targets.
 *
 * The vertical presentation already lists Photo Grid and Direct Messages as their own targets, so
 * those two do not repeat a panel switch the way the compact grouped bar does. Home, Search,
 * Photo Grid, and Notifications keep their Compose action, which matches the action the replaced
 * rail offered on every screen. Direct Messages opens the recipient finder that the direct-message
 * contract owns. Profile reuses the shared profile policy.
 */
@Composable
internal fun wideContextualAction(
    target: WideNavigationItem,
    profileTarget: Account?,
    authenticatedAccountId: AccountId?,
    profileState: ProfileUiState,
    directMessagesEnabled: Boolean,
    onCompose: () -> Unit,
    onNewConversation: () -> Unit,
    onEditProfile: () -> Unit,
    onFollowProfile: () -> Unit,
    onUnfollowProfile: () -> Unit,
    composeTrigger: TriggerSurfaceSource? = null,
    editProfileTrigger: TriggerSurfaceSource? = null,
): ContextualNavigationAction? = when (target) {
    WideNavigationItem.Home,
    WideNavigationItem.Search,
    WideNavigationItem.PhotoGrid,
    WideNavigationItem.Notifications,
    -> ContextualNavigationAction(
        AppIcons.Compose,
        stringResource(R.string.nav_compose),
        true,
        onCompose,
        composeTrigger,
    )
    WideNavigationItem.DirectMessages -> ContextualNavigationAction(
        AppIcons.DirectMessage,
        stringResource(R.string.dm_new_conversation),
        directMessagesEnabled,
        onNewConversation,
    )
    WideNavigationItem.Profile -> profileContextualAction(
        profileTarget = profileTarget,
        authenticatedAccountId = authenticatedAccountId,
        profileState = profileState,
        onEditProfile = onEditProfile,
        onFollowProfile = onFollowProfile,
        onUnfollowProfile = onUnfollowProfile,
        editProfileTrigger = editProfileTrigger,
    )
}

@Composable
internal fun CompactContextualNavigationBar(
    destination: Destination,
    searchPanel: SearchPanel,
    notificationsPanel: NotificationsPanel,
    action: ContextualNavigationAction?,
    account: Account?,
    onOpenAccounts: () -> Unit,
    onDestinationSelected: (Destination) -> Unit,
    tabCaret: TabCaretUiState? = null,
) {
    Row(
        Modifier.fillMaxWidth().height(CompactNavigationHeight),
        horizontalArrangement = Arrangement.spacedBy(CompactNavigationControlSpacing, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        // The caret takes the dead space left of the pill, so the pill and action never move.
        tabCaret?.let { ContextualTabCaretButton(it) }
        NavigationCapsule(
            selectedIndex = destination.ordinal,
            itemCount = Destination.entries.size,
            orientation = Orientation.Horizontal,
            modifier = Modifier.width(CompactNavigationCapsuleWidth).height(56.dp),
        ) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Destination.entries.forEach { item ->
                    val selected = destination == item
                    val showsPhotoGrid = item == Destination.Search && searchPanel == SearchPanel.PhotoGrid
                    val showsDirectMessages = item == Destination.Notifications &&
                        notificationsPanel == NotificationsPanel.DirectMessages
                    val label = when {
                        showsPhotoGrid -> stringResource(R.string.nav_photo_grid)
                        showsDirectMessages -> stringResource(R.string.nav_direct_messages)
                        else -> stringResource(item.labelRes)
                    }
                    val icon = when {
                        showsPhotoGrid -> AppIcons.PhotoGrid
                        showsDirectMessages -> AppIcons.DirectMessage
                        else -> item.icon
                    }
                    if (item == Destination.Profile) {
                        NavigationButton(
                            label = label,
                            icon = null,
                            selected = selected,
                            onClick = { onDestinationSelected(item) },
                            onLongClick = onOpenAccounts,
                            content = { avatarModifier ->
                                val sizedAvatar = avatarModifier.size(30.dp)
                                if (account != null) AccountAvatar(account, sizedAvatar, exposeSemantics = false)
                                else Avatar(sizedAvatar, description = null)
                            },
                        )
                    } else {
                        NavigationButton(
                            label = label,
                            icon = icon,
                            selected = selected,
                            onClick = { onDestinationSelected(item) },
                        )
                    }
                }
            }
        }
        action?.let { contextualAction ->
            ContextualNavigationActionButton(contextualAction)
        }
    }
}
