@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package me.foxtails.palustris.ui.posts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.FavouriteArtworkStyle
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostInteractionCounts
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.components.PillAction
import me.foxtails.palustris.ui.emoji.CustomEmojiImage
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.PopEffect
import me.foxtails.palustris.ui.motion.rememberSelectedColor
import me.foxtails.palustris.ui.motion.springPress
import me.foxtails.palustris.ui.posts.reactionPickerGesture

private val PostInteractionRowHeight = 48.dp
private val PostInteractionIconSize = 24.dp
private val CircleShapeForReaction = RoundedCornerShape(50)
private val ReactionChipHeight = 32.dp
private val ReactionEmojiSlotSize = 20.dp
private val ReactionChipMinWidth = 56.dp

internal data class PostInteractionPresentation(
    val showInteractionSummary: Boolean,
    val showReactionNumbers: Boolean,
) {
    companion object {
        val Feed = PostInteractionPresentation(
            showInteractionSummary = false,
            showReactionNumbers = false,
        )
        val Detailed = PostInteractionPresentation(
            showInteractionSummary = true,
            showReactionNumbers = true,
        )
    }
}

@Composable
internal fun ReactionRow(
    reactions: List<Reaction>,
    ownedPost: OwnedPost,
    enabled: Boolean,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    showReactionNumbers: Boolean = false,
) {
    val scheme = LocalPalustrisMotionScheme.current
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions.filter { it.count > 0 }.forEach { reaction ->
            val interactionSource = remember(reaction.emoji) { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val countText = pluralStringResource(R.plurals.reaction_count, reaction.count, reaction.count)
            val reactionDescription = stringResource(R.string.post_reaction_accessibility, reaction.emoji, countText)
            val selectedStateDescription = if (reaction.selected) {
                if (enabled) stringResource(R.string.emoji_reaction_remove, reaction.emoji)
                else stringResource(R.string.emoji_reaction_selected, reaction.emoji)
            } else null
            val chipColor = rememberSelectedColor(
                selected = reaction.selected,
                selectedColor = MaterialTheme.colorScheme.secondaryContainer,
                unselectedColor = MaterialTheme.colorScheme.primaryContainer,
            )
            Surface(
                modifier = Modifier
                    .heightIn(min = ReactionChipHeight)
                    .widthIn(min = ReactionChipMinWidth)
                    .springPress(interactionSource, pressedScale = scheme.compactPressedScale)
                    .combinedClickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = { onReaction(ownedPost, EmojiChoice(reaction.emoji, reaction.emoji, reaction.emojiMetadata)) },
                    )
                    .testTag("reaction_chip_${reaction.emoji}")
                    .semantics {
                        contentDescription = reactionDescription
                        role = Role.Button
                        this.selected = reaction.selected
                        selectedStateDescription?.let { stateDescription = it }
                    },
                shape = CircleShapeForReaction,
                color = chipColor,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(Modifier.fillMaxHeight().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(ReactionEmojiSlotSize).testTag("reaction_emoji_slot_${reaction.emoji}"), contentAlignment = Alignment.Center) {
                        CustomEmojiImage(
                            emoji = reaction.emojiMetadata,
                            fallbackText = reaction.emoji,
                            modifier = Modifier.fillMaxSize(),
                            textStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                        )
                    }
                    if (showReactionNumbers && reaction.count > 1) {
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.widthIn(min = 16.dp).testTag("reaction_count_${reaction.emoji}"), contentAlignment = Alignment.Center) {
                            AnimatedContent(
                                targetState = reaction.count,
                                transitionSpec = {
                                    if (scheme.reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                                    else (fadeIn(scheme.fastFadeIn) + scaleIn(initialScale = 0.86f, animationSpec = scheme.expressive)) togetherWith
                                        (fadeOut(scheme.fastFadeOut) + scaleOut(targetScale = 0.86f, animationSpec = scheme.expressive))
                                },
                                label = "reactionCount",
                            ) { count -> Text(count.toString(), style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InteractionSummaryRow(counts: PostInteractionCounts) {
    val metrics = buildList {
        counts.favouriteCount?.let { add("favourites" to (R.plurals.post_favourite_count to it)) }
        counts.reactionCount?.let { add("reactions" to (R.plurals.post_reaction_total to it)) }
        counts.repostCount?.let { add("reposts" to (R.plurals.post_repost_count to it)) }
        counts.quoteRepostCount?.let { add("quote_reposts" to (R.plurals.post_quote_repost_count to it)) }
        counts.replyCount?.let { add("replies" to (R.plurals.post_reply_count to it)) }
    }
    if (metrics.isEmpty()) return
    val summaryDescription = stringResource(R.string.post_interaction_summary)
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("interaction_summary")
            .semantics { contentDescription = summaryDescription },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        metrics.forEach { (name, resourceAndCount) ->
            val (resource, count) = resourceAndCount
            Text(
                pluralStringResource(resource, count, count),
                Modifier.testTag("interaction_metric_$name"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
internal fun InteractionRow(
    ownedPost: OwnedPost,
    availableActions: Set<PostAction>,
    favouriteArtworkStyle: FavouriteArtworkStyle = FavouriteArtworkStyle.Heart,
    onReply: (OwnedPost) -> Unit,
    onReact: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    quoteEnabled: Boolean,
    onQuote: (OwnedPost) -> Unit,
    onOpenReactionBubble: (OwnedPost, Rect) -> Unit,
    onOpenReactionPicker: (OwnedPost) -> Unit = {},
    pendingRepost: PendingRepostConfirmation? = null,
    onRepostConfirmationRequest: (OwnedPost, Rect) -> Unit = { _, _ -> },
    onRepostConfirmationDismiss: () -> Unit = {},
    onRepostConfirmationConfirm: (OwnedPost) -> Unit = {},
    onShare: (OwnedPost, Rect) -> Unit,
) {
    val actionDescription = stringResource(R.string.post_actions)
    Row(Modifier.fillMaxWidth().heightIn(min = PostInteractionRowHeight).padding(horizontal = 8.dp).semantics { contentDescription = actionDescription }, verticalAlignment = Alignment.CenterVertically) {
        InteractionButton(Modifier.weight(1f), AppIcons.Comment, stringResource(R.string.post_action_reply), PostAction.Reply in availableActions, onClick = { onReply(ownedPost) })
        InteractionButton(
            Modifier.weight(1f), AppIcons.RepostBeeline,
            stringResource(if (ownedPost.post.reposted) R.string.post_action_undo_repost else R.string.post_action_repost),
            PostAction.Reshare in availableActions, ownedPost.post.reposted, onClick = {},
            onClickWithBounds = { bounds -> onRepostConfirmationRequest(ownedPost, bounds) },
            onLongClick = if (quoteEnabled) ({ onQuote(ownedPost) }) else null,
            customActionLabel = if (quoteEnabled) stringResource(R.string.post_action_quote) else null,
            onCustomAction = if (quoteEnabled) ({ onQuote(ownedPost); true }) else null,
        )
        Box(Modifier.weight(1f)) {
            val favouriteEnabled = PostAction.Favorite in availableActions
            val reactionEnabled = PostAction.React in availableActions
            fun openReactionBubble(bounds: Rect) = onOpenReactionBubble(ownedPost, bounds)
            InteractionButton(
                Modifier.fillMaxWidth(), favouriteIconFor(ownedPost, favouriteArtworkStyle),
                stringResource(if (ownedPost.post.favourited) R.string.post_action_unfavorite else R.string.post_action_favorite),
                favouriteEnabled || reactionEnabled, ownedPost.post.hasVisibleInteractionSelection(),
                onClick = { if (favouriteEnabled) onReact(ownedPost) },
                onClickWithBounds = if (reactionEnabled) ({ bounds -> if (!favouriteEnabled) openReactionBubble(bounds) }) else null,
                reactionGestureKey = "${ownedPost.fetchedBy}:${ownedPost.post.id}:${ownedPost.sessionRevision}",
                reactionLongPressEnabled = true,
                onReactionCompact = { bounds -> openReactionBubble(bounds) },
                onReactionExpanded = { bounds -> onOpenReactionBubble(ownedPost, bounds); onOpenReactionPicker(ownedPost) },
                onLongClick = if (reactionEnabled) ::openReactionBubble else null,
            )
        }
        InteractionButton(Modifier.weight(1f), if (ownedPost.post.saved) AppIcons.FilledBookmark else AppIcons.HollowBookmark, stringResource(if (ownedPost.post.saved) R.string.post_action_remove_bookmark else R.string.post_action_bookmark), PostAction.Bookmark in availableActions, ownedPost.post.saved, onClick = { onBookmark(ownedPost) })
        InteractionButton(Modifier.weight(1f), AppIcons.ShareBeeline, stringResource(R.string.post_action_share), onClick = {}, onClickWithBounds = { bounds -> onShare(ownedPost, bounds) })
    }
    if (pendingRepost?.fetchedBy == ownedPost.fetchedBy && pendingRepost.postId == ownedPost.post.id) {
        androidx.compose.ui.window.Popup(
            popupPositionProvider = WindowAnchorPositionProvider(pendingRepost.anchorBounds, BubblePlacement.Below),
            onDismissRequest = onRepostConfirmationDismiss,
            properties = androidx.compose.ui.window.PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
        ) {
            PillAction(
                label = stringResource(if (pendingRepost.selected) R.string.post_action_undo_repost_confirmation else R.string.post_action_repost_confirmation),
                onClick = { onRepostConfirmationConfirm(ownedPost) },
                modifier = Modifier.testTag("repost_confirmation"),
            )
        }
    }
}

@Composable
private fun InteractionButton(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    isSelected: Boolean = false,
    selectedIndicator: String? = null,
    onClick: () -> Unit,
    onClickWithBounds: ((Rect) -> Unit)? = null,
    onLongClick: ((Rect) -> Unit)? = null,
    reactionGestureKey: Any? = null,
    reactionLongPressEnabled: Boolean = false,
    onReactionCompact: ((Rect) -> Unit)? = null,
    onReactionExpanded: ((Rect) -> Unit)? = null,
    customActionLabel: String? = null,
    onCustomAction: (() -> Boolean)? = null,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var popTrigger by remember { mutableIntStateOf(0) }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val scheme = LocalPalustrisMotionScheme.current
    val density = LocalDensity.current
    val selectedDescription = stringResource(if (isSelected) R.string.post_action_selected else R.string.post_action_not_selected)
    val interactionSelectedDescription = stringResource(R.string.post_action_interaction_selected)
    Box(
        modifier = modifier.heightIn(min = PostInteractionRowHeight).onGloballyPositioned { bounds = it.boundsInWindow() }.then(
            if (reactionLongPressEnabled && onReactionCompact != null && onReactionExpanded != null) {
                Modifier.reactionPickerGesture(enabled, reactionGestureKey ?: Unit, with(density) { 36.dp.toPx() }, { onReactionCompact(bounds) }, { onReactionExpanded(bounds) })
            } else Modifier,
        ).springPress(interactionSource, enabled, scheme.compactPressedScale).combinedClickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = { popTrigger++; onClick(); onClickWithBounds?.invoke(bounds) },
            onLongClick = onLongClick?.let { callback -> { popTrigger++; callback(bounds) } },
        ).semantics {
            contentDescription = label
            role = Role.Button
            this.selected = isSelected
            stateDescription = selectedDescription
            if (customActionLabel != null && onCustomAction != null) customActions = listOf(CustomAccessibilityAction(customActionLabel, onCustomAction))
        },
        contentAlignment = Alignment.Center,
    ) {
        PopEffect(popTrigger) {
            Icon(icon, label, Modifier.size(PostInteractionIconSize), tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f))
            selectedIndicator?.let { indicator ->
                Text(
                    indicator,
                    Modifier.align(Alignment.BottomEnd).semantics { contentDescription = interactionSelectedDescription },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
