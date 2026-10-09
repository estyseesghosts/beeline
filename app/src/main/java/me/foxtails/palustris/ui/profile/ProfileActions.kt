package me.foxtails.palustris.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.ui.AppIcons

private val ProfileActionHeight = 36.dp
private val ProfileActionPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
private const val BIOGRAPHY_SIZE_FACTOR = 0.9f

/** The biography style: always one step smaller than the body style it derives from, at any text size. */
@Composable
internal fun profileBiographyStyle(largeSummary: Boolean): TextStyle {
    val body = if (largeSummary) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    return body.copy(fontSize = body.fontSize * BIOGRAPHY_SIZE_FACTOR, lineHeight = body.lineHeight * BIOGRAPHY_SIZE_FACTOR)
}

/**
 * The profile actions that sit inline with the avatar: Edit for the signed-in account, or a Message
 * bubble and the Follow control for another account. Following and unfollowing ask through
 * [FollowChoice] first; cancelling a pending request stays direct.
 */
@Composable
internal fun ProfileActions(
    state: ProfileUiState,
    accountId: AccountId,
    isSelf: Boolean,
    canMessage: Boolean,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onMessage: () -> Unit,
    onEditProfile: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (isSelf) {
        if (onEditProfile != null && state.editableSupported) {
            OutlinedButton(
                onClick = onEditProfile,
                modifier = modifier.height(ProfileActionHeight).testTag("profile_edit_action"),
                contentPadding = ProfileActionPadding,
            ) {
                Text(stringResource(R.string.profile_edit))
            }
        }
        return
    }
    if (!canMessage) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val messageLabel = stringResource(R.string.profile_message)
        OutlinedIconButton(
            onClick = onMessage,
            modifier = Modifier.size(ProfileActionHeight).semantics { contentDescription = messageLabel }
                .testTag("profile_message_action"),
            shape = CircleShape,
        ) {
            Icon(AppIcons.DirectMessage, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        val relationship = state.relationship
        if (state.relationshipSupported == true && relationship != null) {
            FollowAction(accountId, relationship, state.relationshipMutation, onFollow, onUnfollow)
        }
    }
}

@Composable
private fun FollowAction(
    accountId: AccountId,
    relationship: ProfileRelationship,
    mutating: Boolean,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
) {
    val following = relationship.following || relationship.requested
    // A relationship change, an account change, or a mutation in flight discards an open bubble.
    val choice = remember(accountId, relationship.following, relationship.requested, mutating) { FollowChoiceState() }
    var buttonBounds by remember { mutableStateOf(Rect.Zero) }
    // The popup shares a Box with the button so it is not a spaced sibling in the actions row.
    Box {
        choice.anchor?.let { anchor ->
            FollowChoice(
                anchor = anchor,
                following = relationship.following,
                onDismiss = choice::dismiss,
                onConfirm = {
                    choice.dismiss()
                    if (relationship.following) onUnfollow() else onFollow()
                },
            )
        }
        Button(
            onClick = {
                if (relationship.requested && !relationship.following) onUnfollow() else choice.request(buttonBounds)
            },
            enabled = !mutating,
            modifier = Modifier
                .height(ProfileActionHeight)
                .onGloballyPositioned { buttonBounds = it.boundsInWindow() }
                .testTag("profile_follow_action"),
            contentPadding = ProfileActionPadding,
        ) {
            when {
                mutating -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                !following -> Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Follow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.profile_follow))
                }
                relationship.following -> Text(stringResource(R.string.profile_following))
                else -> Text(stringResource(R.string.profile_requested))
            }
        }
    }
}
