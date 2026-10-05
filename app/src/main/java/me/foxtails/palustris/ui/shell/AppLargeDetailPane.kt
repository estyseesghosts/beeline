package me.foxtails.palustris.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.posts.SinglePostScreen
import me.foxtails.palustris.ui.thread.PostThreadUiState

@Composable
internal fun AppLargeDetailPane(
    selected: OwnedPost?,
    origin: LargePostOrigin,
    availableActions: Set<PostAction>,
    threadState: PostThreadUiState?,
    callbacks: ShellDetailCallbacks,
    modifier: Modifier,
    leftObstructionClearance: Dp = 0.dp,
    rightObstructionClearance: Dp = 0.dp,
) {
    if (selected == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                AppIcons.HoneyHome,
                androidx.compose.ui.res.stringResource(R.string.post_select_title),
                androidx.compose.ui.res.stringResource(R.string.post_select_subtitle),
                modifier = Modifier.absolutePadding(
                    left = leftObstructionClearance,
                    right = rightObstructionClearance,
                ),
            )
        }
        return
    }

    val threadEnabled = threadState != null && origin.supportsComments()
    SinglePostScreen(
        ownedPost = selected,
        presentation = origin.singlePostPresentation(),
         onClose = callbacks.onClose,
        availableActions = availableActions,
         onReact = callbacks.onReact,
         onReply = callbacks.onReply,
         onReshare = callbacks.onReshare,
         onBookmark = callbacks.onBookmark,
         onReaction = callbacks.onReaction,
         onOpenProfile = callbacks.onOpenProfile,
         onSearchHashtag = callbacks.onSearchHashtag,
         onOpenHashtagBubble = callbacks.onOpenHashtagBubble,
          onOpenReactionBubble = { post, bounds -> callbacks.onOpenReactionBubble(post, bounds, callbacks.onReaction) },
          onOpenReactionPicker = callbacks.onOpenReactionPicker,
         onOpenMedia = callbacks.onOpenMedia,
         onOpenUsername = callbacks.onOpenUsername,
        embedded = true,
        threadState = threadState.takeIf { threadEnabled },
         onThreadRefresh = callbacks.onThreadRefresh,
         onThreadContinue = callbacks.onThreadContinue,
         quoteEnabled = callbacks.quoteEnabled,
         leftObstructionClearance = leftObstructionClearance,
         rightObstructionClearance = rightObstructionClearance,
         onQuote = callbacks.onQuote,
        modifier = modifier,
    )
}
