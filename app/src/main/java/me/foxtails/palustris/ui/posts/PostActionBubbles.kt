@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.foxtails.palustris.ui.posts

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.abs
import kotlinx.coroutines.delay
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.emoji.EmojiCatalogState
import me.foxtails.palustris.ui.emoji.EmojiChoiceGrid
import me.foxtails.palustris.ui.emoji.EmojiPreferenceActions
import me.foxtails.palustris.ui.emoji.EmojiRecents
import me.foxtails.palustris.ui.emoji.pinState
import me.foxtails.palustris.ui.emoji.rememberEmojiRecents
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme

private const val BUBBLE_DISMISS_DURATION_MILLIS = 150L
private const val REACTION_FLICK_THRESHOLD = 36f

enum class ReactionBubbleMode { Compact, Expanded }

sealed interface PostActionBubbleTarget {
    val postId: EntityId
    val anchorBounds: Rect

    data class HashtagList(
        override val postId: EntityId,
        val hashtags: List<String>,
        override val anchorBounds: Rect,
    ) : PostActionBubbleTarget

    data class Reaction(
        val ownedPost: OwnedPost,
        override val anchorBounds: Rect,
        val mode: ReactionBubbleMode = ReactionBubbleMode.Compact,
    ) : PostActionBubbleTarget {
        override val postId: EntityId get() = ownedPost.post.id
    }
}

/** A popup host for post actions. It never participates in page measurement. */
@Composable
fun PostActionBubbleHost(
    target: PostActionBubbleTarget?,
    emojiCatalog: EmojiCatalogState,
    emojiCapabilities: EmojiCapabilities,
    onLoadEmojiCatalog: () -> Unit = {},
    onRetryEmojiCatalog: () -> Unit = {},
    onToggleEmojiGroupCollapsed: (String) -> Unit = {},
    onToggleEmojiGroupPinned: (String) -> Unit = {},
    onTogglePinnedEmoji: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onHashtagSelected: (String) -> Unit,
    onReactionSelected: (OwnedPost, EmojiChoice) -> Unit,
    onReactionModeChanged: (PostActionBubbleTarget.Reaction) -> Unit = {},
    hashtagBottomClearance: Dp = 0.dp,
) {
    var renderedTarget by remember { mutableStateOf<PostActionBubbleTarget?>(null) }
    var visible by remember { mutableStateOf(false) }
    var localReactionMode by remember { mutableStateOf(ReactionBubbleMode.Compact) }
    var reactionOpenedAtMillis by remember { mutableLongStateOf(0L) }
    var hashtagOpeningKey by remember { mutableLongStateOf(0L) }
    val dismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(target) {
        val hasHashtags = target !is PostActionBubbleTarget.HashtagList || target.hashtags.isNotEmpty()
        if (target != null && hasHashtags) {
            renderedTarget = target
            hashtagOpeningKey++
            localReactionMode = (target as? PostActionBubbleTarget.Reaction)?.mode ?: ReactionBubbleMode.Compact
            reactionOpenedAtMillis = SystemClock.uptimeMillis()
            visible = true
        } else if (renderedTarget != null) {
            visible = false
            delay(BUBBLE_DISMISS_DURATION_MILLIS)
            renderedTarget = null
        }
    }

    val reactionTarget = renderedTarget as? PostActionBubbleTarget.Reaction
    if (reactionTarget != null && emojiCapabilities.reactionMutation != CapabilityStatus.Supported) {
        LaunchedEffect(reactionTarget) { dismiss() }
        return
    }
    val current = renderedTarget ?: return
    LaunchedEffect(current::class, current.postId) {
        if (current is PostActionBubbleTarget.Reaction) onLoadEmojiCatalog()
    }
    // One recents holder and one action set serve the anchored picker and the sheet.
    val recents = rememberEmojiRecents()
    val actions = EmojiPreferenceActions(onToggleEmojiGroupCollapsed, onToggleEmojiGroupPinned, onTogglePinnedEmoji)
    val selectReaction = { choice: EmojiChoice ->
        (current as? PostActionBubbleTarget.Reaction)?.let { onReactionSelected(it.ownedPost, choice) }
        dismiss()
    }
    if (current is PostActionBubbleTarget.Reaction && reactionSurface(current, localReactionMode) == ReactionPickerSurface.Sheet) {
        ReactionPickerSheet(current.ownedPost.post, emojiCatalog, recents, actions, selectReaction, dismiss)
        return
    }
    AnimatedBubblePopup(
        anchorBounds = current.anchorBounds,
        placement = if (current is PostActionBubbleTarget.Reaction) BubblePlacement.Above else BubblePlacement.Below,
        visible = visible,
        onDismiss = dismiss,
    ) {
        when (current) {
            is PostActionBubbleTarget.HashtagList -> HashtagBubble(
                hashtags = current.hashtags,
                maxHeight = hashtagBubbleMaxHeight(current.anchorBounds, hashtagBottomClearance),
                openingKey = hashtagOpeningKey,
                onSelected = { hashtag ->
                    onHashtagSelected(hashtag)
                    dismiss()
                },
            )
            is PostActionBubbleTarget.Reaction -> ReactionBubble(
                target = current.copy(mode = localReactionMode),
                catalog = emojiCatalog,
                recents = recents,
                actions = actions,
                openedAtMillis = reactionOpenedAtMillis,
                onSelected = selectReaction,
                onExpanded = {
                    localReactionMode = ReactionBubbleMode.Expanded
                    onReactionModeChanged(current.copy(mode = ReactionBubbleMode.Expanded))
                },
            )
        }
    }
}

