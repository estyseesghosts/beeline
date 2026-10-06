package me.foxtails.palustris.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.components.ChipCaretPresentation
import me.foxtails.palustris.ui.components.DestinationChipRow
import me.foxtails.palustris.ui.large.CompactWideTabCaretHost
import me.foxtails.palustris.ui.large.CompactWideTabCaretRegistration
import me.foxtails.palustris.ui.large.LargeBottomDock
import me.foxtails.palustris.ui.layout.CompactOverlayHorizontalPadding
import me.foxtails.palustris.ui.layout.compactContextualControlsPositioningInsets
import me.foxtails.palustris.ui.media.MediaOpenRequest

/**
 * Compact and compact-wide Profile presentation.
 *
 * Both placements share one mobile one-column [ProfileTimelineList]. [ProfileScreen] owns the
 * category visibility, the category chip list state, and the end clearance, so those survive a
 * placement change. This composable renders the list, the mobile header, the details, and the
 * category chips in their compact floating row or compact-wide bottom dock.
 */
@Composable
internal fun ProfileTimelinePresentation(
    account: Account,
    state: ProfileUiState,
    compactLayout: Boolean,
    compactWidePresentation: Boolean,
    compactNavigationVisible: Boolean,
    isSelf: Boolean,
    endContentClearance: Dp,
    categoryChipListState: LazyListState,
    categoryRowVisible: Boolean,
    onToggleCategoryRow: () -> Unit,
    useCompactWideCaret: Boolean,
    tabCaretHost: CompactWideTabCaretHost?,
    listState: LazyListState?,
    rightObstructionClearance: Dp,
    leftObstructionClearance: Dp,
    bottomObstructionClearance: Dp,
    onCategorySelected: (ProfileCategory) -> Unit,
    onOpenDrafts: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onMessage: (Account) -> Unit,
    onOpenProfile: (Account) -> Unit,
    onOpenProfileImage: (String) -> Unit,
    onSearchHashtag: (String) -> Unit,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)?,
    onOpenReactionPicker: (OwnedPost) -> Unit,
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)?,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    onOpenPost: (OwnedPost) -> Unit,
    onOpenUrl: ((String) -> Unit)?,
    onOpenUsername: ((String) -> Unit)?,
    quoteEnabled: Boolean,
    onQuote: (OwnedPost) -> Unit,
) {
    val compactCategoryEntries = profileCategoryChipEntries(
        isSelf = isSelf,
        likedAvailable = state.likedAvailable,
        featuredAvailable = state.pinnedPosts.size > 1,
        includeShowMore = true,
        includeEditProfile = false,
        selected = state.selectedTab,
        onCategorySelected = onCategorySelected,
        onOpenDrafts = onOpenDrafts,
        onOpenBookmarks = onOpenBookmarks,
    )
    if (useCompactWideCaret) {
        CompactWideTabCaretRegistration(
            host = tabCaretHost,
            expanded = categoryRowVisible,
            onToggle = onToggleCategoryRow,
        )
    }
    val profileCaretPresentation = if (useCompactWideCaret) ChipCaretPresentation.Hidden
    else ChipCaretPresentation.Inline

    Box(Modifier.fillMaxSize()) {
        ProfileTimelineList(
            account = account,
            state = state,
            compactLayout = compactLayout,
            endContentClearance = endContentClearance,
            rightObstructionClearance = rightObstructionClearance,
            leftObstructionClearance = leftObstructionClearance,
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
            header = {
                Box(
                    Modifier.fillMaxWidth().absolutePadding(
                        left = leftObstructionClearance,
                        right = rightObstructionClearance,
                    ),
                ) {
                    ProfileHeader(
                        account = account,
                        state = state,
                        isSelf = isSelf,
                        onRefresh = onRefresh,
                        onFollow = onFollow,
                        onUnfollow = onUnfollow,
                        onMessage = { onMessage(account) },
                        onOpenProfile = onOpenProfile,
                        onOpenProfileImage = onOpenProfileImage,
                    )
                }
            },
            details = { ProfileDetails(account) },
            listState = listState,
            showInlineCategories = !compactLayout && !compactWidePresentation,
            categoryChipListState = categoryChipListState,
            categoryRowVisible = categoryRowVisible,
            onToggleCategoryRow = onToggleCategoryRow,
        )

        if (compactLayout) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = CompactOverlayHorizontalPadding)
                    .windowInsetsPadding(
                        compactContextualControlsPositioningInsets(
                            navigationVisible = compactNavigationVisible,
                            ime = WindowInsets.ime,
                        ),
                    ),
            ) {
                DestinationChipRow(
                    entries = compactCategoryEntries,
                    rowContentDescription = stringResource(R.string.a11y_profile_categories),
                    listState = categoryChipListState,
                    visible = categoryRowVisible,
                    onToggleVisibility = onToggleCategoryRow,
                    rowTestTag = "profile_categories",
                    visibilityToggleTestTag = "profile_categories_visibility",
                )
            }
        } else if (compactWidePresentation) {
            LargeBottomDock(
                modifier = Modifier.align(Alignment.BottomStart)
                    .absolutePadding(
                        left = leftObstructionClearance,
                        right = rightObstructionClearance,
                        bottom = bottomObstructionClearance,
                    )
                    .testTag("profile_categories_dock"),
                content = {
                    DestinationChipRow(
                        entries = compactCategoryEntries,
                        rowContentDescription = stringResource(R.string.a11y_profile_categories),
                        listState = categoryChipListState,
                        visible = categoryRowVisible,
                        onToggleVisibility = onToggleCategoryRow,
                        rowTestTag = "profile_categories",
                        visibilityToggleTestTag = "profile_categories_visibility",
                        caretPresentation = profileCaretPresentation,
                    )
                },
            )
        }
    }
}
