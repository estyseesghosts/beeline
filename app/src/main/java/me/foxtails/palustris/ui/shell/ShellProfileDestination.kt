package me.foxtails.palustris.ui.shell

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.ui.large.CompactWideTabCaretHost
import me.foxtails.palustris.ui.navigation.ShellNavigator
import me.foxtails.palustris.ui.profile.ProfileScreen

/**
 * Profile destination wiring for the application shell.
 *
 * Adapts the shell inputs to [ProfileScreen]. It owns no Profile state: the target, categories,
 * relationship, editor, and paging state stay with the Profile owner. The shell keeps the
 * presentation selection and the selected-post clearing. Compact-wide keeps the mobile
 * one-column hierarchy; expanded tablet keeps the large presentation.
 *
 * `leftObstructionClearance` and `rightObstructionClearance` are physical and never reverse with
 * the layout direction. Compact layout ignores both.
 */
@Composable
internal fun ShellProfileDestination(
    navigator: ShellNavigator,
    overlay: ShellOverlayPresenter,
    profile: ProfileContract,
    postCallbacks: DestinationPostCallbacks,
    navigationCallbacks: DestinationNavigationCallbacks,
    account: Account?,
    displayedProfile: Account?,
    largePresentation: Boolean,
    compactWidePresentation: Boolean,
    rightObstructionClearance: Dp,
    leftObstructionClearance: Dp,
    bottomObstructionClearance: Dp,
    profileListState: LazyListState,
    useCompactWideCaret: Boolean,
    tabCaretHost: CompactWideTabCaretHost?,
) {
    ProfileScreen(
        account = displayedProfile,
        profileState = profile.state,
        compactLayout = !largePresentation,
        largeLayout = largePresentation && !compactWidePresentation,
        compactWidePresentation = compactWidePresentation,
        useCompactWideCaret = useCompactWideCaret,
        tabCaretHost = tabCaretHost,
        largeShowSummary = navigator.singlePost == null,
        listState = profileListState,
        rightObstructionClearance = rightObstructionClearance,
        leftObstructionClearance = leftObstructionClearance,
        bottomObstructionClearance = bottomObstructionClearance,
        compactNavigationVisible = navigator.navigationVisible,
        authenticatedAccountId = account?.id,
        onProfileShown = profile.actions::open,
        onCategorySelected = { category ->
            if (largePresentation) navigator.clearSelectedPost()
            profile.actions.selectCategory(category)
        },
        onRefresh = profile.actions::refresh,
        onLoadMore = profile.actions::loadMore,
        onFollow = profile.actions::follow,
        onUnfollow = profile.actions::unfollow,
        onMessage = navigator::openDirectMessage,
        onOpenProfileImage = { url -> overlay.openProfileImage(url, navigator.viewedProfile?.id ?: account?.id) },
        onEditProfile = navigationCallbacks.onEditProfile,
        onOpenDrafts = {
            if (largePresentation) navigator.clearSelectedPost()
            if (account != null && displayedProfile?.id == account.id) navigator.page = LocalPage.Drafts
        },
        onOpenBookmarks = {
            if (largePresentation) navigator.clearSelectedPost()
            if (account != null && displayedProfile?.id == account.id) navigator.page = LocalPage.SavedPosts
        },
        onOpenProfile = navigator::openProfile,
        onSearchHashtag = navigator::openHashtagSearch,
        onOpenHashtagBubble = overlay::openHashtagBubble,
        availableActions = postCallbacks.availableActions,
        onReact = postCallbacks.onReact,
        onReply = postCallbacks.onReply,
        onReshare = postCallbacks.onReshare,
        onBookmark = postCallbacks.onBookmark,
        onReaction = profile.actions::react,
        onOpenReactionBubble = { ownedPost, bounds ->
            overlay.openReactionBubble(ownedPost, bounds, profile.actions::react)
        },
        onOpenReactionPicker = overlay::expandReactionPicker,
        onOpenMedia = overlay::openMedia,
        onOpenPost = { post -> navigationCallbacks.onOpenPost(post, LargePostOrigin.Profile) },
        onOpenUsername = navigator::openAccountSearch,
        quoteEnabled = postCallbacks.quoteEnabled,
        onQuote = postCallbacks.onQuote,
    )
}
