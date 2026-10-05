package me.foxtails.palustris.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListState
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.large.LargeBottomDock
import me.foxtails.palustris.ui.large.LargeBottomDockClearance
import me.foxtails.palustris.ui.media.MediaOpenRequest

@Composable
internal fun ProfileLargePresentation(
    account: Account,
    state: ProfileUiState,
    isSelf: Boolean,
    showSummary: Boolean,
    listState: LazyListState?,
    endContentClearance: Dp,
    rightObstructionClearance: Dp = 0.dp,
    leftObstructionClearance: Dp = 0.dp,
    bottomObstructionClearance: Dp = 0.dp,
    onCategorySelected: (ProfileCategory) -> Unit,
    onOpenDrafts: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onMessage: () -> Unit,
    onOpenProfile: (Account) -> Unit,
    onOpenProfileImage: (String) -> Unit,
    details: @Composable () -> Unit,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenReactionBubble: ((OwnedPost, androidx.compose.ui.geometry.Rect) -> Unit)?,
    onOpenReactionPicker: (OwnedPost) -> Unit,
    onOpenHashtagBubble: ((OwnedPost, List<String>, androidx.compose.ui.geometry.Rect) -> Unit)?,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    onOpenPost: (OwnedPost) -> Unit,
    onSearchHashtag: (String) -> Unit,
    onOpenUrl: ((String) -> Unit)?,
    onOpenUsername: ((String) -> Unit)?,
    quoteEnabled: Boolean = false,
    onQuote: (OwnedPost) -> Unit = {},
    onEditProfile: (() -> Unit)?,
) {
    // The two-column Row mirrors in RTL, so the column that reaches a physical edge changes with
    // the layout direction. Each column takes only the clearance for the edges it reaches, so the
    // other column keeps its usable width. LTR puts the summary at the physical left and the
    // timeline at the physical right. RTL swaps those two.
    val timelineAtPhysicalRight = LocalLayoutDirection.current != LayoutDirection.Rtl
    val timelineRightClearance = if (timelineAtPhysicalRight) rightObstructionClearance else 0.dp
    val summaryRightClearance = if (timelineAtPhysicalRight) 0.dp else rightObstructionClearance
    val timelineLeftClearance = if (timelineAtPhysicalRight) 0.dp else leftObstructionClearance
    val summaryLeftClearance = if (timelineAtPhysicalRight) leftObstructionClearance else 0.dp

    @Composable
    fun timeline(rightClearance: Dp, leftClearance: Dp) {
        ProfileTimelineList(
            account = account,
            state = state,
            compactLayout = false,
            endContentClearance = endContentClearance,
            rightObstructionClearance = rightClearance,
            leftObstructionClearance = leftClearance,
            isSelf = isSelf,
            onCategorySelected = onCategorySelected,
            onOpenDrafts = onOpenDrafts,
            onOpenBookmarks = onOpenBookmarks,
            onRefresh = onRefresh,
            onLoadMore = onLoadMore,
            onOpenProfile = onOpenProfile,
            onSearchHashtag = onSearchHashtag,
            availableActions = availableActions,
            onReact = onReact,
            onReply = onReply,
            onReshare = onReshare,
            onBookmark = onBookmark,
            onReaction = onReaction,
            onOpenReactionBubble = onOpenReactionBubble,
            onOpenReactionPicker = onOpenReactionPicker,
            onOpenHashtagBubble = onOpenHashtagBubble,
            onOpenMedia = onOpenMedia,
            onOpenPost = onOpenPost,
            onOpenUrl = onOpenUrl,
            onOpenUsername = onOpenUsername,
            quoteEnabled = quoteEnabled,
            onQuote = onQuote,
            header = {},
            details = details,
            listState = listState,
            showHeader = false,
            showInlineCategories = false,
            largeLayout = true,
        )
    }

    // The dock keeps its bottom-start placement and its own LargeBottomDock spacing. Clearance only
    // moves it clear of floating obstruction; it adds no dock measurement and no chip travel.
    @Composable
    fun dock(modifier: Modifier = Modifier) {
        LargeBottomDock(
            content = {
                ProfileCategoryChips(
                    selected = state.selectedTab,
                    isSelf = isSelf,
                    likedAvailable = state.likedAvailable,
                    featuredAvailable = state.pinnedPosts.size > 1,
                    onCategorySelected = onCategorySelected,
                    onOpenDrafts = onOpenDrafts,
                    onOpenBookmarks = onOpenBookmarks,
                    onEditProfile = onEditProfile ?: {},
                    includeShowMore = false,
                    includeEditProfile = true,
                )
            },
            modifier = modifier,
        )
    }

    if (showSummary) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                        .absolutePadding(left = summaryLeftClearance, right = summaryRightClearance)
                        .verticalScroll(rememberScrollState()),
                ) {
                    ProfileHeader(
                        account = account,
                        state = state,
                        isSelf = isSelf,
                        onRefresh = onRefresh,
                        onFollow = onFollow,
                        onUnfollow = onUnfollow,
                        onMessage = onMessage,
                        onOpenProfile = onOpenProfile,
                        onOpenProfileImage = onOpenProfileImage,
                        onEditProfile = onEditProfile,
                        largeSummary = showSummary,
                    )
                    details()
                    // The summary column scrolls under the overlaid category dock, so its
                    // scroll range ends past the dock height. The timeline list owns the same
                    // clearance through its own end spacing.
                    Spacer(Modifier.height(endContentClearance))
                }
                Box(Modifier.weight(0.58f).fillMaxHeight()) {
                    timeline(timelineRightClearance, timelineLeftClearance)
                }
            }
            dock(
                Modifier.align(Alignment.BottomStart).absolutePadding(
                    right = rightObstructionClearance,
                    left = leftObstructionClearance,
                    bottom = bottomObstructionClearance,
                ),
            )
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            // Without the summary the timeline fills the pane, so it always reaches the edge.
            timeline(rightObstructionClearance, leftObstructionClearance)
            dock(
                Modifier.align(Alignment.BottomStart).absolutePadding(
                    right = rightObstructionClearance,
                    left = leftObstructionClearance,
                    bottom = bottomObstructionClearance,
                ),
            )
        }
    }
}