/** Chooses the surface for one reaction target from the window, density, and font scale. */
@Composable
private fun reactionSurface(target: PostActionBubbleTarget.Reaction, mode: ReactionBubbleMode): ReactionPickerSurface {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return reactionPickerSurface(
        anchor = target.anchorBounds,
        windowWidthPx = size.width,
        windowHeightPx = size.height,
        density = density.density,
        fontScale = density.fontScale,
        expanded = mode == ReactionBubbleMode.Expanded,
    )
}

/** An anchored popup with the shared bubble motion. Reduced motion shows and hides it at once. */
@Composable
private fun AnimatedBubblePopup(
    anchorBounds: Rect,
    placement: BubblePlacement,
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val motionScheme = LocalPalustrisMotionScheme.current
    Popup(
        popupPositionProvider = WindowAnchorPositionProvider(anchorBounds, placement),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = if (motionScheme.reducedMotion) {
                EnterTransition.None
            } else {
                fadeIn(motionScheme.fastFadeIn) +
                    scaleIn(initialScale = motionScheme.floatingEnterScale, animationSpec = motionScheme.expressive)
            },
            exit = if (motionScheme.reducedMotion) {
                ExitTransition.None
            } else {
                fadeOut(motionScheme.fastFadeOut) +
                    scaleOut(targetScale = motionScheme.floatingEnterScale, animationSpec = motionScheme.expressive)
            },
        ) {
            content()
        }
    }
}

@Composable
private fun ReactionBubble(
    target: PostActionBubbleTarget.Reaction,
    catalog: EmojiCatalogState,
    recents: EmojiRecents,
    actions: EmojiPreferenceActions,
    openedAtMillis: Long,
    onSelected: (EmojiChoice) -> Unit,
    onExpanded: () -> Unit,
) {
    val expanded = target.mode == ReactionBubbleMode.Expanded
    val post = target.ownedPost.post
    val selectedIdentities = remember(post) { post.selectedReactionIdentities() }
    val bubbleDescription = stringResource(
        if (expanded) R.string.post_action_reactions_expanded else R.string.post_action_reactions_collapsed,
    )
    val bubbleStateDescription = stringResource(
        if (expanded) R.string.post_action_bubble_expanded else R.string.post_action_bubble_collapsed,
    )
    val maxHeight = expandedMaxHeight(target.anchorBounds)
    Surface(
        modifier = Modifier
            .widthIn(min = 184.dp, max = 320.dp)
            .semantics {
                contentDescription = bubbleDescription
                stateDescription = bubbleStateDescription
            }
            .reactionBubbleFlick(target.postId, target.mode, openedAtMillis, enabled = !expanded, onExpanded = onExpanded)
            .testTag(if (expanded) "reaction_bubble_expanded" else "reaction_bubble_compact"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        EmojiChoiceGrid(
            catalogItems = catalog.items,
            additionalChoices = post.reactionChoices(),
            selectedIdentities = selectedIdentities,
            preferences = catalog.preferences,
            compact = !expanded,
            pins = catalog.pinState(),
            recents = recents,
            modifier = Modifier
                .then(if (expanded) Modifier.heightIn(max = maxHeight) else Modifier)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            testTag = "reaction_bubble_grid",
            actions = actions,
            onEmojiSelected = onSelected,
        )
    }
}

/** The tallest the expanded picker may be: the roomier side of the anchor, capped. */
@Composable
private fun expandedMaxHeight(anchorBounds: Rect): Dp {
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val available = availableHeightDp(anchorBounds, windowHeight, density.density)
    return available.coerceIn(EXPANDED_PICKER_MIN_HEIGHT_DP, EXPANDED_PICKER_MAX_HEIGHT_DP).dp
}

/**
 * Expands the compact pop-out when a pointer sequence that began after the pop-out opened drags up
 * or down past [REACTION_FLICK_THRESHOLD]. The expanding sequence is consumed to its end.
 */
private fun Modifier.reactionBubbleFlick(
    postId: EntityId,
    mode: ReactionBubbleMode,
    openedAtMillis: Long,
    enabled: Boolean,
    onExpanded: () -> Unit,
): Modifier = pointerInput(postId, mode, openedAtMillis) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.uptimeMillis <= openedAtMillis) return@awaitEachGesture
        var expandedFromGesture = false
        while (!expandedFromGesture) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) break
            val deltaX = change.position.x - down.position.x
            val deltaY = change.position.y - down.position.y
            if (abs(deltaY) >= REACTION_FLICK_THRESHOLD && abs(deltaY) > abs(deltaX)) {
                change.consume()
                expandedFromGesture = true
                onExpanded()
            }
        }
        while (expandedFromGesture) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) break
            change.consume()
        }
    }
}

@Composable
private fun hashtagBubbleMaxHeight(anchorBounds: Rect, bottomClearance: Dp): Dp {
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val anchorTop = with(density) { anchorBounds.top.toDp() }
    val anchorBottom = with(density) { anchorBounds.bottom.toDp() }
    val below = windowHeight - bottomClearance - anchorBottom - 8.dp
    val above = anchorTop - 8.dp
    return maxOf(1.dp, maxOf(below, above))
}
