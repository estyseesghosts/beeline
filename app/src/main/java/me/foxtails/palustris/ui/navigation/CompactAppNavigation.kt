@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.foxtails.palustris.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.shell.Destination
import me.foxtails.palustris.ui.shell.NotificationsPanel
import me.foxtails.palustris.ui.shell.SearchPanel
import me.foxtails.palustris.ui.large.ContextualTabCaretButton
import me.foxtails.palustris.ui.large.TabCaretUiState
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

/** How the compact bar treats the contextual tab caret; see [CompactContextualNavigationBar]. */
internal sealed interface CompactCaretSlot {
    /** The caret lives inline in the chip row, so the bar has no slot for it. */
    data object Inline : CompactCaretSlot

    /** The bar owns a caret slot that is currently empty. */
    data object Empty : CompactCaretSlot

    /** The bar shows [state] in its caret slot. */
    data class Shown(val state: TabCaretUiState) : CompactCaretSlot
}

/**
 * The compact bar: tab caret, destination pill, and one contextual action.
 *
 * With a caret slot ([CompactCaretSlot.Empty] or [CompactCaretSlot.Shown]) the pill sits between two
 * equal-weight slots, so it stays centered and the action keeps its place whether or not the caret or
 * action shows; an emptied slot stays empty. With [CompactCaretSlot.Inline] the pill and action form
 * one group centered with equal gaps.
 */
@Composable
internal fun CompactContextualNavigationBar(
    destination: Destination,
    searchPanel: SearchPanel,
    notificationsPanel: NotificationsPanel,
    action: ContextualNavigationAction?,
    account: Account?,
    onOpenAccounts: () -> Unit,
    onDestinationSelected: (Destination) -> Unit,
    caretSlot: CompactCaretSlot = CompactCaretSlot.Inline,
) {
    val pill = @Composable {
        CompactNavigationPill(
            destination, searchPanel, notificationsPanel, account, onOpenAccounts, onDestinationSelected,
        )
    }
    val actionButton = @Composable { action?.let { ContextualNavigationActionButton(it) } }
    Row(
        Modifier.fillMaxWidth().height(CompactNavigationHeight),
        horizontalArrangement = Arrangement.spacedBy(CompactNavigationControlSpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (caretSlot is CompactCaretSlot.Inline) {
            pill()
            actionButton()
        } else {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                (caretSlot as? CompactCaretSlot.Shown)?.let { ContextualTabCaretButton(it.state) }
            }
            pill()
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { actionButton() }
        }
    }
}
