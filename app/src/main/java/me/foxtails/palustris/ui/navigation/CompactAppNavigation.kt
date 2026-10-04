@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.foxtails.palustris.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import me.foxtails.palustris.ui.layout.CompactNavigationHeight
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.profile.ProfileUiState

internal data class ContextualBottomAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

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
): ContextualBottomAction? = when (destination) {
    Destination.Home -> ContextualBottomAction(AppIcons.Compose, stringResource(R.string.nav_compose), true, onCompose)
    Destination.Search -> if (searchPanel == SearchPanel.Search) {
        ContextualBottomAction(AppIcons.PhotoGrid, stringResource(R.string.nav_photo_grid), true, onSearchToggle)
    } else {
        ContextualBottomAction(AppIcons.SearchBeeline, stringResource(R.string.nav_search), true, onSearchToggle)
    }
    Destination.Notifications -> if (notificationsPanel == NotificationsPanel.Notifications) {
        ContextualBottomAction(AppIcons.DirectMessage, stringResource(R.string.nav_direct_messages), true, onNotificationsToggle)
    } else {
        ContextualBottomAction(AppIcons.Mail, stringResource(R.string.nav_notifications), true, onNotificationsToggle)
    }
    Destination.Profile -> when {
        profileTarget?.movedTo != null -> null
        profileTarget?.id == authenticatedAccountId && authenticatedAccountId != null ->
            ContextualBottomAction(AppIcons.PersonEdit, stringResource(R.string.profile_edit), profileState.editableSupported, onEditProfile)
        profileState.relationshipSupported == true && profileState.relationship != null -> {
            val relationship = profileState.relationship
            val following = relationship.following || relationship.requested
            ContextualBottomAction(
                icon = if (following) AppIcons.Unfollow else AppIcons.Follow,
                contentDescription = when {
                    relationship.following -> stringResource(R.string.profile_unfollow_action)
                    relationship.requested -> stringResource(R.string.profile_cancel_request_action)
                    else -> stringResource(R.string.profile_follow_action)
                },
                enabled = !profileState.relationshipMutation,
                onClick = if (following) onUnfollowProfile else onFollowProfile,
            )
        }
        else -> null
    }
}

@Composable
internal fun CompactContextualNavigationBar(
    destination: Destination,
    searchPanel: SearchPanel,
    notificationsPanel: NotificationsPanel,
    action: ContextualBottomAction?,
    account: Account?,
    onOpenAccounts: () -> Unit,
    onDestinationSelected: (Destination) -> Unit,
) {
    val scheme = LocalPalustrisMotionScheme.current
    Row(
        Modifier.fillMaxWidth().height(CompactNavigationHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        NavigationCapsule(
            selectedIndex = destination.ordinal,
            itemCount = Destination.entries.size,
            orientation = Orientation.Horizontal,
            modifier = Modifier.width(212.dp).height(56.dp),
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
            FilledIconButton(
                onClick = contextualAction.onClick,
                enabled = contextualAction.enabled,
                 modifier = Modifier.size(56.dp).semantics { contentDescription = contextualAction.contentDescription },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                AnimatedContent(
                    targetState = contextualAction,
                    contentKey = { it.contentDescription },
                    transitionSpec = {
                        if (scheme.reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                        else (fadeIn(scheme.fastFadeIn) + scaleIn(initialScale = 0.86f, animationSpec = scheme.expressive)) togetherWith
                            (fadeOut(scheme.fastFadeOut) + scaleOut(targetScale = 0.86f, animationSpec = scheme.expressive))
                    },
                    modifier = Modifier.size(24.dp),
                    label = "contextualAction",
                ) { actionState -> Icon(actionState.icon, null) }
            }
        }
    }
}
